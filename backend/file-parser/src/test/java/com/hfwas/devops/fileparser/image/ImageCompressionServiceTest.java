package com.hfwas.devops.fileparser.image;

import com.hfwas.devops.fileparser.config.FileParserConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ImageCompressionServiceTest {

    @Mock
    private FileParserConfig config;

    @Mock
    private FileParserConfig.CompressionConfig compressionConfig;

    private ImageCompressionService service;

    @BeforeEach
    void setUp() {
        lenient().when(config.getCompression()).thenReturn(compressionConfig);
        lenient().when(compressionConfig.isEnabled()).thenReturn(true);
        lenient().when(compressionConfig.getQuality()).thenReturn(0.8f);
        lenient().when(compressionConfig.getMaxWidth()).thenReturn(1920);
        lenient().when(compressionConfig.getMaxHeight()).thenReturn(1920);
        lenient().when(compressionConfig.getMinCompressRatio()).thenReturn(0.05);
        lenient().when(compressionConfig.getMinFileSize()).thenReturn(1024L);
        service = new ImageCompressionService(config);
    }

    @Test
    void shouldCompressJpegImage() throws Exception {
        File imageFile = createTestImage(3000, 2000, "jpg");
        when(compressionConfig.getMinFileSize()).thenReturn(1L);

        ImageCompressionResult result = service.compress(imageFile, "test.jpg");

        assertTrue(result.applied());
        assertNotNull(result.file());
        assertTrue(result.compressedSize() > 0);
        assertTrue(result.compressedSize() < result.originalSize());
        assertTrue(result.ratio() > 0);
        assertEquals(3000, result.originalWidth());
        assertEquals(2000, result.originalHeight());
        // 压缩后尺寸应不超过 1920x1920
        assertTrue(result.compressedWidth() <= 1920);
        assertTrue(result.compressedHeight() <= 1920);
        // 保持宽高比
        assertEquals(1920, result.compressedWidth());
        assertEquals(1280, result.compressedHeight());

        // 清理临时文件
        Files.deleteIfExists(result.file().toPath());
        Files.deleteIfExists(imageFile.toPath());
    }

    @Test
    void shouldCompressPngImage() throws Exception {
        File imageFile = createTestImage(800, 600, "png");
        when(compressionConfig.getMinFileSize()).thenReturn(1L);

        ImageCompressionResult result = service.compress(imageFile, "test.png");

        assertTrue(result.applied());
        assertNotNull(result.file());
        assertEquals(800, result.originalWidth());
        assertEquals(600, result.originalHeight());

        Files.deleteIfExists(result.file().toPath());
        Files.deleteIfExists(imageFile.toPath());
    }

    @Test
    void shouldSkipCompressionForSmallFile() {
        File smallFile = new File("test-small.txt");
        try {
            Files.writeString(smallFile.toPath(), "tiny");
            when(compressionConfig.getMinFileSize()).thenReturn(10000L);

            ImageCompressionResult result = service.compress(smallFile, "small.txt");

            assertFalse(result.applied());
            assertSame(smallFile, result.file());
        } catch (IOException e) {
            fail("Test setup failed: " + e.getMessage());
        } finally {
            smallFile.delete();
        }
    }

    @Test
    void shouldSkipCompressionWhenDisabled() {
        when(compressionConfig.isEnabled()).thenReturn(false);

        File imageFile = new File("test-disabled.jpg");
        try {
            Files.writeString(imageFile.toPath(), "fake image content");
            ImageCompressionResult result = service.compress(imageFile, "disabled.jpg");

            assertFalse(result.applied());
            assertSame(imageFile, result.file());
        } catch (IOException e) {
            fail("Test setup failed: " + e.getMessage());
        } finally {
            imageFile.delete();
        }
    }

    @Test
    void shouldHandleUnsupportedFormat() throws Exception {
        File textFile = new File("test-unsupported.xyz");
        try {
            Files.writeString(textFile.toPath(), "This is not an image file");
            when(compressionConfig.getMinFileSize()).thenReturn(1L);

            ImageCompressionResult result = service.compress(textFile, "unsupported.xyz");

            assertFalse(result.applied());
            assertSame(textFile, result.file());
            assertNotNull(result.errorMessage());
        } finally {
            textFile.delete();
        }
    }

    @Test
    void shouldRespectMaxDimensions() throws Exception {
        File imageFile = createTestImage(4000, 3000, "jpg");
        when(compressionConfig.getMinFileSize()).thenReturn(1L);
        when(compressionConfig.getMaxWidth()).thenReturn(800);
        when(compressionConfig.getMaxHeight()).thenReturn(600);

        ImageCompressionResult result = service.compress(imageFile, "large.jpg");

        assertTrue(result.applied());
        assertTrue(result.compressedWidth() <= 800);
        assertTrue(result.compressedHeight() <= 600);
        assertEquals(800, result.compressedWidth());
        assertEquals(600, result.compressedHeight());

        Files.deleteIfExists(result.file().toPath());
        Files.deleteIfExists(imageFile.toPath());
    }

    @Test
    void shouldHandleCorruptedImage() throws Exception {
        File corruptedFile = new File("test-corrupted.jpg");
        try {
            // 写入无效的 JPEG 数据
            Files.write(corruptedFile.toPath(), new byte[]{0x00, 0x01, 0x02, 0x03});
            when(compressionConfig.getMinFileSize()).thenReturn(1L);

            ImageCompressionResult result = service.compress(corruptedFile, "corrupted.jpg");

            assertFalse(result.applied());
            assertSame(corruptedFile, result.file());
        } finally {
            corruptedFile.delete();
        }
    }

    @Test
    void shouldKeepSmallImageAtOriginalSize() throws Exception {
        // 创建一张小图（不需要缩放）
        File imageFile = createTestImage(100, 100, "jpg");
        when(compressionConfig.getMinFileSize()).thenReturn(1L);
        when(compressionConfig.getMaxWidth()).thenReturn(1920);
        when(compressionConfig.getMaxHeight()).thenReturn(1920);
        when(compressionConfig.getMinCompressRatio()).thenReturn(0.0);
        when(compressionConfig.getQuality()).thenReturn(0.3f);

        ImageCompressionResult result = service.compress(imageFile, "small.jpg");

        // 小图应该被压缩（质量压缩），但尺寸不变
        assertTrue(result.applied());
        assertEquals(100, result.compressedWidth());
        assertEquals(100, result.compressedHeight());

        Files.deleteIfExists(result.file().toPath());
        Files.deleteIfExists(imageFile.toPath());
    }

    /**
     * 创建测试图片文件
     */
    private File createTestImage(int width, int height, String format) throws IOException {
        File tempFile = Files.createTempFile("test-image-", "." + format).toFile();
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        // 绘制渐变填充，避免纯色图片被过度压缩
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int r = (x * 255) / width;
                int g = (y * 255) / height;
                int b = ((x + y) * 255) / (width + height);
                int rgb = (r << 16) | (g << 8) | b;
                image.setRGB(x, y, rgb);
            }
        }
        ImageIO.write(image, format, tempFile);
        return tempFile;
    }
}