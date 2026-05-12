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
public class LogoGeneratorTool extends BaseTool {

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
        log.info("LogoGeneratorTool 初始化完成");
    }

    @PreDestroy
    public void destroy() {
        if (arkService != null) {
            arkService.shutdownExecutor();
            log.info("LogoGeneratorTool 资源已释放");
        }
    }

    @Tool("根据描述生成 Logo 设计图片，用于网站品牌标识")
    public String generateLogos(@P("Logo 设计描述，包括名称、行业、风格等，尽量详细") String description) {
        List<Map<String, String>> logoList = new ArrayList<>();
        
        try {
            String logoPrompt = String.format("生成 Logo，Logo 中禁止包含任何文字！Logo 介绍：%s", description);

            GenerateImagesRequest generateRequest = GenerateImagesRequest.builder()
                    .model(imageModel)
                    .prompt(logoPrompt)
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
                        Map<String, String> logoInfo = new HashMap<>();
                        logoInfo.put("url", imageUrl);
                        logoInfo.put("description", description);
                        logoList.add(logoInfo);
                    }
                }
                log.info("成功生成 {} 个 Logo，描述: {}", logoList.size(), description);
            }
        } catch (Exception e) {
            log.error("生成 Logo 失败: {}", e.getMessage(), e);
        }
        
        return JSONUtil.toJsonStr(logoList);
    }

    @Override
    public String getToolName() {
        return "generateLogos";
    }

    @Override
    public String getDisplayName() {
        return "生成Logo图片";
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
