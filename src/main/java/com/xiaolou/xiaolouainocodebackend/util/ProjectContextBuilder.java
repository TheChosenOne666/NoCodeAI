package com.xiaolou.xiaolouainocodebackend.util;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.xiaolou.xiaolouainocodebackend.constant.AppConstant;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 项目上下文构建器
 * <p>
 * 用于在继续生成时读取已有项目文件，构造上下文给 AI，使其能够基于现有代码继续完善。
 */
@Slf4j
public class ProjectContextBuilder {

    /**
     * 关键文件优先级：排在前面的文件会被优先读取完整内容
     */
    private static final List<String> KEY_FILES = Arrays.asList(
            "package.json",
            "vite.config.js",
            "vite.config.ts",
            "src/App.vue",
            "src/main.js",
            "src/main.ts",
            "src/router/index.js",
            "src/router/index.ts",
            "src/style.css",
            "index.html"
    );

    /**
     * 单个文件最大读取字符数
     */
    private static final int MAX_FILE_LENGTH = 3000;

    /**
     * 上下文总长度上限（需为 guardrail 限制留出用户消息余量）
     */
    private static final int MAX_TOTAL_LENGTH = 10000;

    /**
     * 构建项目上下文
     *
     * @param appId 应用ID
     * @return 上下文字符串，如果项目不存在则返回空字符串
     */
    public static String buildContext(Long appId) {
        String projectPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + "vue_project_" + appId;
        File projectDir = new File(projectPath);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        context.append("\n\n【当前项目已有文件上下文】\n");
        context.append("项目目录: ").append(projectPath).append("\n");
        context.append("说明: 以下是我已经为你生成的项目文件。请结合用户的新需求，在已有基础上继续修改、补充或优化，不要完全重写整个项目。如果用户没有明确要求修改某个文件，请尽量保持其稳定。\n\n");

        // 1. 列出所有文件
        List<File> allFiles = FileUtil.loopFiles(projectDir, file -> !file.isDirectory());
        List<String> relativePaths = new ArrayList<>();
        for (File file : allFiles) {
            String relativePath = projectDir.toURI().relativize(file.toURI()).getPath();
            relativePaths.add(relativePath);
        }
        Collections.sort(relativePaths);

        context.append("文件列表:\n");
        for (String path : relativePaths) {
            context.append("- ").append(path).append("\n");
        }
        context.append("\n");

        // 2. 读取关键文件内容
        Set<String> readFiles = new LinkedHashSet<>();
        for (String keyFile : KEY_FILES) {
            File file = new File(projectDir, keyFile);
            if (file.exists() && file.isFile()) {
                readFiles.add(keyFile);
            }
        }

        // 如果关键文件不够，再少量补充 vue 文件
        if (readFiles.size() < 3) {
            for (String path : relativePaths) {
                if (readFiles.size() >= 5) {
                    break;
                }
                if (path.endsWith(".vue") || path.endsWith(".js") || path.endsWith(".ts")) {
                    readFiles.add(path);
                }
            }
        }

        for (String path : readFiles) {
            File file = new File(projectDir, path);
            if (!file.exists()) {
                continue;
            }
            try {
                String content = FileUtil.readString(file, StandardCharsets.UTF_8);
                if (StrUtil.isBlank(content)) {
                    continue;
                }
                if (content.length() > MAX_FILE_LENGTH) {
                    content = content.substring(0, MAX_FILE_LENGTH) + "\n\n...（内容已截断）";
                }
                context.append("--- 文件: ").append(path).append(" ---\n");
                context.append("```\n").append(content).append("\n```\n\n");
            } catch (Exception e) {
                log.warn("读取项目文件失败: {}, error: {}", file.getAbsolutePath(), e.getMessage());
            }

            if (context.length() > MAX_TOTAL_LENGTH) {
                context.append("\n...（上下文已截断，只展示了部分文件）");
                break;
            }
        }

        return context.toString();
    }
}
