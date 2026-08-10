package com.memorylane.controller;

import com.memorylane.retrieval.SearchResult;
import com.memorylane.retrieval.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 记忆搜索 API。
 */
@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /**
     * GET /api/v1/search?q=爬山&contactId=1&mode=hybrid
     *
     * @param q         搜索词
     * @param contactId 可选，限定联系人范围
     * @param mode      搜索模式：hybrid（默认）| keyword
     */
    @GetMapping
    public ResponseEntity<List<SearchResult>> search(
            @RequestParam String q,
            @RequestParam(required = false) Long contactId,
            @RequestParam(defaultValue = "hybrid") String mode) {

        List<SearchResult> results;

        if ("keyword".equalsIgnoreCase(mode)) {
            results = searchService.keywordSearch(q, contactId);
        } else {
            results = searchService.hybridSearch(q, contactId);
        }

        return ResponseEntity.ok(results);
    }
}
