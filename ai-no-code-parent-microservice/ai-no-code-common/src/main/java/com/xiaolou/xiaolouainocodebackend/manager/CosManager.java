package com.xiaolou.xiaolouainocodebackend.manager;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObjectSummary;
import com.qcloud.cos.model.ObjectListing;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.xiaolou.xiaolouainocodebackend.config.CosClientConfig;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

/**
 * COS对象存储管理器
 *
 * @author xiaolou
 */
@Component
@ConditionalOnBean(COSClient.class)
@Slf4j
public class CosManager {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;

    /**
     * 上传对象
     *
     * @param key  唯一键
     * @param file 文件
     * @return 上传结果
     */
    public PutObjectResult putObject(String key, File file) {
        PutObjectRequest putObjectRequest = new PutObjectRequest(cosClientConfig.getBucket(), key, file);
        return cosClient.putObject(putObjectRequest);
    }

    /**
     * 上传文件到 COS 并返回访问 URL
     *
     * @param key  COS对象键（完整路径）
     * @param file 要上传的文件
     * @return 文件的访问URL，失败返回null
     */
    public String uploadFile(String key, File file) {
        // 上传文件
        PutObjectResult result = putObject(key, file);
        if (result != null) {
            // 构建访问URL
            String url = String.format("%s%s", cosClientConfig.getHost(), key);
            log.info("文件上传COS成功: {} -> {}", file.getName(), url);
            return url;
        } else {
            log.error("文件上传COS失败，返回结果为空");
            return null;
        }
    }

    /**
     * 递归上传本地目录到 COS 指定前缀下
     *
     * @param prefix    COS 对象键前缀（不以 / 结尾时自动补 /），如 code-deploy/vue_123/
     * @param localDir  待上传的本地目录
     * @return 成功上传的文件数量
     */
    public int uploadDir(String prefix, File localDir) {
        if (localDir == null || !localDir.isDirectory()) {
            log.error("uploadDir 失败：本地目录不存在或非目录: {}", localDir);
            return 0;
        }
        String normalizedPrefix = prefix.endsWith("/") ? prefix : prefix + "/";
        File[] files = localDir.listFiles();
        if (files == null) {
            return 0;
        }
        int count = 0;
        for (File file : files) {
            if (file.isDirectory()) {
                count += uploadDir(normalizedPrefix + file.getName(), file);
            } else {
                String key = normalizedPrefix + file.getName();
                putObject(key, file);
                count++;
            }
        }
        log.info("目录上传COS完成: {} -> {} ({} 个文件)", localDir.getAbsolutePath(), normalizedPrefix, count);
        return count;
    }

    /**
     * 删除 COS 指定前缀下的所有对象（用于清理部署产物/源码）
     *
     * @param prefix 对象键前缀，如 code-deploy/vue_123/
     */
    public void deleteDir(String prefix) {
        String normalizedPrefix = prefix.endsWith("/") ? prefix : prefix + "/";
        ObjectListing objectListing;
        int deleted = 0;
        do {
            objectListing = cosClient.listObjects(cosClientConfig.getBucket(), normalizedPrefix);
            List<COSObjectSummary> objectSummaries = objectListing.getObjectSummaries();
            for (COSObjectSummary summary : objectSummaries) {
                cosClient.deleteObject(cosClientConfig.getBucket(), summary.getKey());
                deleted++;
            }
        } while (objectListing.isTruncated());
        log.info("COS目录清理完成: {} (删除 {} 个对象)", normalizedPrefix, deleted);
    }

    /**
     * 拼接 COS 公有读访问 URL
     *
     * @param key 对象键，如 code-deploy/vue_123/index.html
     * @return 完整访问 URL
     */
    public String buildPublicUrl(String key) {
        String host = cosClientConfig.getHost();
        if (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }
        return host + (key.startsWith("/") ? key : "/" + key);
    }
}
