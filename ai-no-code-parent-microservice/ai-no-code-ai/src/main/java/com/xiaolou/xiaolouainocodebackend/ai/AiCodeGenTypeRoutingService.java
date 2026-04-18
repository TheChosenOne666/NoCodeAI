package com.xiaolou.xiaolouainocodebackend.ai;

import com.xiaolou.xiaolouainocodebackend.model.enums.CodeGenTypeEnum;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/**
 * 智能路由服务
 */
public interface AiCodeGenTypeRoutingService {

    /**
     * 根据用户输入的提示信息，路由到对应的代码生成类型
     *
     * @param userPrompt 用户输入的提示信息
     * @return 对应的代码生成类型
     */
    @SystemMessage(fromResource = "prompt/code-gen-routing-system-prompt.txt")
    CodeGenTypeEnum routeCodeGenType(@UserMessage String userPrompt);

}
