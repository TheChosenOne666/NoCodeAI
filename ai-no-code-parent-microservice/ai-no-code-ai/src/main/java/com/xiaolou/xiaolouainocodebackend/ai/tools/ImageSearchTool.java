package com.xiaolou.xiaolouainocodebackend.ai.tools;


import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class ImageSearchTool extends BaseTool {

    private static final String PEXELS_API_URL = "https://api.pexels.com/v1/search";

    @Value("${pexels.api-key:}")
    private String pexelsApiKey;

    @Tool("搜索内容相关的图片，用于网站内容展示")
    public String searchContentImages(@P("搜索关键词，描述需要的图片内容") String query) {
        List<Map<String, String>> imageList = new ArrayList<>();
        // 减少返回数量以加速图片准备阶段
        int searchCount = 6;
        
        try (HttpResponse response = HttpRequest.get(PEXELS_API_URL)
                .header("Authorization", pexelsApiKey)
                .form("query", query)
                .form("per_page", searchCount)
                .form("page", 1)
                .execute()) {
            
            if (response.isOk()) {
                JSONObject result = JSONUtil.parseObj(response.body());
                JSONArray photos = result.getJSONArray("photos");
                
                for (int i = 0; i < photos.size(); i++) {
                    JSONObject photo = photos.getJSONObject(i);
                    JSONObject src = photo.getJSONObject("src");
                    
                    Map<String, String> imageInfo = new HashMap<>();
                    imageInfo.put("url", src.getStr("medium"));
                    imageInfo.put("description", photo.getStr("alt", query));
                    imageInfo.put("photographer", photo.getStr("photographer", ""));
                    
                    imageList.add(imageInfo);
                }
                
                log.info("成功搜索到 {} 张图片，关键词: {}", imageList.size(), query);
            } else {
                log.error("Pexels API 调用失败，状态码: {}", response.getStatus());
            }
        } catch (Exception e) {
            log.error("Pexels API 调用失败: {}", e.getMessage(), e);
        }
        
        return JSONUtil.toJsonStr(imageList);
    }

    @Override
    public String getToolName() {
        return "searchContentImages";
    }

    @Override
    public String getDisplayName() {
        return "搜索内容图片";
    }

    @Override
    public String generateToolExecutedResult(cn.hutool.json.JSONObject arguments) {
        String query = arguments.getStr("query");
        if (query == null || query.isEmpty()) {
            query = "未指定关键词";
        }
        return String.format("[工具调用] %s 关键词: %s", getDisplayName(), query);
    }
}
