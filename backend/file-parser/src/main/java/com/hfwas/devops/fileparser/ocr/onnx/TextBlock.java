package com.hfwas.devops.fileparser.ocr.onnx;

import java.awt.geom.Point2D;
import java.util.List;

/**
 * OCR 识别出的文本块
 * 包含文本框四个角点坐标和对应的识别文本。
 *
 * @param boxPoints    文本框四个角点（顺时针顺序：左上、右上、右下、左下）
 * @param text         识别文本
 * @param confidence   置信度 (0~1)
 */
public record TextBlock(
        List<Point2D> boxPoints,
        String text,
        double confidence
) {
    /**
     * 创建一个仅包含位置信息的文本框（尚未识别）
     */
    public static TextBlock ofPosition(List<Point2D> boxPoints) {
        return new TextBlock(boxPoints, "", 0.0);
    }

    /**
     * 获取文本框的边界矩形宽度
     */
    public double getWidth() {
        if (boxPoints == null || boxPoints.size() < 2) return 0;
        return Math.max(
                boxPoints.get(0).distance(boxPoints.get(1)),
                boxPoints.get(3).distance(boxPoints.get(2))
        );
    }

    /**
     * 获取文本框的边界矩形高度
     */
    public double getHeight() {
        if (boxPoints == null || boxPoints.size() < 4) return 0;
        return Math.max(
                boxPoints.get(0).distance(boxPoints.get(3)),
                boxPoints.get(1).distance(boxPoints.get(2))
        );
    }

    /**
     * 获取中心点 Y 坐标（用于排序）
     */
    public double getCenterY() {
        if (boxPoints == null || boxPoints.isEmpty()) return 0;
        return boxPoints.stream().mapToDouble(Point2D::getY).average().orElse(0);
    }

    /**
     * 获取中心点 X 坐标（用于排序）
     */
    public double getCenterX() {
        if (boxPoints == null || boxPoints.isEmpty()) return 0;
        return boxPoints.stream().mapToDouble(Point2D::getX).average().orElse(0);
    }

    public double minX() {
        return boxPoints == null ? 0 : boxPoints.stream().mapToDouble(Point2D::getX).min().orElse(0);
    }

    public double maxX() {
        return boxPoints == null ? 0 : boxPoints.stream().mapToDouble(Point2D::getX).max().orElse(0);
    }

    public double minY() {
        return boxPoints == null ? 0 : boxPoints.stream().mapToDouble(Point2D::getY).min().orElse(0);
    }

    public double maxY() {
        return boxPoints == null ? 0 : boxPoints.stream().mapToDouble(Point2D::getY).max().orElse(0);
    }

    public double axisWidth() {
        return Math.max(0, maxX() - minX());
    }

    public double axisHeight() {
        return Math.max(0, maxY() - minY());
    }

    /** 用包围盒合并两个文本框（同一行相邻碎片） */
    public TextBlock union(TextBlock other) {
        double x1 = Math.min(minX(), other.minX());
        double y1 = Math.min(minY(), other.minY());
        double x2 = Math.max(maxX(), other.maxX());
        double y2 = Math.max(maxY(), other.maxY());
        return new TextBlock(
                List.of(
                        new Point2D.Double(x1, y1),
                        new Point2D.Double(x2, y1),
                        new Point2D.Double(x2, y2),
                        new Point2D.Double(x1, y2)),
                "",
                Math.max(confidence, other.confidence));
    }
}