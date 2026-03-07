package com.xiaolou.xiaolouainocodebackend.service.impl;


import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.xiaolou.xiaolouainocodebackend.common.ErrorCode;
import com.xiaolou.xiaolouainocodebackend.exception.ThrowUtils;
import com.xiaolou.xiaolouainocodebackend.manager.CosManager;
import com.xiaolou.xiaolouainocodebackend.service.ScreenshotService;
import com.xiaolou.xiaolouainocodebackend.utils.WebScreenshotUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@Slf4j
public class ScreenshotServiceImpl implements ScreenshotService {

    @Resource
    private CosManager cosManager;

    @Override
    public String generateAndUploadScreenshot(String webUrl){
        ThrowUtils.throwIf(StrUtil.isBlank(webUrl), ErrorCode.PARAMS_ERROR, "网页地址不能为空");
        log.info("开始生成网页截图，网页地址为：{}", webUrl);
        // 1.生成本地截图
        String localScreenshotPath = WebScreenshotUtils.saveWebPageScreenshot(webUrl);
        ThrowUtils.throwIf(StrUtil.isBlank(localScreenshotPath), ErrorCode.SYSTEM_ERROR, "生成网页截图失败");

        try {
            // 2.上传到COS存储
            String cosUrl = uploadScreenshotToCos(localScreenshotPath);
            ThrowUtils.throwIf(StrUtil.isBlank(cosUrl), ErrorCode.SYSTEM_ERROR, "上传截图到COS失败");
            log.info("网页截图生成并上传成功：{) -> {}", webUrl, cosUrl);
            return cosUrl;
        } finally {
            // 3.清理本地文件
            cleanupLocalFile(localScreenshotPath);
        }
    }

    /**
     * 上传截图到对象存储
     *
     * @param localScreenshotPath
     * @return
     */
    private String uploadScreenshotToCos(String localScreenshotPath){
        ThrowUtils.throwIf(StrUtil.isBlank(localScreenshotPath), ErrorCode.SYSTEM_ERROR, "本地截图路径为空");
        File screenshotFile = new File(localScreenshotPath);
        ThrowUtils.throwIf(!screenshotFile.exists(), ErrorCode.SYSTEM_ERROR, "本地截图文件不存在");
        String fileName = UUID.randomUUID().toString().substring(0,8) + "compressed.jpg";
        String cosKey = generateScreenshotKey(fileName);
        return cosManager.uploadFile(cosKey, screenshotFile);
    }

    /**
     * 生成截图的COS存储路径
     *
     * @param fileName
     * @return
     */
    private String generateScreenshotKey(String fileName){
        String datePath = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return String.format("/screenshots/%s/%s", datePath, fileName);
    }

    /**
     * 清理本地截图文件
     *
     * @param localScreenshotPath
     */
    private void cleanupLocalFile(String localScreenshotPath){
        File localFile = new File(localScreenshotPath);
        if (localFile.exists()){
            File parentDir = localFile.getParentFile();
            FileUtil.del(parentDir);
            log.info("本地截图文件已清理：{}", localScreenshotPath);
        }
    }
}
