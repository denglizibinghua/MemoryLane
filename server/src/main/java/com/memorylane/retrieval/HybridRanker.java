package com.memorylane.retrieval;

import java.util.*;

/**
 * Reciprocal Rank Fusion — 混合排序。
 */
public class HybridRanker {

    private static final double K = 60.0;

    /**
     * RRF 融合两个有序结果列表。
     *
     * @param <T> 结果类型
     */
    public static <T> List<RankedResult<T>> fuse(
            List<T> ftsResults,
            List<T> semResults,
            java.util.function.Function<T, Long> idExtractor,
            int topN) {

        Map<Long, Double> scores = new LinkedHashMap<>();
        Map<Long, T> items = new LinkedHashMap<>();

        int rank = 1;
        for (T item : ftsResults) {
            Long id = idExtractor.apply(item);
            scores.merge(id, 1.0 / (K + rank), Double::sum);
            items.putIfAbsent(id, item);
            rank++;
        }

        rank = 1;
        for (T item : semResults) {
            Long id = idExtractor.apply(item);
            scores.merge(id, 1.0 / (K + rank), Double::sum);
            items.putIfAbsent(id, item);
            rank++;
        }

        return scores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(topN)
                .map(e -> new RankedResult<>(items.get(e.getKey()), e.getValue()))
                .toList();
    }

    public record RankedResult<T>(T item, double score) {}
}
