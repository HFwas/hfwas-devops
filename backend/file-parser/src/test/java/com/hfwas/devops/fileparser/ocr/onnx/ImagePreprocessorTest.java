package com.hfwas.devops.fileparser.ocr.onnx;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImagePreprocessorTest {

    @Test
    void shouldKeepImageWithinWorkingMaxSide() {
        BufferedImage source = new BufferedImage(5712, 4284, BufferedImage.TYPE_3BYTE_BGR);
        BufferedImage scaled = new ImagePreprocessor().limitWorkingSize(source);

        assertEquals(ImagePreprocessor.WORKING_MAX_SIDE, Math.max(scaled.getWidth(), scaled.getHeight()));
        assertTrue(scaled.getWidth() <= ImagePreprocessor.WORKING_MAX_SIDE);
        assertTrue(scaled.getHeight() <= ImagePreprocessor.WORKING_MAX_SIDE);
        double ratio = (double) source.getWidth() / source.getHeight();
        assertEquals(ratio, (double) scaled.getWidth() / scaled.getHeight(), 0.01);
    }

    @Test
    void shouldNotUpscaleSmallImage() {
        BufferedImage source = new BufferedImage(800, 600, BufferedImage.TYPE_3BYTE_BGR);
        BufferedImage scaled = new ImagePreprocessor().limitWorkingSize(source);
        assertEquals(source, scaled);
    }

    @Test
    void shouldCropAxisAlignedBoxWithoutChangingAspect() {
        BufferedImage source = new BufferedImage(200, 80, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = source.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 200, 80);
            g.setColor(Color.BLACK);
            g.fillRect(20, 20, 120, 24);
        } finally {
            g.dispose();
        }

        TextBlock block = new TextBlock(List.of(
                new Point2D.Double(20, 20),
                new Point2D.Double(140, 20),
                new Point2D.Double(140, 44),
                new Point2D.Double(20, 44)
        ), "", 0.9);

        BufferedImage crop = new ImagePreprocessor().extractTextRegion(source, block);
        assertTrue(crop.getWidth() > crop.getHeight());
        assertTrue(crop.getWidth() < source.getWidth());
    }
}
