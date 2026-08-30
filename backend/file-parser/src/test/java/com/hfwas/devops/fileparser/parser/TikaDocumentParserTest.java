package com.hfwas.devops.fileparser.parser;

import com.hfwas.devops.fileparser.config.FileParserConfig;
import com.hfwas.devops.fileparser.dto.FileParseResultVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
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

    private File getTestFile(String fileName) {
        URL url = getClass().getClassLoader().getResource("files/" + fileName);
        assertNotNull(url, "Test file not found: " + fileName);
        return new File(url.getFile());
    }
}