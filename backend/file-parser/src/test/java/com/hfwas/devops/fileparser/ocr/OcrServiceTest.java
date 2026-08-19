package com.hfwas.devops.fileparser.ocr;

import com.hfwas.devops.fileparser.config.FileParserConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OcrServiceTest {

    @Mock
    private FileParserConfig config;

    @Mock
    private FileParserConfig.OcrConfig ocrConfig;

    @BeforeEach
    void setUp() {
        lenient().when(config.getOcr()).thenReturn(ocrConfig);
        lenient().when(ocrConfig.getMaxConcurrent()).thenReturn(2);
    }

    @Test
    void shouldReturnEmptyWhenEngineNotInitialized() {
        // 创建一个未初始化的 OcrService（模拟引擎初始化失败的情况）
        OcrService service = new OcrService(config);

        // 不调用 init()
        assertFalse(service.isAvailable());

        File tempFile = new File("test.png");
        String result = service.recognize(tempFile);
        assertEquals("", result);

        OcrService.OcrResult ocrResult = service.recognizeWithConfidence(tempFile);
        assertNotNull(ocrResult);
        assertEquals("", ocrResult.text());
        assertEquals(0.0, ocrResult.confidence(), 0.01);
    }

    @Test
    void shouldCreateOcrResultRecord() {
        OcrService.OcrResult result = new OcrService.OcrResult("测试文本", 0.95);

        assertEquals("测试文本", result.text());
        assertEquals(0.95, result.confidence(), 0.001);
    }
}