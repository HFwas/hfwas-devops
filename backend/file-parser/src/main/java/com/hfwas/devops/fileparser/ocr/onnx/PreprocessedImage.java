package com.hfwas.devops.fileparser.ocr.onnx;

/**
 * 预处理后的图像数据
 * 包含已处理的张量数据和用于坐标映射的缩放信息。
 *
 * @param tensor      预处理后的图像张量，形状为 [1, 3, H, W]（检测）或 [1, 3, 48, W]（识别）
 * @param originalWidth   原始图片宽度
 * @param originalHeight  原始图片高度
 * @param resizeScale     缩放比例（原始 → 预处理后）
 * @param resizeWidth     缩放后的宽度（不含 padding）
 * @param resizeHeight    缩放后的高度（不含 padding）
 * @param padWidth        padding 后的宽度（含 padding）
 * @param padHeight       padding 后的高度（含 padding）
 */
public record PreprocessedImage(
        float[][][][] tensor,
        int originalWidth,
        int originalHeight,
        double resizeScale,
        int resizeWidth,
        int resizeHeight,
        int padWidth,
        int padHeight
) {
    /**
     * 将检测模型输出的坐标映射回原始图片尺寸
     *
     * @param x 检测输出空间中的 x 坐标（0~1 或像素值）
     * @param y 检测输出空间中的 y 坐标（0~1 或像素值）
     * @return 原始图片中的坐标
     */
    public double mapToOriginalX(double x) {
        return x / resizeScale;
    }

    /**
     * 将检测模型输出的坐标映射回原始图片尺寸
     */
    public double mapToOriginalY(double y) {
        return y / resizeScale;
    }
}