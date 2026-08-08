package com.xiaolou.xiaolouainocodebackend.model.enums;

import lombok.Getter;

/**
 * 代码生成实时展示事件类型
 */
@Getter
public enum CodeGenStreamEventType {

    /**
     * 开始生成某个文件
     */
    FILE_START("file-start", "开始生成文件"),

    /**
     * 追加代码片段
     */
    CODE_CHUNK("code-chunk", "代码片段"),

    /**
     * 单个文件生成结束
     */
    FILE_END("file-end", "文件生成结束"),

    /**
     * 开始构建
     */
    BUILD_START("build-start", "开始构建"),

    /**
     * 构建进度
     */
    BUILD_PROGRESS("build-progress", "构建进度"),

    /**
     * 构建结束
     */
    BUILD_END("build-end", "构建结束"),

    /**
     * 预览就绪
     */
    PREVIEW_READY("preview-ready", "预览就绪"),

    /**
     * 发生错误
     */
    ERROR("error", "错误");

    private final String value;
    private final String text;

    CodeGenStreamEventType(String value, String text) {
        this.value = value;
        this.text = text;
    }
}
