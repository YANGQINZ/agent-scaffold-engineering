package com.ai.agent.domain.agent.tools;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.function.BiFunction;

/**
 * 路线搜索工具 — 模拟路线搜索，返回固定中文数据
 */
public class RouteSearchTool implements BiFunction<String, ToolContext, String> {

    @Override
    public String apply(@ToolParam(description = "出发地-目的地，格式：从A到B") String route, ToolContext toolContext) {
        // 模拟路线搜索数据
        if (route == null || route.isBlank()) {
            return "请提供出发地和目的地信息，格式：从A到B";
        }
        return "搜索到以下路线：\n" +
                "1. 高铁：约3小时，票价250-500元，推荐\n" +
                "2. 飞机：约1.5小时，票价400-800元\n" +
                "3. 长途汽车：约6小时，票价120-200元\n" +
                "路线: " + route;
    }

}
