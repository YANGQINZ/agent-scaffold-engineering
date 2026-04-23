package com.ai.agent.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.document.MetadataMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingModelConfig {

    @Bean
    public EmbeddingModel embeddingModel() {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl("https://dashscope.aliyuncs.com/compatible-mode")
                .apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
                .build();

        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.ALL,
                OpenAiEmbeddingOptions.builder()
                        .model("text-embedding-v1")
                        .dimensions(1536)
                        .build());
    }
}
