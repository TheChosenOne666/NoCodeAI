package com.xiaolou.xiaolouainocodebackend.utils;

import cn.hutool.core.util.StrUtil;

import java.util.Locale;

/**
 * 代码文件语言工具
 */
public class CodeLanguageUtils {

    /**
     * 根据文件路径获取 Monaco 语言标识
     *
     * @param path 文件相对路径
     * @return 语言标识
     */
    public static String getLanguageByPath(String path) {
        if (StrUtil.isBlank(path)) {
            return "plaintext";
        }
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".vue")) {
            return "vue";
        }
        if (lower.endsWith(".ts") || lower.endsWith(".tsx")) {
            return "typescript";
        }
        if (lower.endsWith(".js") || lower.endsWith(".jsx") || lower.endsWith(".mjs") || lower.endsWith(".cjs")) {
            return "javascript";
        }
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return "html";
        }
        if (lower.endsWith(".css")) {
            return "css";
        }
        if (lower.endsWith(".scss") || lower.endsWith(".sass")) {
            return "scss";
        }
        if (lower.endsWith(".less")) {
            return "less";
        }
        if (lower.endsWith(".json")) {
            return "json";
        }
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) {
            return "markdown";
        }
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) {
            return "yaml";
        }
        if (lower.endsWith(".xml")) {
            return "xml";
        }
        if (lower.endsWith(".java")) {
            return "java";
        }
        if (lower.endsWith(".py")) {
            return "python";
        }
        return "plaintext";
    }
}
