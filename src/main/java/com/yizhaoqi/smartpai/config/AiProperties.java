package com.yizhaoqi.smartpai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 全局 AI 相关配置，包含 Prompt 模板和生成参数。
 */
@Component
@ConfigurationProperties(prefix = "ai")
@Data
public class AiProperties {

    private Prompt prompt = new Prompt();
    private Generation generation = new Generation();
    private Reranker reranker = new Reranker();
    private ContextExpansion contextExpansion = new ContextExpansion();

    @Data
    public static class Prompt {
        /** 规则文案 */
        private String rules;
        /** 引用开始分隔符 */
        private String refStart;
        /** 引用结束分隔符 */
        private String refEnd;
        /** 无检索结果时的占位文案 */
        private String noResultText;
    }

    @Data
    public static class Generation {
        /** 采样温度 */
        private Double temperature = 0.3;
        /** 最大输出 tokens */
        private Integer maxTokens = 2000;
        /** nucleus top-p */
        private Double topP = 0.9;
    }

    @Data
    public static class Reranker {
        /** 是否启用 reranker 精排；默认关闭，避免依赖外部精排服务 */
        private Boolean enabled = false;
        /** RRF 后送入 reranker 的候选窗口大小 */
        private Integer candidateLimit = 50;
    }

    @Data
    public static class ContextExpansion {
        /** 是否为最终命中的 chunk 补充上下文；只影响回答上下文，不参与 RRF/reranker 排序 */
        private Boolean enabled = true;
        /** 命中 chunk 前方补充的相邻 chunk 数量 */
        private Integer beforeWindow = 1;
        /** 命中 chunk 后方补充的相邻 chunk 数量 */
        private Integer afterWindow = 1;
    }
} 
