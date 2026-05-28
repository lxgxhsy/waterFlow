package com.yizhaoqi.smartpai.agent.impl;

import com.yizhaoqi.smartpai.agent.AgentTool;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 站点属性查询工具：根据站点 ID/名称查属性（汛期、调度规程链接等）。
 * 当前为占位，后续可对接真实站点库或从 KG 中查属性。
 */
@Component
public class GetSiteAttributesTool implements AgentTool {

    @Override
    public String getName() {
        return "get_site_attributes";
    }

    @Override
    public String getDescription() {
        return "查询站点属性（如汛期、设计水位、所属规程等）。参数：site_id 或 site_name。在已知站点名称或 ID 后，需要其属性时调用。";
    }

    @Override
    public String execute(Map<String, String> arguments) {
        String siteId = arguments.getOrDefault("site_id", "");
        String siteName = arguments.getOrDefault("site_name", "");
        if (siteId.isBlank() && siteName.isBlank()) {
            return "未提供 site_id 或 site_name。";
        }
        // 占位：后续可查 DB 或 KG 中站点表/节点属性
        return "（站点属性服务未接入，当前仅占位。可先使用 search_rag 按站点名+汛期规程检索。）";
    }
}
