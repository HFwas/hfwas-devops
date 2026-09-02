package com.hfwas.devops.fileparser.ocr.onnx;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OcrPipelineIT {

    @Test
    void shouldRecognizeChineseScreenshotText() throws Exception {
        Path modelDir = Path.of(System.getProperty("user.home"), ".hfwas-devops/models/ppocrv6");
        Path recModel = modelDir.resolve("rec_medium.onnx");
        Assumptions.assumeTrue(Files.exists(recModel) && Files.size(recModel) > 1024,
                "PP-OCRv6 medium models not installed");

        File image = new File("/Users/hfwas/Downloads/IMG_1908.JPG");
        Assumptions.assumeTrue(image.isFile(), "Sample screenshot not present");

        try (OcrPipeline pipeline = new OcrPipeline(modelDir.toFile(), "medium", 1)) {
            OcrPipeline.OcrResult result = pipeline.recognize(image);
            String text = result.text();
            System.out.println("===== OCR RESULT =====\n" + text + "\n===== END =====");
            assertTrue(text.contains("容器管理"), "got: " + text);
            assertTrue(text.contains("部署空间"), "got: " + text);
            assertTrue(text.contains("Hostname") || text.contains("ai-agent-installer-7c6d46b4fc"), "got: " + text);
            assertTrue(text.contains("pzhagpf"), "got: " + text);
            assertTrue(text.contains("guest"), "got: " + text);
        }
    }

    @Test
    void shouldRecognizeRenderedChinese() throws Exception {
        Path modelDir = Path.of(System.getProperty("user.home"), ".hfwas-devops/models/ppocrv6");
        Path recModel = modelDir.resolve("rec_medium.onnx");
        Assumptions.assumeTrue(Files.exists(recModel) && Files.size(recModel) > 1024,
                "PP-OCRv6 medium models not installed");

        BufferedImage image = new BufferedImage(480, 96, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = image.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 480, 96);
            g.setColor(Color.BLACK);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(new Font("SansSerif", Font.PLAIN, 36));
            g.drawString("容器管理 详情", 24, 60);
        } finally {
            g.dispose();
        }

        File tmp = Files.createTempFile("ocr-zh-", ".png").toFile();
        tmp.deleteOnExit();
        ImageIO.write(image, "png", tmp);

        try (OcrPipeline pipeline = new OcrPipeline(modelDir.toFile(), "medium", 1)) {
            String text = pipeline.recognize(tmp).text();
            System.out.println("Rendered OCR: " + text);
            assertTrue(text.contains("容器") || text.contains("管理") || text.contains("详情"),
                    "Rendered Chinese should be recognizable, got: " + text);
        }
    }
}
