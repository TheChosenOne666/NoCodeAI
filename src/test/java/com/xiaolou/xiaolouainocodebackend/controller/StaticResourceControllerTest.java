package com.xiaolou.xiaolouainocodebackend.controller;

import com.xiaolou.xiaolouainocodebackend.constant.AppConstant;
import com.xiaolou.xiaolouainocodebackend.mapper.AppDeployAssetMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证预览路由从 CODE_OUTPUT_ROOT_DIR 读取生成代码，
 * 而非部署目录 CODE_DEPLOY_ROOT_DIR。
 */
class StaticResourceControllerTest {

    private AppDeployAssetMapper assetMapper;
    private StaticResourceController controller;
    private File outputDir;

    @BeforeEach
    void setUp() throws Exception {
        assetMapper = mock(AppDeployAssetMapper.class);
        controller = new StaticResourceController(assetMapper);

        // 在 code_output 下建立 html_<appId> 目录与 index.html
        String dir = AppConstant.CODE_OUTPUT_ROOT_DIR + "/html_123456";
        outputDir = new File(dir);
        outputDir.mkdirs();
        try (FileWriter writer = new FileWriter(new File(outputDir, "index.html"))) {
            writer.write("<html><body>preview test</body></html>");
        }
    }

    @AfterEach
    void tearDown() {
        File index = new File(outputDir, "index.html");
        if (index.exists()) {
            index.delete();
        }
        if (outputDir.exists()) {
            outputDir.delete();
        }
    }

    @Test
    void servePreviewResource_returnsGeneratedHtmlFromOutputDir() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/static/preview/html_123456/");
        // 模拟 HandlerMapping 属性
        request.setAttribute(
                org.springframework.web.servlet.HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE,
                "/static/preview/html_123456/");

        ResponseEntity<?> response = controller.servePreviewResource("html_123456", request);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody() instanceof org.springframework.core.io.Resource);
        org.springframework.core.io.Resource body = (org.springframework.core.io.Resource) response.getBody();
        String content = Files.readString(body.getFile().toPath());
        assertTrue(content.contains("preview test"));
    }

    @Test
    void servePreviewResource_missingFile_returns404() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(
                org.springframework.web.servlet.HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE,
                "/static/preview/html_not_exist/");
        ResponseEntity<?> response = controller.servePreviewResource("html_not_exist", request);
        assertEquals(404, response.getStatusCode().value());
    }
}
