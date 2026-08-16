package com.hfwas.devops.fileparser.parser;

import com.hfwas.devops.fileparser.dto.FileParseResultVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class TikaDocumentParserTest {

    private final TikaDocumentParser parser = new TikaDocumentParser();

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

    private File getTestFile(String fileName) {
        URL url = getClass().getClassLoader().getResource("files/" + fileName);
        assertNotNull(url, "Test file not found: " + fileName);
        return new File(url.getFile());
    }
}