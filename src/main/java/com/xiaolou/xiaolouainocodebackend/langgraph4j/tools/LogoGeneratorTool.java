package com.xiaolou.xiaolouainocodebackend.langgraph4j.tools;

import cn.hutool.core.util.StrUtil;
import com.volcengine.ark.runtime.model.images.generation.GenerateImagesRequest;
import com.volcengine.ark.runtime.model.images.generation.ImagesResponse;
import com.volcengine.ark.runtime.model.images.generation.ResponseFormat;
import com.volcengine.ark.runtime.service.ArkService;
import com.xiaolou.xiaolouainocodebackend.langgraph4j.model.ImageResource;
import com.xiaolou.xiaolouainocodebackend.langgraph4j.model.enums.ImageCategoryEnum;
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
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class LogoGeneratorTool {

    @Value("${ai.api-key:}")
    private String volcengineApiKey;

    @Value("${ai.image-model:}")
    private String imageModel;

    private ArkService arkService;

    @PostConstruct
    public void init() {
        // 诊断日志（只显示 ENV 长度 + key 长度/前 8 字符，不暴露完整密钥）
        String envValue = System.getenv("VOLC_IMAGE_API_KEY");
        String envDisplay = envValue == null ? "null" : "len=" + envValue.length();
        if (volcengineApiKey == null) {
            log.warn("LogoGeneratorTool: ai.api-key 环境变量未注入. ENV VOLC_IMAGE_API_KEY={}", envDisplay);
        } else if (volcengineApiKey.isEmpty()) {
            log.warn("LogoGeneratorTool: ai.api-key 是空字符串. ENV VOLC_IMAGE_API_KEY={}", envDisplay);
        } else {
            log.info("LogoGeneratorTool: ai.api-key 已注入 (len={}, head={}...)", volcengineApiKey.length(),
                    volcengineApiKey.length() > 8 ? volcengineApiKey.substring(0, 8) : volcengineApiKey);
        }

        if (StrUtil.isBlank(volcengineApiKey)) {
            log.warn("LogoGeneratorTool 未配置 ai.api-key，文生图功能将不可用");
            return;
        }
        try {
            ConnectionPool connectionPool = new ConnectionPool(5, 1, TimeUnit.SECONDS);
            Dispatcher dispatcher = new Dispatcher();
            this.arkService = ArkService.builder()
                    .dispatcher(dispatcher)
                    .connectionPool(connectionPool)
                    .apiKey(volcengineApiKey)
                    .build();
            log.info("LogoGeneratorTool 初始化完成");
        } catch (Exception e) {
            log.error("LogoGeneratorTool ArkService 创建失败，文生图功能将不可用: {}", e.getMessage(), e);
            this.arkService = null;
        }
    }

    @PreDestroy
    public void destroy() {
        if (arkService != null) {
            arkService.shutdownExecutor();
        }
    }

    @Tool("根据描述生成 Logo 设计图片，用于网站品牌标识")
    public List<ImageResource> generateLogos(@P("Logo 设计描述，如名称、行业、风格等，尽量详细") String description) {
        List<ImageResource> logoList = new ArrayList<>();
        if (arkService == null) {
            log.warn("LogoGeneratorTool 未初始化（ai.api-key 缺失或 ArkService 创建失败），跳过生成");
            return logoList;
        }
        try {
            // 构建 Logo 设计提示词
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
                        logoList.add(ImageResource.builder()
                                .category(ImageCategoryEnum.LOGO)
                                .description(description)
                                .url(imageUrl)
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            log.error("生成 Logo 失败: {}", e.getMessage(), e);
        }
        return logoList;
    }
}