package com.xiaolou.xiaolouainocodebackend.manager;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObjectSummary;
import com.qcloud.cos.model.ObjectListing;
import com.xiaolou.xiaolouainocodebackend.config.CosClientConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CosManager 单元测试（使用 Mockito 模拟 COSClient，不依赖真实腾讯云凭证）。
 * 覆盖 uploadDir 递归遍历计数、deleteDir 列出并删除、buildPublicUrl 拼接。
 */
class CosManagerTest {

    private COSClient cosClient;
    private CosClientConfig cosClientConfig;
    private CosManager cosManager;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        cosClient = mock(COSClient.class);
        cosClientConfig = mock(CosClientConfig.class);
        when(cosClientConfig.getHost()).thenReturn("https://bucket-123.cos.ap-guangzhou.myqcloud.com");
        when(cosClientConfig.getBucket()).thenReturn("bucket-123");
        cosManager = new CosManager();
        ReflectionTestUtils.setField(cosManager, "cosClient", cosClient);
        ReflectionTestUtils.setField(cosManager, "cosClientConfig", cosClientConfig);
    }

    @Test
    void uploadDir_shouldUploadAllFilesRecursively() throws IOException {
        // 构造 dist 结构：index.html + assets/app.js + nested/a/b.txt
        File dist = new File(tempDir.toFile(), "dist");
        File assets = new File(dist, "assets");
        File nested = new File(dist, "nested/a");
        assertTrueDirs(dist, assets, nested);
        Files.write(new File(dist, "index.html").toPath(), "html".getBytes());
        Files.write(new File(assets, "app.js").toPath(), "js".getBytes());
        Files.write(new File(nested, "b.txt").toPath(), "txt".getBytes());

        int count = cosManager.uploadDir("code-deploy/vue_1", dist);

        assertEquals(3, count);
        verify(cosClient, atLeastOnce()).putObject(any());
    }

    @Test
    void uploadDir_emptyDir_returnsZero() throws IOException {
        File empty = new File(tempDir.toFile(), "empty");
        assertTrueDirs(empty);
        assertEquals(0, cosManager.uploadDir("code-deploy/empty", empty));
    }

    @Test
    void deleteDir_listsAndDeletesObjects() {
        COSObjectSummary s1 = new COSObjectSummary();
        s1.setKey("code-deploy/vue_1/index.html");
        COSObjectSummary s2 = new COSObjectSummary();
        s2.setKey("code-deploy/vue_1/assets/app.js");
        ObjectListing listing = mock(ObjectListing.class);
        when(listing.getObjectSummaries()).thenReturn(List.of(s1, s2));
        when(listing.isTruncated()).thenReturn(false);
        when(cosClient.listObjects(eq("bucket-123"), any())).thenReturn(listing);

        cosManager.deleteDir("code-deploy/vue_1");

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(cosClient, atLeastOnce()).deleteObject(eq("bucket-123"), keyCaptor.capture());
        assertEquals(2, keyCaptor.getAllValues().size());
    }

    @Test
    void buildPublicUrl_shouldPrependHostWithSlash() {
        String url = cosManager.buildPublicUrl("code-deploy/vue_1/index.html");
        assertEquals("https://bucket-123.cos.ap-guangzhou.myqcloud.com/code-deploy/vue_1/index.html", url);
    }

    @Test
    void buildPublicUrl_shouldNotDoubleSlash() {
        when(cosClientConfig.getHost()).thenReturn("https://bucket-123.cos.ap-guangzhou.myqcloud.com/");
        CosManager m = new CosManager();
        ReflectionTestUtils.setField(m, "cosClient", cosClient);
        ReflectionTestUtils.setField(m, "cosClientConfig", cosClientConfig);
        String url = m.buildPublicUrl("/code-deploy/vue_1/index.html");
        assertEquals("https://bucket-123.cos.ap-guangzhou.myqcloud.com/code-deploy/vue_1/index.html", url);
    }

    private void assertTrueDirs(File... dirs) {
        for (File d : dirs) {
            if (!d.mkdirs() && !d.isDirectory()) {
                throw new IllegalStateException("无法创建目录: " + d);
            }
        }
    }
}
