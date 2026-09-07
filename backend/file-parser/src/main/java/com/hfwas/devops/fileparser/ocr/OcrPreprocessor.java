package com.hfwas.devops.fileparser.ocr;

import lombok.extern.slf4j.Slf4j;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * OCR 前图像预处理管线
 * <p>
 * 对输入图片进行轻量增强处理，提高 OCR 识别精度。
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>最少干预</b>：RapidOCR (PP-OCRv4) 是深度学习模型，训练数据为自然图像。
 *       过度预处理（二值化、CLAHE、中值滤波）会改变图像分布，反而降低置信度。</li>
 *   <li><b>仅做灰度化</b>：RapidOCR 内部处理时会自行灰度化，但显式灰度化可避免
 *       色彩空间转换带来的不确定性。</li>
 *   <li><b>轻量对比度拉伸</b>：对于低对比度图片，做线性对比度拉伸增强文字边界。</li>
 * </ul>
 *
 * 所有处理使用 Java 标准库（ImageIO/Java2D），零额外依赖。
 */
@Slf4j
public class OcrPreprocessor {

    /**
     * OCR 预处理最大图片尺寸（宽高中较大值）。
     * 超过此值会等比缩放，减少后续处理（灰度化、对比度拉伸、PNG 写入、Python 读取）的耗时。
     * <p>
     * 5712px 的图片经过灰度化+对比度拉伸需要遍历 24.5MP 像素，写入 16MB PNG，
     * Python 端还要再缩放一次（内置 max_side_limit=4000），全程浪费。
     * 缩放到 1200px 后，像素数降至 1.1MP，PNG 约 1MB。
     * <p>
     * 1200px 对 OCR 准确率无影响（PP-OCRv6 训练数据中文字区域通常在 30-60px 高度，
     * 1200px 照片中一行文字约 40px，仍在模型识别范围内）。
     */
    private static final int MAX_PIXEL_DIMENSION = 1200;

    /**
     * 对图片执行预处理
     * <p>
     * 流程：等比缩放（如需要）→ 灰度化 → 轻度对比度拉伸（仅对低对比度图片）。
     * 缩放放在最前面，可以减少后续灰度化和对比度拉伸的像素遍历量。
     * 不做二值化、不做中值滤波、不做 CLAHE，避免改变模型训练分布。
     *
     * @param image 原始图片
     * @return 预处理后的图片
     */
    public BufferedImage preprocess(BufferedImage image) {
        if (image == null) {
            return null;
        }

        // 诊断：记录输入 BufferedImage 的实际内存占用
        int dataBufferSize = image.getRaster().getDataBuffer().getSize();
        log.info("OCR preprocess input: type={}, size={}x{}, dataBufferSize={} elements, storage={}",
                image.getType(), image.getWidth(), image.getHeight(),
                dataBufferSize, describeDataBuffer(image));

        long start = System.currentTimeMillis();
        boolean modified = false;

        // 0. 等比缩放（如需要），减少后续处理量
        BufferedImage result = resizeIfNeeded(image);
        if (result != image) {
            modified = true;
        }

        // 1. 如果图片是彩色的，转为灰度
        if (result.getType() != BufferedImage.TYPE_BYTE_GRAY) {
            result = toGrayscale(result);
            modified = true;
        }

        // 2. 检查是否需要对比度拉伸（仅对低对比度图片）
        int[] histogram = computeHistogram(result);
        int minGray = findMinNonZero(histogram);
        int maxGray = findMaxNonZero(histogram);
        int range = maxGray - minGray;

        // 对比度范围 < 150 时做拉伸（增强文字边界）
        if (range < 150 && range > 10) {
            result = contrastStretch(result, minGray, maxGray);
            modified = true;
        }

        if (modified) {
            long elapsed = System.currentTimeMillis() - start;
            log.debug("OCR preprocessing applied in {}ms ({}x{}, contrast range={})",
                    elapsed, result.getWidth(), result.getHeight(), range);
        } else {
            log.trace("OCR preprocessing skipped, image already optimal");
        }

        return result;
    }

    /**
     * 诊断：描述 DataBuffer 的存储类型和实际字节数
     */
    private String describeDataBuffer(BufferedImage image) {
        java.awt.image.DataBuffer buf = image.getRaster().getDataBuffer();
        String typeName;
        int bytesPerElement;
        switch (buf.getDataType()) {
            case java.awt.image.DataBuffer.TYPE_BYTE:
                typeName = "BYTE";
                bytesPerElement = 1;
                break;
            case java.awt.image.DataBuffer.TYPE_USHORT:
                typeName = "USHORT";
                bytesPerElement = 2;
                break;
            case java.awt.image.DataBuffer.TYPE_INT:
                typeName = "INT";
                bytesPerElement = 4;
                break;
            case java.awt.image.DataBuffer.TYPE_FLOAT:
                typeName = "FLOAT";
                bytesPerElement = 4;
                break;
            case java.awt.image.DataBuffer.TYPE_DOUBLE:
                typeName = "DOUBLE";
                bytesPerElement = 8;
                break;
            default:
                typeName = "UNKNOWN";
                bytesPerElement = 4;
        }
        long totalBytes = (long) buf.getSize() * bytesPerElement;
        return String.format("DataBuffer[type=%s, banks=%d, elements=%d, ~%dMB]",
                typeName, buf.getNumBanks(), buf.getSize(), totalBytes / 1024 / 1024);
    }

    /**
     * 如果图片尺寸超过 {@link #MAX_PIXEL_DIMENSION}，等比缩放到限制值以内。
     * 提前缩小可大幅减少后续灰度化、对比度拉伸、PNG 写入的耗时，
     * 且 Python 端无需再做二次缩放。
     *
     * @param image 原始图片
     * @return 缩放后的图片（尺寸未超限时返回原对象）
     */
    BufferedImage resizeIfNeeded(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int maxSide = Math.max(w, h);

        if (maxSide <= MAX_PIXEL_DIMENSION) {
            return image;
        }

        double scale = (double) MAX_PIXEL_DIMENSION / maxSide;
        int newW = (int) Math.round(w * scale);
        int newH = (int) Math.round(h * scale);

        // 确保最小尺寸 ≥ 1px
        newW = Math.max(1, newW);
        newH = Math.max(1, newH);

        BufferedImage resized = new BufferedImage(newW, newH, image.getType());
        Graphics2D g = resized.createGraphics();
        try {
            // 高质量缩放：双三次插值，保留文字边缘清晰度
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image, 0, 0, newW, newH, null);
        } finally {
            g.dispose();
        }

        log.info("Resized image: {}x{} -> {}x{} (scale={}, maxSide={})",
                w, h, newW, newH, String.format("%.2f", scale), MAX_PIXEL_DIMENSION);
        return resized;
    }

    /**
     * 将图片转换为灰度图
     */
    BufferedImage toGrayscale(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage gray = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        try {
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();
        }
        return gray;
    }

    /**
     * 线性对比度拉伸
     * <p>
     * 将灰度范围 [minGray, maxGray] 线性映射到 [0, 255]。
     * 增强低对比度图片的文字边界，不引入非线性失真。
     *
     * @param image   灰度图
     * @param minGray 最小灰度值（非零直方图 bin）
     * @param maxGray 最大灰度值
     * @return 对比度拉伸后的图片
     */
    BufferedImage contrastStretch(BufferedImage image, int minGray, int maxGray) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);

        double scale = 255.0 / (maxGray - minGray);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int gray = rgb & 0xFF;
                int stretched = (int) Math.round((gray - minGray) * scale);
                stretched = Math.min(255, Math.max(0, stretched));
                int outRgb = (stretched << 16) | (stretched << 8) | stretched;
                result.setRGB(x, y, outRgb);
            }
        }

        log.debug("Contrast stretch: [{}..{}] -> [0..255]", minGray, maxGray);
        return result;
    }

    /**
     * 计算灰度直方图
     */
    int[] computeHistogram(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int[] histogram = new int[256];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int gray = rgb & 0xFF;
                histogram[gray]++;
            }
        }
        return histogram;
    }

    /**
     * 找到直方图中第一个非零 bin
     */
    private int findMinNonZero(int[] histogram) {
        for (int i = 0; i < 256; i++) {
            if (histogram[i] > 0) return i;
        }
        return 0;
    }

    /**
     * 找到直方图中最后一个非零 bin
     */
    private int findMaxNonZero(int[] histogram) {
        for (int i = 255; i >= 0; i--) {
            if (histogram[i] > 0) return i;
        }
        return 255;
    }

    /**
     * Otsu 二值化（保留方法，供测试和特殊场景使用，不在管线中自动调用）
     * <p>
     * 注意：不要对 RapidOCR 使用二值化。
     * 纯黑白图（只有 0 和 255）超出模型训练分布，会大幅降低置信度。
     */
    BufferedImage otsuBinarization(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        int[] histogram = computeHistogram(image);
        int threshold = computeOtsuThreshold(histogram, width * height);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int gray = image.getRGB(x, y) & 0xFF;
                int value = gray <= threshold ? 0 : 255;
                int rgb = (value << 16) | (value << 8) | value;
                result.setRGB(x, y, rgb);
            }
        }
        return result;
    }

    /**
     * 计算 Otsu 最优阈值
     */
    int computeOtsuThreshold(int[] histogram, int totalPixels) {
        if (totalPixels <= 0) return 128;
        double sum = 0;
        for (int i = 0; i < 256; i++) {
            sum += (double) i * histogram[i];
        }
        double sumB = 0;
        int wB = 0;
        int wF = 0;
        double maxVariance = 0;
        int threshold = 128;
        for (int t = 0; t < 256; t++) {
            wB += histogram[t];
            if (wB == 0) continue;
            wF = totalPixels - wB;
            if (wF == 0) break;
            sumB += (double) t * histogram[t];
            double meanB = sumB / wB;
            double meanF = (sum - sumB) / wF;
            double variance = (double) wB * wF * (meanB - meanF) * (meanB - meanF);
            if (variance > maxVariance) {
                maxVariance = variance;
                threshold = t;
            }
        }
        return threshold;
    }
}