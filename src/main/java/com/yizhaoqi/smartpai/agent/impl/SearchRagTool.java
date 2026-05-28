package com.yizhaoqi.smartpai.agent.impl;

import com.yizhaoqi.smartpai.agent.AgentTool;
import com.yizhaoqi.smartpai.entity.SearchResult;
import com.yizhaoqi.smartpai.service.HybridSearchService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG 检索工具：按查询词在知识库中检索规程/文档片段。
 */
@Component
public class SearchRagTool implements AgentTool {

    private final HybridSearchService searchService;

    public SearchRagTool(HybridSearchService searchService) {
        this.searchService = searchService;
    }

    @Override
    public String getName() {
        return "search_rag";
    }

    @Override
    public String getDescription() {
        return "在知识库中检索文档/规程条文。参数：query=检索关键词（如汛期调度规程、某站点规程）, topK=条数默认5。需要查规程、办法、条文时调用。";
    }

    @Override
    public String execute(Map<String, String> arguments) {
        String query = arguments.getOrDefault("query", "");
        String userId = arguments.getOrDefault("userId", "");
        int topK = 5;
        try {
            if (arguments.containsKey("topK")) {
                topK = Integer.parseInt(arguments.get("topK"));
            }
        } catch (NumberFormatException ignored) {
        }
        if (query.isBlank()) {
            return "未提供检索关键词。";
        }
        List<SearchResult> results = searchService.searchWithPermission(query, userId, topK);
        if (results == null || results.isEmpty()) {
            return "未检索到相关文档。";
        }
        return results.stream()
                .map(r -> "[" + r.getFileName() + "] " + (r.getTextContent() != null ? r.getTextContent().substring(0, Math.min(400, r.getTextContent().length())) : ""))
                .collect(Collectors.joining("\n\n"));
    }
}
