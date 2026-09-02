package com.hfwas.devops.fileparser.ocr.onnx;

import org.junit.jupiter.api.Test;

import java.awt.geom.Point2D;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DetectionPostProcessorTest {

    @Test
    void shouldMergeSplitEnglishFragmentsOnSameLine() {
        TextBlock left = box(10, 100, 80, 130);
        TextBlock right = box(82, 102, 400, 132);
        assertTrue(DetectionPostProcessor.shouldMerge(left, right, 12));

        List<TextBlock> merged = DetectionPostProcessor.mergeLineFragments(List.of(left, right), 1000);
        assertEquals(1, merged.size());
        assertEquals(10, merged.get(0).minX(), 0.1);
        assertEquals(400, merged.get(0).maxX(), 0.1);
    }

    @Test
    void shouldNotMergeStackedRows() {
        TextBlock top = box(10, 100, 400, 130);
        TextBlock bottom = box(10, 138, 400, 168);
        assertFalse(DetectionPostProcessor.shouldMerge(top, bottom, 34));
        List<TextBlock> merged = DetectionPostProcessor.mergeLineFragments(List.of(top, bottom), 1000);
        assertEquals(2, merged.size());
    }

    private static TextBlock box(double x1, double y1, double x2, double y2) {
        return new TextBlock(List.of(
                new Point2D.Double(x1, y1),
                new Point2D.Double(x2, y1),
                new Point2D.Double(x2, y2),
                new Point2D.Double(x1, y2)
        ), "", 0.9);
    }
}
