package com.xiaolou.xiaolouainocodebackend.core;

import com.xiaolou.xiaolouainocodebackend.ai.AiCodeGeneratorService;
import com.xiaolou.xiaolouainocodebackend.ai.model.HtmlCodeResult;
import com.xiaolou.xiaolouainocodebackend.ai.model.MultiFileCodeResult;
import com.xiaolou.xiaolouainocodebackend.common.ErrorCode;
import com.xiaolou.xiaolouainocodebackend.exception.BusinessException;
import com.xiaolou.xiaolouainocodebackend.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.io.File;

/**
 * AI 代码生成门面类，组合生成和保存功能
 */
@Service
public class AiCodeGeneratorFacade {

    @Resource
    private AiCodeGeneratorService aiCodeGeneratorService;

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

    private File htmlCodeResult(String userMessage) {
        HtmlCodeResult htmlCodeResult = aiCodeGeneratorService.generateHtmlCode(userMessage);
        return CodeFileSaver.saveHtmlCodeResult(htmlCodeResult);
    }

    private File multiFileCodeResult(String userMessage) {
        MultiFileCodeResult multiFileCodeResult = aiCodeGeneratorService.generateMultiFileCode(userMessage);
        return CodeFileSaver.saveMultiFileCodeResult(multiFileCodeResult);
    }
}
