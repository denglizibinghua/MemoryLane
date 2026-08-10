package com.memorylane.retrieval;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 统一检索服务 — 编排全文搜索和语义搜索，通过 RRF 融合结果。
 */
@Service
@RequiredArgsConstructor
public class SearchService {

    private final FullTextSearch fullTextSearch;
    private final SemanticSearch semanticSearch;

    private static final int TOP_N = 20;

    /**
     * 混合搜索 — FTS + 语义 + RRF 融合。
     */
    public List<SearchResult> hybridSearch(String query, Long contactId) {
        List<SearchResult> ftsResults = fullTextSearch.search(query, contactId, TOP_N);
        List<SearchResult> semResults = semanticSearch.search(query, contactId, TOP_N);

        return HybridRanker.fuse(ftsResults, semResults, SearchResult::id, TOP_N)
                .stream()
                .map(HybridRanker.RankedResult::item)
                .toList();
    }

    /**
     * 仅关键词搜索。
     */
    public List<SearchResult> keywordSearch(String query, Long contactId) {
        return fullTextSearch.search(query, contactId, TOP_N);
    }
}
