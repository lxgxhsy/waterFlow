package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.entity.SearchResult;

import java.util.List;

/**
 * Optional rerank extension point after hybrid RRF merge.
 */
public interface SearchReranker {

    List<SearchResult> rerank(String query, List<SearchResult> candidates, int topK);

    static SearchReranker identity() {
        return (query, candidates, topK) -> candidates;
    }
}
