package com.xiaolou.xiaolouainocodebackend.ai;

import com.xiaolou.xiaolouainocodebackend.ai.model.HtmlCodeResult;
import com.xiaolou.xiaolouainocodebackend.ai.model.MultiFileCodeResult;
import dev.langchain4j.service.SystemMessage;

/**
 * AI代码生成服务接口
 */
public interface AiCodeGeneratorService {

    /**
     * 生成 HTML 代码
     *
     * @param userMessage
     * @return
     */
    @SystemMessage(fromResource = "prompt/code-gen-html-system-prompt.txt")
    HtmlCodeResult generateHtmlCode(String userMessage);

    /**
     * 生成多文件代码
     *
     * @param userMessage
     * @return
     */
    @SystemMessage(fromResource = "prompt/code-gen-multi-file-system-prompt.txt")
    MultiFileCodeResult generateMultiFileCode(String userMessage);
}
