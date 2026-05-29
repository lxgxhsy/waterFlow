package com.yizhaoqi.smartpai.service.impl;

import com.yizhaoqi.smartpai.dto.IntentResult;
import com.yizhaoqi.smartpai.service.IntentRecognitionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于关键词/规则的意图识别（水利调度场景）。
 * 后续可替换为 LLM 或小模型做分类 + 槽位抽取。
 */
@Service
public class IntentRecognitionServiceImpl implements IntentRecognitionService {

    private static final Logger logger = LoggerFactory.getLogger(IntentRecognitionServiceImpl.class);

    private static final String[] RAG_KEYWORDS = {
            "规程", "条款", "文档", "规定", "办法", "第几条", "第几章", "依据", "参照", "案例", "历史",
            "介绍", "简介", "概况", "基本情况", "是什么", "有哪些", "多少", "查询", "说明",
            "数据", "参数", "高程", "水位", "流量", "计划", "调度", "运行",
            "水库", "流域", "位置", "位于", "坝址", "地点", "坐标"
    };
    private static final String[] KG_KEYWORDS = {
            "关系", "下游", "上游", "约束", "隶属", "连接", "拓扑", "控制"
    };

    private static final Pattern RESERVOIR_PATTERN = Pattern.compile("([\\u4e00-\\u9fa5]{2,10}水库)");
    private static final Pattern GATE_PATTERN = Pattern.compile("([\\u4e00-\\u9fa5]{2,6}闸)");
    private static final String[] ENTITY_QUERY_PREFIXES = {
            "请介绍一下", "介绍一下", "请介绍", "介绍", "查询一下", "查询", "说明一下", "说明", "关于"
    };

    @Override
    public IntentResult recognize(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return IntentResult.builder()
                    .intentType(IntentResult.IntentType.GENERAL)
                    .slots(Map.of())
                    .build();
        }
        String q = userMessage.trim();
        boolean hasRag = containsAny(q, RAG_KEYWORDS);
        boolean hasKg = containsAny(q, KG_KEYWORDS);

        Map<String, String> slots = extractSlots(q);
        boolean hasEntitySlot = !slots.isEmpty();

        IntentResult.IntentType type;
        if (hasRag && hasKg) {
            type = IntentResult.IntentType.RAG_AND_KG;
        } else if (hasKg && hasEntitySlot) {
            type = IntentResult.IntentType.RAG_AND_KG;
        } else if (hasKg) {
            type = IntentResult.IntentType.KG_ONLY;
        } else if (hasRag || hasEntitySlot) {
            type = IntentResult.IntentType.RAG_ONLY;
        } else {
            type = IntentResult.IntentType.GENERAL;
        }

        logger.debug("意图识别: query={}, type={}, slots={}", q, type, slots);
        return IntentResult.builder()
                .intentType(type)
                .slots(slots)
                .build();
    }

    private static boolean containsAny(String text, String[] keywords) {
        for (String k : keywords) {
            if (text.contains(k)) return true;
        }
        return false;
    }

    private static Map<String, String> extractSlots(String text) {
        Map<String, String> slots = new HashMap<>();
        Matcher mRes = RESERVOIR_PATTERN.matcher(text);
        if (mRes.find()) {
            slots.put("reservoir", normalizeEntityName(mRes.group(1)));
        }
        Matcher mGate = GATE_PATTERN.matcher(text);
        if (mGate.find()) {
            slots.put("gate", normalizeEntityName(mGate.group(1)));
        }
        return slots;
    }

    private static String normalizeEntityName(String raw) {
        String normalized = raw;
        for (String prefix : ENTITY_QUERY_PREFIXES) {
            if (normalized.startsWith(prefix)) {
                normalized = normalized.substring(prefix.length());
                break;
            }
        }
        return normalized;
    }
}
