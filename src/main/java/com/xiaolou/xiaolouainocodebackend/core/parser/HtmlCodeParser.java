package com.xiaolou.xiaolouainocodebackend.core.parser;

import com.xiaolou.xiaolouainocodebackend.ai.model.HtmlCodeResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML单文件代码解析器
 */
public class HtmlCodeParser implements CodeParser<HtmlCodeResult> {

    /**
     * 闭合的 HTML 代码块：```html ... ```（含结束标记）。
     */
    private static final Pattern HTML_CODE_PATTERN = Pattern.compile("```html\\s*\\n([\\s\\S]*?)```", Pattern.CASE_INSENSITIVE);
    /**
     * 未闭合兜底：```html ... 到内容末尾。某些模型输出被截断时，结束标记可能丢失。
     */
    private static final Pattern HTML_CODE_PATTERN_UNCLOSED = Pattern.compile("```html\\s*\\n([\\s\\S]*)", Pattern.CASE_INSENSITIVE);

    @Override
    public HtmlCodeResult parseCode(String codeContent) {
        HtmlCodeResult result = new HtmlCodeResult();
        // 提取 HTML 代码
        String htmlCode = extractHtmlCode(codeContent);
        if (htmlCode != null && !htmlCode.trim().isEmpty()) {
            result.setHtmlCode(htmlCode.trim());
        } else {
            // 如果没有找到代码块，将整个内容作为HTML
            result.setHtmlCode(codeContent.trim());
        }
        return result;
    }

    /**
     * 提取 HTML 代码内容。优先匹配完整闭合的代码块；若 AI 输出被截断没有结束标记，
     * 兜底提取 ```html 之后到内容末尾的部分，尽量挽救可运行代码。
     *
     * @param content 原始内容
     * @return HTML代码
     */
    private static String extractHtmlCode(String content) {
        Matcher closedMatcher = HTML_CODE_PATTERN.matcher(content);
        if (closedMatcher.find()) {
            return closedMatcher.group(1);
        }
        Matcher unclosedMatcher = HTML_CODE_PATTERN_UNCLOSED.matcher(content);
        if (unclosedMatcher.find()) {
            return unclosedMatcher.group(1);
        }
        return null;
    }
}
