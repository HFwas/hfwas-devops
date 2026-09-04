package com.hfwas.devops.fileparser.parser;

import com.hfwas.devops.fileparser.config.FileParserConfig;
import com.hfwas.devops.fileparser.dto.FileParseResultVO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TikaDocumentParserTest {

    @Mock
    private FileParserConfig config;

    @Mock
    private FileParserConfig.TikaConfig tikaConfig;

    private TikaDocumentParser parser;

    @BeforeEach
    void setUp() {
        lenient().when(config.getTika()).thenReturn(tikaConfig);
        lenient().when(tikaConfig.getMaxTextLength()).thenReturn(10 * 1024 * 1024);
        lenient().when(tikaConfig.getCleanupStrategy()).thenReturn("basic");
        parser = new TikaDocumentParser(config);
    }

    @Test
    void shouldSupportOfficeMimeTypes() {
        assertTrue(parser.supports("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        assertTrue(parser.supports("application/vnd.openxmlformats-officedocument.presentationml.presentation"));
        assertTrue(parser.supports("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        assertTrue(parser.supports("application/pdf"));
        assertTrue(parser.supports("text/plain"));
        assertFalse(parser.supports("image/png"));
        assertFalse(parser.supports("image/jpeg"));
        assertFalse(parser.supports(null));
    }

    @Test
    void shouldSupportCompatibilityMimeTypes() {
        // WPS Office 兼容类型
        assertTrue(parser.supports("application/wps-office.pdf"));
        assertTrue(parser.supports("application/wps-office.doc"));
        assertTrue(parser.supports("application/wps-office.docx"));
        assertTrue(parser.supports("application/wps-office.xls"));
        assertTrue(parser.supports("application/wps-office.xlsx"));
        assertTrue(parser.supports("application/wps-office.ppt"));
        assertTrue(parser.supports("application/wps-office.pptx"));

        // CSV 兼容类型
        assertTrue(parser.supports("application/csv"));
        assertTrue(parser.supports("text/x-csv"));
        assertTrue(parser.supports("text/comma-separated-values"));

        // 旧版 Word 兼容
        assertTrue(parser.supports("application/x-msword"));

        // 旧版 Excel 兼容
        assertTrue(parser.supports("application/msexcel"));
        assertTrue(parser.supports("application/x-msexcel"));
        assertTrue(parser.supports("application/x-excel"));
        assertTrue(parser.supports("application/excel"));

        // 旧版 PowerPoint 兼容
        assertTrue(parser.supports("application/mspowerpoint"));
        assertTrue(parser.supports("application/powerpoint"));
        assertTrue(parser.supports("application/x-mspowerpoint"));

        // Markdown 兼容
        assertTrue(parser.supports("text/x-markdown"));
        assertTrue(parser.supports("application/markdown"));

        // OFD 兼容
        assertTrue(parser.supports("application/vnd.ofd"));

        // 音频格式
        assertTrue(parser.supports("audio/wave"));
        assertTrue(parser.supports("audio/x-mpeg"));
        assertTrue(parser.supports("audio/x-mp3"));

        // 压缩包格式
        assertTrue(parser.supports("application/x-rar"));
        assertTrue(parser.supports("application/rar"));
    }

    @Test
    void shouldParseTxtFile() throws Exception {
        File file = getTestFile("sample.txt");
        FileParseResultVO result = parser.parse(file, "sample.txt");

        assertTrue(result.isSuccess());
        assertEquals("sample.txt", result.getFileName());
        assertEquals("tika", result.getParseMethod());
        assertNotNull(result.getContent());
        assertNotNull(result.getContent().getText());
        // Tika 解析文本可能包含元数据前缀，但应包含原文内容
        assertTrue(result.getContent().getText().contains("Hello World"));
    }

    @Test
    @DisplayName("Tika 探测 PDF 不应因 commons-compress ArchiveException 失败")
    void shouldParseMinimalPdf() throws Exception {
        File pdf = File.createTempFile("tiny-", ".pdf");
        pdf.deleteOnExit();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(72, 700);
                cs.showText("hello pdf");
                cs.endText();
            }
            doc.save(pdf);
        }

        FileParseResultVO result = parser.parse(pdf, "tiny.pdf");

        assertTrue(result.isSuccess(), () -> result.getErrorMessage());
        assertEquals("tika", result.getParseMethod());
        assertNotNull(result.getContent());
        assertTrue(result.getContent().getText().contains("hello pdf"));
    }

    @Test
    void shouldHandleNonExistentFile() {
        File nonExistent = new File("/nonexistent/file.docx");
        FileParseResultVO result = parser.parse(nonExistent, "nonexistent.docx");

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void shouldExtractMetadataForTxtFile() throws Exception {
        File file = getTestFile("sample.txt");
        FileParseResultVO result = parser.parse(file, "sample.txt");

        assertTrue(result.isSuccess());
        // Tika 应该提取 Content-Type 等元数据
        if (result.getContent() != null && result.getContent().getMetadata() != null) {
            assertFalse(result.getContent().getMetadata().isEmpty());
        }
    }

    @Test
    void shouldReturnFriendlyErrorWhenExceedingTextLengthLimit() throws Exception {
        // 设置极小的文本长度限制，解析 sample.txt（60+ char）必然触发
        lenient().when(tikaConfig.getMaxTextLength()).thenReturn(5);
        parser = new TikaDocumentParser(config);

        File file = getTestFile("sample.txt");
        FileParseResultVO result = parser.parse(file, "sample.txt");

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
        // 验证返回友好提示，包含"最大提取限制"字样
        assertTrue(result.getErrorMessage().contains("最大提取限制"),
                "应返回友好提示，而非原始异常: " + result.getErrorMessage());
        // 验证提示包含可配置的提示信息
        assertTrue(result.getErrorMessage().contains("max-text-length"),
                "应提示用户可调整配置: " + result.getErrorMessage());
    }

    @Test
    void shouldParseSuccessfullyWithSmallLimitIfContentFits() throws Exception {
        // 设置足够大的限制，确保能正常解析
        lenient().when(tikaConfig.getMaxTextLength()).thenReturn(1000);
        parser = new TikaDocumentParser(config);

        File file = getTestFile("sample.txt");
        FileParseResultVO result = parser.parse(file, "sample.txt");

        assertTrue(result.isSuccess());
        assertNotNull(result.getContent());
        assertNotNull(result.getContent().getText());
        assertTrue(result.getContent().getText().contains("Hello World"));
    }

    // ========== cleanupText 空白行清洗测试 ==========

    @Test
    @DisplayName("cleanupText: 正常文本不应被修改")
    void shouldKeepNormalTextUnchanged() throws Exception {
        String input = "第一行内容\n\n第二行内容\n\n第三行内容";
        String result = invokeCleanupText(input);
        assertEquals(input, result);
    }

    @Test
    @DisplayName("cleanupText: 连续 3+ 空行应压缩为 1 个")
    void shouldCollapseConsecutiveBlankLines() throws Exception {
        assertEquals("第一行\n\n第二行", invokeCleanupText("第一行\n\n\n\n\n第二行"));
        assertEquals("第一行\n\n第二行", invokeCleanupText("第一行\n\n\n\n\n\n\n\n第二行"));
    }

    @Test
    @DisplayName("cleanupText: 行尾空白字符应被去除")
    void shouldTrimTrailingWhitespace() throws Exception {
        assertEquals("第一行\n第二行", invokeCleanupText("第一行  \n第二行\t\n"));
    }

    @Test
    @DisplayName("cleanupText: 行首空白字符应被去除")
    void shouldTrimLeadingWhitespace() throws Exception {
        assertEquals("第一行\n第二行", invokeCleanupText("第一行\n  第二行"));
    }

    @Test
    @DisplayName("cleanupText: 纯空白行（空格/Tab）应被移除")
    void shouldRemoveWhitespaceOnlyLines() throws Exception {
        // 空格组成的行
        assertEquals("第一行\n\n第二行", invokeCleanupText("第一行\n     \n\n第二行"));
        // Tab 组成的行
        assertEquals("第一行\n\n第二行", invokeCleanupText("第一行\n\t\t\n\n第二行"));
        // 混合空白字符
        assertEquals("第一行\n\n第二行", invokeCleanupText("第一行\n \t \n\n第二行"));
    }

    @Test
    @DisplayName("cleanupText: 首尾换行应被去除")
    void shouldTrimLeadingAndTrailingNewlines() throws Exception {
        assertEquals("第一行\n第二行", invokeCleanupText("\n\n第一行\n第二行\n\n"));
        assertEquals("第一行\n第二行", invokeCleanupText("\n\n\n第一行\n第二行\n\n\n"));
    }

    @Test
    @DisplayName("cleanupText: 混合场景 — 多种空白问题同时存在")
    void shouldHandleMixedWhitespaceIssues() throws Exception {
        String input = "\n\n  标题  \n\n\n\n正文内容\n\n\t\n\n第三行  \n\n\n";
        String expected = "标题\n\n正文内容\n\n第三行";
        assertEquals(expected, invokeCleanupText(input));
    }

    @Test
    @DisplayName("cleanupText: \\r\\n 和 \\r 应统一为 \\n")
    void shouldNormalizeLineEndings() throws Exception {
        String input = "第一行\r\n\n\n第二行\r\n第三行\r";
        String result = invokeCleanupText(input);
        // 统一后不应包含 \r
        assertFalse(result.contains("\r"));
        // 连续空行已被压缩
        assertEquals("第一行\n\n第二行\n第三行", result);
    }

    @Test
    @DisplayName("cleanupText: 空字符串应返回空字符串")
    void shouldReturnEmptyForEmptyString() throws Exception {
        assertEquals("", invokeCleanupText(""));
    }

    @Test
    @DisplayName("cleanupText: 单个换行符应返回空字符串")
    void shouldHandleSingleNewline() throws Exception {
        assertEquals("", invokeCleanupText("\n"));
    }

    @Test
    @DisplayName("cleanupText: 仅有空白字符应返回空字符串")
    void shouldHandleOnlyWhitespace() throws Exception {
        assertEquals("", invokeCleanupText("   \n\n\t\n  \n"));
    }

    @Test
    @DisplayName("cleanupText: 真实 DOCX 场景模拟 — 表格/段落混合空白")
    void shouldCleanupDocxLikeContent() throws Exception {
        // 模拟 DOCX 中常见的：标题 + 空段落 + 正文 + 表格空行 + 正文
        String input = "一、项目背景\n\n\n\n\n\n\n1.1 概述\n\n\n\n\n\n\n\n\n\n\n表格内容\n\n\n\n\n\n\n\n\n\n\n\n三、总结\n\n";
        String expected = "一、项目背景\n\n1.1 概述\n\n表格内容\n\n三、总结";
        assertEquals(expected, invokeCleanupText(input));
    }

    // ========== docx 策略深度清洗测试 ==========

    @Test
    @DisplayName("cleanupText(docx): 应删除 @xxx 内部协同标记")
    void shouldRemoveAtMentions() throws Exception {
        assertEquals("前面内容\n\n后面内容", invokeCleanupTextDocx("前面内容\n@郑威 补充\n后面内容"));
        assertEquals("前面内容\n\n后面内容", invokeCleanupTextDocx("前面内容\n@董皓辰 从这边开始写设计\n后面内容"));
        assertEquals("前面内容\n\n后面内容", invokeCleanupTextDocx("前面内容\n@林培峰\n后面内容"));
    }

    @Test
    @DisplayName("cleanupText(basic): Markdown 图片语法（![alt](image.png)）不应被删除")
    void shouldKeepMarkdownImageSyntax() throws Exception {
        // MarkdownContentHandler 输出图片为 ![alt](image.png)，不是纯文本 image1.png
        assertEquals("正文\n\n![图片](image1.png)", invokeCleanupText("正文\n\n![图片](image1.png)"));
        assertEquals("正文\n\n![截图](screenshot.png)", invokeCleanupText("正文\n\n![截图](screenshot.png)"));
    }

    @Test
    @DisplayName("cleanupText(docx): 应删除 to研发/待研发确认 备注")
    void shouldRemoveDevNotes() throws Exception {
        assertEquals("正文", invokeCleanupTextDocx("正文\nto研发：需要先判断文件夹是否存在"));
        assertEquals("正文", invokeCleanupTextDocx("正文\n待研发确认这里是选项还是输入框"));
    }

    @Test
    @DisplayName("cleanupText(docx): 应删除占位文本")
    void shouldRemovePlaceholderText() throws Exception {
        assertEquals("正文", invokeCleanupTextDocx("正文\nxxx岗位选择xxx菜单，进入XXXX界面"));
    }

    @Test
    @DisplayName("cleanupText(docx): 应删除单独一行的数字页码")
    void shouldRemovePageNumber() throws Exception {
        assertEquals("前面内容\n\n后面内容", invokeCleanupTextDocx("前面内容\n22\n后面内容"));
        // 段落中的数字不应被删除
        assertEquals("第 1 章 引言", invokeCleanupTextDocx("第 1 章 引言"));
    }

    @Test
    @DisplayName("cleanupText(docx): 完整 DOCX 文档清洗场景")
    void shouldFullyCleanDocxContent() throws Exception {
        String input = "一、背景\n"
                + "\n"
                + "【功能描述】\n"
                + "@郑威 补充\n"
                + "\n"
                + "页面内容\n"
                + "to研发：需要确认接口\n"
                + "\n"
                + "三、总结\n"
                + "\n";
        String expected = "一、背景\n\n【功能描述】\n\n页面内容\n\n三、总结";
        assertEquals(expected, invokeCleanupTextDocx(input));
    }

    @Test
    @DisplayName("cleanupText: 非 Word/PPT 类型也执行基础空白清洗")
    void shouldApplyBasicCleanupForAllTypes() throws Exception {
        // MarkdownContentHandler 输出后，基础空白清洗适用于所有文档类型
        // 行尾空格、首尾空白、多余空行会被清理
        assertEquals("indented\n\ntext", invokeCleanupText("  indented\n\n\n\ntext", "text/plain"));
        assertEquals("content", invokeCleanupText("content\nimage1.png", "text/plain"));
        assertEquals("内容", invokeCleanupText("内容\nimage1.png", "application/json"));
        assertEquals("content", invokeCleanupText("content\nimage1.png", "application/pdf"));
        // 电子表格也做基础清洗，但保留 @ 标记
        assertEquals("列1\t列2\n@xxx\t数据", invokeCleanupText("列1\t列2\n@xxx\t数据",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    @DisplayName("cleanupText: PPT 的 Markdown 图片语法（![alt](image.png)）不被删除")
    void shouldKeepMarkdownImagesForPptx() throws Exception {
        // MarkdownContentHandler 输出 PPT 图片为 Markdown 格式
        assertEquals("封面\n\n正文\n\n![图表](image1.png)", invokeCleanupText("封面\n\n\n\n正文\n\n![图表](image1.png)",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation"));
    }

    @Test
    @DisplayName("cleanupText: PDF 也执行基础空白清洗")
    void shouldApplyBasicCleanupForPdf() throws Exception {
        // MarkdownContentHandler 输出 PDF 后，基础空白清洗同样适用
        assertEquals("第一行\n\n第二行", invokeCleanupText("第一行  \n\n\n\n第二行", "application/pdf"));
        assertEquals("第一行\n\n第二行", invokeCleanupText("第一行  \n\n\n\n第二行", "application/pdf; charset=UTF-8"));
    }

    @Test
    @DisplayName("cleanupText(docx): 电子表格不应执行深度清洗")
    void shouldNotCleanSpreadsheetDeeply() throws Exception {
        String input = "列1\t列2\n@xxx\t数据";
        // 电子表格只做基础清洗，保留 @xxx
        assertEquals("列1\t列2\n@xxx\t数据", invokeCleanupText(input, "text/csv"));
    }

    @Test
    @DisplayName("cleanupText(docx): 分隔线（--- 和 ===）在 Markdown 中是有效语法，不应删除")
    void shouldKeepMarkdownHorizontalRules() throws Exception {
        // Markdown 中 --- 是水平线，=== 是 setext 标题下划线，都是有效语法
        assertEquals("前面内容\n\n---\n\n后面内容", invokeCleanupTextDocx("前面内容\n\n---\n\n后面内容"));
        assertEquals("前面内容\n\n===\n\n后面内容", invokeCleanupTextDocx("前面内容\n\n===\n\n后面内容"));
    }

    private String invokeCleanupTextDocx(String input) throws Exception {
        // 创建 docx 策略的解析器实例
        lenient().when(tikaConfig.getCleanupStrategy()).thenReturn("docx");
        TikaDocumentParser docxParser = new TikaDocumentParser(config);
        return invokeCleanupText(docxParser, input, "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    private String invokeCleanupText(String input) throws Exception {
        return invokeCleanupText(parser, input,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    private String invokeCleanupText(String input, String mimeType) throws Exception {
        return invokeCleanupText(parser, input, mimeType);
    }

    private String invokeCleanupText(TikaDocumentParser p, String input, String mimeType) throws Exception {
        Method method = TikaDocumentParser.class.getDeclaredMethod("cleanupText", String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(p, input, mimeType);
    }

    private File getTestFile(String fileName) {
        URL url = getClass().getClassLoader().getResource("files/" + fileName);
        assertNotNull(url, "Test file not found: " + fileName);
        return new File(url.getFile());
    }
}