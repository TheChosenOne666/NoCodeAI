package com.xiaolou.xiaolouainocodebackend.service;


/**
 * 截图服务
 */
public interface ScreenshotService {

    /**
     * 生成并上传网页截图
     *
     * @param webUrl 网页地址
     * @return 压缩图片路径
     */
    String generateAndUploadScreenshot(String webUrl);
}
