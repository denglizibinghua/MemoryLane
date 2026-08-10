package com.memorylane.memory;

import com.memorylane.entity.MemoryCategory;

/**
 * LLM 提取的原始记忆事实，尚未合并入持久化记忆库。
 */
public record ExtractedFact(
        MemoryCategory category,
        String content,
        double confidence
) {}
