package com.xiaolou.xiaolouainocodebackend.core;

import cn.hutool.json.JSONUtil;
import com.xiaolou.xiaolouainocodebackend.ai.AiCodeGeneratorService;
import com.xiaolou.xiaolouainocodebackend.ai.AiCodeGeneratorServiceFactory;
import com.xiaolou.xiaolouainocodebackend.ai.model.HtmlCodeResult;
import com.xiaolou.xiaolouainocodebackend.ai.model.MultiFileCodeResult;
import com.xiaolou.xiaolouainocodebackend.ai.model.message.AiResponseMessage;
import com.xiaolou.xiaolouainocodebackend.ai.model.message.ToolExecutedMessage;
import com.xiaolou.xiaolouainocodebackend.ai.model.message.ToolRequestMessage;
import com.xiaolou.xiaolouainocodebackend.common.ErrorCode;
import com.xiaolou.xiaolouainocodebackend.constant.AppConstant;
import com.xiaolou.xiaolouainocodebackend.core.builder.VueProjectBuilder;
import com.xiaolou.xiaolouainocodebackend.core.parser.CodeParserExecutor;
import com.xiaolou.xiaolouainocodebackend.core.saver.CodeFileSaverExecutor;
import com.xiaolou.xiaolouainocodebackend.exception.BusinessException;
import com.xiaolou.xiaolouainocodebackend.model.enums.CodeGenTypeEnum;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.tool.ToolExecution;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.Map;

/**
 * AI 代码生成门面类，组合生成和保存功能
 */
@Service
@Slf4j
public class AiCodeGeneratorFacade {

    @Resource
    private AiCodeGeneratorServiceFactory aiCodeGeneratorServiceFactory;

    @Resource
    private VueProjectBuilder vueProjectBuilder;

    /**
     * 统一入口：根据类型生成并保存代码
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @param appId 应用id
     * @return 保存的目录
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        // 根据appId获取对应的AI服务生成实例
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId);
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        return switch (codeGenTypeEnum) {
            case HTML -> {
                HtmlCodeResult result = aiCodeGeneratorService.generateHtmlCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                MultiFileCodeResult result = aiCodeGeneratorService.generateMultiFileCode(userMessage);
                yield CodeFileSaverExecutor.executeSaver(result, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }

    /**
     * 统一入口：根据类型生成并保存代码（流式）
     *
     * @param userMessage     用户提示词
     * @param codeGenTypeEnum 生成类型
     * @param appId 应用id
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum, Long appId) {
        AiCodeGeneratorService aiCodeGeneratorService = aiCodeGeneratorServiceFactory.getAiCodeGeneratorService(appId, codeGenTypeEnum);
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成类型为空");
        }
        return switch (codeGenTypeEnum) {
            case HTML -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.HTML, appId);
            }
            case MULTI_FILE -> {
                Flux<String> codeStream = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
                yield processCodeStream(codeStream, CodeGenTypeEnum.MULTI_FILE, appId);
            }
            case VUE_PROJECT -> {
                TokenStream tokenStream = aiCodeGeneratorService.generateVueProjectCodeStream(appId, userMessage);
                yield processTokenStream(tokenStream, appId);
            }
            default -> {
                String errorMessage = "不支持的生成类型：" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, errorMessage);
            }
        };
    }
    /**
     * 通用流式代码处理方法
     *
     * @param codeStream  代码流
     * @param codeGenType 代码生成类型
     * @param appId 应用id
     * @return 流式响应
     */
    private Flux<String> processCodeStream(Flux<String> codeStream, CodeGenTypeEnum codeGenType, Long appId) {
        StringBuilder codeBuilder = new StringBuilder();
        // 实时收集代码片段
        return codeStream.doOnNext(codeBuilder::append).doOnComplete(() -> {
            // 流式返回完成后保存代码
            try {
                String completeCode = codeBuilder.toString();
                // 使用执行器解析代码
                Object parsedResult = CodeParserExecutor.executeParser(completeCode, codeGenType);
                // 使用执行器保存代码
                File savedDir = CodeFileSaverExecutor.executeSaver(parsedResult, codeGenType, appId);
                log.info("保存成功，路径为：" + savedDir.getAbsolutePath());
            } catch (Exception e) {
                log.error("保存失败: {}", e.getMessage());
            }
        });
    }

    /**
     * 将 TokenStream 转换为 Flux<String>，并传递工具调用信息
     *
     * @param tokenStream TokenStream 对象
     * @return Flux<String> 流式响应
     */
    private Flux<String> processTokenStream(TokenStream tokenStream, Long appId) {
        return Flux.create(sink -> {
            tokenStream.onPartialResponse((String partialResponse) -> {
                        try {
                            AiResponseMessage aiResponseMessage = new AiResponseMessage(partialResponse);
                            sink.next(JSONUtil.toJsonStr(aiResponseMessage));
                        } catch (Exception e) {
                            log.error("Error processing partial response: {}", e.getMessage(), e);
                        }
                    })
                    .onPartialToolExecutionRequest((index, toolExecutionRequest) -> {
                        try {
                            ToolRequestMessage toolRequestMessage = new ToolRequestMessage(toolExecutionRequest);
                            sink.next(JSONUtil.toJsonStr(toolRequestMessage));
                        } catch (Exception e) {
                            log.error("Error processing tool execution request: {}", e.getMessage(), e);
                        }
                    })
                    .onToolExecuted((ToolExecution toolExecution) -> {
                        try {
                            ToolExecutedMessage toolExecutedMessage = new ToolExecutedMessage(toolExecution);
                            sink.next(JSONUtil.toJsonStr(toolExecutedMessage));
                        } catch (Exception e) {
                            log.error("Error processing tool execution: {}", e.getMessage(), e);
                        }
                    })
                    .onCompleteResponse((ChatResponse response) -> {
                        // 执行vue项目构建（同步执行，确保预览时项目已就绪）
                        String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + "vue_project_" + appId;
                        vueProjectBuilder.buildProject(projectPath);
                        sink.complete();
                    })
                    .onError((Throwable error) -> {
                        log.error("Error in token stream: {}", error.getMessage(), error);
                        // 发送错误信息到客户端
                        try {
                            Map<String, Object> errorData = Map.of(
                                "error", true,
                                "message", error.getMessage()
                            );
                            sink.next(JSONUtil.toJsonStr(errorData));
                        } catch (Exception e) {
                            log.error("Failed to send error to client: {}", e.getMessage());
                        }
                        sink.complete();
                    })
                    .start();
        });
    }

}
