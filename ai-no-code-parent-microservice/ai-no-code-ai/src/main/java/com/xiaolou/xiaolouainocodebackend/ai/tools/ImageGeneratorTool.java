package com.xiaolou.xiaolouainocodebackend.ai.tools;


import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.volcengine.ark.runtime.model.images.generation.GenerateImagesRequest;
import com.volcengine.ark.runtime.model.images.generation.ImagesResponse;
import com.volcengine.ark.runtime.model.images.generation.ResponseFormat;
import com.volcengine.ark.runtime.service.ArkService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.Dispatcher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ImageGeneratorTool extends BaseTool {

    @Value("${ai.api-key:}")
    private String volcengineApiKey;

    @Value("${ai.image-model:doubao-seedream-4-0-250828}")
    private String imageModel;

    private ArkService arkService;

    @PostConstruct
    public void init() {
        ConnectionPool connectionPool = new ConnectionPool(5, 1, TimeUnit.SECONDS);
        Dispatcher dispatcher = new Dispatcher();
        this.arkService = ArkService.builder()
                .dispatcher(dispatcher)
                .connectionPool(connectionPool)
                .apiKey(volcengineApiKey)
                .build();
        log.info("ImageGeneratorTool 初始化完成");
    }

    @PreDestroy
    public void destroy() {
        if (arkService != null) {
            arkService.shutdownExecutor();
            log.info("ImageGeneratorTool 资源已释放");
        }
    }

    @Tool("根据描述生成内容相关的图片，用于网站内容展示。仅在搜索不到合适图片或需求特别复杂时使用")
    public String generateContentImages(@P("图片描述，详细说明需要的图片内容、风格、场景等，尽量详细") String description) {
        List<Map<String, String>> imageList = new ArrayList<>();
        
        try {
            String imagePrompt = String.format("生成高质量图片，%s", description);

            GenerateImagesRequest generateRequest = GenerateImagesRequest.builder()
                    .model(imageModel)
                    .prompt(imagePrompt)
                    .size("2k")
                    .sequentialImageGeneration("disabled")
                    .responseFormat(ResponseFormat.Url)
                    .stream(false)
                    .watermark(true)
                    .build();

            ImagesResponse imagesResponse = arkService.generateImages(generateRequest);

            if (imagesResponse != null && imagesResponse.getData() != null) {
                for (ImagesResponse.Image imageData : imagesResponse.getData()) {
                    String imageUrl = imageData.getUrl();
                    if (StrUtil.isNotBlank(imageUrl)) {
                        Map<String, String> imageInfo = new HashMap<>();
                        imageInfo.put("url", imageUrl);
                        imageInfo.put("description", description);
                        imageList.add(imageInfo);
                    }
                }
                log.info("成功生成 {} 个图片，描述: {}", imageList.size(), description);
            }
        } catch (Exception e) {
            log.error("生成图片失败: {}", e.getMessage(), e);
        }
        
        return JSONUtil.toJsonStr(imageList);
    }

    @Override
    public String getToolName() {
        return "generateContentImages";
    }

    @Override
    public String getDisplayName() {
        return "生成内容图片";
    }

    @Override
    public String generateToolExecutedResult(cn.hutool.json.JSONObject arguments) {
        String description = arguments.getStr("description");
        if (description == null || description.isEmpty()) {
            description = "未提供描述";
        }
        return String.format("[工具调用] %s 描述: %s", getDisplayName(), description);
    }
}
