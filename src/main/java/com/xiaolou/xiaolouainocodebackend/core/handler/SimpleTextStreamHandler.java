package com.xiaolou.xiaolouainocodebackend.core.handler;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.xiaolou.xiaolouainocodebackend.model.entity.User;
import com.xiaolou.xiaolouainocodebackend.model.enums.ChatHistoryMessageTypeEnum;
import com.xiaolou.xiaolouainocodebackend.service.ChatHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * 简单文本流处理器
 * 处理 HTML 和 MULTI_FILE 类型的流式响应
 */
@Slf4j
public class SimpleTextStreamHandler {

    /**
     * 处理传统流（HTML, MULTI_FILE）
     * 透传 SSE 事件，同时收集 data 事件中的 AI 响应内容用于保存对话历史。
     *
     * @param originFlux         原始 SSE 流（data 事件 data 字段为 JSON 字符串 {"data": chunk}）
     * @param chatHistoryService 聊天历史服务
     * @param appId              应用ID
     * @param loginUser          登录用户
     * @return 处理后的 SSE 流
     */
    public Flux<ServerSentEvent<String>> handle(Flux<ServerSentEvent<String>> originFlux,
                                                 ChatHistoryService chatHistoryService,
                                                 long appId, User loginUser) {
        StringBuilder aiResponseBuilder = new StringBuilder();
        return originFlux
                .doOnNext(event -> {
                    // 仅收集默认 message 事件（event 为空）中的 AI 响应内容
                    if (event.event() == null && event.data() != null) {
                        String chunk = extractChunk(event.data());
                        aiResponseBuilder.append(chunk);
                    }
                })
                .doOnComplete(() -> {
                    // 流式响应完成后，添加AI消息到对话历史
                    String aiResponse = aiResponseBuilder.toString();
                    chatHistoryService.addChatMessage(appId, aiResponse, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                })
                .doOnError(error -> {
                    // 如果AI回复失败，也要记录错误消息
                    String errorMessage = "AI回复失败: " + error.getMessage();
                    chatHistoryService.addChatMessage(appId, errorMessage, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
                });
    }

    /**
     * 从 SSE data 字段的 JSON 包装中提取原始 chunk。
     * 兼容非 JSON 的兜底情况。
     */
    private String extractChunk(String dataJson) {
        try {
            JSONObject jsonObject = JSONUtil.parseObj(dataJson);
            return jsonObject.getStr("data", "");
        } catch (Exception e) {
            return dataJson;
        }
    }
}
