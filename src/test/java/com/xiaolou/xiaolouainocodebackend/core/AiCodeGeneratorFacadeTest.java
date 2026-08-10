package com.xiaolou.xiaolouainocodebackend.core;

import cn.hutool.json.JSONUtil;
import com.xiaolou.xiaolouainocodebackend.model.enums.CodeGenTypeEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.io.File;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AiCodeGeneratorFacadeTest {

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Test
    void generateAndSaveCode() {
        File file = aiCodeGeneratorFacade.generateAndSaveCode("生成不超过50行代码的个人博客", CodeGenTypeEnum.HTML, 1L);
        Assertions.assertNotNull(file);
    }

    @Test
    void generateAndSaveCodeStream() {
        Flux<ServerSentEvent<String>> sseStream = aiCodeGeneratorFacade.generateAndSaveCodeStream(
                "生成不超过50行代码的个人主页", CodeGenTypeEnum.MULTI_FILE, 1L, null);
        // 阻塞等待全部生成完，仅收集默认 message 事件的原始 chunk（data 字段 JSON 中 "data" 键）
        List<String> result = sseStream
                .filter(event -> event != null && event.event() == null)
                .map(event -> JSONUtil.parseObj(Objects.requireNonNull(event.data())).getStr("data"))
                .collectList()
                .block();
        Assertions.assertNotNull(result);
        String completeContent = String.join("", result);
        Assertions.assertNotNull(completeContent);
    }
}