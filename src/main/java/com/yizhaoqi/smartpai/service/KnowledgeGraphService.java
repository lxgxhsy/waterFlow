package com.yizhaoqi.smartpai.service;

import java.util.Map;

/**
 * 知识图谱：根据查询与槽位返回相关子图/三元组描述，供大模型上下文使用。
 */
public interface KnowledgeGraphService {

    /**
     * 根据用户问题与意图槽位查询图谱，返回可读的上下文文本（如三元组列表或自然语言描述）。
     *
     * @param query 用户问题
     * @param slots 意图识别得到的槽位（如 reservoir=XX水库, gate=XX闸）
     * @return 图谱上下文字符串，无结果时返回空字符串或说明文案
     */
    String getSubgraphContext(String query, Map<String, String> slots);
}
