package com.xiaolou.xiaolouainocodebackend.model.dto.codegen;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 代码生成实时展示事件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeGenStreamEvent {

    /**
     * 事件类型
     *
     * @see com.xiaolou.xiaolouainocodebackend.model.enums.CodeGenStreamEventType
     */
    private String type;

    /**
     * 文件相对路径（file-start / code-chunk / file-end 时使用）
     */
    private String path;

    /**
     * 代码语言（file-start 时使用，便于前端 Monaco 选择语言）
     */
    private String language;

    /**
     * 代码片段内容（code-chunk 时使用）
     */
    private String content;

    /**
     * 进度/提示文本（build-progress / error 时使用）
     */
    private String message;

    /**
     * 预览地址（preview-ready 时使用）
     */
    private String url;
}
