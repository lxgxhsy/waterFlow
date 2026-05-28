package com.yizhaoqi.smartpai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 针对 ParseService 中滑动窗口冗余逻辑的单元测试
 */
class ParseServiceOverlapTest {

    private ParseService parseService;

    @BeforeEach
    void setUp() {
        parseService = new ParseService();
        ReflectionTestUtils.setField(parseService, "chunkSize", 50);
        ReflectionTestUtils.setField(parseService, "bufferSize", 8192);
        ReflectionTestUtils.setField(parseService, "maxMemoryThreshold", 0.8);
        ReflectionTestUtils.setField(parseService, "overlapSize", 10);
    }

    @Test
    void splitTextIntoChunksWithSemantics_shouldApplyOverlapBetweenChunks() throws Exception {
        String text = "段落一：这是第一段内容，用于测试。\n\n" +
                      "段落二：这是第二段内容，也用于测试窗口重叠效果。\n\n" +
                      "段落三：这是第三段内容，用于确认相邻块之间存在冗余。";

        Method method = ParseService.class.getDeclaredMethod(
                "splitTextIntoChunksWithSemantics", String.class, int.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<String> chunks = (List<String>) method.invoke(parseService, text, 50);

        assertNotNull(chunks);
        assertTrue(chunks.size() >= 2, "测试数据应被切分成至少两个块");

        String first = chunks.get(0);
        String second = chunks.get(1);

        assertTrue(first.length() > 0);
        assertTrue(second.length() > 0);

        // 取第一块的尾部 overlapSize 字符，检查是否出现在第二块开头附近
        int overlapSize = 10;
        String tail = first.length() <= overlapSize
                ? first
                : first.substring(first.length() - overlapSize);

        assertTrue(second.contains(tail),
                "第二个 chunk 应该包含第一个 chunk 尾部的重叠内容");
    }
}

