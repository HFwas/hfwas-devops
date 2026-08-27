package com.hfwas.devops.fileparser.image;

import com.hfwas.devops.fileparser.config.FileParserConfig;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 图片压缩服务
 * 使用 Thumbnailator 在 OCR 前对图片进行压缩/缩放，减少 OCR 耗时和内存占用。
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>压缩是尽力而为的优化，从不因压缩失败而中断流程</li>
 *   <li>压缩后的文件比原图还大时，自动回退到原图</li>
 *   <li>非常小的文件跳过压缩，避免不必要的开销</li>
 *   <li>调用者负责清理返回的临时压缩文件</li>
 * </ul>
 */
@Slf4j
@Service
public class ImageCompressionService {

    private final FileParserConfig.CompressionConfig config;

    public ImageCompressionService(FileParserConfig config) {
        this.config = config.getCompression();
    }

    /**
     * 压缩图片文件
     * <p>
     * 根据配置对图片进行缩放和品质压缩。如果压缩未启用、文件太小、或压缩失败，
     * 返回一个表示未压缩的 {@link ImageCompressionResult}，其 file 字段指向原文件。
     *
     * @param sourceFile 源图片文件
     * @param fileName   原始文件名（仅用于日志）
     * @return 压缩结果，包含压缩后的文件引用或原文件引用
     */
    public ImageCompressionResult compress(File sourceFile, String fileName) {
        // 1. 检查压缩是否启用
        if (!config.isEnabled()) {
            log.debug("Image compression is disabled, skipping: {}", fileName);
            return ImageCompressionResult.skipped(sourceFile);
        }

        // 2. 检查文件是否大于最小阈值
        long fileSize = sourceFile.length();
        if (fileSize < config.getMinFileSize()) {
            log.debug("Image too small ({} bytes), skipping compression: {}", fileSize, fileName);
            return ImageCompressionResult.skipped(sourceFile);
        }

        // 3. 读取原始图片尺寸
        int originalWidth;
        int originalHeight;
        try {
            BufferedImage originalImage = ImageIO.read(sourceFile);
            if (originalImage == null) {
                log.warn("Cannot read image dimensions, skipping compression: {}", fileName);
                return ImageCompressionResult.failed(sourceFile, "Unreadable image format");
            }
            originalWidth = originalImage.getWidth();
            originalHeight = originalImage.getHeight();
        } catch (IOException e) {
            log.warn("Failed to read image dimensions for {}: {}", fileName, e.getMessage());
            return ImageCompressionResult.failed(sourceFile, "Failed to read image: " + e.getMessage());
        }

        // 4. 执行压缩
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("compressed-", ".jpg");
            File outputFile = tempFile.toFile();

            // 计算目标尺寸（保持宽高比）
            int targetWidth = originalWidth;
            int targetHeight = originalHeight;
            int maxWidth = config.getMaxWidth();
            int maxHeight = config.getMaxHeight();

            if (originalWidth > maxWidth || originalHeight > maxHeight) {
                double scale = Math.min(
                        (double) maxWidth / originalWidth,
                        (double) maxHeight / originalHeight
                );
                targetWidth = (int) (originalWidth * scale);
                targetHeight = (int) (originalHeight * scale);
                log.debug("Scaling image from {}x{} to {}x{} for {}",
                        originalWidth, originalHeight, targetWidth, targetHeight, fileName);
            }

            // 使用 Thumbnailator 压缩
            Thumbnails.of(sourceFile)
                    .size(targetWidth, targetHeight)
                    .outputQuality(config.getQuality())
                    .keepAspectRatio(true)
                    .toFile(outputFile);

            long compressedSize = outputFile.length();
            long originalSize = sourceFile.length();

            // 5. 检查压缩是否有效（压缩后文件是否更小）
            double ratio = originalSize > 0 ? (double) (originalSize - compressedSize) / originalSize : 0;

            if (compressedSize >= originalSize || ratio < config.getMinCompressRatio()) {
                log.debug("Compression ineffective (ratio={}), using original: {}", ratio, fileName);
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.warn("Failed to delete ineffective compressed temp file: {}", e.getMessage());
                }
                return ImageCompressionResult.skipped(sourceFile);
            }

            log.info("Image compressed: {} ({} -> {} bytes, ratio={}, {}x{} -> {}x{})",
                    fileName, originalSize, compressedSize, String.format("%.2f", ratio),
                    originalWidth, originalHeight, targetWidth, targetHeight);

            return ImageCompressionResult.success(outputFile, originalSize, compressedSize,
                    originalWidth, originalHeight, targetWidth, targetHeight);

        } catch (IOException | IllegalArgumentException e) {
            log.warn("Image compression failed for {}: {}", fileName, e.getMessage());
            // 清理可能创建的部分文件
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
            return ImageCompressionResult.failed(sourceFile, "Compression failed: " + e.getMessage());
        }
    }
}