package com.yizhaoqi.smartpai.agent;

import com.yizhaoqi.smartpai.client.DeepSeekClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 多步推理 Agent：根据用户问题决定先查 KG（如下游站点）再查 RAG（规程）或站点属性，
 * 通过 TOOL_CALL / FINAL_ANSWER 与 LLM 交互，直到得到最终答案。
 */
@Service
public class AgentService {

    private static final Logger logger = LoggerFactory.getLogger(AgentService.class);
    private static final int MAX_STEPS = 6;
    private static final Pattern TOOL_CALL_PATTERN = Pattern.compile("TOOL_CALL:\\s*([a-z_]+)\\s*\\|\\s*(.+)", Pattern.DOTALL);
    private static final Pattern FINAL_ANSWER_PATTERN = Pattern.compile("FINAL_ANSWER:\\s*(.+)", Pattern.DOTALL);

    private final DeepSeekClient deepSeekClient;
    private final List<AgentTool> tools;

    public AgentService(DeepSeekClient deepSeekClient, List<AgentTool> tools) {
        this.deepSeekClient = deepSeekClient;
        this.tools = tools != null ? tools : new ArrayList<>();
    }

    /**
     * 执行 Agent 多步推理，返回最终回答文本。
     *
     * @param userMessage 用户问题（如：淮沭新河在泗洪段的xx站点的下游测站点的汛期调度规程是什么？）
     * @param userId      当前用户 ID，用于 RAG 权限
     * @param history     对话历史（可为空）
     * @return 最终回答
     */
    public String run(String userMessage, String userId, List<Map<String, String>> history) {
        StringBuilder systemPrompt = new StringBuilder();
        systemPrompt.append("你是水利调度智能助手，需要结合知识图谱与知识库分步回答问题。\n\n");
        systemPrompt.append("可用工具：\n");
        for (AgentTool t : tools) {
            systemPrompt.append("- ").append(t.getName()).append(": ").append(t.getDescription()).append("\n");
        }
        systemPrompt.append("\n请按步骤推理。例如：先确定河段与站点，再查下游测站，再查该站点的汛期调度规程。\n");
        systemPrompt.append("若需要调用工具，请在一行内输出：TOOL_CALL: 工具名 | 参数1=值1,参数2=值2（如 query_kg | river=淮沭新河,segment=泗洪段,site=xx站点,relation=下游测站）。\n");
        systemPrompt.append("若已有足够信息可回答，请输出：FINAL_ANSWER: 你的完整回答内容。\n");
        systemPrompt.append("注意：调用 search_rag 时无需传 userId，系统会自动带上。");

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt.toString()));
        if (history != null && !history.isEmpty()) {
            messages.addAll(history);
        }
        messages.add(Map.of("role", "user", "content", userMessage));

        for (int step = 0; step < MAX_STEPS; step++) {
            String response = deepSeekClient.chatCompletionNonStream(messages);
            if (response == null || response.isBlank()) {
                return "抱歉，本次推理未得到有效回复。";
            }
            response = response.trim();

            Matcher toolMatcher = TOOL_CALL_PATTERN.matcher(response);
            if (toolMatcher.find()) {
                String toolName = toolMatcher.group(1).trim();
                String argsStr = toolMatcher.group(2).trim();
                Map<String, String> args = parseArguments(argsStr);
                args.put("userId", userId);

                AgentTool tool = tools.stream().filter(t -> t.getName().equals(toolName)).findFirst().orElse(null);
                if (tool == null) {
                    messages.add(Map.of("role", "assistant", "content", response));
                    messages.add(Map.of("role", "user", "content", "Observation: 未知工具 " + toolName + "，请换用已有工具或直接给出 FINAL_ANSWER。"));
                    continue;
                }
                String observation = tool.execute(args);
                messages.add(Map.of("role", "assistant", "content", response));
                messages.add(Map.of("role", "user", "content", "Observation: " + observation));
                logger.info("Agent step {}: tool={}, observationLength={}", step + 1, toolName, observation.length());
                continue;
            }

            Matcher finalMatcher = FINAL_ANSWER_PATTERN.matcher(response);
            if (finalMatcher.find()) {
                return finalMatcher.group(1).trim();
            }
            if (response.contains("FINAL_ANSWER")) {
                int idx = response.indexOf("FINAL_ANSWER:");
                return response.substring(idx + "FINAL_ANSWER:".length()).trim();
            }
            messages.add(Map.of("role", "assistant", "content", response));
            messages.add(Map.of("role", "user", "content", "请根据已有信息给出 FINAL_ANSWER，或继续使用 TOOL_CALL 调用工具。"));
        }

        return "已达到最大推理步数，请简化问题或稍后重试。";
    }

    private static Map<String, String> parseArguments(String argsStr) {
        Map<String, String> args = new HashMap<>();
        for (String pair : argsStr.split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String k = pair.substring(0, eq).trim();
                String v = pair.substring(eq + 1).trim();
                if (!k.isEmpty()) args.put(k, v);
            }
        }
        return args;
    }
}
