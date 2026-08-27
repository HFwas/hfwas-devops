package com.hfwas.devops.fileparser.parser;

import com.hfwas.devops.fileparser.config.FileParserConfig;
import com.hfwas.devops.fileparser.dto.FileParseResultVO;
import com.hfwas.devops.fileparser.image.ImageCompressionResult;
import com.hfwas.devops.fileparser.image.ImageCompressionService;
import com.hfwas.devops.fileparser.ocr.OcrService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/**
 * 图片 OCR 解析器
 * 使用 RapidOCR 直接对图片文件进行文字识别。
 * 支持格式：JPG、PNG、BMP、TIFF、WEBP 等常见图片格式，以及信创/国产图片格式。
 *
 * <h3>图片压缩预处理</h3>
 * 在 OCR 识别前，可选择性地对图片进行压缩/缩放，以减少 OCR 耗时和内存占用。
 * 压缩失败时自动回退到原图，不中断流程。
 *
 * <h3>信创/国产图片格式支持</h3>
 * <ul>
 *   <li>PCX - 国产系统常用位图格式</li>
 *   <li>JPEG 2000 - 国产文档管理系统常用</li>
 *   <li>WMF/EMF - Office 文档中嵌入的矢量图形</li>
 *   <li>DJVU - 国产数字图书馆扫描文档格式</li>
 *   <li>XBM/XPM - Linux 信创系统图片格式</li>
 *   <li>Netpbm (PBM/PGM/PPM/PNM) - 国产图像处理流程中常用</li>
 *   <li>WBMP - 嵌入式信创设备位图</li>
 *   <li>HEIC/HEIF - 国产移动端生态高压缩率图片</li>
 *   <li>AVIF - 现代图片格式</li>
 * </ul>
 */
@Slf4j
@Component
public class ImageOcrParser implements DocumentParser {

    private static final String[] IMAGE_MIME_PREFIXES = {
            "image/jpeg", "image/png", "image/bmp", "image/tiff", "image/webp",
            // 信创/国产图片格式
            "image/pcx", "image/jp2", "image/jpeg2000",
            "image/wmf", "image/emf",
            "image/vnd.djvu",
            "image/x-xbitmap", "image/x-xpixmap",
            "image/x-portable-bitmap", "image/x-portable-graymap",
            "image/x-portable-pixmap", "image/x-portable-anymap",
            "image/vnd.wap.wbmp",
            "image/heic", "image/heif",
            "image/avif",
    };

    private final OcrService ocrService;
    private final ImageCompressionService compressionService;
    private final FileParserConfig config;

    public ImageOcrParser(OcrService ocrService,
                          ImageCompressionService compressionService,
                          FileParserConfig config) {
        this.ocrService = ocrService;
        this.compressionService = compressionService;
        this.config = config;
    }

    @Override
    public boolean supports(String mimeType) {
        if (mimeType == null) return false;
        String lower = mimeType.toLowerCase();
        for (String prefix : IMAGE_MIME_PREFIXES) {
            if (lower.startsWith(prefix)) return true;
        }
        // 也支持 image/ 通配
        return lower.startsWith("image/");
    }

    @Override
    public FileParseResultVO parse(File file, String fileName) {
        long start = System.currentTimeMillis();

        // 1. 可选：图片压缩预处理
        ImageCompressionResult compressionResult = compressIfEnabled(file, fileName);
        File ocrFile = compressionResult.file();
        File compressedFile = compressionResult.applied() ? ocrFile : null;

        try {
            log.info("Processing image OCR: {}", fileName);

            // 2. 执行 OCR 识别
            OcrService.OcrResult result = ocrService.recognizeWithConfidence(ocrFile);

            long elapsed = System.currentTimeMillis() - start;
            log.info("Image OCR parsed {} in {}ms, text length={}, confidence={}",
                    fileName, elapsed, result.text().length(), result.confidence());

            FileParseResultVO.Content content = FileParseResultVO.Content.builder()
                    .text(result.text())
                    .build();

            List<String> warnings = null;
            if (result.confidence() < 0.5) {
                warnings = List.of("OCR 识别质量较低，建议使用清晰度更高的图片");
            }

            // 3. 构建压缩信息
            FileParseResultVO.CompressionInfo compressionInfo = null;
            if (compressionResult.applied()) {
                compressionInfo = buildCompressionInfo(compressionResult, ocrFile);
            }

            return FileParseResultVO.builder()
                    .success(true)
                    .fileName(fileName)
                    .fileSize(file.length())
                    .mimeType(detectMimeType(fileName))
                    .parseMethod("ocr")
                    .parseTimeMs(elapsed)
                    .content(content)
                    .warnings(warnings)
                    .ocrInfo(FileParseResultVO.OcrInfo.builder()
                            .engine("rapidocr")
                            .pagesProcessed(1)
                            .confidence(result.confidence())
                            .build())
                    .compressionInfo(compressionInfo)
                    .build();

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("Image OCR parse failed for {}: {}", fileName, e.getMessage());
            return FileParseResultVO.builder()
                    .success(false)
                    .fileName(fileName)
                    .fileSize(file.length())
                    .errorMessage("图片 OCR 识别失败: " + e.getMessage())
                    .parseTimeMs(elapsed)
                    .build();
        } finally {
            // 4. 清理压缩临时文件
            if (compressedFile != null) {
                try {
                    Files.deleteIfExists(compressedFile.toPath());
                } catch (IOException e) {
                    log.warn("Failed to delete compressed temp file: {}", compressedFile.getName(), e);
                }
            }
        }
    }

    /**
     * 如果压缩配置启用，对图片进行压缩预处理
     */
    private ImageCompressionResult compressIfEnabled(File file, String fileName) {
        if (!config.getCompression().isEnabled()) {
            return ImageCompressionResult.skipped(file);
        }
        return compressionService.compress(file, fileName);
    }

    /**
     * 构建压缩信息
     */
    private FileParseResultVO.CompressionInfo buildCompressionInfo(ImageCompressionResult result, File compressedFile) {
        return FileParseResultVO.CompressionInfo.builder()
                .originalSize(result.originalSize())
                .compressedSize(result.compressedSize())
                .compressionRatio(result.ratio())
                .quality(config.getCompression().getQuality())
                .originalWidth(result.originalWidth())
                .originalHeight(result.originalHeight())
                .compressedWidth(result.compressedWidth())
                .compressedHeight(result.compressedHeight())
                .build();
    }

    private String detectMimeType(String fileName) {
        if (fileName == null) return "image/unknown";
        String lower = fileName.toLowerCase();
        // 标准图片格式
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".bmp")) return "image/bmp";
        if (lower.endsWith(".tiff") || lower.endsWith(".tif")) return "image/tiff";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".ico")) return "image/x-icon";
        // 信创/国产图片格式
        if (lower.endsWith(".pcx")) return "image/pcx";
        if (lower.endsWith(".jp2")) return "image/jp2";
        if (lower.endsWith(".j2k") || lower.endsWith(".jpf")) return "image/jpeg2000";
        if (lower.endsWith(".wmf")) return "image/wmf";
        if (lower.endsWith(".emf")) return "image/emf";
        if (lower.endsWith(".djvu") || lower.endsWith(".djv")) return "image/vnd.djvu";
        if (lower.endsWith(".xbm")) return "image/x-xbitmap";
        if (lower.endsWith(".xpm")) return "image/x-xpixmap";
        if (lower.endsWith(".pbm")) return "image/x-portable-bitmap";
        if (lower.endsWith(".pgm")) return "image/x-portable-graymap";
        if (lower.endsWith(".ppm")) return "image/x-portable-pixmap";
        if (lower.endsWith(".pnm")) return "image/x-portable-anymap";
        if (lower.endsWith(".wbmp")) return "image/vnd.wap.wbmp";
        if (lower.endsWith(".heic")) return "image/heic";
        if (lower.endsWith(".heif")) return "image/heif";
        if (lower.endsWith(".avif")) return "image/avif";
        return "image/unknown";
    }
}