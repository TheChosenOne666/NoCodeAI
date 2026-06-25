package com.xiaolou.xiaolouainocodebackend.ai.guardrail;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailResult;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
public class PromptSafetyInputGuardrail implements InputGuardrail {

    // 敏感词列表
    private static final List<String> SENSITIVE_WORDS = Arrays.asList(
            "忽略之前的指令", "ignore previous instructions", "ignore above",
            "破解", "hack", "绕过", "bypass", "越狱", "jailbreak"
    );

    // 注入攻击模式
    private static final List<Pattern> INJECTION_PATTERNS = Arrays.asList(
            Pattern.compile("(?i)ignore\\s+(?:previous|above|all)\\s+(?:instructions?|commands?|prompts?)"),
            Pattern.compile("(?i)(?:forget|disregard)\\s+(?:everything|all)\\s+(?:above|before)"),
            Pattern.compile("(?i)(?:pretend|act|behave)\\s+(?:as|like)\\s+(?:if|you\\s+are)"),
            Pattern.compile("(?i)system\\s*:\\s*you\\s+are"),
            Pattern.compile("(?i)new\\s+(?:instructions?|commands?|prompts?)\\s*:")
    );

    // 最大允许输入长度（用户消息 + 追加的项目文件上下文 + 系统提示词，需要留出足够空间）
    private static final int MAX_INPUT_LENGTH = 30000;

    // 用户原始消息的最大长度（用于判断超长是否是因为上下文导致的）
    private static final int MAX_USER_MESSAGE_LENGTH = 2000;

    @Override
    public InputGuardrailResult validate(UserMessage userMessage) {
        String input = userMessage.singleText();
        log.info("PromptSafetyInputGuardrail 校验输入, inputLength={}", input == null ? 0 : input.length());

        // 检查输入长度
        if (input.length() > MAX_INPUT_LENGTH) {
            log.warn("输入内容超过最大限制: {} > {}", input.length(), MAX_INPUT_LENGTH);
            return fatal("输入内容过长，请简化您的需求描述");
        }

        // 检查是否为空（只检查去除上下文标记后的用户消息部分）
        String trimmedInput = input;
        // 如果包含上下文标记，只检查上下文之前的原始用户消息
        int contextMarkerIndex = input.indexOf("【当前项目已有文件上下文】");
        if (contextMarkerIndex > 0) {
            trimmedInput = input.substring(0, contextMarkerIndex);
        }
        if (trimmedInput.trim().isEmpty()) {
            log.warn("用户消息为空");
            return fatal("输入内容不能为空");
        }

        // 检查敏感词
        String lowerInput = input.toLowerCase();
        for (String sensitiveWord : SENSITIVE_WORDS) {
            if (lowerInput.contains(sensitiveWord.toLowerCase())) {
                log.warn("检测到敏感词: {}", sensitiveWord);
                return fatal("输入包含不当内容，请修改后重试");
            }
        }
        // 检查注入攻击模式
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                log.warn("检测到恶意输入模式");
                return fatal("检测到恶意输入，请求被拒绝");
            }
        }
        log.info("PromptSafetyInputGuardrail 校验通过");
        return success();
    }
}
