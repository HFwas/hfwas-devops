package com.hfwas.devops.fileparser.parser;

import com.hfwas.devops.fileparser.config.FileParserConfig;
import com.hfwas.devops.fileparser.dto.FileParseResultVO;
import com.hfwas.devops.fileparser.image.ImageCompressionService;
import com.hfwas.devops.fileparser.ocr.OcrService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

/**
 * 图片 OCR 解析器
 * 使用 PP-OCRv6 对图片文件进行文字识别。
 * 支持格式：JPG、PNG、BMP、TIFF、WEBP 等常见图片格式，以及信创/国产图片格式。
 *
 * <p>OCR 始终使用原图。不要在识别前做 1920 JPEG 压缩，手机拍屏/终端小字会被打糊。
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
    @SuppressWarnings("unused")
    private final ImageCompressionService compressionService;
    @SuppressWarnings("unused")
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

        try {
            log.info("Processing image OCR: {}", fileName);

            // OCR 必须走原图。压缩到 1920 JPEG 会把屏幕截图里的终端小字打糊。
            OcrService.OcrResult result = ocrService.recognizeWithConfidence(file);

            long elapsed = System.currentTimeMillis() - start;
            log.info("Image OCR parsed {} in {}ms, text length={}, confidence={}",
                    fileName, elapsed, result.text().length(), result.confidence());

            FileParseResultVO.Content content = FileParseResultVO.Content.builder()
                    .text(result.text())
                    .build();

            List<String> warnings = null;
            if (result.confidence() < 0.3) {
                warnings = List.of("OCR 识别质量较低，建议使用清晰度更高的图片");
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
                            .engine("ppocrv6")
                            .pagesProcessed(1)
                            .confidence(result.confidence())
                            .build())
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
        }
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