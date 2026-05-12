package com.xiaolou.xiaolouainocodebackend.ai.config;

import com.xiaolou.xiaolouainocodebackend.ai.ImageCollectionService;
import com.xiaolou.xiaolouainocodebackend.ai.tools.ImageSearchTool;
import com.xiaolou.xiaolouainocodebackend.ai.tools.LogoGeneratorTool;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ImageCollectionServiceConfig {

    @Resource(name = "openAiChatModel")
    private ChatModel chatModel;

    @Resource
    private ImageSearchTool imageSearchTool;

    @Resource
    private LogoGeneratorTool logoGeneratorTool;

    @Bean
    public ImageCollectionService imageCollectionService() {
        return AiServices.builder(ImageCollectionService.class)
                .chatModel(chatModel)
                .tools(imageSearchTool, logoGeneratorTool)
                .build();
    }
}
