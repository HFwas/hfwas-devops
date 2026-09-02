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
     * 对图片执行预处理
     * <p>
     * 流程：灰度化 → 轻度对比度拉伸（仅对低对比度图片）。
     * 不做二值化、不做中值滤波、不做 CLAHE，避免改变模型训练分布。
     *
     * @param image 原始图片
     * @return 预处理后的图片
     */
    public BufferedImage preprocess(BufferedImage image) {
        if (image == null) {
            return null;
        }

        long start = System.currentTimeMillis();
        boolean modified = false;

        // 1. 如果图片是彩色的，转为灰度
        BufferedImage result = image;
        if (image.getType() != BufferedImage.TYPE_BYTE_GRAY) {
            result = toGrayscale(image);
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