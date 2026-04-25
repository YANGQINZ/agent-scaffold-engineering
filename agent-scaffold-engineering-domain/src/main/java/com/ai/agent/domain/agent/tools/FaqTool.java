package com.ai.agent.domain.agent.tools;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.function.BiFunction;

/**
 * FAQ查询工具 — 模拟FAQ知识库搜索，返回固定中文数据
 */
public class FaqTool implements BiFunction<String, ToolContext, String> {

    @Override
    public String apply(@ToolParam(description = "FAQ问题关键词") String query, ToolContext toolContext) {
        // 模拟FAQ知识库搜索
        if (query == null || query.isBlank()) {
            return "请输入问题关键词进行搜索";
        }
        return "FAQ搜索结果：\n" +
                "Q: 如何退换货？\n" +
                "A: 您可以在订单详情页面申请退换货，7天内无理由退货，15天内质量问题可换货。\n\n" +
                "Q: 配送需要多久？\n" +
                "A: 标准配送3-5个工作日，加急配送1-2个工作日。\n\n" +
                "搜索关键词: " + query;
    }

}
