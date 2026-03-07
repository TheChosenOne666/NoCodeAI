package com.xiaolou.xiaolouainocodebackend.service.impl;

import com.xiaolou.xiaolouainocodebackend.innerservice.InnerScreenshotService;
import com.xiaolou.xiaolouainocodebackend.service.ScreenshotService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService
@Slf4j
public class InnerScreenshotServiceImpl implements InnerScreenshotService {

    @Resource
    private ScreenshotService screenshotService;

    @Override
    public String generateAndUploadScreenshot(String webUrl) {
        return screenshotService.generateAndUploadScreenshot(webUrl);
    }
}
