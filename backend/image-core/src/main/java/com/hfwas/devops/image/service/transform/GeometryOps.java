package com.hfwas.devops.image.service.transform;

import com.hfwas.devops.image.dto.ImageConvertRequest;

import java.awt.Graphics2D;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;

public final class GeometryOps {

    private GeometryOps() {
    }

    public static BufferedImage toSrgb(BufferedImage src) {
        if (src == null || src.getColorModel() == null || src.getColorModel().getColorSpace() == null) {
            return src;
        }
        ColorSpace cs = src.getColorModel().getColorSpace();
        if (cs.getType() != ColorSpace.TYPE_CMYK && cs.isCS_sRGB()) {
            return src;
        }
        if (cs.getType() != ColorSpace.TYPE_CMYK && cs.getType() != ColorSpace.TYPE_3CLR) {
            return src;
        }
        ColorConvertOp op = new ColorConvertOp(ColorSpace.getInstance(ColorSpace.CS_sRGB), null);
        return op.filter(src, null);
    }

    public static BufferedImage applyOrientation(BufferedImage src, Integer orientation) {
        if (src == null || orientation == null || orientation == 1) {
            return src;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        AffineTransform tx = new AffineTransform();
        int outW = w;
        int outH = h;
        switch (orientation) {
            case 2 -> tx.scale(-1, 1); // flip X, then translate
            case 3 -> {
                tx.translate(w, h);
                tx.rotate(Math.PI);
            }
            case 4 -> { // flip Y
                tx.translate(0, h);
                tx.scale(1, -1);
            }
            case 5 -> { // transpose
                outW = h;
                outH = w;
                tx.rotate(Math.PI / 2);
                tx.scale(1, -1);
            }
            case 6 -> { // 90 CW
                outW = h;
                outH = w;
                tx.translate(h, 0);
                tx.rotate(Math.PI / 2);
            }
            case 7 -> {
                outW = h;
                outH = w;
                tx.translate(h, 0);
                tx.rotate(Math.PI / 2);
                tx.scale(-1, 1);
            }
            case 8 -> { // 90 CCW
                outW = h;
                outH = w;
                tx.translate(0, w);
                tx.rotate(-Math.PI / 2);
            }
            default -> {
                return src;
            }
        }
        if (orientation == 2) {
            tx = AffineTransform.getScaleInstance(-1, 1);
            tx.translate(-w, 0);
        }
        if (orientation == 7) {
            tx = new AffineTransform();
            tx.translate(0, w);
            tx.rotate(-Math.PI / 2);
            tx.scale(-1, 1);
        }
        if (orientation == 5) {
            tx = new AffineTransform();
            tx.rotate(Math.PI / 2);
            tx.scale(1, -1);
        }
        BufferedImage dest = new BufferedImage(outW, outH, opaqueType(src));
        Graphics2D g = dest.createGraphics();
        g.drawImage(src, tx, null);
        g.dispose();
        return dest;
    }

    public static BufferedImage applyGeometry(BufferedImage src, ImageConvertRequest.Geometry geometry) {
        if (src == null || geometry == null) {
            return src;
        }
        BufferedImage current = src;
        int rotate = ((geometry.getRotate() % 360) + 360) % 360;
        if (rotate == 90 || rotate == 180 || rotate == 270) {
            current = rotateClockwise(current, rotate);
        }
        if (geometry.isFlipX()) {
            current = flip(current, true);
        }
        if (geometry.isFlipY()) {
            current = flip(current, false);
        }
        ImageConvertRequest.Crop crop = geometry.getCrop();
        if (crop != null && crop.getWidth() > 0 && crop.getHeight() > 0) {
            int x = Math.max(0, crop.getX());
            int y = Math.max(0, crop.getY());
            int w = Math.min(crop.getWidth(), current.getWidth() - x);
            int h = Math.min(crop.getHeight(), current.getHeight() - y);
            if (w > 0 && h > 0) {
                current = current.getSubimage(x, y, w, h);
                BufferedImage copy = new BufferedImage(w, h, opaqueType(current));
                Graphics2D g = copy.createGraphics();
                g.drawImage(current, 0, 0, null);
                g.dispose();
                current = copy;
            }
        }
        return current;
    }

    public static BufferedImage scaleMaxSide(BufferedImage src, Integer maxSide) {
        if (src == null || maxSide == null || maxSide <= 0) {
            return src;
        }
        int w = src.getWidth();
        int h = src.getHeight();
        int longest = Math.max(w, h);
        if (longest <= maxSide) {
            return src;
        }
        double scale = maxSide / (double) longest;
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        BufferedImage dest = new BufferedImage(nw, nh, opaqueType(src));
        Graphics2D g = dest.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return dest;
    }

    public static BufferedImage flattenForJpeg(BufferedImage src) {
        if (src == null) {
            return null;
        }
        if (src.getColorModel() == null || !src.getColorModel().hasAlpha()) {
            return src;
        }
        BufferedImage dest = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dest.createGraphics();
        g.setColor(java.awt.Color.WHITE);
        g.fillRect(0, 0, src.getWidth(), src.getHeight());
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return dest;
    }

    static BufferedImage rotateClockwise(BufferedImage src, int degrees) {
        int w = src.getWidth();
        int h = src.getHeight();
        AffineTransform tx = new AffineTransform();
        int outW = w;
        int outH = h;
        if (degrees == 90) {
            outW = h;
            outH = w;
            tx.translate(h, 0);
            tx.rotate(Math.PI / 2);
        } else if (degrees == 180) {
            tx.translate(w, h);
            tx.rotate(Math.PI);
        } else if (degrees == 270) {
            outW = h;
            outH = w;
            tx.translate(0, w);
            tx.rotate(-Math.PI / 2);
        } else {
            return src;
        }
        BufferedImage dest = new BufferedImage(outW, outH, opaqueType(src));
        Graphics2D g = dest.createGraphics();
        g.drawImage(src, tx, null);
        g.dispose();
        return dest;
    }

    static BufferedImage flip(BufferedImage src, boolean horizontal) {
        int w = src.getWidth();
        int h = src.getHeight();
        AffineTransform tx = horizontal
                ? AffineTransform.getScaleInstance(-1, 1)
                : AffineTransform.getScaleInstance(1, -1);
        if (horizontal) {
            tx.translate(-w, 0);
        } else {
            tx.translate(0, -h);
        }
        BufferedImage dest = new BufferedImage(w, h, opaqueType(src));
        Graphics2D g = dest.createGraphics();
        g.drawImage(src, tx, null);
        g.dispose();
        return dest;
    }

    private static int opaqueType(BufferedImage src) {
        if (src.getColorModel() != null && src.getColorModel().hasAlpha()) {
            return BufferedImage.TYPE_INT_ARGB;
        }
        return BufferedImage.TYPE_INT_RGB;
    }
}
