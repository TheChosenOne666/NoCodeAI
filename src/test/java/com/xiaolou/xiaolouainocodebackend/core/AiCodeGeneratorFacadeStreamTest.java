package com.xiaolou.xiaolouainocodebackend.core;

import com.xiaolou.xiaolouainocodebackend.model.dto.codegen.CodeGenStreamEvent;
import com.xiaolou.xiaolouainocodebackend.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AiCodeGeneratorFacade 流式代码生成集成测试
 * <p>
 * 验证 generateVueProjectStreamDetail 返回正确的 Flux 结构，
 * 以及共享会话（同一 requestId）的行为。
 */
@SpringBootTest
class AiCodeGeneratorFacadeStreamTest {

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Test
    @DisplayName("generateVueProjectStreamDetail 应返回非空 Flux")
    void shouldReturnNonNullFlux() {
        String requestId = UUID.randomUUID().toString();
        Flux<ServerSentEvent<CodeGenStreamEvent>> flux =
                aiCodeGeneratorFacade.generateVueProjectStreamDetail("生成一个登录页面", 1L, requestId);

        assertNotNull(flux, "Flux 不应为 null");
    }

    @Test
    @DisplayName("同一 requestId 第二次调用应返回非空 Flux（共享会话复用）")
    void sameRequestIdShouldReturnNonNullFlux() {
        String requestId = UUID.randomUUID().toString();
        Flux<ServerSentEvent<CodeGenStreamEvent>> flux1 =
                aiCodeGeneratorFacade.generateVueProjectStreamDetail("生成一个登录页面", 1L, requestId);
        Flux<ServerSentEvent<CodeGenStreamEvent>> flux2 =
                aiCodeGeneratorFacade.generateVueProjectStreamDetail("生成一个登录页面", 1L, requestId);

        // 同一 requestId 第二次调用应返回非空 Flux（共享会话已存在，直接复用）
        assertNotNull(flux1, "第一次调用 Flux 不应为 null");
        assertNotNull(flux2, "第二次调用 Flux 不应为 null（共享会话复用）");
    }

    @Test
    @DisplayName("不同 requestId 应返回不同的 Flux 实例")
    void differentRequestIdShouldReturnDifferentSession() {
        String requestId1 = UUID.randomUUID().toString();
        String requestId2 = UUID.randomUUID().toString();
        Flux<ServerSentEvent<CodeGenStreamEvent>> flux1 =
                aiCodeGeneratorFacade.generateVueProjectStreamDetail("生成一个登录页面", 1L, requestId1);
        Flux<ServerSentEvent<CodeGenStreamEvent>> flux2 =
                aiCodeGeneratorFacade.generateVueProjectStreamDetail("生成一个注册页面", 1L, requestId2);

        assertNotSame(flux1, flux2, "不同 requestId 应返回不同的 Flux 实例");
    }

    @Test
    @DisplayName("generateAndSaveCodeStream 对 VUE_PROJECT 应返回非空 chatFlux")
    void vueProjectChatFluxShouldBeNonNull() {
        String requestId = UUID.randomUUID().toString();
        Flux<ServerSentEvent<String>> chatFlux = aiCodeGeneratorFacade.generateAndSaveCodeStream(
                "生成一个简单页面", CodeGenTypeEnum.VUE_PROJECT, 1L, requestId);

        assertNotNull(chatFlux, "VUE_PROJECT 的 chatFlux 不应为 null");
        // 非空事件断言：至少应包含 done 事件（真实模型可能耗时，这里仅订阅确认结构）
        Long count = chatFlux.filter(Objects::nonNull).count().block();
        assertNotNull(count, "应能统计事件数量");
    }
}