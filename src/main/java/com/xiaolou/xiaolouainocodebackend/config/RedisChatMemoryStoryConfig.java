package com.xiaolou.xiaolouainocodebackend.config;

import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis 对话记忆存储配置
 */
@Configuration
@ConfigurationProperties(prefix = "spring.data.redis")
@Data
public class RedisChatMemoryStoryConfig {

    private String host;

    private int port;

    private String username;

    private String password;

    private long ttl;


    @Bean
    public RedisChatMemoryStore redisChatMemoryStory() {
        RedisChatMemoryStore.Builder builder = RedisChatMemoryStore.builder()
                .host(host)
                .port(port)
                .ttl(ttl);
        if (username != null && !username.isBlank()) {
            builder = builder.user(username).password(password);
        }
        return builder.build();
    }
}
