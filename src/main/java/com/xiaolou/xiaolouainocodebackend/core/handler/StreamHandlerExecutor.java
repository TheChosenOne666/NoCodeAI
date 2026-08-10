package com.xiaolou.xiaolouainocodebackend.core.handler;

import com.xiaolou.xiaolouainocodebackend.model.entity.User;
import com.xiaolou.xiaolouainocodebackend.model.enums.CodeGenTypeEnum;
import com.xiaolou.xiaolouainocodebackend.service.ChatHistoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 流处理器执行器
 * 根据代码生成类型创建合适的流处理器：
 * 1. SSE 流（HTML、MULTI_FILE） -> SimpleTextStreamHandler（透传并收集 AI 内容）
 * 2. TokenStream 格式的复杂流（VUE_PROJECT） -> JsonMessageStreamHandler
 */
@Slf4j
@Component
public class StreamHandlerExecutor {

    @Resource
    private JsonMessageStreamHandler jsonMessageStreamHandler;

    /**
     * 创建流处理器并处理聊天历史记录
     *
     * @param originFlux         原始 SSE 流
     * @param chatHistoryService 聊天历史服务
     * @param appId              应用ID
     * @param loginUser          登录用户
     * @param codeGenType        代码生成类型
     * @return 处理后的 SSE 流
     */
    public Flux<ServerSentEvent<String>> doExecute(Flux<ServerSentEvent<String>> originFlux,
                                                   ChatHistoryService chatHistoryService,
                                                   long appId, User loginUser, CodeGenTypeEnum codeGenType) {
        return switch (codeGenType) {
            case VUE_PROJECT -> {
                // 只把默认 message 事件中的 data 交给 JsonMessageStreamHandler，过滤掉 done/business-error 事件
                Flux<String> dataFlux = originFlux
                        .filter(event -> event.event() == null)
                        .mapNotNull(event -> event.data());
                Flux<String> handledFlux = jsonMessageStreamHandler.handle(dataFlux, chatHistoryService, appId, loginUser);
                Flux<ServerSentEvent<String>> wrapped = handledFlux.map(chunk -> ServerSentEvent.<String>builder().data(chunk).build());
                // 最后追加 done 事件
                yield Flux.concat(wrapped, Mono.just(ServerSentEvent.<String>builder().event("done").data("").build()));
            }
            case HTML, MULTI_FILE -> // 简单文本处理器不需要依赖注入
                    new SimpleTextStreamHandler().handle(originFlux, chatHistoryService, appId, loginUser);
        };
    }
}
