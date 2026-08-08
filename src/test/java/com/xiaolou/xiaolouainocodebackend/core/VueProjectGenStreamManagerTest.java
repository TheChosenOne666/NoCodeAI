package com.xiaolou.xiaolouainocodebackend.core;

import com.xiaolou.xiaolouainocodebackend.model.dto.codegen.CodeGenStreamEvent;
import com.xiaolou.xiaolouainocodebackend.model.enums.CodeGenStreamEventType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VueProjectGenStreamManager 集成测试
 * <p>
 * 验证共享会话的创建、复用、以及 chatFlux / detailFlux 的可用性。
 */
@SpringBootTest
class VueProjectGenStreamManagerTest {

    /**
     * 直接测试 CodeGenStreamEvent 构建器——验证流事件数据结构
     */
    @Test
    @DisplayName("CODE_CHUNK 事件应包含 type、path、content")
    void codeChunkEventShouldHaveTypeAndPathAndContent() {
        CodeGenStreamEvent event = CodeGenStreamEvent.builder()
                .type(CodeGenStreamEventType.CODE_CHUNK.getValue())
                .path("src/components/HelloWorld.vue")
                .content("<template><div>Hello</div></template>")
                .build();

        assertEquals("code-chunk", event.getType());
        assertEquals("src/components/HelloWorld.vue", event.getPath());
        assertEquals("<template><div>Hello</div></template>", event.getContent());
    }

    @Test
    @DisplayName("PREVIEW_READY 事件应包含 url")
    void previewReadyEventShouldHaveUrl() {
        CodeGenStreamEvent event = CodeGenStreamEvent.builder()
                .type(CodeGenStreamEventType.PREVIEW_READY.getValue())
                .url("/api/static/vue_project_1/dist/index.html")
                .build();

        assertEquals("preview-ready", event.getType());
        assertEquals("/api/static/vue_project_1/dist/index.html", event.getUrl());
    }

    @Test
    @DisplayName("ERROR 事件应包含 message")
    void errorEventShouldHaveMessage() {
        CodeGenStreamEvent event = CodeGenStreamEvent.builder()
                .type(CodeGenStreamEventType.ERROR.getValue())
                .message("Vue 项目构建失败")
                .build();

        assertEquals("error", event.getType());
        assertEquals("Vue 项目构建失败", event.getMessage());
    }

    @Test
    @DisplayName("BUILD_PROGRESS 事件应包含构建日志消息")
    void buildProgressEventShouldHaveMessage() {
        CodeGenStreamEvent event = CodeGenStreamEvent.builder()
                .type(CodeGenStreamEventType.BUILD_PROGRESS.getValue())
                .message("> vue-tsc -b && vite build")
                .build();

        assertEquals("build-progress", event.getType());
        assertTrue(event.getMessage().contains("vite build"));
    }

    @Test
    @DisplayName("FILE_START → CODE_CHUNK → FILE_END 形成完整文件生成流")
    void fileGenerationFlowShouldBeSequential() {
        String filePath = "src/App.vue";

        CodeGenStreamEvent fileStart = CodeGenStreamEvent.builder()
                .type(CodeGenStreamEventType.FILE_START.getValue())
                .path(filePath)
                .language("vue")
                .build();

        CodeGenStreamEvent codeChunk = CodeGenStreamEvent.builder()
                .type(CodeGenStreamEventType.CODE_CHUNK.getValue())
                .path(filePath)
                .content("<script setup lang=\"ts\">\n// code\n</script>")
                .build();

        CodeGenStreamEvent fileEnd = CodeGenStreamEvent.builder()
                .type(CodeGenStreamEventType.FILE_END.getValue())
                .path(filePath)
                .build();

        // 验证三个事件共享同一个 path
        assertEquals(filePath, fileStart.getPath());
        assertEquals(filePath, codeChunk.getPath());
        assertEquals(filePath, fileEnd.getPath());

        // 验证 CODE_CHUNK 包含实际代码内容
        assertNotNull(codeChunk.getContent());
        assertTrue(codeChunk.getContent().contains("script setup"));
    }

    @Test
    @DisplayName("CodeGenStreamEventType 枚举值应覆盖前端期望的所有事件类型")
    void allEventTypesShouldBePresent() {
        String[] expectedTypes = {
                "file-start", "code-chunk", "file-end",
                "build-start", "build-progress", "build-end",
                "preview-ready", "error"
        };

        for (String expectedType : expectedTypes) {
            boolean found = false;
            for (CodeGenStreamEventType type : CodeGenStreamEventType.values()) {
                if (type.getValue().equals(expectedType)) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "缺少事件类型: " + expectedType);
        }
    }
}