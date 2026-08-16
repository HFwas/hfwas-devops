package com.hfwas.devops.fileparser.parser;

import com.hfwas.devops.fileparser.dto.FileParseResultVO;
import com.hfwas.devops.fileparser.ocr.OcrService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

/**
 * 图片 OCR 解析器
 * 使用 RapidOCR 直接对图片文件进行文字识别。
 * 支持格式：JPG、PNG、BMP、TIFF、WEBP 等常见图片格式。
 */
@Slf4j
@Component
public class ImageOcrParser implements DocumentParser {

    private static final String[] IMAGE_MIME_PREFIXES = {
            "image/jpeg", "image/png", "image/bmp", "image/tiff", "image/webp"
    };

    private final OcrService ocrService;

    public ImageOcrParser(OcrService ocrService) {
        this.ocrService = ocrService;
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

            // 执行 OCR 识别
            OcrService.OcrResult result = ocrService.recognizeWithConfidence(file);

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
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".bmp")) return "image/bmp";
        if (lower.endsWith(".tiff") || lower.endsWith(".tif")) return "image/tiff";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/unknown";
    }
}