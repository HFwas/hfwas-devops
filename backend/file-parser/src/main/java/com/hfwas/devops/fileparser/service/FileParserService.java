package com.hfwas.devops.fileparser.service;

import com.hfwas.devops.fileparser.dto.FileParseResultVO;
import com.hfwas.devops.fileparser.parser.DocumentParser;
import com.hfwas.devops.fileparser.parser.ImageOcrParser;
import com.hfwas.devops.fileparser.parser.ScannedPdfParser;
import com.hfwas.devops.fileparser.parser.TikaDocumentParser;
import com.hfwas.devops.fileparser.parser.PlainTextParser;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 文件解析编排服务
 * 负责文件类型检测、解析器路由、结果组装。
 */
@Slf4j
@Service
public class FileParserService {

    private final Tika tika;
    private final FileStorageService fileStorageService;
    private final List<DocumentParser> parsers;

    public FileParserService(FileStorageService fileStorageService,
                             TikaDocumentParser tikaParser,
                             ScannedPdfParser scannedPdfParser,
                             ImageOcrParser imageOcrParser,
                             PlainTextParser plainTextParser) {
        this.tika = new Tika();
        this.fileStorageService = fileStorageService;
        // 解析器优先级顺序：Tika（含 DOCX/PPTX/XLSX/文本PDF） > 扫描PDF > 图片OCR > 纯文本
        this.parsers = List.of(tikaParser, scannedPdfParser, imageOcrParser, plainTextParser);
    }

    /**
     * 解析上传的文件
     */
    public FileParseResultVO parse(MultipartFile multipartFile, String options) {
        long start = System.currentTimeMillis();

        // 1. 保存临时文件
        File tempFile = null;
        try {
            tempFile = fileStorageService.save(multipartFile);

            // 2. 检测文件 MIME 类型
            String mimeType = detectMimeType(tempFile, multipartFile.getOriginalFilename());
            log.info("Detected MIME type: {} for file: {}", mimeType, multipartFile.getOriginalFilename());

            // 3. 路由到对应解析器
            FileParseResultVO result = null;
            for (DocumentParser parser : parsers) {
                if (parser.supports(mimeType)) {
                    log.info("Routing to parser: {} for {}", parser.getClass().getSimpleName(),
                            multipartFile.getOriginalFilename());
                    result = parser.parse(tempFile, multipartFile.getOriginalFilename());
                    break;
                }
            }

            // 4. 如果没有匹配的解析器
            if (result == null) {
                long elapsed = System.currentTimeMillis() - start;
                log.warn("No parser found for MIME type: {} (file: {})", mimeType,
                        multipartFile.getOriginalFilename());
                return FileParseResultVO.builder()
                        .success(false)
                        .fileName(multipartFile.getOriginalFilename())
                        .fileSize(multipartFile.getSize())
                        .mimeType(mimeType)
                        .errorMessage("不支持的文件格式: " + mimeType)
                        .parseTimeMs(elapsed)
                        .build();
            }

            return result;

        } catch (IOException e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("File parse failed for {}: {}", multipartFile.getOriginalFilename(), e.getMessage());
            return FileParseResultVO.builder()
                    .success(false)
                    .fileName(multipartFile.getOriginalFilename())
                    .fileSize(multipartFile.getSize())
                    .errorMessage("文件解析失败: " + e.getMessage())
                    .parseTimeMs(elapsed)
                    .build();
        } finally {
            // 5. 清理临时文件
            if (tempFile != null) {
                fileStorageService.delete(tempFile);
            }
        }
    }

    /**
     * 检测文件 MIME 类型
     */
    private String detectMimeType(File file, String originalFileName) {
        try {
            // 优先使用 Tika 自动检测
            String mimeType = tika.detect(file);
            if (mimeType != null && !mimeType.equals("application/octet-stream")) {
                return mimeType;
            }
        } catch (IOException e) {
            log.warn("Tika MIME detection failed, falling back to extension: {}", e.getMessage());
        }

        // 回退：根据文件扩展名判断
        if (originalFileName != null) {
            return detectByExtension(originalFileName);
        }

        return "application/octet-stream";
    }

    /**
     * 根据文件扩展名检测 MIME 类型
     */
    private String detectByExtension(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if (lower.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".ppt")) return "application/vnd.ms-powerpoint";
        if (lower.endsWith(".xls")) return "application/vnd.ms-excel";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".bmp")) return "image/bmp";
        if (lower.endsWith(".tiff") || lower.endsWith(".tif")) return "image/tiff";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".txt")) return "text/plain";
        if (lower.endsWith(".csv")) return "text/csv";
        if (lower.endsWith(".md")) return "text/markdown";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".xml")) return "application/xml";
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) return "text/yaml";
        return "application/octet-stream";
    }
}