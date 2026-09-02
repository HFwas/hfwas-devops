package com.hfwas.devops.fileparser.ocr;

import com.benjaminwan.ocrlibrary.Point;
import com.benjaminwan.ocrlibrary.TextBlock;
import com.hfwas.devops.fileparser.config.FileParserConfig;
import io.github.mymonstercat.Model;
import io.github.mymonstercat.ocr.InferenceEngine;
import io.github.mymonstercat.ocr.config.ParamConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * OCR 识别服务
 * 使用 RapidOCR（基于 PaddleOCR + ONNX Runtime）进行图片文字识别。
 *
 * RapidOCR 通过 Java 进程内直接调用 ONNX 模型推理，无需安装任何系统级 OCR 引擎。
 * 模型文件在首次调用时自动下载到 ~/.rapidocr/models/。
 *
 * <h3>文本块后处理</h3>
 * 利用 RapidOCR 返回的 TextBlock 坐标信息，进行行合并和空格重排：
 * <ul>
 *   <li>根据 Y 坐标差异判断是否属于同一行（解决换行切割问题）</li>
 *   <li>根据 X 坐标间隙判断是否应插入空格（解决空格丢失问题）</li>
 * </ul>
 *
 * <h3>图像预处理</h3>
 * 通过 {@link OcrPreprocessor} 在 OCR 前对图片进行增强处理：
 * <ul>
 *   <li>二值化（Otsu thresholding）：消除颜色干扰，减少数字字母混淆</li>
 *   <li>中值滤波去噪：去除 JPEG 伪影和扫描噪点</li>
 *   <li>自适应直方图均衡化（CLAHE）：增强文字与背景对比度</li>
 * </ul>
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "file-parser.ocr.enabled", havingValue = "true", matchIfMissing = true)
public class OcrService {

    private InferenceEngine engine;

    /** PP-OCRv6 常驻 Python worker（v6 不走 Java ONNX） */
    private OcrPythonWorker engineV6;

    /**
     * 并发控制信号量，限制同时进行的 OCR 推理数。
     * ONNX Runtime 推理在堆外内存中执行，并发数过高会导致 native memory 膨胀。
     */
    private Semaphore ocrPermits;

    private final FileParserConfig config;

    /**
     * 图像预处理器
     */
    private final OcrPreprocessor preprocessor;

    public OcrService(FileParserConfig config) {
        this.config = config;
        this.preprocessor = new OcrPreprocessor();
    }

    @PostConstruct
    public void init() {
        // 初始化并发控制信号量
        int maxConcurrent = config.getOcr().getMaxConcurrent();
        this.ocrPermits = new Semaphore(maxConcurrent, true);
        log.info("OcrService concurrency limited to {} (Semaphore, fair=true)", maxConcurrent);

        String modelVersion = config.getOcr().getModelVersion();
        log.info("OCR 模型版本: {}", modelVersion);

        if ("v6".equalsIgnoreCase(modelVersion)) {
            initV6();
        } else {
            initV4();
        }
    }

    /**
     * 初始化 PP-OCRv4 (RapidOCR) 引擎
     */
    private void initV4() {
        try {
            log.info("Initializing RapidOCR engine (ONNX_PPOCR_V4)...");
            long start = System.currentTimeMillis();

            engine = InferenceEngine.getInstance(Model.ONNX_PPOCR_V4);

            long elapsed = System.currentTimeMillis() - start;
            log.info("RapidOCR engine initialized successfully in {}ms", elapsed);
            log.info("OCR 诊断 - 模型: ONNX_PPOCR_V4, 语言配置: {}, 预处理: {}",
                    config.getOcr().getLang(), config.getOcr().isPreprocessing());

            // 打印系统属性诊断信息
            log.info("OCR 诊断 - os.name={}, os.arch={}, java.library.path={}",
                    System.getProperty("os.name"),
                    System.getProperty("os.arch"),
                    System.getProperty("java.library.path"));
        } catch (Exception e) {
            log.error("Failed to initialize RapidOCR engine: {}", e.getMessage());
        }
    }

    /**
     * 初始化 PP-OCRv6 Python worker
     */
    private void initV6() {
        try {
            log.info("Initializing PP-OCRv6 Python worker...");
            long start = System.currentTimeMillis();
            String python = config.getOcr().getPythonPath();
            String script = config.getOcr().getPythonWorker();
            if (script == null || script.isBlank()) {
                script = OcrPythonWorker.extractBundledScript().toString();
            }
            engineV6 = new OcrPythonWorker(python, script, config.getOcr().getPythonTimeoutMs(), Map.of(
                    "FILE_PARSER_OCR_DET_MODEL_DIR", config.getOcr().getPythonDetModelDir(),
                    "FILE_PARSER_OCR_REC_MODEL_DIR", config.getOcr().getPythonRecModelDir()
            ));
            log.info("PP-OCRv6 Python worker initialized in {}ms ({})",
                    System.currentTimeMillis() - start, script);
        } catch (Exception e) {
            log.error("Failed to initialize PP-OCRv6 Python worker: {}", e.getMessage());
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
     *
     * <h3>预处理</h3>
     * 如果配置启用了预处理（ocr.preprocessing=true），
     * 在 OCR 推理前对图片进行二值化、去噪、对比度增强。
     *
     * <h3>文本块后处理</h3>
     * 利用 TextBlock 坐标信息进行行合并和空格重排，解决换行切割和空格丢失问题。
     */
    public OcrResult recognizeWithConfidence(File imageFile) {
        // 检查使用哪个引擎
        if ("v6".equalsIgnoreCase(config.getOcr().getModelVersion())) {
            return recognizeWithV6(imageFile);
        }

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

        long start = System.nanoTime();
        File processedFile = imageFile;

        try {
            // 1. 可选：图像预处理
            if (config.getOcr().isPreprocessing()) {
                processedFile = preprocessImage(imageFile);
            }

            // 2. 执行 OCR 推理
            String imagePath = processedFile.getAbsolutePath();
            com.benjaminwan.ocrlibrary.OcrResult ocrResult = engine.runOcr(imagePath);

            if (ocrResult == null || ocrResult.getTextBlocks() == null || ocrResult.getTextBlocks().isEmpty()) {
                long elapsedNs = System.nanoTime() - start;
                log.warn("OCR returned no text blocks for {} ({}μs)",
                        imageFile.getName(), TimeUnit.NANOSECONDS.toMicros(elapsedNs));
                return new OcrResult("", 0.0);
            }

            // 3. 文本块后处理：基于坐标的行合并和空格重排
            String postProcessedText = postProcessTextBlocks(ocrResult.getTextBlocks());

            // 4. 计算平均置信度
            double confidence = calculateAverageConfidence(ocrResult.getTextBlocks());

            long elapsedNs = System.nanoTime() - start;
            log.info("OCR recognized {}: {} blocks, {} chars, confidence={}, {}ms",
                    imageFile.getName(),
                    ocrResult.getTextBlocks().size(),
                    postProcessedText.length(),
                    String.format("%.2f", confidence),
                    TimeUnit.NANOSECONDS.toMillis(elapsedNs));

            return new OcrResult(postProcessedText, confidence);

        } catch (Exception e) {
            long elapsedNs = System.nanoTime() - start;
            log.error("RapidOCR recognition failed for {}: {} ({}ms)",
                    imageFile.getName(), e.getMessage(),
                    TimeUnit.NANOSECONDS.toMillis(elapsedNs));
            return new OcrResult("", 0.0);
        } finally {
            // 清理预处理临时文件
            if (processedFile != imageFile && processedFile.exists()) {
                try {
                    processedFile.delete();
                } catch (Exception ignored) {
                }
            }
            ocrPermits.release();
        }
    }

    /**
     * 使用 PP-OCRv6 Python worker 识别
     */
    private OcrResult recognizeWithV6(File imageFile) {
        if (engineV6 == null) {
            log.warn("PP-OCRv6 Python worker not initialized, cannot recognize: {}", imageFile.getName());
            return new OcrResult("", 0.0);
        }

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

        File processedFile = imageFile;
        try {
            if (config.getOcr().isPreprocessing()) {
                processedFile = preprocessImage(imageFile);
            }

            OcrPythonWorker.Result v6Result = engineV6.recognize(processedFile);
            if (!v6Result.ok()) {
                log.warn("PP-OCRv6 worker returned error for {}", imageFile.getName());
                return new OcrResult("", 0.0);
            }

            String text = v6Result.text();
            double confidence = v6Result.confidence();

            String corrected = correctBlockText(text);
            String finalText = applyRegexPostProcessing(corrected);

            log.info("V6 OCR recognized {}: {} chars, confidence={}",
                    imageFile.getName(), finalText.length(), String.format("%.2f", confidence));

            return new OcrResult(finalText, confidence);

        } catch (Exception e) {
            log.error("PP-OCRv6 recognition failed for {}: {}", imageFile.getName(), e.getMessage());
            return new OcrResult("", 0.0);
        } finally {
            if (processedFile != imageFile && processedFile.exists()) {
                try {
                    processedFile.delete();
                } catch (Exception ignored) {
                }
            }
            if (acquired) {
                ocrPermits.release();
            }
        }
    }

    /**
     * 使用自定义 ParamConfig 识别图片中的文字
     * <p>
     * 与 {@link #recognizeWithConfidence(File)} 共享相同的预处理和后处理管线，
     * 但允许调用方自定义 OCR 参数（如 unClipRatio、boxThresh、maxSideLen 等）。
     * 适用于需要针对特定类型图片（如终端截图、低对比度图片）微调参数的场景。
     *
     * <h3>ParamConfig 参数说明</h3>
     * <ul>
     *   <li><b>unClipRatio</b> (1.0-3.0): 文本块扩张系数，越大越容易合并相邻文本。
     *       终端日志等密集文本建议 1.2-1.6，防止段落粘连。</li>
     *   <li><b>boxThresh</b> (0.1-0.5): 文本检测阈值，越低越容易检测到文本区域。
     *       低对比度图片建议 0.2-0.3。</li>
     *   <li><b>boxScoreThresh</b> (0.3-0.7): 文本块得分阈值，越低越容易保留低置信度块。
     *       终端截图建议 0.4-0.5。</li>
     *   <li><b>maxSideLen</b> (0-4096): 图片最大边长，0 表示不缩放。
     *       大图可设为 2048-4096 以加速推理。</li>
     *   <li><b>padding</b> (0-100): 检测区域边距，越大越容易检测边缘文本。</li>
     *   <li><b>doAngle</b> / <b>mostAngle</b>: 文本方向检测和纠正，建议 false/false 以提升速度。</li>
     * </ul>
     *
     * @param imageFile  图片文件
     * @param paramConfig 自定义 OCR 参数配置
     * @return OCR 识别结果
     */
    public OcrResult recognizeWithConfig(File imageFile, ParamConfig paramConfig) {
        // V6 Python worker 不支持 RapidOCR ParamConfig，走默认识别
        if ("v6".equalsIgnoreCase(config.getOcr().getModelVersion())) {
            return recognizeWithV6(imageFile);
        }

        if (engine == null) {
            log.warn("RapidOCR engine not initialized, cannot recognize: {}", imageFile.getName());
            return new OcrResult("", 0.0);
        }

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

        long start = System.nanoTime();
        File processedFile = imageFile;

        try {
            // 1. 可选：图像预处理
            if (config.getOcr().isPreprocessing()) {
                processedFile = preprocessImage(imageFile);
            }

            // 2. 执行 OCR 推理（使用自定义参数）
            String imagePath = processedFile.getAbsolutePath();
            com.benjaminwan.ocrlibrary.OcrResult ocrResult = engine.runOcr(imagePath, paramConfig);

            if (ocrResult == null || ocrResult.getTextBlocks() == null || ocrResult.getTextBlocks().isEmpty()) {
                long elapsedNs = System.nanoTime() - start;
                log.warn("OCR (with config) returned no text blocks for {} ({}μs)",
                        imageFile.getName(), TimeUnit.NANOSECONDS.toMicros(elapsedNs));
                return new OcrResult("", 0.0);
            }

            // 3. 文本块后处理：基于坐标的行合并和空格重排
            String postProcessedText = postProcessTextBlocks(ocrResult.getTextBlocks());

            // 4. 计算平均置信度
            double confidence = calculateAverageConfidence(ocrResult.getTextBlocks());

            long elapsedNs = System.nanoTime() - start;
            log.info("OCR (with config) recognized {}: {} blocks, {} chars, confidence={}, {}ms, params={}",
                    imageFile.getName(),
                    ocrResult.getTextBlocks().size(),
                    postProcessedText.length(),
                    String.format("%.2f", confidence),
                    TimeUnit.NANOSECONDS.toMillis(elapsedNs),
                    paramConfig);

            return new OcrResult(postProcessedText, confidence);

        } catch (Exception e) {
            long elapsedNs = System.nanoTime() - start;
            log.error("OCR (with config) recognition failed for {}: {} ({}ms)",
                    imageFile.getName(), e.getMessage(),
                    TimeUnit.NANOSECONDS.toMillis(elapsedNs));
            return new OcrResult("", 0.0);
        } finally {
            if (processedFile != imageFile && processedFile.exists()) {
                try {
                    processedFile.delete();
                } catch (Exception ignored) {
                }
            }
            ocrPermits.release();
        }
    }

    /**
     * 对图片进行 OCR 前预处理（二值化、去噪、对比度增强）
     *
     * @param imageFile 原始图片文件
     * @return 预处理后的临时图片文件
     * @throws IOException 如果图片读取或写入失败
     */
    private File preprocessImage(File imageFile) throws IOException {
        BufferedImage originalImage = ImageIO.read(imageFile);
        if (originalImage == null) {
            log.warn("Cannot read image for preprocessing, using original: {}", imageFile.getName());
            return imageFile;
        }

        BufferedImage processedImage = preprocessor.preprocess(originalImage);
        if (processedImage == null) {
            log.warn("Preprocessing returned null, using original: {}", imageFile.getName());
            return imageFile;
        }

        // 写入临时文件
        File tempFile = File.createTempFile("ocr-preprocessed-", ".png");
        ImageIO.write(processedImage, "png", tempFile);

        log.debug("Preprocessed {} -> {} ({}x{} -> {}x{})",
                imageFile.getName(), tempFile.getName(),
                originalImage.getWidth(), originalImage.getHeight(),
                processedImage.getWidth(), processedImage.getHeight());

        return tempFile;
    }

    /**
     * 文本块后处理：基于坐标的行合并和空格重排
     * <p>
     * 利用 RapidOCR 返回的 TextBlock 坐标信息进行比默认拼接更精确的行/列重组：
     * <ol>
     *   <li>按 Y 坐标排序，通过自适应间隙检测（gap-based clustering）分行</li>
     *   <li>同一行内按 X 坐标排序（从左到右）</li>
     *   <li>根据 X 坐标间隙判断是否应插入空格</li>
     * </ol>
     *
     * <h3>自适应行检测算法</h3>
     * 对终端日志等固定行距的文本，使用间隙聚类算法：
     * <ul>
     *   <li>计算相邻文本块 centerY 的差值</li>
     *   <li>取差值的中位数作为行间距基准</li>
     *   <li>差值 &gt; 1.5× 中位数时视为新行</li>
     * </ul>
     * 此方法比固定阈值更鲁棒，适应不同字体大小和行距。
     *
     * @param textBlocks OCR 文本块列表
     * @return 后处理后的文本
     */
    String postProcessTextBlocks(List<TextBlock> textBlocks) {
        if (textBlocks == null || textBlocks.isEmpty()) {
            return "";
        }

        // 1. 提取每个文本块的坐标信息
        List<BlockInfo> blocks = new ArrayList<>();
        for (TextBlock tb : textBlocks) {
            String text = tb.getText();
            if (text == null || text.trim().isEmpty()) {
                continue;
            }

            float centerY = 0;
            float minX = Float.MAX_VALUE;
            float maxX = 0;
            float minY = Float.MAX_VALUE;
            float maxY = 0;
            int pointCount = 0;
            for (Point p : tb.getBoxPoint()) {
                centerY += p.getY();
                minX = Math.min(minX, p.getX());
                maxX = Math.max(maxX, p.getX());
                minY = Math.min(minY, p.getY());
                maxY = Math.max(maxY, p.getY());
                pointCount++;
            }
            centerY = pointCount > 0 ? centerY / pointCount : 0;

            blocks.add(new BlockInfo(correctBlockText(text), minX, maxX, centerY, maxY - minY, tb.getBoxScore()));
        }

        if (blocks.isEmpty()) {
            return "";
        }

        // 2. 按 Y 坐标排序
        blocks.sort(Comparator.comparingDouble(b -> b.centerY));

        // 3. 自适应间隙检测：用相邻块 Y 差值的分布来确定行边界
        // 计算所有相邻块的 Y 差值，取中位数作为行间距基准
        List<Double> gaps = new ArrayList<>();
        for (int i = 1; i < blocks.size(); i++) {
            double gap = blocks.get(i).centerY - blocks.get(i - 1).centerY;
            if (gap > 0) {
                gaps.add(gap);
            }
        }

        // 如果只有一行或没有间隙数据，直接输出
        if (gaps.isEmpty()) {
            return buildSingleLine(blocks);
        }

        // 取中位数作为行间距基准
        gaps.sort(Double::compareTo);
        double medianGap = gaps.get(gaps.size() / 2);

        // 计算中位高度，用于 height-based 阈值
        List<Double> heights = new ArrayList<>();
        for (BlockInfo b : blocks) {
            heights.add((double) b.height);
        }
        heights.sort(Double::compareTo);
        double medianHeight = heights.get(heights.size() / 2);

        // 双重阈值：取 gap-based 和 height-based 中较小的
        // gap-based 阈值：适应同一行内不同块之间的 Y 抖动
        double gapBasedThreshold = Math.max(medianGap * 1.3, 3.0);
        // height-based 阈值：行间距通常大于块高度，用块高度作为安全下界
        double heightBasedThreshold = Math.max(medianHeight * 0.8, 4.0);
        // 取较小者：gap-based 优先（同一行内 Y 抖动小），
        // height-based 兜底（防止 gap-based 因数据点少而阈值过大）
        double lineThreshold = Math.min(gapBasedThreshold, heightBasedThreshold);

        // 4. 行分组
        List<List<BlockInfo>> lines = new ArrayList<>();
        List<BlockInfo> currentLine = new ArrayList<>();
        double currentLineY = blocks.get(0).centerY;

        for (BlockInfo block : blocks) {
            if (Math.abs(block.centerY - currentLineY) > lineThreshold) {
                if (!currentLine.isEmpty()) {
                    // 对行内按 X 排序并输出
                    lines.add(currentLine);
                }
                currentLine = new ArrayList<>();
                currentLineY = block.centerY;
            }
            currentLine.add(block);
        }
        if (!currentLine.isEmpty()) {
            lines.add(currentLine);
        }

        // 5. 对每行内按 X 坐标排序，并根据间隙插入空格
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            List<BlockInfo> line = lines.get(i);
            line.sort(Comparator.comparingDouble(b -> b.minX));

            for (int j = 0; j < line.size(); j++) {
                BlockInfo block = line.get(j);

                if (j > 0) {
                    BlockInfo prev = line.get(j - 1);
                    float gap = block.minX - prev.maxX;
                    float prevWidth = prev.maxX - prev.minX;
                    // 间隙大于前块宽度 20% 时插入空格（比 30% 更敏感，解决日志字段间空格丢失）
                    if (prevWidth > 0 && gap > prevWidth * 0.2) {
                        result.append(' ');
                    }
                }

                result.append(block.text);
            }

            if (i < lines.size() - 1) {
                result.append('\n');
            }
        }

        return applyRegexPostProcessing(result.toString().trim());
    }

    /**
     * 正则表达式后处理：修复 TextBlock 层面无法处理的结构性空格缺失
     * <p>
     * 当 OCR 模型将本应分开的 token 检测为单个 TextBlock 时，块内空格已丢失，
     * 无法通过坐标间隙恢复。此方法通过正则匹配常见模式来插入缺失的空格：
     * <ul>
     *   <li>日期与时间之间：{@code 2026-07-1117:30:48} → {@code 2026-07-11 17:30:48}</li>
     *   <li>日志级别与类名之间：{@code INFOi.a.p.} → {@code INFO i.a.p.}</li>
     *   <li>命令与参数之间：{@code grep-R} → {@code grep -R}</li>
     * </ul>
     *
     * @param text 后处理文本
     * @return 修复后的文本
     */
    static String applyRegexPostProcessing(String text) {
        if (text == null || text.isEmpty()) return text;

        // Fix 0: Unicode 标点符号归一化
        // 全角数字 (U+FF10-U+FF19) → ASCII 数字 (U+0030-U+0039)
        // 中文引号 → ASCII 引号
        // Unicode 连字符 → ASCII 连字符
        StringBuilder sb = new StringBuilder(text);
        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            if (c >= '０' && c <= '９') {
                // 全角数字 → ASCII 数字: '０'→'0', '１'→'1', etc.
                sb.setCharAt(i, (char) (c - 0xFF10 + '0'));
            } else if (c == '‘' || c == '’') {
                // 中文单引号 → ASCII 单引号: '‘' → "'", '’' → "'"
                sb.setCharAt(i, '\'');
            } else if (c == '“' || c == '”') {
                // 中文双引号 → ASCII 双引号
                sb.setCharAt(i, '"');
            } else if (c == '‑' || c == '–' || c == '—') {
                // 连字符/短破折号/长破折号 → ASCII 连字符
                sb.setCharAt(i, '-');
            } else if (c == '？') {
                // 全角问号 → ASCII 问号
                sb.setCharAt(i, '?');
            }
        }
        text = sb.toString();

        // Fix 1: 日期与时间之间缺少空格
        // "2026-07-1117:30:48.631" → "2026-07-11 17:30:48.631"
        text = text.replaceAll("(\\d{4}-\\d{2}-\\d{2})(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})", "$1 $2");

        // Fix 2: 日志级别与后续文本之间缺少空格
        // "INFOi.a.p.w.c.r.i." → "INFO i.a.p.w.c.r.i."
        text = text.replaceAll("(INFO|DEBUG|WARN|ERROR|TRACE)([a-z])", "$1 $2");

        // Fix 3: 常见命令与参数之间缺少空格
        // "grep-R" → "grep -R", "ps-ef" → "ps -ef"
        text = text.replaceAll("(?<![a-zA-Z])(grep|wc|ls|cat|tail|head|sed|awk|ssh|curl|ping|ps|uniq)(" +
                "-[a-zA-Z0-9])", "$1 $2");

        // Fix 3b: 命令 flag 把 l 认成 1（必须在插入空格之后）
        // "wc -1" → "wc -l"
        text = text.replaceAll("(?<![a-zA-Z])(wc|ls)\\s+-1\\b", "$1 -l");

        // Fix 3c: ps 连写 flag 中间多出来的连字符 "ps -ef-L" → "ps -efL"
        text = text.replaceAll("\\bps -([a-zA-Z0-9]+)-([A-Za-z])", "ps -$1$2");

        // Fix 4: 时间与后续文本之间缺少空格
        // "00:00:00java" → "00:00:00 java"
        text = text.replaceAll("(\\d{2}:\\d{2}:\\d{2})([a-zA-Z])", "$1 $2");

        // Fix 5: 去掉 prompt 前的截图边框伪影（sh 有时被认成 sn）
        text = text.replaceAll("(?m)^\\[+(?=s[hn]-)", "");

        // Fix 6: shell prompt 与命令之间缺少空格
        // "sh-4.4$ps" → "sh-4.4$ ps"
        text = text.replaceAll("([$#])([A-Za-z])", "$1 $2");

        // Fix 7: 管道符两侧缺少空格
        text = text.replaceAll("(?<=\\S)\\|(?=\\S)", " | ");

        // Fix 7b: OCR 丢掉细管道符，根据后续命令还原
        // "ps -eLheadn10" → "ps -eL | headn10"
        text = text.replaceAll(
                "\\bps((?:\\s+-[a-zA-Z0-9]+)+)(?=head|awk|grep|wc|sort|uniq|sed|tail)",
                "ps$1 | ");
        text = text.replaceAll("'(?=sort|uniq|head|wc|grep)", "' | ");
        text = text.replaceAll("\\bsort(?=uniq)", "sort | ");
        text = text.replaceAll("\\bheadn(\\d+)", "head -n $1");

        // Fix 8: ps 表头粘连（完整串 + 残余碎片）
        text = text.replace("UIDPIDPPIDCSTIMETTYTIMECMD", "UID PID PPID C STIME TTY TIME CMD");
        text = text.replace("PIDLWPTTYTIMECMD", "PID LWP TTY TIME CMD");
        text = text.replace("CSTIMETTY", "C STIME TTY");
        text = text.replace("STIMETTY", "STIME TTY");
        text = text.replace("LWPTTY", "LWP TTY");
        text = text.replace("TIMECMD", "TIME CMD");

        // Fix 9: awk 引号/字段粘连（\\b 会漏掉 efLawk；replacement 用 lambda 避免 $ 组引用）
        text = java.util.regex.Pattern.compile("(?<![a-z])awk'\\{print\\$(\\d+)\\}'")
                .matcher(text)
                .replaceAll(mr -> java.util.regex.Matcher.quoteReplacement(
                        "awk '{print $" + mr.group(1) + "}'"));
        text = java.util.regex.Pattern.compile("print\\$(\\d+)")
                .matcher(text)
                .replaceAll(mr -> java.util.regex.Matcher.quoteReplacement(
                        "print $" + mr.group(1)));

        // Fix 10: 两行 ps 输出粘在同一行（CMD 或线程名后面直接跟 PID?TIME）
        text = text.replaceAll("(?<=[A-Za-z.)])(\\d{2,})\\?(\\d{2}:\\d{2}:\\d{2})", "\n$1?$2");

        // Fix 11: ps -eL 的 PID+LWP 粘连在 TTY ? 之前
        // "11?00:00:22" → "1 1 ? 00:00:22", "1010?00:00:00" → "10 10 ? 00:00:00"
        text = java.util.regex.Pattern.compile("(\\d{2,})\\?(\\d{2}:\\d{2}:\\d{2})")
                .matcher(text)
                .replaceAll(mr -> {
                    String digits = mr.group(1);
                    int mid = digits.length() / 2;
                    return digits.substring(0, mid) + " " + digits.substring(mid) + " ? " + mr.group(2);
                });

        // Fix 11b: 已分开的 PID LWP 后仍缺 ? 前空格 "1 1? 00:00:22"
        text = text.replaceAll("(\\d)\\?(?=\\s*\\d{2}:\\d{2}:\\d{2})", "$1 ?");

        // Fix 12: uniq -c 两行粘连 "1547815824" → "1 5478\n1 5824"
        text = text.replaceAll("(?m)^1(\\d{4})1(\\d{4})$", "1 $1\n1 $2");
        // "15478" → "1 5478"
        text = text.replaceAll("(?m)^1(\\d{4,})$", "1 $1");
        // "41310" → "413 10"（三位数 count + 两位数 PID）
        text = text.replaceAll("(?m)^([2-9]\\d{2})(\\d{2})$", "$1 $2");

        // Fix 13: java 启动参数、脚本参数、tini -- path
        text = text.replaceAll("\\bjava-javaagent:", "java -javaagent:");
        text = text.replaceAll("\\.sh(?=[a-z])", ".sh ");
        text = text.replaceAll("([a-z0-9])--/", "$1 -- /");
        text = text.replaceAll("(\\d{2}:\\d{2}:\\d{2})(/)", "$1 $2");
        text = text.replaceAll("(\\d{2}:\\d{2})pts/", "$1 pts/");

        // Fix 14: ps STIME "OJU109?" → "0 Jul09 ?"
        text = text.replaceAll("(?i)(?<=\\d\\s)[O0l]Ju[l1i](\\d{2})\\?", "0 Jul$1 ?");

        // Fix 15: 常见 JVM 线程名
        text = text.replace("GCThread", "GC Thread");
        text = text.replace("G1MainMarker", "G1 Main Marker");
        text = text.replace("G1Conc#", "G1 Conc#");
        text = text.replace("G1Refine#", "G1 Refine#");
        text = text.replaceAll("\\bG1Service\\b", "G1 Service");
        text = text.replace("Mi11is", "Millis");
        text = text.replace("-XXX:", "-XX:");

        // 管道还原后补一次命令 flag 空格（uniq-c、head -n）
        text = text.replaceAll("(?<![a-zA-Z])(grep|wc|ls|cat|tail|head|sed|awk|ssh|curl|ping|ps|uniq)(" +
                "-[a-zA-Z0-9])", "$1 $2");

        return text;
    }

    /**
     * 对 OCR 文本块进行字符级纠错
     * <p>
     * 处理 RapidOCR 常见识别错误：
     * <ul>
     *   <li>{@code 1 ↔ l} 混淆：数字 1 在字母上下文中被误识别为小写 l，反之亦然</li>
     *   <li>{@code 0 ↔ O} 混淆：数字 0 在大写字母上下文中被误识别为大写 O</li>
     *   <li>大小写漂移：已识别为大写为主的单词自动纠正为全大写</li>
     * </ul>
     * <p>
     * 使用上下文感知规则判断，避免误改（如 {@code 618d97adad76} 中的数字不会被误改）。
     *
     * @param text OCR 原始文本
     * @return 纠错后的文本
     */
    static String correctBlockText(String text) {
        if (text == null || text.isEmpty()) return text;

        StringBuilder sb = new StringBuilder(text);

        // 第一遍：处理 l/1、O/0 混淆
        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);

            boolean hasLetterBefore = i > 0 && Character.isLetter(sb.charAt(i - 1));
            boolean hasLetterAfter = i < sb.length() - 1 && Character.isLetter(sb.charAt(i + 1));
            boolean hasDigitBefore = i > 0 && Character.isDigit(sb.charAt(i - 1));
            boolean hasDigitAfter = i < sb.length() - 1 && Character.isDigit(sb.charAt(i + 1));
            boolean hasUppercaseBefore = i > 0 && Character.isUpperCase(sb.charAt(i - 1));
            boolean hasUppercaseAfter = i < sb.length() - 1 && Character.isUpperCase(sb.charAt(i + 1));

            // Rule 1: '1' at start of word → 'l' (lowercase L)
            // e.g., "1og" → "log", "1ibrary" → "library", "1s" → "ls"
            // NOT: "G1GC" where 1 is between two uppercase letters (JVM version)
            if (c == '1' && hasLetterAfter && !hasDigitBefore && !hasLetterBefore) {
                sb.setCharAt(i, 'l');
            }

            // Rule 2: '0' in uppercase letter context → 'O'
            // e.g., "INF0" → "INFO", "0CR" → "OCR"
            // Three cases:
            //   a) Start of uppercase word: "0CR" → "OCR"
            if (c == '0' && !hasLetterBefore && !hasDigitBefore && hasUppercaseAfter) {
                sb.setCharAt(i, 'O');
            }
            //   b) Middle of uppercase word: "INF0" → "INFO"
            if (c == '0' && hasUppercaseBefore && hasUppercaseAfter) {
                sb.setCharAt(i, 'O');
            }
            //   c) End of uppercase word: "INF0" → "INFO"
            if (c == '0' && hasUppercaseBefore && !hasLetterAfter && !hasDigitAfter) {
                sb.setCharAt(i, 'O');
            }

            // Rule 3: 'o' surrounded by uppercase letters → 'O'
            // e.g., "INFo" → "INFO"
            if (c == 'o' && hasUppercaseBefore && hasUppercaseAfter) {
                sb.setCharAt(i, 'O');
            }
            // 'o' at end of uppercase word
            if (c == 'o' && hasUppercaseBefore && !hasLetterAfter) {
                sb.setCharAt(i, 'O');
            }

            // Rule 4: lowercase letter between uppercase letters → uppercase
            // e.g., "OcR" → "OCR", "pRE" → "PRE"
            if (Character.isLowerCase(c)
                    && hasUppercaseBefore && hasUppercaseAfter) {
                sb.setCharAt(i, Character.toUpperCase(c));
            }

            // Rule 5: '1' between two different letters, followed by lowercase → 'l'
            // e.g., "Mi1lis" → "Millis" (i1l: 1 between i(lower) and l(lower), different letters)
            // NOT: "G1Heap" (1 between G(upper) and H(upper) → G1 is valid JVM version)
            if (c == '1' && hasLetterBefore && hasLetterAfter
                    && sb.charAt(i - 1) != sb.charAt(i + 1)
                    && Character.isLowerCase(sb.charAt(i + 1))) {
                sb.setCharAt(i, 'l');
            }

            // Rule 6: '1' after '-' preceded by space → 'l' (command flag)
            // e.g., "wc -1" → "wc -l"
            // NOT: "WorkflowNodeExecutor-1" (r before -, not space)
            if (c == '1' && i > 1 && sb.charAt(i - 1) == '-'
                    && sb.charAt(i - 2) == ' '
                    && !hasLetterAfter && !hasDigitAfter) {
                sb.setCharAt(i, 'l');
            }

            // Rule 7: 'l' after 'G' and before uppercase → '1' (JVM G1 GC pattern)
            // e.g., "GlHeapRegionSize" → "G1HeapRegionSize", "GlGC" → "G1GC"
            // G1 is a well-known JVM GC notation where '1' (digit) is correct
            if (c == 'l' && i > 0 && sb.charAt(i - 1) == 'G'
                    && hasUppercaseAfter) {
                sb.setCharAt(i, '1');
            }

            // Rule 8: '0' in camelCase boundary → 'O'
            // e.g., "0ccupanc" → "Occupanc" (0 at start of camelCase word)
            if (c == '0' && !hasDigitBefore && hasLetterAfter) {
                // 8a: Start of word: "0ccupanc" → "Occupanc"
                if (!hasLetterBefore) {
                    sb.setCharAt(i, 'O');
                }
                // 8b: CamelCase boundary (lowercase → uppercase via 0):
                // "Heap0ccupancy" → "HeapOccupancy" (0 between lowercase p and letter c)
                else if (hasLetterBefore && Character.isLowerCase(sb.charAt(i - 1))) {
                    sb.setCharAt(i, 'O');
                }
            }
        }

        // 第二遍：大小写漂移纠正
        // 如果单词中 ≥70% 的字母是大写，将整个单词转为大写
        // 修正 "TEssDATA_PREFIX" → "TESSDATA_PREFIX", "INFo" → "INFO"
        // 不影响 "KnowledgeBaseIndexPipeline" (12% uppercase)
        // 不影响 "localhost" (0% uppercase)
        String[] words = sb.toString().split("(?=[^a-zA-Z])|(?<=[^a-zA-Z])");
        StringBuilder result = new StringBuilder();
        int wordStart = 0;
        for (String word : words) {
            if (word.isEmpty()) continue;
            int wordEnd = wordStart + word.length();
            if (word.length() >= 3) {
                int upperCount = 0;
                int letterCount = 0;
                for (int i = 0; i < word.length(); i++) {
                    if (Character.isLetter(word.charAt(i))) {
                        letterCount++;
                        if (Character.isUpperCase(word.charAt(i))) {
                            upperCount++;
                        }
                    }
                }
                // ≥70% 的字母是大写 → 全大写
                if (letterCount > 0 && (double) upperCount / letterCount >= 0.65) {
                    String corrected = word.toUpperCase();
                    result.append(corrected);
                    wordStart = wordEnd;
                    continue;
                }
            }
            result.append(sb.substring(wordStart, wordEnd));
            wordStart = wordEnd;
        }

        return result.toString();
    }

    /**
     * 所有块在同一行时直接按 X 排序输出
     */
    private String buildSingleLine(List<BlockInfo> blocks) {
        blocks.sort(Comparator.comparingDouble(b -> b.minX));
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < blocks.size(); j++) {
            if (j > 0) {
                BlockInfo prev = blocks.get(j - 1);
                float gap = blocks.get(j).minX - prev.maxX;
                float prevWidth = prev.maxX - prev.minX;
                if (prevWidth > 0 && gap > prevWidth * 0.2) {
                    sb.append(' ');
                }
            }
            sb.append(blocks.get(j).text);
        }
        return sb.toString().trim();
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
        if ("v6".equalsIgnoreCase(config.getOcr().getModelVersion())) {
            return engineV6 != null && engineV6.isAvailable();
        }
        return engine != null;
    }

    @PreDestroy
    public void cleanup() {
        if ("v6".equalsIgnoreCase(config.getOcr().getModelVersion())) {
            if (engineV6 != null) {
                log.info("Closing PP-OCRv6 Python worker");
                engineV6.close();
            }
        } else {
            if (engine != null) {
                log.info("Closing RapidOCR engine");
                engine = null;
            }
        }
    }

    /**
     * 文本块信息（用于后处理）
     */
    private record BlockInfo(String text, float minX, float maxX, float centerY, float height, float score) {
    }

    public record OcrResult(String text, double confidence) {
    }
}