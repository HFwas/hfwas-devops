package com.hfwas.devops.image.service.identify;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.image.TestImages;
import com.hfwas.devops.image.config.ImageProcessorConfig;
import com.hfwas.devops.image.service.EngineProbe;
import com.hfwas.devops.image.service.NativeProcessRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageIdentifyServiceTest {

    @TempDir
    Path tempDir;

    private ImageIdentifyService identifyService;
    private ImageProcessorConfig config;

    @BeforeEach
    void setUp() {
        config = new ImageProcessorConfig();
        config.setTempDir(tempDir.toString());
        EngineProbe probe = new EngineProbe(config);
        NativeProcessRunner runner = new NativeProcessRunner(config);
        identifyService = new ImageIdentifyService(config, probe, runner);
    }

    @Test
    void identifiesJpegByMagicEvenWhenExtensionIsPng() throws Exception {
        byte[] jpeg = TestImages.jpeg(80, 40);
        Path file = tempDir.resolve("photo.png");
        Files.write(file, jpeg);
        ImageIdentifyService.IdentifyResult result = identifyService.identify(file);
        assertEquals("image/jpeg", result.getMimeType());
        assertEquals(80, result.getWidth());
        assertEquals(40, result.getHeight());
        assertFalse(result.isNeedsServerPreview());
    }

    @Test
    void identifiesPngDimensionsAndAlphaNotRequiredForPreview() throws Exception {
        Path file = TestImages.writePng(tempDir, "a.png", 32, 24, true);
        ImageIdentifyService.IdentifyResult result = identifyService.identify(file);
        assertEquals("image/png", result.getMimeType());
        assertEquals(32, result.getWidth());
        assertEquals(24, result.getHeight());
        assertTrue(result.isHasAlpha());
    }

    @Test
    void rejectsUnknownBytes() throws Exception {
        Path file = tempDir.resolve("x.bin");
        Files.writeString(file, "hello");
        BizException ex = assertThrows(BizException.class, () -> identifyService.identify(file));
        assertTrue(ex.getMessage().contains("不支持"));
    }

    @Test
    void rejectsWhenPixelCountExceedsLimit() throws Exception {
        config.setMaxPixels(100);
        Path file = TestImages.writeJpeg(tempDir, "big.jpg", 20, 20);
        BizException ex = assertThrows(BizException.class, () -> identifyService.identify(file));
        assertTrue(ex.getMessage().contains("像素"));
    }

    @Test
    void multipartJpegIsIdentifiableAfterWrite() throws Exception {
        MockMultipartFile upload = new MockMultipartFile(
                "file", "shot.jpg", "application/octet-stream", TestImages.jpeg(16, 16));
        Path dest = tempDir.resolve("original.bin");
        upload.transferTo(dest);
        ImageIdentifyService.IdentifyResult result = identifyService.identify(dest);
        assertEquals("image/jpeg", result.getMimeType());
        assertEquals("jpg", result.getExt());
    }

    @Test
    void magickIdentifyParsesCmykAndIcc() {
        ImageIdentifyService.Dimensions dims = ImageIdentifyService.parseMagickIdentify(
                "4032\t3024\t1\tCMYK\ticc,xmp\t6");
        assertEquals(4032, dims.width());
        assertEquals(3024, dims.height());
        assertEquals(1, dims.frames());
        assertEquals("CMYK", dims.colorSpace());
        assertTrue(dims.hasIcc());
        assertEquals(6, dims.orientation());
        assertFalse(dims.hasAlpha());
    }

    @Test
    void magickIdentifyParsesAlphaChannel() {
        ImageIdentifyService.Dimensions dims = ImageIdentifyService.parseMagickIdentify(
                "32\t24\t1\tsRGB\t\t1\tTrue");
        assertTrue(dims.hasAlpha());
    }

    @Test
    void cmykAndNonSrgbNeedServerPreview() {
        assertTrue(ImageIdentifyService.needsServerPreview("image/jpeg", "CMYK", false, 1, false));
        assertTrue(ImageIdentifyService.needsServerPreview("image/jpeg", "AdobeRGB", true, 1, false));
        assertTrue(ImageIdentifyService.needsServerPreview("image/jpeg", "sRGB", false, 6, false));
        assertTrue(ImageIdentifyService.needsServerPreview("image/heic", "sRGB", false, 1, false));
        assertFalse(ImageIdentifyService.needsServerPreview("image/jpeg", "sRGB", true, 1, false));
    }

    @Test
    void jpegIdentifyReportsSrgbColorSpace() throws Exception {
        Path file = TestImages.writeJpeg(tempDir, "plain.jpg", 16, 12);
        ImageIdentifyService.IdentifyResult result = identifyService.identify(file);
        assertEquals("sRGB", result.getColorSpace());
        assertFalse(result.isHasAlpha());
        assertFalse(result.isNeedsServerPreview());
    }
}
