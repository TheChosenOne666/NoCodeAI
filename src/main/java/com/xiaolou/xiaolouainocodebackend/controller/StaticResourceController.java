package com.xiaolou.xiaolouainocodebackend.controller;

import com.xiaolou.xiaolouainocodebackend.constant.AppConstant;
import com.xiaolou.xiaolouainocodebackend.mapper.AppDeployAssetMapper;
import com.xiaolou.xiaolouainocodebackend.model.entity.AppDeployAsset;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.HandlerMapping;

import java.io.File;

@RestController
@RequestMapping("/static")
public class StaticResourceController {

    // 应用部署根目录（与 AppServiceImpl.deploy 写入的 CODE_DEPLOY_ROOT_DIR 一致）
    private static final String PREVIEW_ROOT_DIR = AppConstant.CODE_DEPLOY_ROOT_DIR;

    private final AppDeployAssetMapper appDeployAssetMapper;

    public StaticResourceController(AppDeployAssetMapper appDeployAssetMapper) {
        this.appDeployAssetMapper = appDeployAssetMapper;
    }

    /**
     * 提供静态资源访问，支持目录重定向。
     * 优先从本地磁盘读取；本地盘不存在时回退到数据库（app_deploy_asset 表），
     * 以支持无持久化磁盘的生产环境直接查库返回精品案例作品。
     * 访问格式：http://localhost:8123/api/static/{deployKey}[/{fileName}]
     */
    @GetMapping("/{deployKey}/**")
    public ResponseEntity<?> serveStaticResource(
            @PathVariable String deployKey,
            HttpServletRequest request) {
        try {
            // 获取资源路径
            String resourcePath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            resourcePath = resourcePath.substring(("/static/" + deployKey).length());
            // 如果是目录访问（不带斜杠），重定向到带斜杠的URL
            if (resourcePath.isEmpty()) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("Location", request.getRequestURI() + "/");
                return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
            }
            // 默认返回 index.html
            if (resourcePath.equals("/")) {
                resourcePath = "/index.html";
            }
            // 构建文件路径（统一使用正斜杠，跨平台兼容）
            String filePath = PREVIEW_ROOT_DIR + "/" + deployKey + resourcePath;
            File file = new File(filePath);
            // 本地磁盘命中则直接返回
            if (file.exists()) {
                Resource resource = new FileSystemResource(file);
                return ResponseEntity.ok()
                        .header("Content-Type", getContentTypeWithCharset(filePath))
                        .body(resource);
            }
            // 本地不存在：回退查数据库
            String dbPath = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
            AppDeployAsset asset = appDeployAssetMapper.selectByDeployKeyAndPath(deployKey, dbPath);
            if (asset != null && asset.getContent() != null) {
                // 直接以字节数组作为响应体，避免 HttpMessageConverter 覆盖 Content-Type
                return ResponseEntity.ok()
                        .header("Content-Type", asset.getContentType())
                        .body(asset.getContent());
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * 根据文件扩展名返回带字符编码的 Content-Type
     */
    private String getContentTypeWithCharset(String filePath) {
        if (filePath.endsWith(".html")) return "text/html; charset=UTF-8";
        if (filePath.endsWith(".css")) return "text/css; charset=UTF-8";
        if (filePath.endsWith(".js")) return "application/javascript; charset=UTF-8";
        if (filePath.endsWith(".png")) return "image/png";
        if (filePath.endsWith(".jpg")) return "image/jpeg";
        return "application/octet-stream";
    }
}
