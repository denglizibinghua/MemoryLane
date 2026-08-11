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
}
