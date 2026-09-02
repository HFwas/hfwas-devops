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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        assertTrue(parser.supports("image/pcx"));
        assertTrue(parser.supports("image/jp2"));
        assertTrue(parser.supports("image/jpeg2000"));
        assertTrue(parser.supports("image/wmf"));
        assertTrue(parser.supports("image/emf"));
        assertTrue(parser.supports("image/vnd.djvu"));
        assertTrue(parser.supports("image/x-xbitmap"));
        assertTrue(parser.supports("image/x-xpixmap"));
        assertTrue(parser.supports("image/x-portable-bitmap"));
        assertTrue(parser.supports("image/x-portable-graymap"));
        assertTrue(parser.supports("image/x-portable-pixmap"));
        assertTrue(parser.supports("image/x-portable-anymap"));
        assertTrue(parser.supports("image/vnd.wap.wbmp"));
        assertTrue(parser.supports("image/heic"));
        assertTrue(parser.supports("image/heif"));
        assertTrue(parser.supports("image/avif"));
        assertFalse(parser.supports("application/pdf"));
        assertFalse(parser.supports("text/plain"));
        assertFalse(parser.supports(null));
    }

    @Test
    void shouldParseOriginalImageWithOcr() {
        File file = getTestFile("sample.png");
        when(ocrService.recognizeWithConfidence(any(File.class)))
                .thenReturn(new OcrService.OcrResult("OCR识别文本", 0.92));

        FileParseResultVO result = parser.parse(file, "sample.png");

        assertTrue(result.isSuccess());
        assertEquals("sample.png", result.getFileName());
        assertEquals("ocr", result.getParseMethod());
        assertEquals("OCR识别文本", result.getContent().getText());
        assertNotNull(result.getOcrInfo());
        assertEquals(0.92, result.getOcrInfo().getConfidence(), 0.01);

        verify(ocrService).recognizeWithConfidence(file);
    }

    @Test
    void shouldHandleOcrFailure() {
        File file = getTestFile("sample.png");
        when(ocrService.recognizeWithConfidence(any(File.class)))
                .thenReturn(new OcrService.OcrResult("", 0.0));

        FileParseResultVO result = parser.parse(file, "sample.png");

        assertTrue(result.isSuccess());
        assertEquals("", result.getContent().getText());
        assertEquals(0.0, result.getOcrInfo().getConfidence(), 0.01);
    }

    @Test
    void shouldHandleOcrException() {
        File file = getTestFile("sample.png");
        when(ocrService.recognizeWithConfidence(any(File.class)))
                .thenThrow(new RuntimeException("OCR引擎异常"));

        FileParseResultVO result = parser.parse(file, "sample.png");

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("OCR"));
    }

    @Test
    void shouldAddWarningForLowConfidence() {
        File file = getTestFile("sample.png");
        when(ocrService.recognizeWithConfidence(any(File.class)))
                .thenReturn(new OcrService.OcrResult("模糊文本", 0.29));

        FileParseResultVO result = parser.parse(file, "sample.png");

        assertTrue(result.isSuccess());
        assertNotNull(result.getWarnings());
        assertFalse(result.getWarnings().isEmpty());
    }

    @Test
    void shouldDetectXinchuangImageMimeTypes() {
        assertEquals("image/pcx", parser.detectMimeType("image.pcx"));
        assertEquals("image/jp2", parser.detectMimeType("image.jp2"));
        assertEquals("image/jpeg2000", parser.detectMimeType("image.j2k"));
        assertEquals("image/jpeg2000", parser.detectMimeType("image.jpf"));
        assertEquals("image/wmf", parser.detectMimeType("image.wmf"));
        assertEquals("image/emf", parser.detectMimeType("image.emf"));
        assertEquals("image/vnd.djvu", parser.detectMimeType("image.djvu"));
        assertEquals("image/vnd.djvu", parser.detectMimeType("image.djv"));
        assertEquals("image/x-xbitmap", parser.detectMimeType("image.xbm"));
        assertEquals("image/x-xpixmap", parser.detectMimeType("image.xpm"));
        assertEquals("image/x-portable-bitmap", parser.detectMimeType("image.pbm"));
        assertEquals("image/x-portable-graymap", parser.detectMimeType("image.pgm"));
        assertEquals("image/x-portable-pixmap", parser.detectMimeType("image.ppm"));
        assertEquals("image/x-portable-anymap", parser.detectMimeType("image.pnm"));
        assertEquals("image/vnd.wap.wbmp", parser.detectMimeType("image.wbmp"));
        assertEquals("image/heic", parser.detectMimeType("image.heic"));
        assertEquals("image/heif", parser.detectMimeType("image.heif"));
        assertEquals("image/avif", parser.detectMimeType("image.avif"));
        assertEquals("image/unknown", parser.detectMimeType("image.unknown"));
        assertEquals("image/unknown", parser.detectMimeType(null));
    }

    private File getTestFile(String fileName) {
        URL url = getClass().getClassLoader().getResource("files/" + fileName);
        assertNotNull(url, "Test file not found: " + fileName);
        return new File(url.getFile());
    }
}
