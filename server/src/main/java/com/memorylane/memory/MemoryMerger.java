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
     * 尝试将新内容合并到已有记忆中。
     *
     * @return true 如果合并成功（无需新增），false 如果应新建一条记忆
     */
    private boolean tryMerge(List<Memory> existing, String newContent, Instant now) {
        for (Memory old : existing) {
            if (isSimilar(old.getContent(), newContent)) {
                // 语义相似：提高置信度，不新增
                double newConf = Math.min(0.95, old.getConfidence() + 0.1);
                old.setConfidence(newConf);
                memoryRepository.save(old);
                log.debug("Merged similar memory: {} → confidence={}", old.getContent(), newConf);
                return true;
            }

            if (isConflicting(old.getContent(), newContent)) {
                // 语义冲突：旧记忆标记过期
                old.setValidUntil(now);
                memoryRepository.save(old);
                log.info("Conflict resolved: old='{}' expired, new='{}'", old.getContent(), newContent);
                return false; // 仍需插入新记忆
            }
        }
        return false;
    }

    /**
     * 判断两段内容是否语义相似（v0.1：关键词重叠 ≥ 60%）。
     */
    private boolean isSimilar(String oldContent, String newContent) {
        double overlap = keywordOverlap(oldContent, newContent);
        return overlap >= 0.6;
    }

    /**
     * 判断两段内容是否冲突（v0.1：禁用 — 关键词比对无法可靠判断语义矛盾。
     * 等 v0.2 LLM 语义比对再做）。
     */
    private boolean isConflicting(String oldContent, String newContent) {
        return false;
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
