package com.ai.agent.config;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingModelConfig {

    @Bean
    @ConditionalOnBean(name = "openAiApi")
    public EmbeddingModel embeddingModel(@Value("${spring.ai.openai.embedding.options.model}") String model,
                                         @Value("${spring.ai.vectorstore.pgvector.dimensions}") Integer dimensions,
                                         @Value("${spring.ai.openai.base-url}") String baseUrl,
                                         @Value("${spring.ai.openai.api-key}") String apikey) {

        OpenAiApi openAiApi = OpenAiApi.builder().baseUrl(baseUrl).apiKey(apikey).build();

        return new OpenAiEmbeddingModel(openAiApi, MetadataMode.ALL,
            OpenAiEmbeddingOptions.builder().model(model).dimensions(dimensions).build());
    }

    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter();
    }

}
