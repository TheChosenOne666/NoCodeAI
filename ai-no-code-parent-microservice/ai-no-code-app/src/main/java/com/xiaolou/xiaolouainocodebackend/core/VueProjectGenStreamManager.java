package com.xiaolou.xiaolouainocodebackend.core;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.xiaolou.xiaolouainocodebackend.ai.AiCodeGeneratorService;
import com.xiaolou.xiaolouainocodebackend.ai.AiCodeGeneratorServiceFactory;
import com.xiaolou.xiaolouainocodebackend.ai.model.message.AiResponseMessage;
import com.xiaolou.xiaolouainocodebackend.ai.model.message.ToolExecutedMessage;
import com.xiaolou.xiaolouainocodebackend.constant.AppConstant;
import com.xiaolou.xiaolouainocodebackend.core.builder.VueProjectBuilder;
import com.xiaolou.xiaolouainocodebackend.mapper.AppMapper;
import com.xiaolou.xiaolouainocodebackend.model.dto.codegen.CodeGenStreamEvent;
import com.xiaolou.xiaolouainocodebackend.model.entity.App;
import com.xiaolou.xiaolouainocodebackend.model.enums.CodeGenStreamEventType;
import com.xiaolou.xiaolouainocodebackend.model.enums.CodeGenTypeEnum;
import com.xiaolou.xiaolouainocodebackend.util.ProjectContextBuilder;
import com.xiaolou.xiaolouainocodebackend.utils.CodeLanguageUtils;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.io.File;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Vue 项目生成流管理器
 * <p>
 * 用于在「左侧对话 SSE」和「右侧代码实时展示 SSE」之间共享同一次 AI 生成过程，
 * 避免对同一个用户请求调用两次 AI。
 */
@Slf4j
@Component
public class VueProjectGenStreamManager {

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    @Resource
    private AppMapper appMapper;

    @Resource
    private com.xiaolou.xiaolouainocodebackend.manager.CosManager cosManager;

    /**
     * 生成会话缓存，key: requestId
     */
    private final Map<String, GenSession> sessionCache = new ConcurrentHashMap<>();

    /**
     * 获取或创建生成会话
     *
     * @param appId     应用ID
     * @param requestId 请求ID，用于唯一标识一次生成请求
     * @param message   用户消息
     * @return 生成会话
     */
    public GenSession getOrCreateSession(Long appId, String requestId, String message) {
        String key = StrUtil.isNotBlank(requestId) ? requestId : appId + ":" + message;
        return sessionCache.computeIfAbsent(key, k -> {
            log.info("创建 Vue 项目共享生成会话, appId: {}, requestId: {}", appId, key);
            GenSession session = new GenSession(appId, message);
            session.start();
            return session;
        });
    }

    /**
     * 移除生成会话
     */
    private void removeSession(String key) {
        sessionCache.remove(key);
    }

    /**
     * 根据 requestId 停止生成会话
     *
     * @param requestId 请求ID
     */
    public void stopSession(String requestId) {
        if (StrUtil.isBlank(requestId)) {
            return;
        }
        GenSession session = sessionCache.get(requestId);
        if (session != null) {
            session.stop();
        }
    }

    /**
     * 生成会话
     */
    public class GenSession {

        private final Long appId;
        private final String message;
        private final String key;
        private final Sinks.Many<String> chatSink;
        private final Sinks.Many<ServerSentEvent<CodeGenStreamEvent>> detailSink;
        private final AtomicBoolean stopped = new AtomicBoolean(false);
        private volatile boolean started = false;

        public GenSession(Long appId, String message) {
            this.appId = appId;
            this.message = message;
            this.key = appId + ":" + message;
            this.chatSink = Sinks.many().multicast().onBackpressureBuffer(1024, false);
            this.detailSink = Sinks.many().multicast().onBackpressureBuffer(1024, false);
        }

        /**
         * 停止当前生成会话
         */
        public void stop() {
            if (stopped.compareAndSet(false, true)) {
                log.info("用户主动停止 Vue 项目生成会话, appId: {}, requestId: {}", appId, key);
                try {
                    AiResponseMessage stopMessage = new AiResponseMessage("\n\n已停止生成。已保留当前生成的代码，您可以继续提出需求进行完善。");
                    chatSink.tryEmitNext(JSONUtil.toJsonStr(stopMessage));
                } catch (Exception e) {
                    log.error("发送停止消息失败: {}", e.getMessage());
                }
                chatSink.tryEmitComplete();
                detailSink.tryEmitComplete();
                removeSession(key);
            }
        }

        /**
         * 检查会话是否已停止
         */
        private boolean isStopped() {
            return stopped.get();
        }

        /**
         * 启动 AI 生成
         */
        public synchronized void start() {
            if (started) {
                return;
            }
            started = true;

            try {
                AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory
                        .getAiCodeGeneratorService(appId, CodeGenTypeEnum.VUE_PROJECT);

                // 如果项目已有文件，追加上下文，让 AI 基于已有代码继续完善
                String projectContext = ProjectContextBuilder.buildContext(appId);
                String messageWithContext = message + projectContext;

                TokenStream tokenStream = aiCodeGeneratorService.generateVueProjectCodeStream(appId, messageWithContext);

                tokenStream.onPartialResponse((String partialResponse) -> {
                            if (isStopped()) {
                                return;
                            }
                            try {
                                AiResponseMessage aiResponseMessage = new AiResponseMessage(partialResponse);
                                String json = JSONUtil.toJsonStr(aiResponseMessage);
                                chatSink.tryEmitNext(json);
                            } catch (Exception e) {
                                log.error("Error processing partial response: {}", e.getMessage(), e);
                            }
                        })
                        .onToolExecuted((ToolExecution toolExecution) -> {
                            if (isStopped()) {
                                return;
                            }
                            try {
                                ToolExecutedMessage toolExecutedMessage = new ToolExecutedMessage(toolExecution);
                                String json = JSONUtil.toJsonStr(toolExecutedMessage);
                                chatSink.tryEmitNext(json);

                                // 同时向 detail sink 发送文件事件
                                emitToolExecutedDetail(toolExecution);
                            } catch (Exception e) {
                                log.error("Error processing tool execution: {}", e.getMessage(), e);
                            }
                        })
                        .onCompleteResponse((ChatResponse response) -> {
                            if (isStopped()) {
                                return;
                            }

                            // 构建仅在 AI 生成完全结束后触发，确保左右面板内容一致
                            String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + "vue_project_" + appId;
                            detailSink.tryEmitNext(buildEvent(CodeGenStreamEventType.BUILD_START, null, null, null, null, null));

                            try {
                                boolean buildSuccess = vueProjectBuilder.buildProject(projectPath);

                                if (buildSuccess) {
                                    detailSink.tryEmitNext(buildEvent(CodeGenStreamEventType.BUILD_END, null, null, null, null, null));
                                    String previewUrl = "/api/static/vue_project_" + appId + "/dist/index.html";
                                    detailSink.tryEmitNext(buildEvent(CodeGenStreamEventType.PREVIEW_READY, null, null, null, null, previewUrl));

                                    // 自动更新部署目录（如果已部署过，确保部署 URL 也展示最新内容）
                                    try {
                                        App app = appMapper.selectById(appId);
                                        if (app != null && StrUtil.isNotBlank(app.getDeployKey())) {
                                            File distDir = new File(projectPath, "dist");
                                            if (distDir.exists() && distDir.isDirectory()) {
                                                if (cosManager != null) {
                                                    String cosPrefix = AppConstant.CODE_DEPLOY_COS_PREFIX + "/" + app.getDeployKey();
                                                    cosManager.uploadDir(cosPrefix, distDir);
                                                    log.info("已自动更新COS部署目录: appId={}, deployKey={}", appId, app.getDeployKey());
                                                } else {
                                                    String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + app.getDeployKey();
                                                    FileUtil.copyContent(distDir, new File(deployDirPath), true);
                                                    log.info("已自动更新部署目录: appId={}, deployKey={}", appId, app.getDeployKey());
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        log.warn("自动更新部署目录失败: appId={}, error={}", appId, e.getMessage());
                                    }
                                } else {
                                    detailSink.tryEmitNext(buildEvent(CodeGenStreamEventType.ERROR, null, null, null, "Vue 项目构建失败", null));
                                }
                            } catch (Exception e) {
                                log.error("Vue 项目构建过程发生异常: {}", e.getMessage(), e);
                                detailSink.tryEmitNext(buildEvent(CodeGenStreamEventType.ERROR, null, null, null, "Vue 项目构建异常: " + e.getMessage(), null));
                            } finally {
                                chatSink.tryEmitComplete();
                                detailSink.tryEmitComplete();
                                removeSession(key);
                            }
                        })
                        .onError((Throwable error) -> {
                            if (isStopped()) {
                                return;
                            }
                            log.error("Error in vue project stream: {}", error.getMessage(), error);
                            String errorMessage = "AI服务暂时不可用,请稍后重试";
                            if (error.getMessage() != null && error.getMessage().contains("rate_limit")) {
                                errorMessage = "AI服务繁忙,请稍后重试";
                            }
                            try {
                                AiResponseMessage aiResponseMessage = new AiResponseMessage("\n\n" + errorMessage);
                                chatSink.tryEmitNext(JSONUtil.toJsonStr(aiResponseMessage));
                            } catch (Exception e) {
                                log.error("Failed to send error to client: {}", e.getMessage());
                            }
                            detailSink.tryEmitNext(buildEvent(CodeGenStreamEventType.ERROR, null, null, null, errorMessage, null));
                            chatSink.tryEmitComplete();
                            detailSink.tryEmitComplete();
                            removeSession(key);
                        })
                        .start();
            } catch (Exception e) {
                log.error("启动 Vue 项目生成会话失败: {}", e.getMessage(), e);
                chatSink.tryEmitComplete();
                detailSink.tryEmitComplete();
                removeSession(key);
            }
        }

        /**
         * 将工具执行结果转换为 detail 事件
         */
        private void emitToolExecutedDetail(ToolExecution toolExecution) {
            try {
                String toolName = toolExecution.request().name();
                String argumentsJson = toolExecution.request().arguments();
                JSONObject arguments = JSONUtil.parseObj(argumentsJson);

                if ("writeFile".equals(toolName) || "modifyFile".equals(toolName)) {
                    String relativePath = arguments.getStr("relativeFilePath");
                    String content = arguments.getStr("content");
                    String language = CodeLanguageUtils.getLanguageByPath(relativePath);

                    detailSink.tryEmitNext(buildEvent(CodeGenStreamEventType.FILE_START, relativePath, language, null, null, null));
                    detailSink.tryEmitNext(buildEvent(CodeGenStreamEventType.CODE_CHUNK, relativePath, null, content, null, null));
                    detailSink.tryEmitNext(buildEvent(CodeGenStreamEventType.FILE_END, relativePath, null, null, null, null));
                }
            } catch (Exception e) {
                log.error("Error emitting tool executed detail: {}", e.getMessage(), e);
            }
        }

        /**
         * 获取聊天格式 SSE 流
         */
        public Flux<String> getChatFlux() {
            return chatSink.asFlux()
                    .timeout(Duration.ofMinutes(10))
                    .onErrorResume(error -> {
                        log.error("Chat flux error: {}", error.getMessage());
                        return Mono.empty();
                    });
        }

        /**
         * 获取右侧代码实时展示 SSE 流
         */
        public Flux<ServerSentEvent<CodeGenStreamEvent>> getDetailFlux() {
            return detailSink.asFlux()
                    .timeout(Duration.ofMinutes(10))
                    .onErrorResume(error -> {
                        log.error("Detail flux error: {}", error.getMessage());
                        return Mono.empty();
                    });
        }
    }

    /**
     * 构建 SSE 事件
     */
    private ServerSentEvent<CodeGenStreamEvent> buildEvent(CodeGenStreamEventType type, String path,
                                                           String language, String content, String message, String url) {
        CodeGenStreamEvent event = CodeGenStreamEvent.builder()
                .type(type.getValue())
                .path(path)
                .language(language)
                .content(content)
                .message(message)
                .url(url)
                .build();
        // 不设置 event name，统一使用默认事件，前端通过 onmessage 接收
        return ServerSentEvent.<CodeGenStreamEvent>builder()
                .data(event)
                .build();
    }
}
