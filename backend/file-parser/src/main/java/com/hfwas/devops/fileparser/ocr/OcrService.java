package com.hfwas.devops.fileparser.ocr;

import com.benjaminwan.ocrlibrary.TextBlock;
import io.github.mymonstercat.Model;
import io.github.mymonstercat.ocr.InferenceEngine;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

/**
 * OCR 识别服务
 * 使用 RapidOCR（基于 PaddleOCR + ONNX Runtime）进行图片文字识别。
 *
 * RapidOCR 通过 Java 进程内直接调用 ONNX 模型推理，无需安装任何系统级 OCR 引擎。
 * 模型文件在首次调用时自动下载到 ~/.rapidocr/models/。
 *
 * 支持模型：
 * - ONNX_PPOCR_V3：PP-OCRv3 模型（轻量级，速度更快）
 * - ONNX_PPOCR_V4：PP-OCRv4 模型（精度更高，推荐）
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "file-parser.ocr.enabled", havingValue = "true", matchIfMissing = true)
public class OcrService {

    private InferenceEngine engine;

    @PostConstruct
    public void init() {
        try {
            log.info("Initializing RapidOCR engine (ONNX_PPOCR_V4)...");
            engine = InferenceEngine.getInstance(Model.ONNX_PPOCR_V4);
            log.info("RapidOCR engine initialized successfully");
        } catch (Exception e) {
            log.error("Failed to initialize RapidOCR engine: {}", e.getMessage());
        }
    }

    /**
     * 识别图片中的文字
     *
     * @param imageFile 图片文件
     * @return 识别文本
     */
    public String recognize(File imageFile) {
        if (engine == null) {
            log.warn("RapidOCR engine not initialized, cannot recognize: {}", imageFile.getName());
            return "";
        }

        try {
            String imagePath = imageFile.getAbsolutePath();
            com.benjaminwan.ocrlibrary.OcrResult result = engine.runOcr(imagePath);

            if (result == null) {
                log.warn("RapidOCR returned null for {}", imageFile.getName());
                return "";
            }

            String text = result.getStrRes();
            if (text == null) {
                return "";
            }

            return text.trim();

        } catch (Exception e) {
            log.error("RapidOCR recognition failed for {}: {}", imageFile.getName(), e.getMessage());
            return "";
        }
    }

    /**
     * 识别图片中的文字，并返回置信度
     * 置信度取所有文本块的平均值
     */
    public OcrResult recognizeWithConfidence(File imageFile) {
        if (engine == null) {
            log.warn("RapidOCR engine not initialized, cannot recognize: {}", imageFile.getName());
            return new OcrResult("", 0.0);
        }

        try {
            String imagePath = imageFile.getAbsolutePath();
            com.benjaminwan.ocrlibrary.OcrResult ocrResult = engine.runOcr(imagePath);

            if (ocrResult == null || ocrResult.getStrRes() == null) {
                return new OcrResult("", 0.0);
            }

            String text = ocrResult.getStrRes().trim();
            double confidence = calculateAverageConfidence(ocrResult.getTextBlocks());

            return new OcrResult(text, confidence);

        } catch (Exception e) {
            log.error("RapidOCR recognition failed for {}: {}", imageFile.getName(), e.getMessage());
            return new OcrResult("", 0.0);
        }
    }

    /**
     * 计算平均置信度
     */
    private double calculateAverageConfidence(List<TextBlock> textBlocks) {
        if (textBlocks == null || textBlocks.isEmpty()) {
            return 0.0;
        }
        return textBlocks.stream()
                .mapToDouble(tb -> (double) tb.getBoxScore())
                .average()
                .orElse(0.0);
    }

    /**
     * 检查 OCR 引擎是否可用
     */
    public boolean isAvailable() {
        return engine != null;
    }

    @PreDestroy
    public void cleanup() {
        if (engine != null) {
            log.info("Closing RapidOCR engine");
            engine = null;
        }
    }

    public record OcrResult(String text, double confidence) {
    }
}