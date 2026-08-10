package com.memorylane.memory;

import com.memorylane.entity.Contact;
import com.memorylane.entity.Memory;
import com.memorylane.entity.MemoryCategory;
import com.memorylane.repository.MemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 记忆合并器 — 新记忆与已有记忆的冲突处理。
 *
 * <p>采用 Graphiti bi-temporal 模型：
 * <ul>
 *   <li>语义不重叠 → 直接 INSERT 新记忆</li>
 *   <li>语义冲突（同类别、内容不同）→ 旧记忆标记 {@code valid_until}，
 *       新记忆从冲突时点起生效</li>
 *   <li>语义重复 → 不重复插入，提高旧记忆的 confidence</li>
 * </ul>
 *
 * <p>v0.1 简化：仅做同类别+关键词重叠检测，不做 LLM 语义比对（成本控制）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryMerger {

    private final MemoryRepository memoryRepository;

    /**
     * 将提取的事实合并进记忆库。
     *
     * @param facts    LLM 提取的新事实
     * @param contact  关联的联系人
     * @param msgIds   源消息 ID 列表
     * @param now      当前时间
     */
    public List<Memory> merge(List<ExtractedFact> facts, Contact contact, Long[] msgIds, Instant now) {
        List<Memory> created = new ArrayList<>();
        for (ExtractedFact fact : facts) {
            MemoryCategory category = fact.category();
            String content = fact.content();
            double confidence = fact.confidence();

            // 查找同联系人+同类别中可能冲突的旧记忆
            List<Memory> existing = memoryRepository
                    .findByContactIdAndCategoryAndValidUntilIsNull(contact.getId(), category.name().toLowerCase());

            boolean merged = tryMerge(existing, content, now);

            if (!merged) {
                Memory memory = Memory.builder()
                        .contact(contact)
                        .category(category)
                        .content(content)
                        .confidence(confidence)
                        .sourceMsgIds(msgIds)
                        .validFrom(now)
                        .build();
                memory = memoryRepository.save(memory);
                created.add(memory);
                log.debug("New memory: [{}] {}", category, content);
            }
        }
        return created;
    }

    /**
     * 尝试将新内容合并到已有记忆中，三层决策链（仿 Graphiti）：
     * ① 精确匹配 → 直接合并（零计算成本）
     * ② Bigram 相似 → 合并提升置信度
     * ③ 时间区间冲突 → 旧记忆过期，新记忆插入
     *
     * @return true 如果合并成功（无需新增），false 如果应新建一条记忆
     */
    private boolean tryMerge(List<Memory> existing, String newContent, Instant now) {
        for (Memory old : existing) {
            // ① Exact normalized match (Graphiti fast path)
            if (isExactMatch(old.getContent(), newContent)) {
                double newConf = Math.min(0.95, old.getConfidence() + 0.1);
                old.setConfidence(newConf);
                memoryRepository.save(old);
                log.debug("Exact match merged: {}", old.getContent());
                return true;
            }

            // ② Bigram similarity (Chinese-aware)
            if (isSimilar(old.getContent(), newContent)) {
                double newConf = Math.min(0.95, old.getConfidence() + 0.1);
                old.setConfidence(newConf);
                memoryRepository.save(old);
                log.debug("Merged similar memory: {} → confidence={}", old.getContent(), newConf);
                return true;
            }

            // ③ Interval conflict (Graphiti resolve_edge_contradictions)
            if (isConflicting(old, newContent, now)) {
                old.setValidUntil(now);
                memoryRepository.save(old);
                log.info("Conflict resolved: old='{}' expired, new='{}'", old.getContent(), newContent);
                return false; // 仍需插入新记忆
            }
        }
        return false;
    }

    /**
     * 精确规范化匹配：trim + lowercase + 空白折叠。
     * 仿 Graphiti _normalize_string_exact 快速路径。
     */
    private boolean isExactMatch(String a, String b) {
        return normalize(a).equals(normalize(b));
    }

    private String normalize(String text) {
        return text.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    /**
     * 判断两段内容是否语义相似（v0.1：关键词重叠 ≥ 60%）。
     */
    private boolean isSimilar(String oldContent, String newContent) {
        double overlap = keywordOverlap(oldContent, newContent);
        return overlap >= 0.6;
    }

    /**
     * 时间区间冲突检测（仿 Graphiti resolve_edge_contradictions）。
     *
     * <p>两个同类别记忆冲突当且仅当：
     * <ol>
     *   <li>旧记忆仍然有效（validUntil 为 null 或在 now 之后）</li>
     *   <li>内容不相似（bigram &lt; 60%，已由 isSimilar 前置排除）</li>
     *   <li>但有话题重叠（bigram &gt; 10%）—— 同一话题的不同信息</li>
     * </ol>
     *
     * <p>v0.1 简化：不做 LLM 语义比对，用 bigram 重叠度做启发式。
     * 旧记忆被过期后，新记忆作为更正版本插入。
     */
    private boolean isConflicting(Memory old, String newContent, Instant now) {
        // 旧记忆已过期 → 不冲突
        if (old.getValidUntil() != null && !old.getValidUntil().isAfter(now)) {
            return false;
        }

        // 内容相似 → 应由 isSimilar 合并，不算冲突
        if (isSimilar(old.getContent(), newContent)) {
            return false;
        }

        // 话题重叠度 > 10% → 同一话题的更新/更正
        double overlap = keywordOverlap(old.getContent(), newContent);
        return overlap > 0.1;
    }

    /**
     * 计算两段文本的关键词重叠率（适用于中英文混合）。
     *
     * <p>中文使用字符级 bigram 切分（"中午一起" → ["中午","午一","一起"]），
     * 英文保留按空格切词，兼容器混合文本。
     */
    private double keywordOverlap(String a, String b) {
        List<String> tokensA = tokenize(a);
        List<String> tokensB = tokenize(b);

        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0;

        int common = 0;
        for (String ta : tokensA) {
            if (tokensB.contains(ta)) common++;
        }
        return (double) common / Math.max(tokensA.size(), tokensB.size());
    }

    /** Tokenize: Chinese → character bigrams, ASCII → whitespace-split words. */
    private List<String> tokenize(String text) {
        String cleaned = text.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9]", "");
        if (cleaned.isEmpty()) return List.of();

        List<String> tokens = new ArrayList<>();
        StringBuilder cjkBuffer = new StringBuilder();
        StringBuilder asciiBuffer = new StringBuilder();

        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                if (asciiBuffer.length() > 0) {
                    for (String w : asciiBuffer.toString().split("\\s+")) {
                        if (!w.isBlank()) tokens.add(w.toLowerCase());
                    }
                    asciiBuffer.setLength(0);
                }
                cjkBuffer.append(c);
            } else {
                if (cjkBuffer.length() > 0) {
                    tokens.addAll(bigrams(cjkBuffer.toString()));
                    cjkBuffer.setLength(0);
                }
                asciiBuffer.append(c);
            }
        }
        // flush
        if (cjkBuffer.length() > 0) tokens.addAll(bigrams(cjkBuffer.toString()));
        if (asciiBuffer.length() > 0) {
            for (String w : asciiBuffer.toString().split("\\s+")) {
                if (!w.isBlank()) tokens.add(w.toLowerCase());
            }
        }
        return tokens;
    }

    private List<String> bigrams(String s) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < s.length() - 1; i++) {
            result.add(s.substring(i, i + 2));
        }
        // Also include single chars for short strings (1-2 chars)
        if (s.length() <= 2) {
            for (int i = 0; i < s.length(); i++) {
                result.add(String.valueOf(s.charAt(i)));
            }
        }
        return result;
    }
}
