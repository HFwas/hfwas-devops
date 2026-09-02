package com.hfwas.devops.fileparser.ocr.onnx;

import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacpp.FloatPointer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Size;
import static org.bytedeco.opencv.global.opencv_core.CV_8UC3;
import static org.bytedeco.opencv.global.opencv_core.CV_32FC2;
import static org.bytedeco.opencv.global.opencv_imgproc.getPerspectiveTransform;

import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 图像预处理
 * 负责将原始图片转换为 ONNX 模型所需的输入张量。
 *
 * <h3>检测模型预处理</h3>
 * <ul>
 *   <li>工作图长边上限 2560px；检测图再压到 1920px 并对齐到 32 的倍数</li>
 *   <li>ImageNet 归一化: mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225]</li>
 *   <li>BGR、HWC → CHW → NCHW</li>
 * </ul>
 *
 * <h3>识别模型预处理</h3>
 * <ul>
 *   <li>将文本区域裁剪并缩放至高度 48px，保持宽高比</li>
 *   <li>归一化: (x / 255.0 - 0.5) / 0.5</li>
 *   <li>HWC → CHW → NCHW</li>
 * </ul>
 */
@Slf4j
public class ImagePreprocessor {

    /** 检测模型：长边上限，控制 ONNX 激活值占用的堆外内存 */
    private static final int DET_MAX_SIDE = 1920;

    /** OCR 工作图长边上限（内存中高质量缩放，不是 JPEG 压缩） */
    public static final int WORKING_MAX_SIDE = 2560;

    /** 检测模型：对齐倍数 */
    private static final int DET_ALIGN = 32;

    /** 识别模型：固定高度 */
    private static final int REC_TARGET_HEIGHT = 48;

    /** 识别模型动态轴上限（与 PP-OCRv6 ONNX 导出一致） */
    private static final int REC_MAX_WIDTH = 3200;

    /**
     * 将工作图限制在长边以内，避免 5000px 原图把 ONNX/OpenCV 堆外内存打满。
     * 使用内存双线性缩放，不经过 JPEG。
     */
    public BufferedImage limitWorkingSize(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int maxSide = Math.max(width, height);
        if (maxSide <= WORKING_MAX_SIDE) {
            return image;
        }
        double scale = (double) WORKING_MAX_SIDE / maxSide;
        int targetW = Math.max(1, (int) Math.round(width * scale));
        int targetH = Math.max(1, (int) Math.round(height * scale));
        BufferedImage resized = new BufferedImage(targetW, targetH, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = resized.createGraphics();
        try {
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image, 0, 0, targetW, targetH, null);
        } finally {
            g.dispose();
        }
        log.info("OCR working image scaled {}x{} -> {}x{}", width, height, targetW, targetH);
        return resized;
    }

    /**
     * 对原始图片进行检测预处理。
     * <p>
     * PP-OCRv6 检测模型使用 ImageNet 归一化:
     * mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225]
     *
     * @param image 原始图片
     * @return 预处理后的张量和缩放信息
     */
    public PreprocessedImage preprocessForDetection(BufferedImage image) {
        int originalWidth = image.getWidth();
        int originalHeight = image.getHeight();

        // 1. 长边超过上限才缩小；再对齐到 32（Paddle DetResize 直接 resize，不 padding）
        double scale = 1.0;
        int maxSide = Math.max(originalHeight, originalWidth);
        if (maxSide > DET_MAX_SIDE) {
            scale = (double) DET_MAX_SIDE / maxSide;
        }

        int resizeHeight = Math.max(DET_ALIGN, roundToMultiple(originalHeight * scale, DET_ALIGN));
        int resizeWidth = Math.max(DET_ALIGN, roundToMultiple(originalWidth * scale, DET_ALIGN));

        BufferedImage resized = new BufferedImage(resizeWidth, resizeHeight, BufferedImage.TYPE_3BYTE_BGR);
        var g = resized.createGraphics();
        try {
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image, 0, 0, resizeWidth, resizeHeight, null);
        } finally {
            g.dispose();
        }

        // 4. 将图片转换为 float 张量并使用 ImageNet 归一化
        // mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225], scale=1/255
        float[][][][] tensor = imageToFloatTensorImageNet(resized);

        log.debug("Detection preprocess: {}x{} -> {}x{}, scale={}",
                originalWidth, originalHeight, resizeWidth, resizeHeight,
                String.format("%.4f", scale));

        return new PreprocessedImage(
                tensor, originalWidth, originalHeight,
                scale, resizeWidth, resizeHeight, resizeWidth, resizeHeight
        );
    }

    /**
     * 对裁剪的文本区域进行识别预处理
     * <p>
     * PP-OCRv6 识别模型使用默认归一化:
     * mean=[0.5, 0.5, 0.5], std=[0.5, 0.5, 0.5] (即 (x/255 - 0.5)/0.5)
     *
     * @param crop 裁剪的文本区域图片
     * @return 预处理后的张量 [1, 3, 48, W]
     */
    public float[][][][] preprocessForRecognition(BufferedImage crop) {
        int cropWidth = crop.getWidth();
        int cropHeight = crop.getHeight();

        // 1. 缩放至高度 48px，保持宽高比
        double scale = (double) REC_TARGET_HEIGHT / cropHeight;
        int targetWidth = Math.max(8, (int) Math.round(cropWidth * scale));
        if (targetWidth > REC_MAX_WIDTH) {
            targetWidth = REC_MAX_WIDTH;
        }
        if (targetWidth % 8 != 0) {
            targetWidth = Math.min(REC_MAX_WIDTH, ((targetWidth + 7) / 8) * 8);
        }

        BufferedImage resized = new BufferedImage(targetWidth, REC_TARGET_HEIGHT, BufferedImage.TYPE_3BYTE_BGR);
        var g = resized.createGraphics();
        try {
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(crop, 0, 0, targetWidth, REC_TARGET_HEIGHT, null);
        } finally {
            g.dispose();
        }

        // 2. 转换为 float 张量并使用默认归一化 (x/255 - 0.5)/0.5
        return imageToFloatTensorDefault(resized);
    }

    /**
     * 从文本块裁剪并校正图片区域（PaddleOCR get_rotate_crop_image）
     */
    public BufferedImage extractTextRegion(BufferedImage image, TextBlock block) {
        List<Point2D> points = block.boxPoints();
        if (points == null || points.size() < 4) {
            return image;
        }

        Point2D[] ordered = orderClockwise(points);
        double dstHd = Math.max(ordered[0].distance(ordered[3]), ordered[1].distance(ordered[2]));
        // 主要向左扩，补回 DB 收缩掉的首字；上下少扩，避免卷进终端邻行
        double padLeft = Math.max(5.0, dstHd * 0.32);
        double padRight = Math.max(3.0, dstHd * 0.12);
        double padY = Math.max(2.0, dstHd * 0.08);
        ordered = inflateQuad(ordered, padLeft, padRight, padY);
        for (int i = 0; i < ordered.length; i++) {
            ordered[i] = new Point2D.Double(
                    clamp(ordered[i].getX(), 0, image.getWidth() - 1),
                    clamp(ordered[i].getY(), 0, image.getHeight() - 1));
        }

        double dstWd = Math.max(ordered[0].distance(ordered[1]), ordered[3].distance(ordered[2]));
        dstHd = Math.max(ordered[0].distance(ordered[3]), ordered[1].distance(ordered[2]));
        int dstW = Math.max(8, (int) Math.round(dstWd));
        int dstH = Math.max(8, (int) Math.round(dstHd));

        int minX = (int) Math.floor(Arrays.stream(ordered).mapToDouble(Point2D::getX).min().orElse(0));
        int minY = (int) Math.floor(Arrays.stream(ordered).mapToDouble(Point2D::getY).min().orElse(0));
        int maxX = (int) Math.ceil(Arrays.stream(ordered).mapToDouble(Point2D::getX).max().orElse(image.getWidth() - 1));
        int maxY = (int) Math.ceil(Arrays.stream(ordered).mapToDouble(Point2D::getY).max().orElse(image.getHeight() - 1));
        int pad = 4;
        minX = Math.max(0, minX - pad);
        minY = Math.max(0, minY - pad);
        maxX = Math.min(image.getWidth() - 1, maxX + pad);
        maxY = Math.min(image.getHeight() - 1, maxY + pad);
        int cropW = maxX - minX + 1;
        int cropH = maxY - minY + 1;
        if (cropW <= 1 || cropH <= 1) {
            return image;
        }

        BufferedImage aabb = new BufferedImage(cropW, cropH, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = aabb.createGraphics();
        try {
            g.drawImage(image, 0, 0, cropW, cropH, minX, minY, maxX + 1, maxY + 1, null);
        } finally {
            g.dispose();
        }

        // 接近水平的截图文字走 AABB，避免每个框都分配 OpenCV native 内存
        if (nearlyAxisAligned(ordered)) {
            if ((double) dstH / dstW >= 1.5) {
                return rotateCounterClockwise90(aabb);
            }
            return aabb;
        }

        Point2D[] local = new Point2D[4];
        for (int i = 0; i < 4; i++) {
            local[i] = new Point2D.Double(ordered[i].getX() - minX, ordered[i].getY() - minY);
        }

        BufferedImage warped = warpQuad(aabb, local, dstW, dstH);
        if ((double) dstH / dstW >= 1.5) {
            return rotateCounterClockwise90(warped);
        }
        return warped;
    }

    private static Point2D[] orderClockwise(List<Point2D> points) {
        Point2D[] pts = points.toArray(Point2D[]::new);
        Arrays.sort(pts, Comparator.comparingDouble(Point2D::getX));
        Point2D[] left = {pts[0], pts[1]};
        Point2D[] right = {pts[2], pts[3]};
        Arrays.sort(left, Comparator.comparingDouble(Point2D::getY));
        Arrays.sort(right, Comparator.comparingDouble(Point2D::getY));
        return new Point2D[]{left[0], right[0], right[1], left[1]};
    }

    private static boolean nearlyAxisAligned(Point2D[] q) {
        double width = Math.max(1.0, q[0].distance(q[1]));
        double height = Math.max(1.0, q[0].distance(q[3]));
        return Math.abs(q[1].getY() - q[0].getY()) < 0.08 * width
                && Math.abs(q[3].getX() - q[0].getX()) < 0.08 * height;
    }

    private static Point2D[] inflateQuad(Point2D[] q, double padLeft, double padRight, double padY) {
        Point2D top = unit(q[0], q[1]);
        Point2D left = unit(q[0], q[3]);
        return new Point2D[]{
                offset(q[0], top, -padLeft, left, -padY),
                offset(q[1], top, padRight, left, -padY),
                offset(q[2], top, padRight, left, padY),
                offset(q[3], top, -padLeft, left, padY)
        };
    }

    private static Point2D unit(Point2D from, Point2D to) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double len = Math.hypot(dx, dy);
        if (len < 1e-3) {
            return new Point2D.Double(0, 0);
        }
        return new Point2D.Double(dx / len, dy / len);
    }

    private static Point2D offset(Point2D p, Point2D xDir, double x, Point2D yDir, double y) {
        return new Point2D.Double(
                p.getX() + xDir.getX() * x + yDir.getX() * y,
                p.getY() + xDir.getY() * x + yDir.getY() * y);
    }

    private static BufferedImage warpQuad(BufferedImage srcImg, Point2D[] srcPts, int dstW, int dstH) {
        Mat src = null;
        Mat dst = null;
        Mat srcTri = null;
        Mat dstTri = null;
        Mat transform = null;
        FloatPointer srcPtsPtr = null;
        FloatPointer dstPtsPtr = null;
        try {
            src = bufferedImageToMat(srcImg);
            dst = new Mat();
            srcPtsPtr = new FloatPointer(
                    (float) srcPts[0].getX(), (float) srcPts[0].getY(),
                    (float) srcPts[1].getX(), (float) srcPts[1].getY(),
                    (float) srcPts[2].getX(), (float) srcPts[2].getY(),
                    (float) srcPts[3].getX(), (float) srcPts[3].getY());
            dstPtsPtr = new FloatPointer(
                    0f, 0f,
                    (float) dstW, 0f,
                    (float) dstW, (float) dstH,
                    0f, (float) dstH);
            srcTri = new Mat(4, 1, CV_32FC2, srcPtsPtr);
            dstTri = new Mat(4, 1, CV_32FC2, dstPtsPtr);
            transform = getPerspectiveTransform(srcTri, dstTri);
            Size size = new Size(dstW, dstH);
            try {
                org.bytedeco.opencv.global.opencv_imgproc.warpPerspective(src, dst, transform, size);
            } finally {
                size.close();
            }
            return matToBufferedImage(dst);
        } finally {
            if (transform != null) transform.close();
            if (srcTri != null) srcTri.close();
            if (dstTri != null) dstTri.close();
            if (srcPtsPtr != null) srcPtsPtr.close();
            if (dstPtsPtr != null) dstPtsPtr.close();
            if (dst != null) dst.close();
            if (src != null) src.close();
        }
    }

    private static BufferedImage rotateCounterClockwise90(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        BufferedImage dst = new BufferedImage(h, w, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = dst.createGraphics();
        try {
            g.translate(0, w);
            g.rotate(-Math.PI / 2);
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return dst;
    }

    private static Mat bufferedImageToMat(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        byte[] data = new byte[width * height * 3];
        int i = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                data[i++] = (byte) (rgb & 0xFF);
                data[i++] = (byte) ((rgb >> 8) & 0xFF);
                data[i++] = (byte) ((rgb >> 16) & 0xFF);
            }
        }
        Mat mat = new Mat(height, width, CV_8UC3);
        mat.data().put(data);
        return mat;
    }

    private static BufferedImage matToBufferedImage(Mat mat) {
        int width = mat.cols();
        int height = mat.rows();
        byte[] data = new byte[width * height * 3];
        mat.data().get(data);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        int i = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int b = data[i++] & 0xFF;
                int g = data[i++] & 0xFF;
                int r = data[i++] & 0xFF;
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return image;
    }

    /**
     * 将 BufferedImage 转换为 float 张量 [1, 3, H, W]
     * 仅除以 255 映射到 [0, 1] 范围，不做额外归一化（由 ONNX 模型内部处理）
     */
    private float[][][][] imageToFloatTensorRaw(BufferedImage image) {
        int height = image.getHeight();
        int width = image.getWidth();

        float[][][][] tensor = new float[1][3][height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                float b = ((rgb >> 0) & 0xFF) / 255.0f;
                float g = ((rgb >> 8) & 0xFF) / 255.0f;
                float r = ((rgb >> 16) & 0xFF) / 255.0f;

                // 仅除以 255，不做额外归一化
                tensor[0][0][y][x] = b;  // B 通道
                tensor[0][1][y][x] = g;  // G 通道
                tensor[0][2][y][x] = r;  // R 通道
            }
        }

        return tensor;
    }

    /**
     * 将 BufferedImage 转换为 float 张量 [1, 3, H, W]
     * 使用 ImageNet 归一化: mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225]
     */
    private float[][][][] imageToFloatTensorImageNet(BufferedImage image) {
        int height = image.getHeight();
        int width = image.getWidth();
        float[] mean = {0.485f, 0.456f, 0.406f};
        float[] std = {0.229f, 0.224f, 0.225f};

        float[][][][] tensor = new float[1][3][height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                float b = ((rgb >> 0) & 0xFF) / 255.0f;
                float g = ((rgb >> 8) & 0xFF) / 255.0f;
                float r = ((rgb >> 16) & 0xFF) / 255.0f;

                // ImageNet 归一化: (x/255 - mean) / std
                tensor[0][0][y][x] = (b - mean[0]) / std[0];  // B 通道
                tensor[0][1][y][x] = (g - mean[1]) / std[1];  // G 通道
                tensor[0][2][y][x] = (r - mean[2]) / std[2];  // R 通道
            }
        }

        return tensor;
    }

    /**
     * 将 BufferedImage 转换为 float 张量 [1, 3, H, W]
     * 使用默认归一化: (x/255 - 0.5) / 0.5
     */
    private float[][][][] imageToFloatTensorDefault(BufferedImage image) {
        int height = image.getHeight();
        int width = image.getWidth();

        float[][][][] tensor = new float[1][3][height][width];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                // 注意：BufferedImage 的 getRGB 返回 ARGB，其中 R、G、B 通道顺序为 ARGB
                // PP-OCR 使用 BGR 格式，这里按 B、G、R 顺序提取
                float b = ((rgb >> 0) & 0xFF) / 255.0f;
                float g = ((rgb >> 8) & 0xFF) / 255.0f;
                float r = ((rgb >> 16) & 0xFF) / 255.0f;

                // 归一化: (x / 255.0 - 0.5) / 0.5  →  (value - 0.5) / 0.5
                tensor[0][0][y][x] = (b - 0.5f) / 0.5f;  // B 通道
                tensor[0][1][y][x] = (g - 0.5f) / 0.5f;  // G 通道
                tensor[0][2][y][x] = (r - 0.5f) / 0.5f;  // R 通道
            }
        }

        return tensor;
    }

    private static int roundToMultiple(double value, int multiple) {
        return Math.max(multiple, (int) Math.round(value / multiple) * multiple);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}