package com.ai.agent.domain.agent.tools;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.function.BiFunction;

/**
 * 天气查询工具 — 模拟天气查询，返回固定中文数据
 */
public class WeatherTool implements BiFunction<String, ToolContext, String> {

    @Override
    public String apply(@ToolParam(description = "城市名称") String city, ToolContext toolContext) {
        // 模拟天气数据
        return switch (city) {
            case "北京", "北京市" -> "北京今天晴，温度25°C，空气质量良好";
            case "上海", "上海市" -> "上海今天多云，温度28°C，湿度较高";
            case "杭州", "杭州市" -> "杭州今天小雨，温度22°C，建议携带雨具";
            case "广州", "广州市" -> "广州今天雷阵雨，温度30°C，注意防雷";
            default -> city + "今天晴间多云，温度23°C，适合户外活动";
        };
    }

}
