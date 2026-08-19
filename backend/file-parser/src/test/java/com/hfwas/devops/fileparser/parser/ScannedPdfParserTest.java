package com.hfwas.devops.fileparser.parser;

import com.hfwas.devops.fileparser.config.FileParserConfig;
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
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ScannedPdfParserTest {

    @Mock
    private OcrService ocrService;

    @Mock
    private FileParserConfig config;

    @Mock
    private FileParserConfig.ScannedPdfConfig scannedPdfConfig;

    private ScannedPdfParser parser;

    @BeforeEach
    void setUp() {
        lenient().when(config.getScannedPdf()).thenReturn(scannedPdfConfig);
        lenient().when(scannedPdfConfig.getMaxPages()).thenReturn(50);
        lenient().when(scannedPdfConfig.getMaxImageDimension()).thenReturn(2048);
        parser = new ScannedPdfParser(ocrService, config);
    }

    @Test
    void shouldSupportPdfMimeType() {
        assertTrue(parser.supports("application/pdf"));
        assertFalse(parser.supports("image/png"));
        assertFalse(parser.supports("text/plain"));
        assertFalse(parser.supports(null));
    }

    @Test
    void shouldHandleNonExistentPdf() {
        File nonExistent = new File("/nonexistent/file.pdf");
        FileParseResultVO result = parser.parse(nonExistent, "nonexistent.pdf");

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    void shouldUseOcrServiceForScannedPdf() throws Exception {
        // 创建一个真实的 PDF 文件用于测试
        File pdfFile = createMinimalPdf();

        when(ocrService.recognizeWithConfidence(any(File.class)))
                .thenReturn(new OcrService.OcrResult("扫描页文本", 0.88));

        FileParseResultVO result = parser.parse(pdfFile, "scanned.pdf");

        assertTrue(result.isSuccess());
        assertEquals("scanned.pdf", result.getFileName());
        assertEquals("ocr", result.getParseMethod());
        assertNotNull(result.getContent());
        assertNotNull(result.getContent().getPages());
        assertFalse(result.getContent().getPages().isEmpty());

        // 验证 OcrService 被调用（PDF 页数取决于创建的 PDF）
        verify(ocrService, atLeastOnce()).recognizeWithConfidence(any(File.class));

        // 清理临时文件
        pdfFile.delete();
    }

    @Test
    void shouldHandleOcrExceptionDuringPdfProcessing() throws Exception {
        File pdfFile = createMinimalPdf();

        when(ocrService.recognizeWithConfidence(any(File.class)))
                .thenThrow(new RuntimeException("OCR引擎异常"));

        FileParseResultVO result = parser.parse(pdfFile, "scanned.pdf");

        // 如果 OCR 异常，PDF 解析仍然继续（只空结果）
        assertTrue(result.isSuccess());
        assertNotNull(result.getContent());

        pdfFile.delete();
    }

    /**
     * 创建一个包含单页空白内容的微型 PDF
     */
    private File createMinimalPdf() throws Exception {
        File tempFile = File.createTempFile("test-", ".pdf");
        tempFile.deleteOnExit();

        // 最小的有效 PDF 文件（单页空白）
        byte[] pdfContent = {
                '%', 'P', 'D', 'F', '-', '1', '.', '4', '\n',
                '1', ' ', '0', ' ', 'o', 'b', 'j', '\n',
                '<', '<', ' ', '/', 'T', 'y', 'p', 'e', ' ', '/', 'C', 'a', 't', 'a', 'l', 'o', 'g', ' ',
                '/', 'P', 'a', 'g', 'e', 's', ' ', '2', ' ', '0', ' ', 'R', ' ', '>', '>', '\n',
                'e', 'n', 'd', 'o', 'b', 'j', '\n',
                '2', ' ', '0', ' ', 'o', 'b', 'j', '\n',
                '<', '<', ' ', '/', 'T', 'y', 'p', 'e', ' ', '/', 'P', 'a', 'g', 'e', 's', ' ',
                '/', 'K', 'i', 'd', 's', ' ', '[', '3', ' ', '0', ' ', 'R', ']', ' ',
                '/', 'C', 'o', 'u', 'n', 't', ' ', '1', ' ', '>', '>', '\n',
                'e', 'n', 'd', 'o', 'b', 'j', '\n',
                '3', ' ', '0', ' ', 'o', 'b', 'j', '\n',
                '<', '<', ' ', '/', 'T', 'y', 'p', 'e', ' ', '/', 'P', 'a', 'g', 'e', ' ',
                '/', 'P', 'a', 'r', 'e', 'n', 't', ' ', '2', ' ', '0', ' ', 'R', ' ',
                '/', 'M', 'e', 'd', 'i', 'a', 'B', 'o', 'x', ' ', '[', '0', ' ', '0', ' ', '6', '1', '2', ' ', '7', '9', '2', ']', ' ',
                '>', '>', '\n',
                'e', 'n', 'd', 'o', 'b', 'j', '\n',
                'x', 'r', 'e', 'f', '\n',
                '0', ' ', '4', '\n',
                '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', ' ', '6', '5', '5', '3', '5', ' ', 'f', ' ', '\n',
                '0', '0', '0', '0', '0', '0', '0', '0', '0', '1', '5', ' ', '0', '0', '0', '0', '0', ' ', 'n', ' ', '\n',
                '0', '0', '0', '0', '0', '0', '0', '0', '0', '1', '0', '6', ' ', '0', '0', '0', '0', '0', ' ', 'n', ' ', '\n',
                '0', '0', '0', '0', '0', '0', '0', '0', '0', '1', '9', '7', ' ', '0', '0', '0', '0', '0', ' ', 'n', ' ', '\n',
                't', 'r', 'a', 'i', 'l', 'e', 'r', '\n',
                '<', '<', ' ', '/', 'S', 'i', 'z', 'e', ' ', '4', ' ', '/', 'R', 'o', 'o', 't', ' ', '1', ' ', '0', ' ', 'R', ' ', '>', '>', '\n',
                's', 't', 'a', 'r', 't', 'x', 'r', 'e', 'f', '\n',
                '2', '9', '1', '\n',
                '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '2', '9', '1', ' ', '0', '0', '0', '0', '0', '\n',
                '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', ' ', '0', '0', '0', '0', '0', '\n',
                '%', '%', 'E', 'O', 'F'
        };
        java.nio.file.Files.write(tempFile.toPath(), pdfContent);
        return tempFile;
    }
}