package com.hfwas.devops.fileparser.parser;

import com.hfwas.devops.fileparser.config.FileParserConfig;
import com.hfwas.devops.fileparser.dto.FileParseResultVO;
import com.hfwas.devops.fileparser.image.ImageCompressionResult;
import com.hfwas.devops.fileparser.image.ImageCompressionService;
import com.hfwas.devops.fileparser.ocr.OcrService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageOcrParserTest {

    @Mock
    private OcrService ocrService;

    @Mock
    private ImageCompressionService compressionService;

    @Mock
    private FileParserConfig config;

    @Mock
    private FileParserConfig.CompressionConfig compressionConfig;

    private ImageOcrParser parser;

    @BeforeEach
    void setUp() {
        // 默认配置：压缩启用
        lenient().when(config.getCompression()).thenReturn(compressionConfig);
        lenient().when(compressionConfig.isEnabled()).thenReturn(true);
        // 默认压缩服务返回跳过（不压缩）
        lenient().when(compressionConfig.getQuality()).thenReturn(0.8f);
        lenient().when(compressionService.compress(any(File.class), anyString()))
                .thenAnswer(invocation -> {
                    File file = invocation.getArgument(0);
                    return ImageCompressionResult.skipped(file);
                });
        parser = new ImageOcrParser(ocrService, compressionService, config);
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

        verify(compressionService).compress(any(File.class), anyString());
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
    void shouldSkipCompressionWhenDisabled() throws Exception {
        when(compressionConfig.isEnabled()).thenReturn(false);

        File file = getTestFile("sample.png");
        when(ocrService.recognizeWithConfidence(any(File.class)))
                .thenReturn(new OcrService.OcrResult("文本", 0.9));

        FileParseResultVO result = parser.parse(file, "sample.png");

        assertTrue(result.isSuccess());
        verify(compressionService, never()).compress(any(File.class), anyString());
        assertNull(result.getCompressionInfo());
    }

    @Test
    void shouldIncludeCompressionInfoInResult() throws Exception {
        // 创建临时压缩文件
        Path compressedPath = Files.createTempFile("compressed-", ".jpg");
        File compressedFile = compressedPath.toFile();
        compressedFile.deleteOnExit();

        ImageCompressionResult compressionResult = ImageCompressionResult.success(
                compressedFile, 100000, 30000, 1920, 1080, 800, 450);
        when(compressionService.compress(any(File.class), anyString()))
                .thenReturn(compressionResult);

        File file = getTestFile("sample.png");
        when(ocrService.recognizeWithConfidence(any(File.class)))
                .thenReturn(new OcrService.OcrResult("压缩后文本", 0.85));

        FileParseResultVO result = parser.parse(file, "sample.png");

        assertTrue(result.isSuccess());
        assertNotNull(result.getCompressionInfo());
        assertEquals(100000, result.getCompressionInfo().getOriginalSize());
        assertEquals(30000, result.getCompressionInfo().getCompressedSize());
        assertEquals(0.7, result.getCompressionInfo().getCompressionRatio(), 0.01);
        assertEquals(1920, result.getCompressionInfo().getOriginalWidth());
        assertEquals(1080, result.getCompressionInfo().getOriginalHeight());
        assertEquals(800, result.getCompressionInfo().getCompressedWidth());
        assertEquals(450, result.getCompressionInfo().getCompressedHeight());
        assertEquals(0.8f, result.getCompressionInfo().getQuality(), 0.01);
    }

    @Test
    void shouldHandleCompressionFailure() throws Exception {
        // 压缩服务返回失败
        File file = getTestFile("sample.png");
        when(compressionService.compress(any(File.class), anyString()))
                .thenReturn(ImageCompressionResult.failed(file, "Compression failed"));

        when(ocrService.recognizeWithConfidence(any(File.class)))
                .thenReturn(new OcrService.OcrResult("原图文本", 0.9));

        FileParseResultVO result = parser.parse(file, "sample.png");

        assertTrue(result.isSuccess());
        assertEquals("原图文本", result.getContent().getText());
        // 压缩失败时 compressionInfo 应为 null
        assertNull(result.getCompressionInfo());
    }

    @Test
    void shouldCleanupCompressedTempFile() throws Exception {
        // 创建临时压缩文件
        Path compressedPath = Files.createTempFile("compressed-", ".jpg");
        File compressedFile = compressedPath.toFile();
        assertTrue(compressedFile.exists());

        ImageCompressionResult compressionResult = ImageCompressionResult.success(
                compressedFile, 100000, 30000, 1920, 1080, 800, 450);
        when(compressionService.compress(any(File.class), anyString()))
                .thenReturn(compressionResult);

        File file = getTestFile("sample.png");
        when(ocrService.recognizeWithConfidence(any(File.class)))
                .thenReturn(new OcrService.OcrResult("文本", 0.9));

        parser.parse(file, "sample.png");

        // 验证压缩临时文件已被清理
        assertFalse(compressedFile.exists());
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