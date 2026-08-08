package com.xiaolou.xiaolouainocodebackend.model.dto.codegen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CodeGenStreamEvent DTO 单元测试
 */
class CodeGenStreamEventTest {

    @Test
    void shouldBuildWithAllFields() {
        CodeGenStreamEvent event = CodeGenStreamEvent.builder()
                .type("file-start")
                .path("src/App.vue")
                .language("vue")
                .content("<template>...</template>")
                .message("开始生成")
                .url("/preview")
                .build();

        assertEquals("file-start", event.getType());
        assertEquals("src/App.vue", event.getPath());
        assertEquals("vue", event.getLanguage());
        assertEquals("<template>...</template>", event.getContent());
        assertEquals("开始生成", event.getMessage());
        assertEquals("/preview", event.getUrl());
    }

    @Test
    void shouldBuildWithPartialFields() {
        CodeGenStreamEvent event = CodeGenStreamEvent.builder()
                .type("build-progress")
                .message("npm install completed")
                .build();

        assertEquals("build-progress", event.getType());
        assertNull(event.getPath());
        assertNull(event.getLanguage());
        assertNull(event.getContent());
        assertEquals("npm install completed", event.getMessage());
        assertNull(event.getUrl());
    }

    @Test
    void shouldBuildWithNoFields() {
        CodeGenStreamEvent event = new CodeGenStreamEvent();
        assertNull(event.getType());
        assertNull(event.getPath());
        assertNull(event.getLanguage());
        assertNull(event.getContent());
        assertNull(event.getMessage());
        assertNull(event.getUrl());
    }
}