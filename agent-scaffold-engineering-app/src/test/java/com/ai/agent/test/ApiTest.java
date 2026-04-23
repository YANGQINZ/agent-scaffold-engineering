package com.ai.agent.test;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

import static com.alibaba.cloud.ai.graph.agent.tools.ToolContextConstants.AGENT_CONFIG_CONTEXT_KEY;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest
public class ApiTest {

    @Test
    public void test() {
        log.info("测试完成");
    }

    public static void main(String[] args) throws Exception {
        // 创建模型实例
        DashScopeApi dashScopeApi = DashScopeApi.builder().apiKey(System.getenv("AI_DASHSCOPE_API_KEY")).build();
        ChatModel chatModel = DashScopeChatModel.builder()
            .dashScopeApi(dashScopeApi)
            .defaultOptions(DashScopeChatOptions.builder()
                // Note: model must be set when use options build.
                .withModel(DashScopeChatModel.DEFAULT_MODEL_NAME)
                .withMaxToken(1000)
                .build())
            .build();

        // 创建 Agent
        /*ReactAgent agent = ReactAgent.builder()
            .name("weather_agent")
            .model(chatModel)
            .instruction("You are a helpful weather forecast assistant.")
            .build();

        // 运行 Agent
        AssistantMessage call = agent.call("what is the weather in Hangzhou?");
        System.out.println(call.getText());*/
        /*String SYSTEM_PROMPT = """
            You are an expert weather forecaster, who speaks in puns.
            
            You have access to two tools:
            
            - getWeatherForLocation: use this to get the weather for a specific location
            - getUserLocation: use this to get the user's location
            
            If a user asks you for the weather, make sure you know the location.
            If you can tell from the question that they mean wherever they are,
            use the getUserLocation tool to find their location.
            """;
        // 创建工具回调
        ToolCallback getWeatherTool =
            FunctionToolCallback.builder("getWeatherForLocation", new WeatherForLocationTool())
                .description("Get weather for a given city").inputType(String.class).build();

        ToolCallback getUserLocationTool = FunctionToolCallback.builder("getUserLocation", new UserLocationTool())
            .description("Retrieve user location based on user ID").inputType(String.class).build();
        // 创建 agent
        String threadId = "w";
        ReactAgent agent = ReactAgent.builder().name("weather_pun_agent").model(chatModel).systemPrompt(SYSTEM_PROMPT)
            .tools(getWeatherTool, getUserLocationTool)
            .outputType(ResponseFormat.class).saver(new MemorySaver()).build();

        // threadId 是给定对话的唯一标识符
        RunnableConfig runnableConfig = RunnableConfig.builder().threadId(threadId).addMetadata("user_id", "1").build();

        // 第一次调用
        AssistantMessage response = agent.call("what is the weather outside?", runnableConfig);
        System.out.println(response.getText());
        // 输出类似：
        // Florida is still having a 'sun-derful' day! The sunshine is playing
        // 'ray-dio' hits all day long! I'd say it's the perfect weather for
        // some 'solar-bration'!

        // 注意我们可以使用相同的 threadId 继续对话
        response = agent.call("thank you!", runnableConfig);
        System.out.println(response.getText());*/


        /*ReactAgent agent = ReactAgent.builder()
            .name("poem_agent")
            .model(chatModel)
            .outputType(PoemOutput.class)
            .saver(new MemorySaver())
            .build();

        AssistantMessage response = agent.call("写一首关于春天的诗");
        // 输出会遵循 PoemOutput 的结构
        System.out.println(response.getText());*/

        BeanOutputConverter<TextAnalysisResult> outputConverter = new BeanOutputConverter<>(TextAnalysisResult.class);
        String format = outputConverter.getFormat();

        ReactAgent agent = ReactAgent.builder()
            .name("analysis_agent")
            .model(chatModel)
            .outputSchema(format)
            .saver(new MemorySaver())
            .build();

        AssistantMessage response = agent.call("分析这段文本：春天来了，万物复苏。");
        System.out.println(response.getText());
    }
}
 class PoemOutput {
    private String title;
    private String content;
    private String style;

    // Getters and Setters
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getStyle() { return style; }
    public void setStyle(String style) { this.style = style; }
}

class TextAnalysisResult {
    private String summary;
    private List<String> keywords;
    private String sentiment;
    private Double confidence;

    // Getters and Setters
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords; }
    public String getSentiment() { return sentiment; }
    public void setSentiment(String sentiment) { this.sentiment = sentiment; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
}

@Setter
@Getter
class ResponseFormat {
    // Getters and Setters
    // 一个双关语响应（始终必需）
    private String punnyResponse;

    // 如果可用的话，关于天气的任何有趣信息
    private String weatherConditions;

}

class WeatherForLocationTool implements BiFunction<String, ToolContext, String> {
    @Override
    public String apply(@ToolParam(description = "The city name") String city, ToolContext toolContext) {
        return "It's always sunny in " + city + "!";
    }
}

// 用户位置工具 - 使用上下文
class UserLocationTool implements BiFunction<String, ToolContext, String> {
    @Override
    public String apply(@ToolParam(description = "User query") String query, ToolContext toolContext) {
        // 从上下文中获取用户信息
        String userId = "";
        if (toolContext != null && toolContext.getContext() != null) {
            RunnableConfig runnableConfig = (RunnableConfig)toolContext.getContext().get(AGENT_CONFIG_CONTEXT_KEY);
            Optional<Object> userIdObjOptional = runnableConfig.metadata("user_id");
            if (userIdObjOptional.isPresent()) {
                userId = (String)userIdObjOptional.get();
            }
        }
        if (userId == null) {
            userId = "1";
        }
        return "1".equals(userId) ? "Florida" : "San Francisco";
    }
}


