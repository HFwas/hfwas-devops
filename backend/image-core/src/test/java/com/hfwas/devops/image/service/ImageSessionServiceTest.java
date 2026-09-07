package com.hfwas.devops.image.service;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.image.TestImages;
import com.hfwas.devops.image.config.ImageProcessorConfig;
import com.hfwas.devops.image.dto.ImageConvertRequest;
import com.hfwas.devops.image.dto.ImageConvertVO;
import com.hfwas.devops.image.dto.ImageSessionVO;
import com.hfwas.devops.image.service.identify.ImageIdentifyService;
import com.hfwas.devops.image.service.metadata.ExifToolReader;
import com.hfwas.devops.image.service.metadata.ImageMetadataService;
import com.hfwas.devops.image.service.metadata.MetadataExtractorReader;
import com.hfwas.devops.image.service.transform.ImageIoTransformer;
import com.hfwas.devops.image.service.transform.ImageMagickTransformer;
import com.hfwas.devops.image.service.transform.ImageTransformService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageSessionServiceTest {

    @TempDir
    Path tempDir;

    private ImageProcessorConfig config;
    private ImageSessionService sessionService;

    @BeforeEach
    void setUp() throws Exception {
        config = new ImageProcessorConfig();
        config.setTempDir(tempDir.toString());
        config.setSessionTtl(Duration.ofMinutes(30));
        EngineProbe probe = new EngineProbe(config);
        NativeProcessRunner runner = new NativeProcessRunner(config);
        ImageStorageService storage = new ImageStorageService(config);
        storage.init();
        MetadataExtractorReader extractor = new MetadataExtractorReader();
        ExifToolReader exifTool = new ExifToolReader(config, probe, runner);
        sessionService = new ImageSessionService(
                config,
                storage,
                new ImageIdentifyService(config, probe, runner),
                new ImageMetadataService(exifTool, extractor),
                new ImageTransformService(config, new ImageMagickTransformer(config, probe, runner), new ImageIoTransformer()),
                extractor,
                exifTool,
                new ImageConvertJobService(config),
                null,
                null
        );
    }

    @Test
    void createSessionThenConvertJpegToPng() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "IMG_0001.jpg", "image/jpeg", TestImages.jpeg(48, 32));
        ImageSessionVO session = sessionService.create(file);
        assertEquals("image/jpeg", session.getMimeType());
        assertEquals(48, session.getWidth());
        assertEquals(32, session.getHeight());
        assertTrue(session.getSessionId().length() >= 20);
        assertFalse(session.isNeedsServerPreview());

        var meta = sessionService.metadata(session.getSessionId());
        assertEquals(48, meta.getPixel().getWidth());
        assertFalse(meta.getPrivacy().isHasGps());

        ImageConvertRequest request = new ImageConvertRequest();
        request.setTargetFormat("png");
        request.setQuality(85);
        request.setStripMetadata(true);
        ImageConvertVO converted = sessionService.convert(session.getSessionId(), request);
        assertEquals("image/png", converted.getMimeType());
        assertEquals("completed", converted.getStatus());
        assertTrue(converted.getResultSize() > 0);
        assertTrue(converted.getResultFileName().endsWith(".png"));
        Path result = sessionService.resultFile(session.getSessionId());
        assertTrue(Files.size(result) > 0);
    }

    @Test
    void expiredSessionReturnsNotFound() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.jpg", "image/jpeg", TestImages.jpeg(8, 8));
        ImageSessionVO session = sessionService.create(file);
        ImageSession stored = sessionService.require(session.getSessionId());
        stored.setExpiresAt(Instant.now().minusSeconds(5));
        BizException ex = assertThrows(BizException.class, () -> sessionService.get(session.getSessionId()));
        assertEquals(10003, ex.getCode());
    }

    @Test
    void unknownSessionIsNotFound() {
        BizException ex = assertThrows(BizException.class, () -> sessionService.get("01MISSING"));
        assertEquals(10003, ex.getCode());
    }

    @Test
    void ulidIsCrockfordBase32() {
        String id = Ulids.next();
        assertEquals(26, id.length());
        assertTrue(id.chars().allMatch(c -> "0123456789ABCDEFGHJKMNPQRSTVWXYZ".indexOf(c) >= 0));
    }

    @Test
    void orientationAppliedWhenPreviewExistsAndExifNotUpright() {
        assertTrue(ImageSessionService.shouldMarkOrientationApplied(6, true));
        assertFalse(ImageSessionService.shouldMarkOrientationApplied(1, true));
        assertFalse(ImageSessionService.shouldMarkOrientationApplied(6, false));
    }

    @Test
    void convertQueuesWhenPixelsExceedAsyncThreshold() throws Exception {
        config.setAsyncMinPixels(1);
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.jpg", "image/jpeg", TestImages.jpeg(24, 16));
        ImageSessionVO session = sessionService.create(file);
        ImageConvertRequest request = new ImageConvertRequest();
        request.setTargetFormat("png");
        ImageConvertVO queued = sessionService.convert(session.getSessionId(), request);
        assertEquals("queued", queued.getStatus());
        assertTrue(queued.getJobId() != null && queued.getJobId().length() >= 20);
        ImageConvertVO done = null;
        for (int i = 0; i < 80; i++) {
            done = sessionService.job(session.getSessionId(), queued.getJobId());
            if ("completed".equals(done.getStatus()) || "failed".equals(done.getStatus())) {
                break;
            }
            Thread.sleep(50);
        }
        assertTrue(done != null);
        assertEquals("completed", done.getStatus());
        assertTrue(done.getResultSize() > 0);
        assertTrue(Files.size(sessionService.resultFile(session.getSessionId())) > 0);
    }

    @Test
    void convertsToTiff() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.jpg", "image/jpeg", TestImages.jpeg(12, 10));
        ImageSessionVO session = sessionService.create(file);
        ImageConvertRequest request = new ImageConvertRequest();
        request.setTargetFormat("tiff");
        ImageConvertVO converted = sessionService.convert(session.getSessionId(), request);
        assertEquals("image/tiff", converted.getMimeType());
        assertTrue(converted.getResultFileName().endsWith(".tif"));
        assertTrue(converted.getResultSize() > 0);
    }
}
