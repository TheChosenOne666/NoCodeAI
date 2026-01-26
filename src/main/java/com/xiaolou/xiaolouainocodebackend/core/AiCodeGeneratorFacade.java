package com.xiaolou.xiaolouainocodebackend.core;

import com.xiaolou.xiaolouainocodebackend.ai.AiCodeGeneratorService;
import com.xiaolou.xiaolouainocodebackend.ai.model.HtmlCodeResult;
import com.xiaolou.xiaolouainocodebackend.ai.model.MultiFileCodeResult;
import com.xiaolou.xiaolouainocodebackend.common.ErrorCode;
import com.xiaolou.xiaolouainocodebackend.exception.BusinessException;
import com.xiaolou.xiaolouainocodebackend.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;

/**
 * AI 代码生成门面类，组合生成和保存功能
 */
@Service
@Slf4j
public class AiCodeGeneratorFacade {

    @Resource
    private AiCodeGeneratorService aiCodeGeneratorService;

    /**
     * 统一入口：生成并保存代码（流式）
     * @param userMessage
     * @param codeGenTypeEnum
     * @return
     */
    public Flux<String> generateAndSaveCodeStream(String userMessage, CodeGenTypeEnum codeGenTypeEnum){
        if (codeGenTypeEnum == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "代码生成类型不能为空");
        }
        return switch (codeGenTypeEnum) {
            case HTML -> htmlCodeResultStream(userMessage);
            case MULTI_FILE -> multiFileCodeResultStream(userMessage);
            default -> {
                String systemError = "不支持的生成类型" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, systemError);
            }
        };
    }


    /**
     * 生成并保存 HTML 代码（流式）
     * @param userMessage
     * @return
     */
    private Flux<String> htmlCodeResultStream(String userMessage) {
        Flux<String> result = aiCodeGeneratorService.generateHtmlCodeStream(userMessage);
        StringBuilder codeBuild = new StringBuilder();
        return
                // 实时收集代码片段
                result.doOnNext(codeBuild::append)
                .doOnComplete(() -> {
                    try {
                        // 流式返回完成后保存代码
                        HtmlCodeResult htmlCodeResult = CodeParser.parseHtmlCode(codeBuild.toString());
                        File savedDir = CodeFileSaver.saveHtmlCodeResult(htmlCodeResult);
                        log.info("代码保存成功，路径为：{}", savedDir.getAbsolutePath());
                    } catch (Exception e) {
                        log.error("代码保存失败: {}", e.getMessage());
                    }

                });

    }
    /**
     * 生成并保存多文件代码（流式）
     * @param userMessage
     * @return
     */
    private Flux<String> multiFileCodeResultStream(String userMessage) {
        Flux<String> result = aiCodeGeneratorService.generateMultiFileCodeStream(userMessage);
        StringBuilder codeBuild = new StringBuilder();
        return
                result.doOnNext(codeBuild::append)
                        .doOnComplete(() -> {
                            try {
                                MultiFileCodeResult multiFileCodeResult = CodeParser.parseMultiFileCode(codeBuild.toString());
                                File savedDir = CodeFileSaver.saveMultiFileCodeResult(multiFileCodeResult);
                                log.info("代码保存成功，路径为：{}", savedDir.getAbsolutePath());
                            } catch (Exception e){
                                log.error("代码保存失败: {}", e.getMessage());
                            }
                        });
    }

    /**
     * 统一入口：生成并保存代码
     * @param userMessage
     * @param codeGenTypeEnum
     * @return
     */
    public File generateAndSaveCode(String userMessage, CodeGenTypeEnum codeGenTypeEnum){
        if (codeGenTypeEnum == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "代码生成类型不能为空");
        }
        return switch (codeGenTypeEnum) {
            case HTML -> htmlCodeResult(userMessage);
            case MULTI_FILE -> multiFileCodeResult(userMessage);
            default -> {
                String systemError = "不支持的生成类型" + codeGenTypeEnum.getValue();
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, systemError);
            }
        };
    }

    /**
     * 生成并保存 HTML 代码
     * @param userMessage
     * @return
     */
    private File htmlCodeResult(String userMessage) {
        HtmlCodeResult htmlCodeResult = aiCodeGeneratorService.generateHtmlCode(userMessage);
        return CodeFileSaver.saveHtmlCodeResult(htmlCodeResult);
    }

    /**
     * 生成并保存多文件代码
     * @param userMessage
     * @return
     */
    private File multiFileCodeResult(String userMessage) {
        MultiFileCodeResult multiFileCodeResult = aiCodeGeneratorService.generateMultiFileCode(userMessage);
        return CodeFileSaver.saveMultiFileCodeResult(multiFileCodeResult);
    }
}
