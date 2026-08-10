package com.xiaolou.xiaolouainocodebackend.service.impl;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * {@link ChatHistoryServiceImpl} 历史摘要压缩逻辑单元测试。
 * <p>
 * 验证方案 1 的核心诉求：AI 历史消息回传模型前被精简为摘要，
 * 避免完整 HTML 造成上下文雪球式膨胀导致生成超时。
 */
class ChatHistoryServiceImplTest {

    private final ChatHistoryServiceImpl service = new ChatHistoryServiceImpl();

    @Test
    void summarizeAiMessage_shouldTruncateLongHtmlAndAppendInstruction() {
        String longHtml = "<!DOCTYPE html><html><head><title>跳一跳</title></head><body>"
                + "<script>const a=1;".repeat(2000) + "</script></body></html>";
        Assertions.assertTrue(longHtml.length() > 800, "前置：构造的超长 HTML 应超过摘要阈值");

        String summary = service.summarizeAiMessage(longHtml);

        Assertions.assertTrue(summary.contains("已截断"), "超长消息应被截断标记");
        Assertions.assertTrue(summary.contains("HTML 页面"), "应识别为 HTML 页面类型");
        Assertions.assertTrue(summary.contains("重新输出【完整】的 HTML 代码"),
                "应追加重新生成完整 HTML 的指令");
        // 回传模型的内容长度应远小于原始完整 HTML
        Assertions.assertTrue(summary.length() < longHtml.length() / 2,
                "摘要长度应显著小于原文，避免 token 膨胀");
    }

    @Test
    void summarizeAiMessage_shortMessagePreservedWithInstruction() {
        String shortMsg = "<!DOCTYPE html><html><body>hello</body></html>";
        String summary = service.summarizeAiMessage(shortMsg);

        Assertions.assertTrue(summary.contains(shortMsg), "短消息应原样保留正文");
        Assertions.assertTrue(summary.contains("重新输出【完整】的 HTML 代码"),
                "短消息也应追加重新生成指令");
        Assertions.assertFalse(summary.contains("已截断"), "短消息不应出现截断标记");
    }

    @Test
    void summarizeAiMessage_blankReturnsBlank() {
        Assertions.assertNull(service.summarizeAiMessage(null));
        Assertions.assertEquals("", service.summarizeAiMessage(""));
    }

    @Test
    void codeGenTypeOf_shouldDetectHtml() {
        Assertions.assertEquals("HTML 页面", service.codeGenTypeOf("<!DOCTYPE html><html></html>"));
        Assertions.assertEquals("HTML 页面", service.codeGenTypeOf("<html lang=\"zh\">x</html>"));
        Assertions.assertEquals("代码", service.codeGenTypeOf("console.log('hello')"));
        Assertions.assertEquals("代码", service.codeGenTypeOf(""));
    }
}
