package com.hfwas.devops.fileparser.image;

import java.io.File;

/**
 * 图片压缩结果
 * 包含压缩后的文件引用、压缩前后大小/尺寸、压缩比等信息。
 *
 * @param file            用于 OCR 的文件（压缩后的文件，或压缩失败/跳过时的原文件）
 * @param originalSize    原始文件大小（字节）
 * @param compressedSize  压缩后文件大小（字节），未压缩时为 0
 * @param ratio           压缩比 (originalSize - compressedSize) / originalSize，未压缩时为 0
 * @param applied         是否实际执行了压缩并使用了压缩文件
 * @param errorMessage    压缩失败时的错误信息，成功时为 null
 * @param originalWidth   原始图片宽度（像素），未知时为 0
 * @param originalHeight  原始图片高度（像素），未知时为 0
 * @param compressedWidth 压缩后图片宽度（像素），未压缩时为 0
 * @param compressedHeight 压缩后图片高度（像素），未压缩时为 0
 */
public record ImageCompressionResult(
        File file,
        long originalSize,
        long compressedSize,
        double ratio,
        boolean applied,
        String errorMessage,
        int originalWidth,
        int originalHeight,
        int compressedWidth,
        int compressedHeight
) {
    /**
     * 创建一个未压缩的结果（压缩被跳过、失败或无效时使用）
     */
    public static ImageCompressionResult skipped(File originalFile) {
        return new ImageCompressionResult(originalFile, originalFile.length(), 0, 0, false, null, 0, 0, 0, 0);
    }

    /**
     * 创建一个失败的结果
     */
    public static ImageCompressionResult failed(File originalFile, String errorMessage) {
        return new ImageCompressionResult(originalFile, originalFile.length(), 0, 0, false, errorMessage, 0, 0, 0, 0);
    }

    /**
     * 创建一个成功的压缩结果
     */
    public static ImageCompressionResult success(File compressedFile, long originalSize, long compressedSize,
                                                  int originalWidth, int originalHeight,
                                                  int compressedWidth, int compressedHeight) {
        double ratio = originalSize > 0 ? (double) (originalSize - compressedSize) / originalSize : 0;
        return new ImageCompressionResult(compressedFile, originalSize, compressedSize, ratio, true, null,
                originalWidth, originalHeight, compressedWidth, compressedHeight);
    }
}