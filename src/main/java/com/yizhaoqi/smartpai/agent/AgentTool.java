package com.yizhaoqi.smartpai.agent;

import java.util.Map;

/**
 * Agent 可调用的工具，用于多步推理（如先查 KG 下游站点，再查 RAG 规程）。
 */
public interface AgentTool {

    /** 工具名称，与 LLM 输出中的 tool 名一致 */
    String getName();

    /** 给 LLM 看的工具说明，用于决定何时调用 */
    String getDescription();

    /**
     * 执行工具，参数由 Agent 从 LLM 输出中解析传入。
     *
     * @param arguments 如 river=淮沭新河, segment=泗洪段, site=xx站点, relation=下游测站
     * @return 执行结果文本，将作为 Observation 反馈给 LLM
     */
    String execute(Map<String, String> arguments);
}
