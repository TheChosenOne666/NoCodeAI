package com.xiaolou.xiaolouainocodebackend.config;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.streaming-chat-model")
@Data
public class StreamingChatModelConfig {

    private String baseUrl;

    private String apiKey;

    private String modelName;

    private Double temperature;

    private Integer maxTokens;

    private boolean logRequests;

    private boolean logResponses;

    @Bean
    @Scope("prototype")
    public StreamingChatModel streamingChatModelPrototype() {
        return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .timeout(Duration.ofSeconds(300))
                .build();
    }
}
