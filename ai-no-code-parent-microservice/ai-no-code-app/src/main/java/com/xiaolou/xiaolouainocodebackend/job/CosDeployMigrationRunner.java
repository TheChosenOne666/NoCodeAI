package com.xiaolou.xiaolouainocodebackend.job;

import cn.hutool.core.io.FileUtil;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.PutObjectRequest;
import com.xiaolou.xiaolouainocodebackend.config.CosClientConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.io.File;

/**
 * 一次性迁移脚本：将本地 tmp/code_deploy 下所有 deployKey 目录上传到 COS。
 *
 * 用途：把本地已有的精选案例部署产物迁移到 COS 公有读，使 Railway 重启后
 * "查看作品"仍可访问（不再依赖本地临时盘）。
 *
 * 触发方式：在 application 配置中设置 migrate.cos.deploy=true 后启动应用，
 * 执行完毕后该 runner 会打印结果。生产环境迁移完成后移除该配置即可。
 *
 * 注意：仅迁移 code_deploy（已构建的 dist）。App 表中 deployKey 需与生产环境一致，
 * 前端通过 getDeployUrl(deployKey) 拼出 COS 直链 code-deploy/{deployKey}/。
 */
@Component
@Order(1000)
@Slf4j
@ConditionalOnProperty(prefix = "migrate.cos", name = "deploy", havingValue = "true")
public class CosDeployMigrationRunner implements ApplicationRunner {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;

    @Value("${migrate.cos.local-deploy-dir:tmp/code_deploy}")
    private String localDeployDir;

    @Override
    public void run(ApplicationArguments args) {
        File root = new File(localDeployDir);
        if (!root.exists() || !root.isDirectory()) {
            log.error("迁移源目录不存在: {}", root.getAbsolutePath());
            return;
        }
        File[] deployKeys = root.listFiles(File::isDirectory);
        if (deployKeys == null || deployKeys.length == 0) {
            log.warn("迁移源目录无子目录: {}", root.getAbsolutePath());
            return;
        }
        int totalFiles = 0;
        for (File deployDir : deployKeys) {
            String deployKey = deployDir.getName();
            String prefix = "code-deploy/" + deployKey;
            int count = uploadDir(prefix, deployDir);
            totalFiles += count;
            log.info("[迁移] deployKey={} 上传 {} 个文件 -> {}", deployKey, count, prefix);
        }
        log.info("[迁移完成] 共处理 {} 个 deployKey, 上传 {} 个文件", deployKeys.length, totalFiles);
    }

    private int uploadDir(String prefix, File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return 0;
        }
        int count = 0;
        for (File file : files) {
            if (file.isDirectory()) {
                count += uploadDir(prefix + "/" + file.getName(), file);
            } else {
                String key = prefix + "/" + file.getName();
                cosClient.putObject(new PutObjectRequest(cosClientConfig.getBucket(), key, file));
                count++;
            }
        }
        return count;
    }
}
