package com.memorylane.retrieval;

/**
 * 搜索结果 — 统一输出格式。
 */
public record SearchResult(
        String type,       // "memory"
        String content,    // 记忆内容
        double score,      // 相关性分数
        String category,   // 记忆类别
        Long id,           // 记忆 ID（RRF 去重用）
        Long contactId,    // 关联联系人 ID
        String contactName // 联系人名称
) {}
