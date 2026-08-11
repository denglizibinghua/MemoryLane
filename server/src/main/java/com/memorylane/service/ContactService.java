package com.memorylane.service;

import com.memorylane.entity.Conversation;
import com.memorylane.entity.Contact;
import com.memorylane.entity.Memory;
import com.memorylane.entity.Reminder;
import com.memorylane.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class ContactService {

    private final ContactRepository contactRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final MemoryRepository memoryRepository;
    private final ReminderRepository reminderRepository;
    private final ObjectMapper objectMapper;

    /**
     * 级联删除联系人及其全部关联数据（对话、消息、记忆、提醒）。
     * 所有 FK 都是 NO ACTION，按子→父顺序逐层删除。
     */
    @Transactional
    public void deleteWithCascade(Long contactId) {
        // 1. 删除联系人下的提醒
        reminderRepository.deleteByContactId(contactId);

        // 2. 删除联系人下全部对话的消息
        List<Conversation> conversations = conversationRepository.findByContactIdOrderByLastMsgAtDesc(contactId);
        if (!conversations.isEmpty()) {
            List<Long> conversationIds = conversations.stream().map(Conversation::getId).toList();
            messageRepository.deleteByConversationIdIn(conversationIds);
        }

        // 3. 删除联系人下的对话
        conversationRepository.deleteByContactId(contactId);

        // 4. 删除联系人下的记忆
        memoryRepository.deleteByContactId(contactId);

        // 5. 删除联系人自身
        contactRepository.deleteById(contactId);
    }

    /**
     * 合并多个联系人到目标联系人。
     * 将 sourceIds 的全部对话、记忆、提醒迁移到 targetId，然后删除源联系人。
     *
     * @return 合并统计
     */
    @Transactional
    public MergeResult merge(Long targetId, List<Long> sourceIds) {
        Contact target = contactRepository.findById(targetId)
                .orElseThrow(() -> new IllegalArgumentException("目标联系人不存在"));

        // 去重 + 过滤 null + 排除 target 自身
        List<Long> deduped = sourceIds.stream()
                .filter(Objects::nonNull)
                .filter(id -> !id.equals(targetId))
                .distinct()
                .toList();

        int mergedConversations = 0;
        int mergedMemories = 0;
        int mergedReminders = 0;

        for (Long sourceId : deduped) {
            Contact source = contactRepository.findById(sourceId).orElse(null);
            if (source == null) continue;

            // 深合并 profile JSON（target 已有字段优先，source 补充缺失字段）
            mergeProfile(target, source);

            List<Conversation> conversations = conversationRepository.findByContactIdOrderByLastMsgAtDesc(sourceId);
            conversations.forEach(c -> c.setContact(target));
            conversationRepository.saveAll(conversations);
            mergedConversations += conversations.size();

            List<Memory> memories = memoryRepository.findByContactId(sourceId);
            memories.forEach(m -> m.setContact(target));
            memoryRepository.saveAll(memories);
            mergedMemories += memories.size();

            List<Reminder> reminders = reminderRepository.findByContactIdOrderByRemindAtDesc(sourceId);
            reminders.forEach(r -> r.setContact(target));
            reminderRepository.saveAll(reminders);
            mergedReminders += reminders.size();

            contactRepository.deleteById(sourceId);
        }

        return new MergeResult(target.getName(), mergedConversations, mergedMemories, mergedReminders);
    }

    /**
     * 将 source 的 profile JSONB 合并到 target。
     * target 已有字段优先保留，source 只补充 target 缺失的字段。
     */
    private void mergeProfile(Contact target, Contact source) {
        try {
            Map<String, Object> targetProfile = parseProfile(target.getProfile());
            Map<String, Object> sourceProfile = parseProfile(source.getProfile());
            boolean changed = false;

            for (Map.Entry<String, Object> e : sourceProfile.entrySet()) {
                if (!targetProfile.containsKey(e.getKey())) {
                    targetProfile.put(e.getKey(), e.getValue());
                    changed = true;
                }
            }
            if (changed) {
                target.setProfile(objectMapper.writeValueAsString(targetProfile));
            }
        } catch (Exception ignored) {
            // profile 解析失败则跳过合并，不影响主流程
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseProfile(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, LinkedHashMap.class);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    public record MergeResult(
            String targetName,
            int mergedConversations,
            int mergedMemories,
            int mergedReminders
    ) {}

    // ── Contact dedup ──────────────────────────────────────────────

    public record ContactCandidate(Long id, String name, String platform, int messageCount) {}

    public record DuplicateGroup(
            List<ContactCandidate> candidates,
            String reason,
            double confidence
    ) {}

    /**
     * 发现疑似重复联系人，按信号强弱分组。
     * 每组内按消息数量降序排列（消息最多的排第一，建议保留）。
     * 不自动合并 — 由用户决策。
     */
    public List<DuplicateGroup> findDuplicates() {
        List<Contact> all = contactRepository.findAll();
        if (all.size() < 2) return List.of();

        // Build per-contact message counts from conversations
        Map<Long, Integer> msgCounts = new HashMap<>();
        for (Contact c : all) {
            List<Conversation> convs = conversationRepository.findByContactIdOrderByLastMsgAtDesc(c.getId());
            int total = convs.stream().mapToInt(conv -> conv.getMessageCount() != null ? conv.getMessageCount() : 0).sum();
            msgCounts.put(c.getId(), total);
        }

        // Find candidate pairs with confidence scores
        List<DuplicatePair> pairs = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                Contact a = all.get(i);
                Contact b = all.get(j);
                DuplicatePair pair = detectPair(a, b);
                if (pair != null) pairs.add(pair);
            }
        }

        if (pairs.isEmpty()) return List.of();

        // Group pairs into clusters (union-find)
        Map<Long, Set<Long>> clusters = new LinkedHashMap<>();
        Map<Long, DuplicatePair> bestPair = new HashMap<>(); // best confidence per contact
        for (DuplicatePair p : pairs) {
            Long keyA = p.a().getId();
            Long keyB = p.b().getId();
            // Find existing clusters
            Set<Long> cluster = null;
            for (Set<Long> c : clusters.values()) {
                if (c.contains(keyA) || c.contains(keyB)) {
                    cluster = c;
                    break;
                }
            }
            if (cluster == null) {
                cluster = new LinkedHashSet<>();
                clusters.put(keyA, cluster);
            }
            cluster.add(keyA);
            cluster.add(keyB);
            // Track best pair
            bestPair.merge(keyA, p, (old, cur) -> old.confidence() >= cur.confidence() ? old : cur);
            bestPair.merge(keyB, p, (old, cur) -> old.confidence() >= cur.confidence() ? old : cur);
        }

        // Build DuplicateGroups from clusters
        List<DuplicateGroup> groups = new ArrayList<>();
        for (Set<Long> cluster : clusters.values()) {
            if (cluster.size() < 2) continue;
            List<ContactCandidate> candidates = cluster.stream()
                    .map(id -> {
                        Contact c = all.stream().filter(x -> x.getId().equals(id)).findFirst().orElse(null);
                        return c == null ? null
                                : new ContactCandidate(c.getId(), c.getName(),
                                        c.getPlatform() != null ? c.getPlatform() : "other",
                                        msgCounts.getOrDefault(c.getId(), 0));
                    })
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparingInt(ContactCandidate::messageCount).reversed())
                    .toList();

            // Pick reason from best pair
            double maxConf = cluster.stream()
                    .mapToDouble(id -> bestPair.getOrDefault(id, new DuplicatePair(null, null, "", 0)).confidence())
                    .max().orElse(0);

            groups.add(new DuplicateGroup(candidates, "疑似重复联系人", maxConf));
        }

        return groups;
    }

    private record DuplicatePair(Contact a, Contact b, String reason, double confidence) {}

    private DuplicatePair detectPair(Contact a, Contact b) {
        String n1 = a.getName() != null ? a.getName().trim() : "";
        String n2 = b.getName() != null ? b.getName().trim() : "";
        if (n1.isEmpty() || n2.isEmpty()) return null;

        String p1 = a.getPlatform() != null ? a.getPlatform() : "other";
        String p2 = b.getPlatform() != null ? b.getPlatform() : "other";
        boolean samePlatform = p1.equals(p2);

        // Same platform: name contains or Levenshtein close
        if (samePlatform) {
            if (n1.contains(n2) || n2.contains(n1)) {
                return new DuplicatePair(a, b, "同平台名字包含", 0.9);
            }
            if (levenshtein(n1, n2) <= 2) {
                return new DuplicatePair(a, b, "同平台名字相似", 0.7);
            }
        }

        // Different platform: exact match or similar + same first char
        if (!samePlatform) {
            if (n1.equals(n2)) {
                return new DuplicatePair(a, b, "跨平台同名", 0.95);
            }
            if (levenshtein(n1, n2) <= 2 && n1.charAt(0) == n2.charAt(0)) {
                return new DuplicatePair(a, b, "跨平台名字相似", 0.6);
            }
        }

        return null;
    }

    private static int levenshtein(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();
        int[][] dp = new int[len1 + 1][len2 + 1];
        for (int i = 0; i <= len1; i++) dp[i][0] = i;
        for (int j = 0; j <= len2; j++) dp[0][j] = j;
        for (int i = 1; i <= len1; i++) {
            for (int j = 1; j <= len2; j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i][j - 1] + cost);
            }
        }
        return dp[len1][len2];
    }
}
