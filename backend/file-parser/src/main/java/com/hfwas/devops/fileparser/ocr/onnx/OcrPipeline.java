package com.hfwas.devops.fileparser.ocr.onnx;

import ai.onnxruntime.OrtException;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * OCR 推理管线
 * 编排完整的 PP-OCRv6 推理流程：
 * 预处理 → 检测推理 → DB 后处理 → 识别推理 → CTC 解码
 *
 * <h3>线程安全</h3>
 * 使用 Semaphore 控制并发数，防止堆外内存膨胀。
 * 每个推理调用独立，不共享可变状态。
 */
@Slf4j
public class OcrPipeline implements AutoCloseable {

    private final PaddleOcrEngine engine;
    private final ImagePreprocessor preprocessor;
    private final DetectionPostProcessor detPostProcessor;
    private final RecognitionPostProcessor recPostProcessor;
    private final Semaphore ocrPermits;

    /**
     * 创建 OCR 推理管线
     *
     * @param modelDir        模型目录
     * @param modelTier       模型档次: tiny | small | medium
     * @param maxConcurrent   最大并发推理数
     */
    public OcrPipeline(File modelDir, String modelTier, int maxConcurrent) throws IOException, OrtException {
        this.engine = new PaddleOcrEngine(modelDir.toPath(), modelTier);
        this.preprocessor = new ImagePreprocessor();
        this.detPostProcessor = new DetectionPostProcessor();
        this.recPostProcessor = new RecognitionPostProcessor(
                modelDir.toPath().resolve("dict.txt")
        );
        this.ocrPermits = new Semaphore(maxConcurrent, true);
        log.info("OcrPipeline initialized: modelTier={}, maxConcurrent={}", modelTier, maxConcurrent);
    }

    /**
     * 执行 OCR 识别
     *
     * @param imageFile 图片文件
     * @return OCR 结果
     */
    public OcrResult recognize(File imageFile) {
        OcrResult result = recognizeWithConfidence(imageFile);
        return result != null ? result : new OcrResult("", 0.0);
    }

    /**
     * 执行 OCR 识别，返回置信度
     *
     * @param imageFile 图片文件
     * @return OCR 结果（含置信度），或 null 表示失败
     */
    public OcrResult recognizeWithConfidence(File imageFile) {
        if (!engine.isAvailable()) {
            log.warn("OCR engine not available, cannot recognize: {}", imageFile.getName());
            return new OcrResult("", 0.0);
        }

        // 尝试获取信号量
        boolean acquired = false;
        try {
            acquired = ocrPermits.tryAcquire(60, TimeUnit.SECONDS);
            if (!acquired) {
                log.error("OCR concurrency limit reached, timed out: {}", imageFile.getName());
                return new OcrResult("", 0.0);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("OCR thread interrupted: {}", imageFile.getName());
            return new OcrResult("", 0.0);
        }

        long start = System.nanoTime();
        try {
            BufferedImage image = ImageIO.read(imageFile);
            if (image == null) {
                log.warn("Cannot read image: {}", imageFile.getName());
                return new OcrResult("", 0.0);
            }
            log.info("OCR image {} size={}x{}", imageFile.getName(), image.getWidth(), image.getHeight());
            image = preprocessor.limitWorkingSize(image);

            PreprocessedImage detInput = preprocessor.preprocessForDetection(image);
            float[][][][] detOutput = engine.runDetection(detInput.tensor());
            List<TextBlock> textBlocks = detPostProcessor.process(detOutput, detInput);
            detOutput = null;

            if (textBlocks.isEmpty()) {
                return new OcrResult("", 0.0);
            }

            StringBuilder fullText = new StringBuilder();
            double totalConfidence = 0;
            double mergeY = DetectionPostProcessor.rowMergeThreshold(image.getHeight());
            Double prevY = null;
            int recognized = 0;

            for (TextBlock block : textBlocks) {
                BufferedImage crop = preprocessor.extractTextRegion(image, block);
                float[][][][] recInput = preprocessor.preprocessForRecognition(crop);
                float[][][] recOutput = engine.runRecognition(recInput);
                RecognitionPostProcessor.DecodeResult decoded = recPostProcessor.decodeWithConfidence(recOutput);
                String text = decoded.text();

                if (text.isEmpty()) {
                    continue;
                }
                if (fullText.length() > 0) {
                    if (prevY != null && Math.abs(block.getCenterY() - prevY) < mergeY) {
                        fullText.append(' ');
                    } else {
                        fullText.append('\n');
                    }
                }
                fullText.append(text);
                totalConfidence += decoded.confidence();
                prevY = block.getCenterY();
                recognized++;
            }

            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            double avgConfidence = recognized > 0 ? totalConfidence / recognized : 0.0;

            log.info("OCR recognized {} text blocks in {}ms: {}",
                    textBlocks.size(), elapsedMs, imageFile.getName());

            return new OcrResult(fullText.toString(), avgConfidence);

        } catch (OrtException e) {
            log.error("OCR inference failed for {}: {}", imageFile.getName(), e.getMessage());
            return new OcrResult("", 0.0);
        } catch (IOException e) {
            log.error("OCR image read failed for {}: {}", imageFile.getName(), e.getMessage());
            return new OcrResult("", 0.0);
        } catch (OutOfMemoryError e) {
            String msg = e.getMessage();
            log.error("OCR ran out of memory for {}: {}", imageFile.getName(), msg);
            if (msg != null && msg.contains("maxPhysicalBytes")) {
                throw new IllegalStateException("图片过大，OCR 内存不足，请缩小图片后重试");
            }
            throw e;
        } finally {
            try {
                org.bytedeco.javacpp.Pointer.deallocateReferences();
            } catch (Exception ignored) {
                // native 回收失败不应掩盖主流程结果
            }
            ocrPermits.release();
        }
    }

    /**
     * 检查引擎是否可用
     */
    public boolean isAvailable() {
        return engine.isAvailable();
    }

    @Override
    public void close() {
        engine.close();
    }

    /**
     * OCR 结果
     */
    public record OcrResult(String text, double confidence) {
    }
}