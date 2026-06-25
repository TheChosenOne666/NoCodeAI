package com.xiaolou.xiaolouainocodebackend.ai.tools;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONObject;
import com.xiaolou.xiaolouainocodebackend.constant.AppConstant;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * 文件写入工具
 * 支持 AI 通过工具调用的方式写入文件
 */
@Slf4j
@Component
public class FileWriteTool extends BaseTool{
    @Tool("写入文件到指定路径")
    public String writeFile(@P("文件的相对路径，例如 src/pages/Home.vue") String relativeFilePath, @P("要写入文件的内容") String content, @ToolMemoryId Long appId) {
        if (relativeFilePath == null || relativeFilePath.isBlank()) {
            return "文件写入失败：relativeFilePath 不能为空";
        }
        if (content == null) {
            content = "";
        }
        try {
            Path path = Paths.get(relativeFilePath);
            if (!path.isAbsolute()) {
                // 相对路径处理，创建基于 appId 的项目目录
                String projectDirName = "vue_project_" + appId;
                Path projectRoot = Paths.get(AppConstant.CODE_OUTPUT_ROOT_DIR, projectDirName);
                path = projectRoot.resolve(relativeFilePath);
            }
            // 创建父目录（如果不存在）
            Path parentDir = path.getParent();
            if (parentDir != null) {
                Files.createDirectories(parentDir);
            }
            // 写入文件内容
            Files.write(path, content.getBytes(),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            log.info("成功写入文件: {}", path.toAbsolutePath());
            // 注意要返回相对路径，不能让 AI 把文件绝对路径返回给用户
            return "文件写入成功: " + relativeFilePath;
        } catch (IOException e) {
            String errorMessage = "文件写入失败: " + relativeFilePath + ", 错误: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }
    @Override
    public String getToolName(){
        return "writeFile";
    }
    @Override
    public String getDisplayName() {
        return "写入文件";
    }
    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        String relativeFilePath = arguments.getStr("relativeFilePath");
        if (relativeFilePath == null || relativeFilePath.isBlank()) {
            return "[工具调用] 写入文件参数错误：relativeFilePath 不能为空";
        }
        String suffix = FileUtil.getSuffix(relativeFilePath);
        String content = arguments.getStr("content");
        if (content == null) {
            content = "";
        }
        // 过长的内容截断显示，避免前端卡顿
        String displayContent = content.length() > 2000 ? content.substring(0, 2000) + "\n...（已截断）" : content;
        return String.format("""
                        [工具调用] %s %s
                        ```%s
                        %s
                        ```
                        """, getDisplayName(), relativeFilePath, suffix, displayContent);
    }
}
