package com.xiaolou.xiaolouainocodebackend.model.enums;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CodeGenStreamEventType 枚举单元测试
 */
class CodeGenStreamEventTypeTest {

    @Test
    void shouldContainAllEightEventTypes() {
        assertEquals(8, CodeGenStreamEventType.values().length);
    }

    @Test
    void fileStartShouldHaveCorrectValue() {
        assertEquals("file-start", CodeGenStreamEventType.FILE_START.getValue());
    }

    @Test
    void codeChunkShouldHaveCorrectValue() {
        assertEquals("code-chunk", CodeGenStreamEventType.CODE_CHUNK.getValue());
    }

    @Test
    void fileEndShouldHaveCorrectValue() {
        assertEquals("file-end", CodeGenStreamEventType.FILE_END.getValue());
    }

    @Test
    void buildStartShouldHaveCorrectValue() {
        assertEquals("build-start", CodeGenStreamEventType.BUILD_START.getValue());
    }

    @Test
    void buildProgressShouldHaveCorrectValue() {
        assertEquals("build-progress", CodeGenStreamEventType.BUILD_PROGRESS.getValue());
    }

    @Test
    void buildEndShouldHaveCorrectValue() {
        assertEquals("build-end", CodeGenStreamEventType.BUILD_END.getValue());
    }

    @Test
    void previewReadyShouldHaveCorrectValue() {
        assertEquals("preview-ready", CodeGenStreamEventType.PREVIEW_READY.getValue());
    }

    @Test
    void errorShouldHaveCorrectValue() {
        assertEquals("error", CodeGenStreamEventType.ERROR.getValue());
    }
}