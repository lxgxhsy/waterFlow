package com.yizhaoqi.smartpai.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.Map;

/**
 * 意图识别结果，用于决定走 RAG、知识图谱或两者融合。
 */
@Data
@Builder
public class IntentResult {

    /** 意图类型 */
    private IntentType intentType;

    /** 槽位：如 reservoir=XX水库, gate=XX闸门, clause=条款号 等 */
    @Builder.Default
    private Map<String, String> slots = Collections.emptyMap();

    public boolean needRag() {
        return intentType == IntentType.RAG_ONLY || intentType == IntentType.RAG_AND_KG;
    }

    public boolean needKg() {
        return intentType == IntentType.KG_ONLY || intentType == IntentType.RAG_AND_KG;
    }

    public enum IntentType {
        /** 仅文档/规程检索 */
        RAG_ONLY,
        /** 仅知识图谱（实体关系） */
        KG_ONLY,
        /** 文档 + 知识图谱融合 */
        RAG_AND_KG,
        /** 通用闲聊或未识别，默认走 RAG */
        GENERAL
    }
}
