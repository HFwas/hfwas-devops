package com.hfwas.devops.image.service.transform;

import com.hfwas.devops.image.dto.ImageConvertRequest;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeometryOpsTest {

    @Test
    void rotate90SwapsSides() {
        BufferedImage src = color(40, 20, Color.RED);
        BufferedImage out = GeometryOps.rotateClockwise(src, 90);
        assertEquals(20, out.getWidth());
        assertEquals(40, out.getHeight());
    }

    @Test
    void cropUsesGeometryRect() {
        BufferedImage src = color(80, 60, Color.BLUE);
        ImageConvertRequest.Geometry geometry = new ImageConvertRequest.Geometry();
        ImageConvertRequest.Crop crop = new ImageConvertRequest.Crop();
        crop.setX(10);
        crop.setY(20);
        crop.setWidth(30);
        crop.setHeight(15);
        geometry.setCrop(crop);
        BufferedImage out = GeometryOps.applyGeometry(src, geometry);
        assertEquals(30, out.getWidth());
        assertEquals(15, out.getHeight());
    }

    @Test
    void orientation6RotatesToUpright() {
        BufferedImage src = color(30, 10, Color.GREEN);
        BufferedImage out = GeometryOps.applyOrientation(src, 6);
        assertEquals(10, out.getWidth());
        assertEquals(30, out.getHeight());
    }

    private static BufferedImage color(int w, int h, Color color) {
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return image;
    }
}
