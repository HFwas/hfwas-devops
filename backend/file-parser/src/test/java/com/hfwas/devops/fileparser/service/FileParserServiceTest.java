package com.hfwas.devops.fileparser.service;

import com.hfwas.devops.fileparser.config.FileParserConfig;
import com.hfwas.devops.fileparser.dto.FileParseResultVO;
import com.hfwas.devops.fileparser.parser.ImageOcrParser;
import com.hfwas.devops.fileparser.parser.PlainTextParser;
import com.hfwas.devops.fileparser.parser.ScannedPdfParser;
import com.hfwas.devops.fileparser.parser.TikaDocumentParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileParserServiceTest {

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private TikaDocumentParser tikaParser;

    @Mock
    private ScannedPdfParser scannedPdfParser;

    @Mock
    private ImageOcrParser imageOcrParser;

    @Mock
    private PlainTextParser plainTextParser;

    @Mock
    private FileParserConfig config;

    @Mock
    private FileParserConfig.MimeConfig mimeConfig;

    private FileParserService fileParserService;

    @BeforeEach
    void setUp() {
        lenient().when(config.getMime()).thenReturn(mimeConfig);
        lenient().when(mimeConfig.getExtensionMappings()).thenReturn(null);
        lenient().when(mimeConfig.getAdditionalMimePrefixes()).thenReturn(null);
        fileParserService = new FileParserService(
                fileStorageService, tikaParser, scannedPdfParser,
                imageOcrParser, plainTextParser, config
        );
    }

    @Test
    void shouldParseTextFile() throws Exception {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", "test.txt", "text/plain", "hello world".getBytes(StandardCharsets.UTF_8)
        );

        File tempFile = File.createTempFile("test-", ".txt");
        tempFile.deleteOnExit();
        java.nio.file.Files.writeString(tempFile.toPath(), "hello world");

        when(fileStorageService.save(any())).thenReturn(tempFile);
        when(plainTextParser.supports("text/plain")).thenReturn(true);
        when(plainTextParser.parse(any(File.class), eq("test.txt")))
                .thenReturn(FileParseResultVO.builder()
                        .success(true)
                        .fileName("test.txt")
                        .fileSize(11)
                        .mimeType("text/plain")
                        .parseMethod("plain")
                        .content(FileParseResultVO.Content.builder()
                                .text("hello world")
                                .build())
                        .build());

        FileParseResultVO result = fileParserService.parse(multipartFile, null);

        assertTrue(result.isSuccess());
        assertEquals("test.txt", result.getFileName());
        assertEquals("hello world", result.getContent().getText());

        verify(fileStorageService).save(any());
        verify(fileStorageService).delete(any());
    }

    @Test
    void shouldHandleStorageException() throws Exception {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", "test.txt", "text/plain", "content".getBytes()
        );

        when(fileStorageService.save(any())).thenThrow(new IOException("存储空间不足"));

        FileParseResultVO result = fileParserService.parse(multipartFile, null);

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void shouldRouteToTikaForOfficeFile() throws Exception {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", "document.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "fake docx content".getBytes()
        );

        File tempFile = File.createTempFile("test-", ".docx");
        tempFile.deleteOnExit();

        when(fileStorageService.save(any())).thenReturn(tempFile);
        when(tikaParser.supports(anyString())).thenReturn(true);
        when(tikaParser.parse(any(File.class), eq("document.docx")))
                .thenReturn(FileParseResultVO.builder()
                        .success(true)
                        .fileName("document.docx")
                        .fileSize(100)
                        .mimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                        .parseMethod("tika")
                        .content(FileParseResultVO.Content.builder()
                                .text("文档内容")
                                .build())
                        .build());

        FileParseResultVO result = fileParserService.parse(multipartFile, null);

        assertTrue(result.isSuccess());
        assertEquals("tika", result.getParseMethod());

        verify(tikaParser).parse(any(File.class), eq("document.docx"));
        verify(fileStorageService).delete(any());
    }

    @Test
    void shouldRouteToTikaForPdfWithoutThrowingOnMimeDetect() throws Exception {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "%PDF-1.4 fake".getBytes(StandardCharsets.UTF_8)
        );

        File tempFile = File.createTempFile("test-", ".pdf");
        tempFile.deleteOnExit();
        java.nio.file.Files.writeString(tempFile.toPath(), "%PDF-1.4 fake");

        when(fileStorageService.save(any())).thenReturn(tempFile);
        when(tikaParser.supports(anyString())).thenReturn(true);
        when(tikaParser.parse(any(File.class), eq("doc.pdf")))
                .thenReturn(FileParseResultVO.builder()
                        .success(true)
                        .fileName("doc.pdf")
                        .fileSize(tempFile.length())
                        .mimeType("application/pdf")
                        .parseMethod("tika")
                        .content(FileParseResultVO.Content.builder().text("pdf text").build())
                        .build());

        FileParseResultVO result = fileParserService.parse(multipartFile, null);

        assertTrue(result.isSuccess(), result.getErrorMessage());
        assertEquals("tika", result.getParseMethod());
        verify(tikaParser).parse(any(File.class), eq("doc.pdf"));
        verify(fileStorageService).delete(any());
    }

    @Test
    void shouldRouteToImageOcrForImageFile() throws Exception {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", "photo.png", "image/png", "fake png content".getBytes()
        );

        File tempFile = File.createTempFile("test-", ".png");
        tempFile.deleteOnExit();

        when(fileStorageService.save(any())).thenReturn(tempFile);
        when(imageOcrParser.supports("image/png")).thenReturn(true);
        when(imageOcrParser.parse(any(File.class), eq("photo.png")))
                .thenReturn(FileParseResultVO.builder()
                        .success(true)
                        .fileName("photo.png")
                        .fileSize(100)
                        .mimeType("image/png")
                        .parseMethod("ocr")
                        .content(FileParseResultVO.Content.builder()
                                .text("图片文字")
                                .build())
                        .build());

        FileParseResultVO result = fileParserService.parse(multipartFile, null);

        assertTrue(result.isSuccess());
        assertEquals("ocr", result.getParseMethod());

        verify(imageOcrParser).parse(any(File.class), eq("photo.png"));
        verify(fileStorageService).delete(any());
    }

    @Test
    void shouldReturnUnsupportedForUnknownFormat() throws Exception {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", "unknown.xyz", "application/octet-stream", "content".getBytes()
        );

        File tempFile = File.createTempFile("test-", ".xyz");
        tempFile.deleteOnExit();

        when(fileStorageService.save(any())).thenReturn(tempFile);

        FileParseResultVO result = fileParserService.parse(multipartFile, null);

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("不支持"));
    }

    @Test
    void shouldReturnOfdUnsupportedForApplicationOfd() throws Exception {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", "doc.ofd", "application/ofd", "fake ofd content".getBytes()
        );

        File tempFile = File.createTempFile("test-", ".ofd");
        tempFile.deleteOnExit();

        when(fileStorageService.save(any())).thenReturn(tempFile);

        FileParseResultVO result = fileParserService.parse(multipartFile, null);

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("OFD"));
    }

    @Test
    void shouldReturnOfdUnsupportedForVndOfd() throws Exception {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file", "doc.ofd", "application/vnd.ofd", "fake ofd content".getBytes()
        );

        File tempFile = File.createTempFile("test-", ".ofd");
        tempFile.deleteOnExit();

        when(fileStorageService.save(any())).thenReturn(tempFile);

        FileParseResultVO result = fileParserService.parse(multipartFile, null);

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("OFD"));
    }

    @Test
    void shouldRouteToImageOcrForXinchuangImageFormats() throws Exception {
        // 测试各种信创图片格式的路由
        String[] xinchuangImages = {
                "image.pcx", "image.jp2", "image.j2k", "image.jpf",
                "image.wmf", "image.emf", "image.djvu", "image.djv",
                "image.xbm", "image.xpm", "image.pbm", "image.pgm",
                "image.ppm", "image.pnm", "image.wbmp", "image.heic",
                "image.heif", "image.avif"
        };

        for (String fileName : xinchuangImages) {
            MockMultipartFile multipartFile = new MockMultipartFile(
                    "file", fileName, "application/octet-stream", "fake content".getBytes()
            );

            File tempFile = File.createTempFile("test-", ".tmp");
            tempFile.deleteOnExit();

            when(fileStorageService.save(any())).thenReturn(tempFile);
            when(imageOcrParser.supports(anyString())).thenReturn(true);
            when(imageOcrParser.parse(any(File.class), eq(fileName)))
                    .thenReturn(FileParseResultVO.builder()
                            .success(true)
                            .fileName(fileName)
                            .fileSize(100)
                            .mimeType("image/unknown")
                            .parseMethod("ocr")
                            .content(FileParseResultVO.Content.builder().text("文字").build())
                            .build());

            FileParseResultVO result = fileParserService.parse(multipartFile, null);

            assertTrue(result.isSuccess(), "应成功路由 " + fileName);
            assertEquals("ocr", result.getParseMethod(), fileName + " 应路由到 OCR 解析器");

            verify(imageOcrParser).parse(any(File.class), eq(fileName));
            verify(fileStorageService).delete(any());

            // 重置 mock 状态
            reset(fileStorageService, imageOcrParser);
            lenient().when(config.getMime()).thenReturn(mimeConfig);
            lenient().when(mimeConfig.getExtensionMappings()).thenReturn(null);
            lenient().when(mimeConfig.getAdditionalMimePrefixes()).thenReturn(null);
        }
    }

    @Test
    void shouldHaveXinchuangImageExtensionMappings() throws Exception {
        // 通过反射获取 EXTENSION_MIME_MAP
        Field field = FileParserService.class.getDeclaredField("EXTENSION_MIME_MAP");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, String> map = (Map<String, String>) field.get(null);

        // 验证信创图片格式的 MIME 映射
        assertEquals("image/pcx", map.get(".pcx"));
        assertEquals("image/jp2", map.get(".jp2"));
        assertEquals("image/jpeg2000", map.get(".j2k"));
        assertEquals("image/jpeg2000", map.get(".jpf"));
        assertEquals("image/wmf", map.get(".wmf"));
        assertEquals("image/emf", map.get(".emf"));
        assertEquals("image/vnd.djvu", map.get(".djvu"));
        assertEquals("image/vnd.djvu", map.get(".djv"));
        assertEquals("image/x-xbitmap", map.get(".xbm"));
        assertEquals("image/x-xpixmap", map.get(".xpm"));
        assertEquals("image/x-portable-bitmap", map.get(".pbm"));
        assertEquals("image/x-portable-graymap", map.get(".pgm"));
        assertEquals("image/x-portable-pixmap", map.get(".ppm"));
        assertEquals("image/x-portable-anymap", map.get(".pnm"));
        assertEquals("image/vnd.wap.wbmp", map.get(".wbmp"));
        assertEquals("image/heic", map.get(".heic"));
        assertEquals("image/heif", map.get(".heif"));
        assertEquals("image/avif", map.get(".avif"));
    }
}