package com.xiaolou.xiaolouainocodebackend.ai;

import com.xiaolou.xiaolouainocodebackend.ai.model.HtmlCodeResult;
import com.xiaolou.xiaolouainocodebackend.ai.model.MultiFileCodeResult;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import reactor.core.publisher.Flux;

/**
 * AI代码生成服务接口
 */
public interface AiCodeGeneratorService {

    /**
     * 生成vue项目代码（流式）
     *
     * @param userMessage
     * @return
     */
    @SystemMessage(fromResource = "prompt/code-gen-vue-project-system-prompt.txt")
    TokenStream generateVueProjectCodeStream(@MemoryId long appId, @UserMessage String userMessage);

    /**
     * 生成 HTML 代码（流式）
     *
     * @param userMessage
     * @return
     */
    @SystemMessage(fromResource = "prompt/code-gen-html-system-prompt.txt")
    Flux<String> generateHtmlCodeStream(@UserMessage String userMessage);

    /**
     * 生成多文件代码（流式）
     *
     * @param userMessage
     * @return
     */
    @SystemMessage(fromResource = "prompt/code-gen-multi-file-system-prompt.txt")
    Flux<String> generateMultiFileCodeStream(@UserMessage String userMessage);

    /**
     * 生成 HTML 代码
     *
     * @param userMessage
     * @return
     */
    @SystemMessage(fromResource = "prompt/code-gen-html-system-prompt.txt")
    HtmlCodeResult generateHtmlCode(@UserMessage String userMessage);

    /**
     * 生成多文件代码
     *
     * @param userMessage
     * @return
     */
    @SystemMessage(fromResource = "prompt/code-gen-multi-file-system-prompt.txt")
    MultiFileCodeResult generateMultiFileCode(@UserMessage String userMessage);
}
