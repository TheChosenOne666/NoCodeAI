package com.xiaolou.xiaolouainocodebackend.service;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 下载代码
 */
public interface ProjectDowmloadService {
    /**
     * 下载打包压缩项目
     *
     * @param projectPath
     * @param downloadFileName
     * @param response
     */
    void downloadProjectAsZip(String projectPath, String downloadFileName, HttpServletResponse response);
}
