package com.hfwas.devops.fileparser.ocr.onnx;

import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.opencv.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 检测模型后处理 — DB 算法。
 * 手机拍屏场景降低 box_thresh、加大 unclip，避免长英文和终端小字被裁掉。
 */
@Slf4j
public class DetectionPostProcessor {

    private static final float THRESHOLD = 0.2f;
    private static final float BOX_THRESH = 0.2f;
    private static final double UNCLIP_RATIO = 2.2;
    private static final double MIN_UNCLIP = 8.0;
    private static final int MIN_BOX_SIZE = 3;
    private static final int MAX_CANDIDATES = 3000;
    private static final double MERGE_Y_RATIO = 0.008;

    public List<TextBlock> process(float[][][][] output, PreprocessedImage preprocessed) {
        int height = output[0][0].length;
        int width = output[0][0][0].length;

        Mat probMap = new Mat(height, width, CV_32F);
        Mat binary = new Mat();
        Mat binaryU8 = new Mat();
        Mat kernel = null;
        Mat dilated = new Mat();
        MatVector contours = new MatVector();
        try {
            for (int y = 0; y < height; y++) {
                BytePointer rowPtr = probMap.ptr(y);
                for (int x = 0; x < width; x++) {
                    rowPtr.putFloat(x * 4, output[0][0][y][x]);
                }
            }

            threshold(probMap, binary, THRESHOLD, 1.0, THRESH_BINARY);
            binary.convertTo(binaryU8, CV_8U, 255.0, 0.0);
            kernel = new Mat(new Size(2, 2), CV_8U, new Scalar(1));
            dilate(binaryU8, dilated, kernel);
            findContours(dilated, contours, RETR_LIST, CHAIN_APPROX_SIMPLE);

            List<TextBlock> boxes = new ArrayList<>();
            int numContours = (int) Math.min(contours.size(), MAX_CANDIDATES);

            for (int i = 0; i < numContours; i++) {
                Mat contour = contours.get(i);
                if (contour == null || contour.isNull() || contour.rows() < 3) continue;

                Rect rect = boundingRect(contour);
                float bw = rect.width();
                float bh = rect.height();
                if (Math.min(bw, bh) < MIN_BOX_SIZE) continue;

                float score = boxScore(output, rect.x(), rect.y(), (int) bw, (int) bh, width, height);
                if (score < BOX_THRESH) continue;

                RotatedRect rotated = minAreaRect(contour);
                List<Point2D> quad = expandRotatedRect(rotated, preprocessed);
                rotated.close();
                if (quad == null) continue;

                boxes.add(new TextBlock(quad, "", score));
            }

            sortBoxes(boxes, preprocessed.originalHeight());
            log.debug("Detection: {} contours -> {} boxes", numContours, boxes.size());
            return boxes;
        } finally {
            contours.close();
            dilated.close();
            if (kernel != null) kernel.close();
            binaryU8.close();
            binary.close();
            probMap.close();
        }
    }

    private List<Point2D> expandRotatedRect(RotatedRect rotated, PreprocessedImage preprocessed) {
        Size2f size = rotated.size();
        double rw = size.width();
        double rh = size.height();
        if (Math.min(rw, rh) < MIN_BOX_SIZE) {
            return null;
        }
        double distance = Math.max(
                (rw * rh) * UNCLIP_RATIO / Math.max(2.0 * (rw + rh), 1.0),
                MIN_UNCLIP);
        RotatedRect expanded = new RotatedRect(
                rotated.center(),
                new Size2f((float) (rw + 2 * distance), (float) (rh + 2 * distance)),
                rotated.angle());
        Point2f pts = new Point2f(4);
        expanded.points(pts);
        List<Point2D> quad = new ArrayList<>(4);
        for (int j = 0; j < 4; j++) {
            Point2f p = pts.position(j);
            double ox = clamp(mapX(p.x(), preprocessed), 0, preprocessed.originalWidth() - 1);
            double oy = clamp(mapY(p.y(), preprocessed), 0, preprocessed.originalHeight() - 1);
            quad.add(new Point2D.Double(ox, oy));
        }
        pts.position(0);
        pts.close();
        expanded.close();

        double boxW = quad.get(0).distance(quad.get(1));
        double boxH = quad.get(0).distance(quad.get(3));
        if (Math.max(boxW, boxH) < MIN_BOX_SIZE) {
            return null;
        }
        return quad;
    }

    /**
     * 只合并几乎挨在一起的碎片（例如 {@code org.spri}+{@code ngframework}）。
     * 间隙太大则不合并，避免整行 Tab 被糊成一个框。
     */
    static List<TextBlock> mergeLineFragments(List<TextBlock> boxes, int imageHeight) {
        if (boxes.size() < 2) {
            return boxes;
        }
        double rowThresh = rowMergeThreshold(imageHeight);
        List<TextBlock> merged = new ArrayList<>();
        TextBlock current = boxes.get(0);
        for (int i = 1; i < boxes.size(); i++) {
            TextBlock next = boxes.get(i);
            if (shouldMerge(current, next, rowThresh)) {
                current = current.union(next);
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    static boolean shouldMerge(TextBlock left, TextBlock right, double rowThresh) {
        double h1 = Math.max(left.axisHeight(), 1);
        double h2 = Math.max(right.axisHeight(), 1);
        double minH = Math.min(h1, h2);
        double maxH = Math.max(h1, h2);

        double vOverlap = Math.min(left.maxY(), right.maxY()) - Math.max(left.minY(), right.minY());
        if (vOverlap < 0.55 * minH) {
            return false;
        }
        if (Math.abs(left.getCenterY() - right.getCenterY()) > Math.max(rowThresh * 0.6, 0.45 * minH)) {
            return false;
        }
        double unionH = Math.max(left.maxY(), right.maxY()) - Math.min(left.minY(), right.minY());
        if (unionH > 1.7 * maxH) {
            return false;
        }
        double heightRatio = minH / maxH;
        if (heightRatio < 0.5) {
            return false;
        }
        double gap = right.minX() - left.maxX();
        return gap <= Math.max(8.0, 0.35 * maxH);
    }

    private static float boxScore(float[][][][] output, int x, int y, int w, int h, int width, int height) {
        int x1 = clampInt(x, 0, width - 1);
        int y1 = clampInt(y, 0, height - 1);
        int x2 = clampInt(x + w, 0, width - 1);
        int y2 = clampInt(y + h, 0, height - 1);
        if (x2 < x1 || y2 < y1) return 0f;
        double sum = 0;
        int count = 0;
        for (int iy = y1; iy <= y2; iy++) {
            for (int ix = x1; ix <= x2; ix++) {
                sum += output[0][0][iy][ix];
                count++;
            }
        }
        return count == 0 ? 0f : (float) (sum / count);
    }

    private static double mapX(double x, PreprocessedImage preprocessed) {
        int srcW = preprocessed.padWidth() > 0 ? preprocessed.padWidth() : preprocessed.resizeWidth();
        return x * preprocessed.originalWidth() / Math.max(srcW, 1);
    }

    private static double mapY(double y, PreprocessedImage preprocessed) {
        int srcH = preprocessed.padHeight() > 0 ? preprocessed.padHeight() : preprocessed.resizeHeight();
        return y * preprocessed.originalHeight() / Math.max(srcH, 1);
    }

    private void sortBoxes(List<TextBlock> boxes, int imageHeight) {
        if (boxes.isEmpty()) return;
        boxes.sort(Comparator.comparingDouble(TextBlock::getCenterY));

        double mergeY = Math.max(8.0, imageHeight * MERGE_Y_RATIO);
        List<List<TextBlock>> rows = new ArrayList<>();
        List<TextBlock> currentRow = new ArrayList<>();
        currentRow.add(boxes.get(0));

        for (int i = 1; i < boxes.size(); i++) {
            TextBlock prev = boxes.get(i - 1);
            TextBlock curr = boxes.get(i);
            if (Math.abs(curr.getCenterY() - prev.getCenterY()) < mergeY) {
                currentRow.add(curr);
            } else {
                currentRow.sort(Comparator.comparingDouble(TextBlock::getCenterX));
                rows.add(currentRow);
                currentRow = new ArrayList<>();
                currentRow.add(curr);
            }
        }
        if (!currentRow.isEmpty()) {
            currentRow.sort(Comparator.comparingDouble(TextBlock::getCenterX));
            rows.add(currentRow);
        }
        boxes.clear();
        for (List<TextBlock> row : rows) boxes.addAll(row);
    }

    static double rowMergeThreshold(int imageHeight) {
        return Math.max(8.0, imageHeight * MERGE_Y_RATIO);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
