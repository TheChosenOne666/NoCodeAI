package com.xiaolou.xiaolouainocodebackend.config;

import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

/**
 * Redis 对话记忆存储配置
 *
 * 优先从 {@code REDIS_URL}（Railway Redis 插件默认注入格式
 * redis://user:password@host:port）解析连接信息；
 * 若未提供 REDIS_URL，则回退到 spring.data.redis 的拆分变量
 * （REDISHOST / REDISPORT / REDISUSER / REDISPASSWORD）。
 */
@Slf4j
@Configuration
@Data
public class RedisChatMemoryStoryConfig {

    @Value("${REDIS_URL:}")
    private String redisUrl;

    @Value("${REDISHOST:localhost}")
    private String host;

    @Value("${REDISPORT:6379}")
    private int port;

    @Value("${REDISUSER:}")
    private String username;

    @Value("${REDISPASSWORD:}")
    private String password;

    @Value("${spring.data.redis.ttl:3600}")
    private long ttl;

    @Bean
    public RedisChatMemoryStore redisChatMemoryStory() {
        String resolvedHost = host;
        Integer resolvedPort = port;
        String resolvedUser = username;
        String resolvedPassword = password;

        if (redisUrl != null && !redisUrl.isBlank()) {
            try {
                URI uri = URI.create(redisUrl.replace("redis://", "//"));
                if (uri.getHost() != null) {
                    resolvedHost = uri.getHost();
                }
                if (uri.getPort() != -1) {
                    resolvedPort = uri.getPort();
                }
                if (uri.getUserInfo() != null) {
                    String[] parts = uri.getUserInfo().split(":", 2);
                    resolvedUser = parts[0];
                    resolvedPassword = parts.length > 1 ? parts[1] : null;
                }
                log.info("RedisChatMemoryStore 从 REDIS_URL 解析连接: host={}, port={}, user={}",
                        resolvedHost, resolvedPort, resolvedUser);
            } catch (Exception e) {
                log.warn("解析 REDIS_URL 失败，回退使用拆分变量: {}", e.getMessage());
            }
        }

        RedisChatMemoryStore.Builder builder = RedisChatMemoryStore.builder()
                .host(resolvedHost)
                .port(resolvedPort)
                .ttl(ttl);
        if (resolvedUser != null && !resolvedUser.isBlank()) {
            builder = builder.user(resolvedUser).password(resolvedPassword);
        }
        return builder.build();
    }
}
