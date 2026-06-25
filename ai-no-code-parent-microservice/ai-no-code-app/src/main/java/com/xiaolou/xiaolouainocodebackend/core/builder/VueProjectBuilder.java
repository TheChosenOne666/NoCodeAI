package com.xiaolou.xiaolouainocodebackend.core.builder;

import cn.hutool.core.util.RuntimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class VueProjectBuilder {

    /**
     * 异步构建 Vue 项目（不阻塞主线程）
     *
     * @param projectPath 项目根目录路径
     */
    public void buildProjectAsync(String projectPath) {
        Thread.ofVirtual().name("vue-builder-" + System.currentTimeMillis()).start(() -> {
            try {
                buildProject(projectPath);
            } catch (Exception e) {
                log.error("异步构建 Vue 项目时发生异常：{}", e.getMessage(), e);
            }
        });
    }

    /**
     * 构建 Vue 项目，失败后会自动诊断并重试
     *
     * @param projectPath 项目根目录路径
     * @return 是否构建成功
     */
    public boolean buildProject(String projectPath) {
        File projectDir = new File(projectPath);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            log.error("项目目录不存在: {}", projectPath);
            return false;
        }
        File packageJson = new File(projectDir, "package.json");
        if (!packageJson.exists()) {
            log.error("package.json 文件不存在: {}", packageJson.getAbsolutePath());
            return false;
        }
        log.info("开始构建 Vue 项目: {}", projectPath);

        // 构建前强制确保 vite.config.js 使用 base: './'
        ensureViteBaseRelative(projectDir);

        BuildResult firstResult = doBuild(projectDir);
        if (firstResult.success) {
            return true;
        }

        log.warn("首次构建失败，尝试自动修复后重试。错误:\n{}", firstResult.errorOutput);
        if (!tryAutoFix(projectDir, firstResult.errorOutput)) {
            log.error("自动修复失败，构建结束");
            return false;
        }

        BuildResult retryResult = doBuild(projectDir);
        if (retryResult.success) {
            log.info("自动修复后构建成功");
            return true;
        }
        log.error("重试构建仍然失败，错误:\n{}", retryResult.errorOutput);
        return false;
    }

    /**
     * 确保 vite.config.js 中包含 base: './'，避免通过 /api/static/... 子路径访问时资源 404 白屏
     */
    private void ensureViteBaseRelative(File projectDir) {
        File viteConfig = new File(projectDir, "vite.config.js");
        if (!viteConfig.exists()) {
            log.warn("vite.config.js 不存在，跳过 base 配置检查");
            return;
        }
        try {
            String content = java.nio.file.Files.readString(viteConfig.toPath(), StandardCharsets.UTF_8);
            // 如果已经正确配置 base: './' 则跳过
            if (content.contains("base: './'")) {
                return;
            }
            // 如果存在其他 base 配置，统一替换为 './'
            if (content.contains("base:")) {
                String replacedContent = content.replaceAll("base\\s*:\\s*['\"][^'\"]*['\"]", "base: './'");
                if (!replacedContent.equals(content)) {
                    java.nio.file.Files.writeString(viteConfig.toPath(), replacedContent, StandardCharsets.UTF_8);
                    log.info("已将 vite.config.js 的 base 配置自动修正为 './'");
                }
                return;
            }
            // 没有 base 配置时，在 defineConfig 后插入 base: './'
            String newContent = content.replaceFirst(
                    "(export\s+default\s+defineConfig\s*\\(",
                    "$1\\n  base: './',"
            );
            if (newContent.equals(content)) {
                // 可能是 const config = defineConfig({ ... })
                newContent = content.replaceFirst(
                        "(defineConfig\s*\\(\s*\\{)",
                        "$1\\n  base: './',"
                );
            }
            if (!newContent.equals(content)) {
                java.nio.file.Files.writeString(viteConfig.toPath(), newContent, StandardCharsets.UTF_8);
                log.info("已自动为 vite.config.js 添加 base: './'");
            }
        } catch (Exception e) {
            log.warn("检查/修改 vite.config.js 失败: {}", e.getMessage());
        }
    }

    /**
     * 执行一次完整的 install + build
     */
    private BuildResult doBuild(File projectDir) {
        if (!executeNpmInstall(projectDir)) {
            return BuildResult.fail("npm install 执行失败");
        }
        return executeNpmBuildWithOutput(projectDir);
    }

    /**
     * 根据构建错误输出尝试自动修复
     */
    private boolean tryAutoFix(File projectDir, String errorOutput) {
        String lowerError = errorOutput.toLowerCase();

        // 1. 依赖损坏或平台依赖问题：删除 node_modules 和 lock 文件后重新 install
        if (lowerError.contains("cannot find module") ||
            lowerError.contains("enoent") ||
            lowerError.contains("rollup") ||
            lowerError.contains("platform") ||
            lowerError.contains("optional dependency")) {
            log.info("检测到依赖问题，清理 node_modules 和 package-lock.json 后重试...");
            deleteDir(new File(projectDir, "node_modules"));
            File lockFile = new File(projectDir, "package-lock.json");
            if (lockFile.exists()) {
                lockFile.delete();
            }
            return true;
        }

        // 2. Vite / esbuild 相关错误：通常需要重新 install
        if (lowerError.contains("vite") || lowerError.contains("esbuild")) {
            log.info("检测到 Vite/esbuild 问题，清理依赖后重试...");
            deleteDir(new File(projectDir, "node_modules"));
            File lockFile = new File(projectDir, "package-lock.json");
            if (lockFile.exists()) {
                lockFile.delete();
            }
            return true;
        }

        // 3. 代码语法错误无法自动修复
        if (lowerError.contains("syntax error") ||
            lowerError.contains("unexpected token") ||
            lowerError.contains("parse error")) {
            log.warn("检测到代码语法错误，无法自动修复");
            return false;
        }

        // 默认重试一次（清理 dist 目录）
        log.info("尝试通用修复：清理 dist 目录...");
        deleteDir(new File(projectDir, "dist"));
        return true;
    }

    /**
     * 执行命令并返回结果（包含错误输出）
     */
    private CommandResult executeCommandWithOutput(File workingDir, String command, int timeoutSeconds) {
        try {
            log.info("在目录 {} 中执行命令: {}", workingDir.getAbsolutePath(), command);
            Process process = RuntimeUtil.exec(
                    null,
                    workingDir,
                    command.split("\\s+")
            );
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                log.error("命令执行超时（{}秒），强制终止进程", timeoutSeconds);
                process.destroyForcibly();
                return CommandResult.fail("命令执行超时");
            }
            int exitCode = process.exitValue();
            String output = readStream(process.getInputStream());
            String errorOutput = readStream(process.getErrorStream());
            if (exitCode == 0) {
                log.info("命令执行成功: {}", command);
                return CommandResult.success(output, errorOutput);
            } else {
                log.error("命令执行失败，退出码: {}, 错误输出:\n{}", exitCode, errorOutput);
                return CommandResult.fail(errorOutput);
            }
        } catch (Exception e) {
            log.error("执行命令失败: {}, 错误信息: {}", command, e.getMessage());
            return CommandResult.fail(e.getMessage());
        }
    }

    private String readStream(java.io.InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            return output.toString();
        } catch (Exception e) {
            log.debug("读取输出流失败: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 执行 npm install 命令（如 node_modules 已存在则跳过）
     */
    private boolean executeNpmInstall(File projectDir) {
        File nodeModules = new File(projectDir, "node_modules");
        if (nodeModules.exists() && nodeModules.isDirectory()) {
            log.info("node_modules 已存在，跳过 npm install");
            return true;
        }
        log.info("执行 npm install...");
        String command = String.format("%s install", buildCommand("npm"));
        CommandResult result = executeCommandWithOutput(projectDir, command, 300);
        if (result.success) {
            return true;
        }
        log.warn("npm install 失败，清理 node_modules 和 package-lock.json 后重试...");
        deleteDir(new File(projectDir, "node_modules"));
        File lockFile = new File(projectDir, "package-lock.json");
        if (lockFile.exists()) {
            lockFile.delete();
        }
        return executeCommandWithOutput(projectDir, command, 300).success;
    }

    /**
     * 执行 npm run build 命令并返回输出
     */
    private BuildResult executeNpmBuildWithOutput(File projectDir) {
        log.info("执行 npm run build...");
        String command = String.format("%s run build", buildCommand("npm"));
        CommandResult result = executeCommandWithOutput(projectDir, command, 180);
        if (!result.success) {
            return BuildResult.fail(result.errorOutput);
        }
        File distDir = new File(projectDir, "dist");
        if (!distDir.exists()) {
            return BuildResult.fail("构建完成但 dist 目录未生成");
        }
        log.info("Vue 项目构建成功，dist 目录: {}", distDir.getAbsolutePath());
        return BuildResult.success();
    }

    private void deleteDir(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    deleteDir(f);
                }
            }
        }
        dir.delete();
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }

    private String buildCommand(String baseCommand) {
        if (isWindows()) {
            return baseCommand + ".cmd";
        }
        return baseCommand;
    }

    private static class CommandResult {
        final boolean success;
        final String output;
        final String errorOutput;

        CommandResult(boolean success, String output, String errorOutput) {
            this.success = success;
            this.output = output;
            this.errorOutput = errorOutput;
        }

        static CommandResult success(String output, String errorOutput) {
            return new CommandResult(true, output, errorOutput);
        }

        static CommandResult fail(String errorOutput) {
            return new CommandResult(false, "", errorOutput);
        }
    }

    private static class BuildResult {
        final boolean success;
        final String errorOutput;

        BuildResult(boolean success, String errorOutput) {
            this.success = success;
            this.errorOutput = errorOutput;
        }

        static BuildResult success() {
            return new BuildResult(true, "");
        }

        static BuildResult fail(String errorOutput) {
            return new BuildResult(false, errorOutput);
        }
    }
}
