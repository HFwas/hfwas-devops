package com.hfwas.devops.image.service.transform;

import com.hfwas.devops.image.TestImages;
import com.hfwas.devops.image.config.ImageProcessorConfig;
import com.hfwas.devops.image.dto.ImageConvertRequest;
import com.hfwas.devops.image.service.EngineProbe;
import com.hfwas.devops.image.service.NativeProcessRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageTransformServiceTest {

    @TempDir
    Path tempDir;

    private ImageTransformService transformService;

    @BeforeEach
    void setUp() {
        ImageProcessorConfig config = new ImageProcessorConfig();
        config.setTempDir(tempDir.toString());
        EngineProbe probe = new EngineProbe(config);
        NativeProcessRunner runner = new NativeProcessRunner(config);
        transformService = new ImageTransformService(
                config,
                new ImageMagickTransformer(config, probe, runner),
                new ImageIoTransformer());
    }

    @Test
    void convertsJpegToPng() throws Exception {
        Path src = TestImages.writeJpeg(tempDir, "in.jpg", 40, 20);
        Path dest = tempDir.resolve("out.png");
        ImageConvertRequest request = new ImageConvertRequest();
        request.setTargetFormat("png");
        request.setStripMetadata(true);
        ImageTransformService.Result result = transformService.convert(src, dest, request);
        assertTrue(Files.exists(dest));
        assertEquals(40, result.getWidth());
        assertEquals(20, result.getHeight());
        assertEquals("image/png", result.getMimeType());
        BufferedImage image = ImageIO.read(dest.toFile());
        assertEquals(40, image.getWidth());
    }

    @Test
    void convertsJpegToPngAsJvmFallbackForWebpWhenMagickMissing() throws Exception {
        Path src = TestImages.writeJpeg(tempDir, "in.jpg", 32, 32);
        Path dest = tempDir.resolve("out.png");
        ImageConvertRequest request = new ImageConvertRequest();
        request.setTargetFormat("png");
        request.setQuality(80);
        ImageTransformService.Result result = transformService.convert(src, dest, request);
        assertTrue(Files.size(dest) > 0);
        assertEquals("image/png", result.getMimeType());
    }

    @Test
    void magickCommandIncludesWebpQualityAndStrip() {
        ImageConvertRequest request = new ImageConvertRequest();
        request.setTargetFormat("webp");
        request.setQuality(80);
        request.setStripMetadata(true);
        request.setApplyOrientation(true);
        var cmd = ImageMagickTransformer.buildConvertCommand(
                "magick", "in.jpg", "out.webp", "webp", request);
        assertTrue(cmd.contains("-quality"));
        assertTrue(cmd.contains("80"));
        assertTrue(cmd.contains("-strip"));
        assertTrue(cmd.contains("-auto-orient"));
        assertTrue(cmd.contains("-colorspace"));
        assertTrue(cmd.contains("sRGB"));
        assertTrue(cmd.contains("-limit"));
        assertTrue(cmd.contains("256MiB"));
        assertEquals("out.webp", cmd.get(cmd.size() - 1));
    }

    @Test
    void magickPreviewCommandBakesOrientationAndSrgb() {
        var cmd = ImageMagickTransformer.buildPreviewCommand(
                "magick", "in.heic", "preview.jpg", 1600, 72);
        assertTrue(cmd.contains("-auto-orient"));
        assertTrue(cmd.contains("-colorspace"));
        assertTrue(cmd.contains("sRGB"));
        assertTrue(cmd.contains("1600x1600>"));
        assertTrue(cmd.contains("72"));
        assertTrue(cmd.contains("-limit"));
        assertTrue(cmd.contains("256MiB"));
    }

    @Test
    void convertsJpegToTiff() throws Exception {
        Path src = TestImages.writeJpeg(tempDir, "in.jpg", 24, 16);
        Path dest = tempDir.resolve("out.tif");
        ImageConvertRequest request = new ImageConvertRequest();
        request.setTargetFormat("tiff");
        ImageTransformService.Result result = transformService.convert(src, dest, request);
        assertTrue(Files.exists(dest));
        assertEquals("image/tiff", result.getMimeType());
        BufferedImage image = ImageIO.read(dest.toFile());
        assertEquals(24, image.getWidth());
        assertEquals(16, image.getHeight());
    }

    @Test
    void previewJpegUsesConfiguredQualityAndMaxSide() throws Exception {
        ImageProcessorConfig config = new ImageProcessorConfig();
        config.setTempDir(tempDir.toString());
        config.getPreview().setMaxSide(32);
        config.getPreview().setQuality(40);
        EngineProbe probe = new EngineProbe(config);
        NativeProcessRunner runner = new NativeProcessRunner(config);
        ImageTransformService service = new ImageTransformService(
                config,
                new ImageMagickTransformer(config, probe, runner),
                new ImageIoTransformer());
        Path src = TestImages.writeJpeg(tempDir, "wide.jpg", 80, 40);
        Path dest = tempDir.resolve("preview.jpg");
        ImageTransformService.Result result = service.writePreviewJpeg(src, dest);
        assertEquals(32, result.getWidth());
        assertEquals(16, result.getHeight());
        assertEquals("image/jpeg", result.getMimeType());
        assertTrue(Files.size(dest) > 0);
    }

    @Test
    void cropAndRotateChangeDimensions() throws Exception {
        Path src = TestImages.writeJpeg(tempDir, "in.jpg", 80, 40);
        Path dest = tempDir.resolve("out.jpg");
        ImageConvertRequest request = new ImageConvertRequest();
        request.setTargetFormat("jpeg");
        ImageConvertRequest.Geometry geometry = new ImageConvertRequest.Geometry();
        geometry.setRotate(90);
        ImageConvertRequest.Crop crop = new ImageConvertRequest.Crop();
        crop.setX(0);
        crop.setY(0);
        crop.setWidth(20);
        crop.setHeight(30);
        geometry.setCrop(crop);
        request.setGeometry(geometry);
        ImageTransformService.Result result = transformService.convert(src, dest, request);
        // rotate 90 → 40x80, then crop 20x30 in rotated space
        assertEquals(20, result.getWidth());
        assertEquals(30, result.getHeight());
    }

    @Test
    void maxSideScalesDown() throws Exception {
        Path src = TestImages.writeJpeg(tempDir, "in.jpg", 100, 50);
        Path dest = tempDir.resolve("out.jpg");
        ImageConvertRequest request = new ImageConvertRequest();
        request.setTargetFormat("jpeg");
        request.setMaxSide(50);
        ImageTransformService.Result result = transformService.convert(src, dest, request);
        assertEquals(50, result.getWidth());
        assertEquals(25, result.getHeight());
    }

    @Test
    void transparentPngToJpegFlattens() throws Exception {
        Path src = TestImages.writePng(tempDir, "in.png", 16, 16, true);
        Path dest = tempDir.resolve("out.jpg");
        ImageConvertRequest request = new ImageConvertRequest();
        request.setTargetFormat("jpeg");
        transformService.convert(src, dest, request);
        BufferedImage jpeg = ImageIO.read(dest.toFile());
        assertFalse(jpeg.getColorModel().hasAlpha());
    }

    @Test
    void stripLeavesNoGpsOnJvmJpeg() throws Exception {
        Path src = TestImages.writeJpeg(tempDir, "in.jpg", 20, 20);
        Path dest = tempDir.resolve("out.jpg");
        ImageConvertRequest request = new ImageConvertRequest();
        request.setTargetFormat("jpeg");
        request.setStripMetadata(true);
        transformService.convert(src, dest, request);
        var vo = new com.hfwas.devops.image.service.metadata.MetadataExtractorReader()
                .read(dest, "image/jpeg", "jpg", 20, 20, 1, false);
        assertFalse(vo.getPrivacy().isHasGps());
    }
}
