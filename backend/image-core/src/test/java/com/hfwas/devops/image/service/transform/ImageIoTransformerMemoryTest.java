package com.hfwas.devops.image.service.transform;

import com.hfwas.devops.image.TestImages;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageIoTransformerMemoryTest {

    @TempDir
    Path tempDir;

    @Test
    void subsampleIsOneWhenImageAlreadyWithinMaxSide() {
        assertEquals(1, ImageIoTransformer.subsampleFactor(100, 50, 200));
        assertEquals(1, ImageIoTransformer.subsampleFactor(32, 32, 32));
    }

    @Test
    void subsampleReducesLargeRastersBeforeDecode() {
        assertEquals(2, ImageIoTransformer.subsampleFactor(100, 50, 50));
        assertEquals(3, ImageIoTransformer.subsampleFactor(8000, 4000, 2048));
        assertTrue(8000 / 3 <= 2667);
    }

    @Test
    void previewJpegOutputFitsMaxSide() throws Exception {
        Path src = TestImages.writeJpeg(tempDir, "in.jpg", 80, 40);
        Path dest = tempDir.resolve("preview.jpg");
        ImageIoTransformer transformer = new ImageIoTransformer();
        ImageIoTransformer.Result result = transformer.writePreviewJpeg(src, dest, 20, 85);
        assertEquals(20, result.getWidth());
        assertEquals(10, result.getHeight());
        BufferedImage out = ImageIO.read(dest.toFile());
        assertEquals(20, out.getWidth());
        assertEquals(10, out.getHeight());
    }
}
