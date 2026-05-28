package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.dto.IntentResult;

/**
 * 意图识别：根据用户问题判断走 RAG、知识图谱或两者融合。
 */
public interface IntentRecognitionService {

    /**
     * 识别用户意图并抽取槽位（如水库名、闸门、规程条款等）。
     *
     * @param userMessage 用户输入
     * @return 意图类型 + 槽位
     */
    IntentResult recognize(String userMessage);
}
