package com.hfwas.devops.fileparser.ocr;

import com.hfwas.devops.fileparser.config.FileParserConfig;
import com.hfwas.devops.fileparser.ocr.onnx.OcrPipeline;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * OCR 识别服务
 * 使用 PP-OCRv6 + ONNX Runtime Java 进行图片文字识别。
 *
 * 相比旧的 RapidOCR 实现：
 * - 模型从 PP-OCRv4 升级到 PP-OCRv6（检测+5~10%，识别+5~10%）
 * - 推理速度提升 2~5×
 * - 直接使用 ONNX Runtime Java，无需第三方封装库
 *
 * 模型文件在首次调用时自动从 HuggingFace 下载到本地缓存目录，
 * 默认使用 Small 模型（7.7M 参数），可通过配置切换为 Tiny 或 Medium。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "file-parser.ocr.enabled", havingValue = "true", matchIfMissing = true)
public class OcrService {

    private OcrPipeline pipeline;
    private final FileParserConfig config;

    public OcrService(FileParserConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void init() {
        try {
            // 读取 ONNX 模型配置
            var onnxConfig = config.getOnnx();
            String modelDir = (onnxConfig != null && onnxConfig.getModelDir() != null && !onnxConfig.getModelDir().isEmpty())
                    ? onnxConfig.getModelDir()
                    : System.getProperty("user.home") + "/.hfwas-devops/models/ppocrv6";
            String modelTier = onnxConfig != null ? onnxConfig.getModelTier() : "medium";
            int maxConcurrent = Math.max(1, config.getOcr().getMaxConcurrent());

            Files.createDirectories(Path.of(modelDir));

            log.info("Initializing OcrPipeline (PP-OCRv6, modelTier={}, maxConcurrent={})...",
                    modelTier, maxConcurrent);
            this.pipeline = new OcrPipeline(new File(modelDir), modelTier, maxConcurrent);
            log.info("OcrPipeline initialized successfully");
        } catch (IOException e) {
            log.error("Failed to initialize OcrPipeline (IO error): {}", e.getMessage());
        } catch (Exception e) {
            log.error("Failed to initialize OcrPipeline: {}", e.getMessage());
        }
    }

    /**
     * 识别图片中的文字
     *
     * @param imageFile 图片文件
     * @return 识别文本
     */
    public String recognize(File imageFile) {
        if (pipeline == null) {
            log.warn("OcrPipeline not initialized, cannot recognize: {}", imageFile.getName());
            return "";
        }
        OcrPipeline.OcrResult result = pipeline.recognize(imageFile);
        return result != null ? result.text() : "";
    }

    /**
     * 识别图片中的文字，并返回置信度
     *
     * @param imageFile 图片文件
     * @return 识别结果（含置信度）
     */
    public OcrResult recognizeWithConfidence(File imageFile) {
        if (pipeline == null) {
            log.warn("OcrPipeline not initialized, cannot recognize: {}", imageFile.getName());
            return new OcrResult("", 0.0);
        }
        OcrPipeline.OcrResult result = pipeline.recognizeWithConfidence(imageFile);
        return new OcrResult(result.text(), result.confidence());
    }

    /**
     * 检查 OCR 引擎是否可用
     */
    public boolean isAvailable() {
        return pipeline != null && pipeline.isAvailable();
    }

    @PreDestroy
    public void cleanup() {
        if (pipeline != null) {
            log.info("Closing OcrPipeline");
            pipeline.close();
            pipeline = null;
        }
    }

    /**
     * OCR 结果
     */
    public record OcrResult(String text, double confidence) {
    }
}