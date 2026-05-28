package com.yizhaoqi.smartpai.service.impl;

import com.yizhaoqi.smartpai.service.KnowledgeGraphService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 知识图谱服务占位实现。
 * 当前返回示例文案，后续可接入 Neo4j/JanusGraph 等图库，按 query + slots 查子图并格式化为文本。
 */
@Service
public class KnowledgeGraphServiceImpl implements KnowledgeGraphService {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeGraphServiceImpl.class);

    private static final String STUB_MESSAGE = "当前未接入图数据库，暂无实体关系数据。可后续接入 Neo4j 等存储水库-闸门-站点等关系。";

    @Override
    public String getSubgraphContext(String query, Map<String, String> slots) {
        if (slots != null && !slots.isEmpty()) {
            logger.debug("KG 查询(占位): query={}, slots={}", query, slots);
        }
        // 占位：真实实现可在此根据 slots 查图库，拼成例如：
        // "水库A -下游-> 闸门B; 闸门B -控制-> 站点C; ..."
        return STUB_MESSAGE;
    }
}
