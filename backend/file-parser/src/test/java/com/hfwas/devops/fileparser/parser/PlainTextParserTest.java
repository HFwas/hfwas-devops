package com.hfwas.devops.fileparser.parser;

import com.hfwas.devops.fileparser.dto.FileParseResultVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.net.URL;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PlainTextParserTest {

    private final PlainTextParser parser = new PlainTextParser();

    @Test
    void shouldSupportTextMimeTypes() {
        assertTrue(parser.supports("text/plain"));
        assertTrue(parser.supports("text/csv"));
        assertTrue(parser.supports("text/markdown"));
        assertTrue(parser.supports("text/html"));
        assertFalse(parser.supports("image/png"));
        assertFalse(parser.supports("application/pdf"));
        assertFalse(parser.supports(null));
    }

    @Test
    void shouldParseTxtFile() throws Exception {
        File file = getTestFile("sample.txt");
        FileParseResultVO result = parser.parse(file, "sample.txt");

        assertTrue(result.isSuccess());
        assertEquals("sample.txt", result.getFileName());
        assertEquals("plain", result.getParseMethod());
        assertNotNull(result.getContent());
        assertNotNull(result.getContent().getText());
        assertTrue(result.getContent().getText().contains("你好"));
        assertTrue(result.getContent().getText().contains("Hello World"));
    }

    @Test
    void shouldParseCsvFile() throws Exception {
        File file = getTestFile("sample.csv");
        FileParseResultVO result = parser.parse(file, "sample.csv");

        assertTrue(result.isSuccess());
        assertNotNull(result.getContent());
        assertNotNull(result.getContent().getText());
        assertTrue(result.getContent().getText().contains("hello"));

        // 验证 CSV 表格解析
        List<FileParseResultVO.TableContent> tables = result.getContent().getTables();
        assertNotNull(tables);
        assertFalse(tables.isEmpty());
        assertEquals("CSV", tables.get(0).getSheetName());
        assertFalse(tables.get(0).getRows().isEmpty());
    }

    @Test
    void shouldParseMdFile() throws Exception {
        File file = getTestFile("sample.md");
        FileParseResultVO result = parser.parse(file, "sample.md");

        assertTrue(result.isSuccess());
        assertNotNull(result.getContent());
        assertNotNull(result.getContent().getText());
        assertTrue(result.getContent().getText().contains("Section 1"));
        assertTrue(result.getContent().getText().contains("markdown"));
    }

    @Test
    void shouldHandleEmptyFile() throws Exception {
        // 创建一个空文件
        File tempFile = File.createTempFile("empty", ".txt");
        tempFile.deleteOnExit();

        FileParseResultVO result = parser.parse(tempFile, "empty.txt");

        assertTrue(result.isSuccess());
        assertNotNull(result.getContent());
        assertEquals("", result.getContent().getText());
    }

    @Test
    void shouldHandleNonExistentFile() {
        File nonExistent = new File("/nonexistent/file.txt");
        FileParseResultVO result = parser.parse(nonExistent, "nonexistent.txt");

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
    }

    private File getTestFile(String fileName) {
        URL url = getClass().getClassLoader().getResource("files/" + fileName);
        assertNotNull(url, "Test file not found: " + fileName);
        return new File(url.getFile());
    }
}