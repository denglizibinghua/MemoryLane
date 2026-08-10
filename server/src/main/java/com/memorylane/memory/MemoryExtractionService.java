package com.memorylane.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.memorylane.entity.Contact;
import com.memorylane.entity.Memory;
import com.memorylane.entity.Message;
import com.memorylane.repository.MessageRepository;
import com.memorylane.retrieval.SemanticSearch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 记忆提取编排服务 — 整个管线的主控。
 *
 * <p>管线流程（ARCHITECTURE.md §5.1 ⑤⑥⑧）：
 * <ol>
 *   <li>{@link ImportanceClassifier} — 批量分类消息重要性</li>
 *   <li>{@link FactExtractor} — 从重要消息中提取结构化事实</li>
 *   <li>{@link MemoryMerger} — 新事实与已有记忆合并</li>
 * </ol>
 *
 * <p>异步执行（@Async），不阻塞导入 API 的 202 响应。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryExtractionService {

    private final ImportanceClassifier importanceClassifier;
    private final FactExtractor factExtractor;
    private final MemoryMerger memoryMerger;
    private final SemanticSearch semanticSearch;
    private final MessageRepository messageRepository;
    private final ObjectMapper objectMapper;

    /**
     * 异步触发记忆提取。
     *
     * @param contact   关联的联系人
     * @param messageIds 新导入的消息 ID 列表
     */
    @Async
    public void extractAsync(Contact contact, List<Long> messageIds) {
        if (messageIds.isEmpty()) return;
        log.info("Starting memory extraction for contact={}, {} messages", contact.getName(), messageIds.size());

        try {
            // ① 准备消息数据（ID + 内容），过滤无效消息
            List<Message> messages = messageRepository.findAllById(messageIds);
            List<String> labeledMessages = new ArrayList<>();
            for (Message msg : messages) {
                String labeled = msg.getId() + ":" + msg.getSpeaker() + ":" + msg.getContent();
                if (MessageFilter.isValidLabeled(labeled)) {
                    labeledMessages.add(labeled);
                }
            }

            if (labeledMessages.isEmpty()) {
                log.info("All messages filtered out for contact={} ({} total, 0 valid)",
                        contact.getName(), messages.size());
                return;
            }

            log.debug("Filtered: {} valid out of {} total messages", labeledMessages.size(), messages.size());

            // ② 重要性分类
            String importanceJson = importanceClassifier.classify(labeledMessages);
            Map<Long, Integer> importanceMap = parseImportance(importanceJson);
            log.debug("Importance classified: {} important out of {}",
                    importanceMap.size(), messages.size());

            // ③ 筛选 L2/L3 消息
            List<String> importantMessages = new ArrayList<>();
            List<Long> importantMsgIds = new ArrayList<>();
            for (Message msg : messages) {
                Integer importance = importanceMap.get(msg.getId());
                if (importance != null && importance >= 2) {
                    importantMessages.add(msg.getSpeaker() + ":" + msg.getContent());
                    importantMsgIds.add(msg.getId());
                    msg.setImportance(importance);
                } else {
                    msg.setImportance(1); // L1 日常寒暄
                }
            }
            messageRepository.saveAll(messages);

            if (importantMessages.isEmpty()) {
                log.info("No important messages found for contact={}", contact.getName());
                return;
            }

            // ④ 提取结构化事实
            String factsJson = factExtractor.extract(importantMessages);
            List<ExtractedFact> facts = parseFacts(factsJson);
            log.info("Extracted {} facts from {} important messages", facts.size(), importantMessages.size());

            if (facts.isEmpty()) return;

            // ⑤ 合并入库 + 生成 embedding
            Instant now = Instant.now();
            Long[] msgIdArray = importantMsgIds.toArray(Long[]::new);
            List<Memory> created = memoryMerger.merge(facts, contact, msgIdArray, now);

            // ⑥ 为新记忆生成 pgvector embedding
            for (Memory mem : created) {
                try {
                    semanticSearch.generateAndStore(mem);
                } catch (Exception e) {
                    log.warn("Embedding failed for memory {}: {}", mem.getId(), e.getMessage());
                }
            }

            log.info("Memory extraction complete: contact={}, facts={}, embeddings={}",
                    contact.getName(), facts.size(), created.size());

        } catch (Exception e) {
            log.error("Memory extraction failed for contact={}", contact.getName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<Long, Integer> parseImportance(String json) {
        try {
            List<Map<String, Object>> list = objectMapper.readValue(
                    extractJson(json), new TypeReference<>() {});
            java.util.Map<Long, Integer> result = new java.util.HashMap<>();
            for (Map<String, Object> item : list) {
                Long id = toLong(item.get("id"));
                Integer imp = toInt(item.get("importance"));
                if (id != null && imp != null && imp >= 1 && imp <= 3) {
                    result.put(id, imp);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to parse importance JSON: {}", json, e);
            return Map.of();
        }
    }

    private List<ExtractedFact> parseFacts(String json) {
        try {
            List<Map<String, Object>> list = objectMapper.readValue(
                    extractJson(json), new TypeReference<>() {});
            List<ExtractedFact> facts = new ArrayList<>();
            for (Map<String, Object> item : list) {
                String cat = (String) item.get("category");
                String content = (String) item.get("content");
                Double conf = toDouble(item.get("confidence"));
                if (cat != null && content != null && conf != null) {
                    try {
                        com.memorylane.entity.MemoryCategory category =
                                com.memorylane.entity.MemoryCategory.valueOf(cat.toLowerCase());
                        facts.add(new ExtractedFact(category, content, Math.min(1.0, Math.max(0.0, conf))));
                    } catch (IllegalArgumentException e) {
                        log.debug("Unknown category: {}", cat);
                    }
                }
            }
            return facts;
        } catch (Exception e) {
            log.warn("Failed to parse facts JSON: {}", json, e);
            return List.of();
        }
    }

    /**
     * 从 LLM 响应中提取 JSON 数组部分（LLM 可能在 JSON 前后加说明文本）。
     */
    private String extractJson(String response) {
        if (response == null) return "[]";
        String s = response.trim();
        int start = s.indexOf('[');
        int end = s.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return s.substring(start, end + 1);
        }
        return "[]";
    }

    private Long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private Integer toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private Double toDouble(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException ignored) {}
        }
        return null;
    }
}
