package com.hfwas.devops.fileparser.ocr;

import com.benjaminwan.ocrlibrary.TextBlock;
import com.hfwas.devops.fileparser.config.FileParserConfig;
import io.github.mymonstercat.Model;
import io.github.mymonstercat.ocr.InferenceEngine;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * OCR 识别服务
 * 使用 RapidOCR（基于 PaddleOCR + ONNX Runtime）进行图片文字识别。
 *
 * RapidOCR 通过 Java 进程内直接调用 ONNX 模型推理，无需安装任何系统级 OCR 引擎。
 * 模型文件在首次调用时自动下载到 ~/.rapidocr/models/。
 *
 * ONNX Runtime 推理在堆外内存（Native Memory）中执行，不受 JVM -Xmx 约束。
 * 通过 Semaphore 限制最大并发推理数，防止堆外内存膨胀导致 OOM。
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

    /**
     * 并发控制信号量，限制同时进行的 OCR 推理数。
     * ONNX Runtime 推理在堆外内存中执行，并发数过高会导致 native memory 膨胀。
     */
    private Semaphore ocrPermits;

    private final FileParserConfig config;

    public OcrService(FileParserConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void init() {
        // 初始化并发控制信号量
        int maxConcurrent = config.getOcr().getMaxConcurrent();
        this.ocrPermits = new Semaphore(maxConcurrent, true);
        log.info("OcrService concurrency limited to {} (Semaphore, fair=true)", maxConcurrent);

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
        OcrResult result = recognizeWithConfidence(imageFile);
        return result != null ? result.text() : "";
    }

    /**
     * 识别图片中的文字，并返回置信度
     * 置信度取所有文本块的平均值
     * 通过 Semaphore 控制并发，防止堆外内存膨胀。
     */
    public OcrResult recognizeWithConfidence(File imageFile) {
        if (engine == null) {
            log.warn("RapidOCR engine not initialized, cannot recognize: {}", imageFile.getName());
            return new OcrResult("", 0.0);
        }

        // 尝试获取信号量，等待最多 60 秒
        boolean acquired = false;
        try {
            acquired = ocrPermits.tryAcquire(60, TimeUnit.SECONDS);
            if (!acquired) {
                log.error("OCR concurrency limit reached, timed out waiting for permit: {}",
                        imageFile.getName());
                return new OcrResult("", 0.0);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("OCR thread interrupted while waiting for permit: {}", imageFile.getName());
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
        } finally {
            ocrPermits.release();
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