package com.yizhaoqi.smartpai.agent.impl;

import com.yizhaoqi.smartpai.agent.AgentTool;
import com.yizhaoqi.smartpai.service.KnowledgeGraphService;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 知识图谱查询工具：根据河段、站点、关系类型等查下游/上游/隶属等。
 */
@Component
public class QueryKgTool implements AgentTool {

    private final KnowledgeGraphService knowledgeGraphService;

    public QueryKgTool(KnowledgeGraphService knowledgeGraphService) {
        this.knowledgeGraphService = knowledgeGraphService;
    }

    @Override
    public String getName() {
        return "query_kg";
    }

    @Override
    public String getDescription() {
        return "查询知识图谱：根据河流、河段、站点名称及关系类型（如下游测站、上游闸门）查询实体关系。"
                + "参数示例：river=淮沭新河, segment=泗洪段, site=xx站点, relation=下游测站";
    }

    @Override
    public String execute(Map<String, String> arguments) {
        StringBuilder query = new StringBuilder();
        arguments.forEach((k, v) -> query.append(k).append(":").append(v).append(" "));
        return knowledgeGraphService.getSubgraphContext(query.toString().trim(), arguments);
    }
}
