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

    // 代码生成输出根目录（与生成时落盘的 CODE_OUTPUT_ROOT_DIR 一致）
    private static final String OUTPUT_ROOT_DIR = AppConstant.CODE_OUTPUT_ROOT_DIR;

    private final AppDeployAssetMapper appDeployAssetMapper;

    public StaticResourceController(AppDeployAssetMapper appDeployAssetMapper) {
        this.appDeployAssetMapper = appDeployAssetMapper;
    }

    /**
     * 提供部署后的静态资源访问。
     * 访问格式：http://localhost:8123/api/static/{deployKey}[/{fileName}]
     */
    @GetMapping("/{deployKey}/**")
    public ResponseEntity<?> serveStaticResource(
            @PathVariable String deployKey,
            HttpServletRequest request) {
        return serveFromRoot(PREVIEW_ROOT_DIR, "/static/" + deployKey, deployKey, request);
    }

    /**
     * 预览路由：从代码生成输出目录（CODE_OUTPUT_ROOT_DIR）读取，
     * 供生成代码后即时预览，不依赖部署目录（CODE_DEPLOY_ROOT_DIR）。
     * 访问格式：http://localhost:8123/api/static/preview/{sourceDir}[/{fileName}]
     */
    @GetMapping("/preview/{sourceDir}/**")
    public ResponseEntity<?> servePreviewResource(
            @PathVariable String sourceDir,
            HttpServletRequest request) {
        return serveFromRoot(OUTPUT_ROOT_DIR, "/static/preview/" + sourceDir, sourceDir, request);
    }

    /**
     * 从指定根目录读取静态资源，优先本地磁盘，部署资源回退查库。
     *
     * @param rootDir   根目录
     * @param prefix    路由前缀（用于从请求路径中剥离，得到资源相对路径）
     * @param dbKey     查库时使用的 key（部署资源用 deployKey，预览用 sourceDir）
     */
    private ResponseEntity<?> serveFromRoot(String rootDir, String prefix, String dbKey, HttpServletRequest request) {
        try {
            // 获取资源路径
            String resourcePath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            resourcePath = resourcePath.substring(prefix.length());
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
            String filePath = rootDir + "/" + dbKey + resourcePath;
            File file = new File(filePath);
            // 本地磁盘命中则直接返回
            if (file.exists()) {
                Resource resource = new FileSystemResource(file);
                return ResponseEntity.ok()
                        .header("Content-Type", getContentTypeWithCharset(filePath))
                        .body(resource);
            }
            // 本地不存在：部署资源回退查数据库（预览资源不查库）
            if (rootDir.equals(PREVIEW_ROOT_DIR)) {
                String dbPath = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
                AppDeployAsset asset = appDeployAssetMapper.selectByDeployKeyAndPath(dbKey, dbPath);
                if (asset != null && asset.getContent() != null) {
                    return ResponseEntity.ok()
                            .header("Content-Type", asset.getContentType())
                            .body(asset.getContent());
                }
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
