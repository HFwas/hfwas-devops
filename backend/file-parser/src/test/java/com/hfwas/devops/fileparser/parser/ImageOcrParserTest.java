package com.hfwas.devops.fileparser.parser;

import com.hfwas.devops.fileparser.dto.FileParseResultVO;
import com.hfwas.devops.fileparser.ocr.OcrService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageOcrParserTest {

    @Mock
    private OcrService ocrService;

    private ImageOcrParser parser;

    @BeforeEach
    void setUp() {
        parser = new ImageOcrParser(ocrService);
    }

    @Test
    void shouldSupportImageMimeTypes() {
        assertTrue(parser.supports("image/jpeg"));
        assertTrue(parser.supports("image/png"));
        assertTrue(parser.supports("image/bmp"));
        assertTrue(parser.supports("image/tiff"));
        assertTrue(parser.supports("image/webp"));
        assertFalse(parser.supports("application/pdf"));
        assertFalse(parser.supports("text/plain"));
        assertFalse(parser.supports(null));
    }

    @Test
    void shouldParseImageWithOcr() throws Exception {
        File file = getTestFile("sample.png");
        when(ocrService.recognizeWithConfidence(any(File.class)))
                .thenReturn(new OcrService.OcrResult("OCR识别文本", 0.92));

        FileParseResultVO result = parser.parse(file, "sample.png");

        assertTrue(result.isSuccess());
        assertEquals("sample.png", result.getFileName());
        assertEquals("ocr", result.getParseMethod());
        assertEquals("OCR识别文本", result.getContent().getText());
        assertNotNull(result.getOcrInfo());
        assertEquals("rapidocr", result.getOcrInfo().getEngine());
        assertEquals(0.92, result.getOcrInfo().getConfidence(), 0.01);

        verify(ocrService).recognizeWithConfidence(any(File.class));
    }

    @Test
    void shouldHandleOcrFailure() throws Exception {
        File file = getTestFile("sample.png");
        when(ocrService.recognizeWithConfidence(any(File.class)))
                .thenReturn(new OcrService.OcrResult("", 0.0));

        FileParseResultVO result = parser.parse(file, "sample.png");

        assertTrue(result.isSuccess());
        assertEquals("", result.getContent().getText());
        assertEquals(0.0, result.getOcrInfo().getConfidence(), 0.01);
    }

    @Test
    void shouldHandleOcrException() throws Exception {
        File file = getTestFile("sample.png");
        when(ocrService.recognizeWithConfidence(any(File.class)))
                .thenThrow(new RuntimeException("OCR引擎异常"));

        FileParseResultVO result = parser.parse(file, "sample.png");

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("OCR"));
    }

    @Test
    void shouldAddWarningForLowConfidence() throws Exception {
        File file = getTestFile("sample.png");
        when(ocrService.recognizeWithConfidence(any(File.class)))
                .thenReturn(new OcrService.OcrResult("模糊文本", 0.3));

        FileParseResultVO result = parser.parse(file, "sample.png");

        assertTrue(result.isSuccess());
        assertNotNull(result.getWarnings());
        assertFalse(result.getWarnings().isEmpty());
    }

    private File getTestFile(String fileName) {
        URL url = getClass().getClassLoader().getResource("files/" + fileName);
        assertNotNull(url, "Test file not found: " + fileName);
        return new File(url.getFile());
    }
}