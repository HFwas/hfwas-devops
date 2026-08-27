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
import java.lang.reflect.Method;

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
        // 信创/国产图片格式
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
        // 不支持的格式
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

    @Test
    void shouldDetectXinchuangImageMimeTypes() throws Exception {
        // 使用反射测试私有方法 detectMimeType
        Method method = ImageOcrParser.class.getDeclaredMethod("detectMimeType", String.class);
        method.setAccessible(true);

        assertEquals("image/pcx", method.invoke(parser, "image.pcx"));
        assertEquals("image/jp2", method.invoke(parser, "image.jp2"));
        assertEquals("image/jpeg2000", method.invoke(parser, "image.j2k"));
        assertEquals("image/jpeg2000", method.invoke(parser, "image.jpf"));
        assertEquals("image/wmf", method.invoke(parser, "image.wmf"));
        assertEquals("image/emf", method.invoke(parser, "image.emf"));
        assertEquals("image/vnd.djvu", method.invoke(parser, "image.djvu"));
        assertEquals("image/vnd.djvu", method.invoke(parser, "image.djv"));
        assertEquals("image/x-xbitmap", method.invoke(parser, "image.xbm"));
        assertEquals("image/x-xpixmap", method.invoke(parser, "image.xpm"));
        assertEquals("image/x-portable-bitmap", method.invoke(parser, "image.pbm"));
        assertEquals("image/x-portable-graymap", method.invoke(parser, "image.pgm"));
        assertEquals("image/x-portable-pixmap", method.invoke(parser, "image.ppm"));
        assertEquals("image/x-portable-anymap", method.invoke(parser, "image.pnm"));
        assertEquals("image/vnd.wap.wbmp", method.invoke(parser, "image.wbmp"));
        assertEquals("image/heic", method.invoke(parser, "image.heic"));
        assertEquals("image/heif", method.invoke(parser, "image.heif"));
        assertEquals("image/avif", method.invoke(parser, "image.avif"));

        // 未知格式返回 unknown
        assertEquals("image/unknown", method.invoke(parser, "image.unknown"));
        assertEquals("image/unknown", method.invoke(parser, (String) null));
    }

    private File getTestFile(String fileName) {
        URL url = getClass().getClassLoader().getResource("files/" + fileName);
        assertNotNull(url, "Test file not found: " + fileName);
        return new File(url.getFile());
    }
}