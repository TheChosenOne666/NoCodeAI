package com.xiaolou.xiaolouainocodebackend.core.builder;

import cn.hutool.core.util.RuntimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class VueProjectBuilder {

    /**
     * 构建进度监听器
     */
    @FunctionalInterface
    public interface BuildProgressListener {
        /**
         * 收到一行构建输出
         *
         * @param line 输出内容
         */
        void onLine(String line);
    }

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
                log.error("异步构建 Vue 项目时发生异常 ：{}", e.getMessage(), e);
            }
        });
    }

    /**
     * 构建 Vue 项目（无进度回调）
     *
     * @param projectPath 项目根目录路径
     * @return 是否构建成功
     */
    public boolean buildProject(String projectPath) {
        return buildProject(projectPath, null);
    }

    /**
     * 构建 Vue 项目（带进度回调）
     *
     * @param projectPath      项目根目录路径
     * @param progressListener 进度监听器，可为 null
     * @return 是否构建成功
     */
    public boolean buildProject(String projectPath, BuildProgressListener progressListener) {
        File projectDir = new File(projectPath);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            log.error("项目目录不存在: {}", projectPath);
            return false;
        }
        // 检查 package.json 是否存在
        File packageJson = new File(projectDir, "package.json");
        if (!packageJson.exists()) {
            log.error("package.json 文件不存在: {}", packageJson.getAbsolutePath());
            return false;
        }
        log.info("开始构建 Vue 项目: {}", projectPath);
        // 执行 npm install
        if (!executeNpmInstall(projectDir, progressListener)) {
            log.error("npm install 执行失败");
            return false;
        }
        // 执行 npm run build
        if (!executeNpmBuild(projectDir, progressListener)) {
            log.error("npm run build 执行失败");
            return false;
        }
        // 验证 dist 目录是否生成
        File distDir = new File(projectDir, "dist");
        if (!distDir.exists()) {
            log.error("构建完成但 dist 目录未生成: {}", distDir.getAbsolutePath());
            return false;
        }
        log.info("Vue 项目构建成功，dist 目录: {}", distDir.getAbsolutePath());
        return true;
    }

    /**
     * 执行命令（带实时进度输出）
     *
     * @param workingDir       工作目录
     * @param command          命令字符串
     * @param timeoutSeconds   超时时间（秒）
     * @param progressListener 进度监听器，可为 null
     * @return 是否执行成功
     */
    private boolean executeCommand(File workingDir, String command, int timeoutSeconds,
                                   BuildProgressListener progressListener) {
        try {
            log.info("在目录 {} 中执行命令: {}", workingDir.getAbsolutePath(), command);
            Process process = RuntimeUtil.exec(
                    null,
                    workingDir,
                    command.split("\\s+") // 命令分割为数组
            );

            // 启动独立线程实时读取标准输出和错误输出
            Thread outputThread = new Thread(() -> readStream(process, process.inputReader(StandardCharsets.UTF_8),
                    progressListener));
            Thread errorThread = new Thread(() -> readStream(process, process.errorReader(StandardCharsets.UTF_8),
                    progressListener));
            outputThread.setDaemon(true);
            errorThread.setDaemon(true);
            outputThread.start();
            errorThread.start();

            // 等待进程完成，设置超时
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                log.error("命令执行超时（{}秒），强制终止进程", timeoutSeconds);
                process.destroyForcibly();
                return false;
            }

            // 等待读取线程结束
            outputThread.join(5000);
            errorThread.join(5000);

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                log.info("命令执行成功: {}", command);
                return true;
            } else {
                log.error("命令执行失败，退出码: {}", exitCode);
                return false;
            }
        } catch (Exception e) {
            log.error("执行命令失败: {}, 错误信息: {}", command, e.getMessage());
            return false;
        }
    }

    /**
     * 读取进程输出流并实时回调
     */
    private void readStream(Process process, BufferedReader reader, BuildProgressListener progressListener) {
        try (reader) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (progressListener != null) {
                    progressListener.onLine(line);
                }
                // 如果进程已被强制终止，提前结束读取
                if (!process.isAlive()) {
                    break;
                }
            }
        } catch (Exception e) {
            log.debug("读取输出流失败: {}", e.getMessage());
        }
    }

    /**
     * 执行 npm install 命令（如 node_modules 已存在则跳过）
     */
    private boolean executeNpmInstall(File projectDir, BuildProgressListener progressListener) {
        File nodeModules = new File(projectDir, "node_modules");
        if (nodeModules.exists() && nodeModules.isDirectory()) {
            log.info("node_modules 已存在，跳过 npm install");
            return true;
        }
        log.info("执行 npm install...");
        String command = String.format("%s install --prefer-offline", buildCommand("npm"));
        return executeCommand(projectDir, command, 300, progressListener); // 5分钟超时
    }

    /**
     * 执行 npm run build 命令
     */
    private boolean executeNpmBuild(File projectDir, BuildProgressListener progressListener) {
        log.info("执行 npm run build...");
        String command = String.format("%s run build", buildCommand("npm"));
        return executeCommand(projectDir, command, 180, progressListener); // 3分钟超时
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
}
