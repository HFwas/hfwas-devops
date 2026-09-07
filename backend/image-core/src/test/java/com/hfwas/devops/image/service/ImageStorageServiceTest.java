package com.hfwas.devops.image.service;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.image.config.ImageProcessorConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageStorageServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void sanitizeExtKeepsAlphanumericOnly() {
        assertEquals(".png", ImageStorageService.sanitizeExt("../png"));
        assertEquals(".jpg", ImageStorageService.sanitizeExt(".JPG"));
        assertEquals("", ImageStorageService.sanitizeExt(".."));
        assertEquals("", ImageStorageService.sanitizeExt(null));
    }

    @Test
    void createSessionDirRejectsPathEscape() throws Exception {
        ImageProcessorConfig config = new ImageProcessorConfig();
        config.setTempDir(tempDir.toString());
        ImageStorageService storage = new ImageStorageService(config);
        storage.init();
        BizException ex = assertThrows(BizException.class, () -> storage.createSessionDir("../outside"));
        assertTrue(ex.getMessage().contains("非法"));
    }
}
