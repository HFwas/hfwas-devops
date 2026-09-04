package com.hfwas.devops.fileparser.service;

import com.hfwas.devops.fileparser.config.FileParserConfig;
import com.hfwas.devops.fileparser.dto.FileParseResultVO;
import com.hfwas.devops.fileparser.parser.DocumentParser;
import com.hfwas.devops.fileparser.parser.ImageOcrParser;
import com.hfwas.devops.fileparser.parser.PlainTextParser;
import com.hfwas.devops.fileparser.parser.ScannedPdfParser;
import com.hfwas.devops.fileparser.parser.TikaDocumentParser;
import com.hfwas.devops.fileparser.parser.TikaHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件解析编排服务
 * 负责文件类型检测、解析器路由、结果组装。
 *
 * <h3>MIME 类型检测优先级</h3>
 * <ol>
 *   <li>Apache Tika 内容检测（基于文件内容特征）</li>
 *   <li>扩展名映射（内置映射 + 配置覆盖 + WPS/信创特殊格式）</li>
 *   <li>回退到 {@code application/octet-stream}</li>
 * </ol>
 *
 * <h3>信创与 WPS 特殊格式支持</h3>
 * <ul>
 *   <li>WPS Office: .wps/.wpt → WPS Writer, .et/.ett → WPS Spreadsheet, .dps/.dpt → WPS Presentation</li>
 *   <li>国产文档格式: .ofd (开放版式文档), .uof (统一办公文档), .ceb (中国电子公文)</li>
 *   <li>国产图片格式: .pcx, .jp2/.j2k/.jpf (JPEG 2000), .wmf/.emf,
 *       .djvu/.djv, .xbm/.xpm, .pbm/.pgm/.ppm/.pnm (Netpbm),
 *       .wbmp, .heic/.heif, .avif</li>
 * </ul>
 */
@Slf4j
@Service
public class FileParserService {

    /**
     * 扩展名到 MIME 类型的映射表。
     * 使用 LinkedHashMap 保证有序，长扩展名优先（如 .docx 在 .doc 之前）。
     */
    private static final Map<String, String> EXTENSION_MIME_MAP = buildExtensionMimeMap();

    private final Tika tika;
    private final FileStorageService fileStorageService;
    private final List<DocumentParser> parsers;
    private final FileParserConfig config;

    public FileParserService(FileStorageService fileStorageService,
                             TikaDocumentParser tikaParser,
                             ScannedPdfParser scannedPdfParser,
                             ImageOcrParser imageOcrParser,
                             PlainTextParser plainTextParser,
                             FileParserConfig config) {
        this.tika = TikaHolder.tika();
        this.fileStorageService = fileStorageService;
        this.config = config;
        // 解析器优先级顺序：Tika（含 DOCX/PPTX/XLSX/WPS/文本PDF） > 扫描PDF > 图片OCR > 纯文本
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

            // 3. 特殊处理：OFD 格式（暂不支持解析）
            if ("application/ofd".equals(mimeType) || "application/vnd.ofd".equals(mimeType)) {
                long elapsed = System.currentTimeMillis() - start;
                log.warn("OFD format not yet supported: {}", multipartFile.getOriginalFilename());
                return FileParseResultVO.builder()
                        .success(false)
                        .fileName(multipartFile.getOriginalFilename())
                        .fileSize(multipartFile.getSize())
                        .mimeType(mimeType)
                        .errorMessage("OFD 格式暂不支持解析，请将文件转换为 PDF 后重试")
                        .parseTimeMs(elapsed)
                        .build();
            }

            // 4. 路由到对应解析器
            FileParseResultVO result = null;
            for (DocumentParser parser : parsers) {
                if (parser.supports(mimeType)) {
                    log.info("Routing to parser: {} for {}", parser.getClass().getSimpleName(),
                            multipartFile.getOriginalFilename());
                    result = parser.parse(tempFile, multipartFile.getOriginalFilename());
                    break;
                }
            }

            // 5. 如果没有匹配的解析器
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
            // 6. 清理临时文件
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
            // 优先使用 Tika 内容检测
            String mimeType = tika.detect(file);
            if (mimeType != null && !"application/octet-stream".equals(mimeType)) {
                return mimeType;
            }
        } catch (Exception e) {
            log.warn("Tika MIME detection failed, falling back to extension: {}", e.getMessage());
        }

        // 回退：根据文件扩展名判断
        if (originalFileName != null) {
            return detectByExtension(originalFileName);
        }

        return "application/octet-stream";
    }

    /**
     * 根据文件扩展名检测 MIME 类型。
     * 支持标准 Office 格式、WPS Office 特殊格式、国产信创格式。
     */
    private String detectByExtension(String fileName) {
        String lower = fileName.toLowerCase();

        // 1. 优先使用配置的扩展名映射覆盖
        Map<String, String> configMappings = config.getMime().getExtensionMappings();
        if (configMappings != null && !configMappings.isEmpty()) {
            for (Map.Entry<String, String> entry : configMappings.entrySet()) {
                if (lower.endsWith(entry.getKey().toLowerCase())) {
                    return entry.getValue();
                }
            }
        }

        // 2. 使用内置映射表
        for (Map.Entry<String, String> entry : EXTENSION_MIME_MAP.entrySet()) {
            if (lower.endsWith(entry.getKey())) {
                return entry.getValue();
            }
        }

        return "application/octet-stream";
    }

    // ========== MIME 类型映射表 ==========

    private static Map<String, String> buildExtensionMimeMap() {
        Map<String, String> map = new LinkedHashMap<>();

        // ---- Microsoft Office 格式 ----
        // Word（长扩展名在前，避免 .doc 误匹配 .docx）
        map.put(".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        map.put(".docm", "application/vnd.ms-word.document.macroEnabled.12");
        map.put(".dotx", "application/vnd.openxmlformats-officedocument.wordprocessingml.template");
        map.put(".doc", "application/msword");
        map.put(".dot", "application/msword");

        // Excel
        map.put(".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        map.put(".xlsm", "application/vnd.ms-excel.sheet.macroEnabled.12");
        map.put(".xltx", "application/vnd.openxmlformats-officedocument.spreadsheetml.template");
        map.put(".xls", "application/vnd.ms-excel");
        map.put(".xlt", "application/vnd.ms-excel");
        map.put(".csv", "text/csv");

        // PowerPoint
        map.put(".pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        map.put(".pptm", "application/vnd.ms-powerpoint.presentation.macroEnabled.12");
        map.put(".potx", "application/vnd.openxmlformats-officedocument.presentationml.template");
        map.put(".ppt", "application/vnd.ms-powerpoint");
        map.put(".pot", "application/vnd.ms-powerpoint");

        // ---- WPS Office 特殊格式 ----
        // WPS Writer（对标 Word）
        map.put(".wps", "application/wps-office.wps");
        map.put(".wpt", "application/wps-office.wpt");

        // WPS Spreadsheet（对标 Excel）
        map.put(".et", "application/wps-office.et");
        map.put(".ett", "application/wps-office.ett");

        // WPS Presentation（对标 PowerPoint）
        map.put(".dps", "application/wps-office.dps");
        map.put(".dpt", "application/wps-office.dpt");

        // ---- 国产信创格式 ----
        // 开放版式文档（GB/T 33190-2016）
        map.put(".ofd", "application/ofd");
        // 统一办公文档格式
        map.put(".uof", "application/uof");
        // 中国电子公文
        map.put(".ceb", "application/ceb");
        // 版式电子公文
        map.put(".cebx", "application/cebx");

        // ---- PDF ----
        map.put(".pdf", "application/pdf");

        // ---- 图片 ----
        map.put(".jpg", "image/jpeg");
        map.put(".jpeg", "image/jpeg");
        map.put(".png", "image/png");
        map.put(".bmp", "image/bmp");
        map.put(".tiff", "image/tiff");
        map.put(".tif", "image/tiff");
        map.put(".webp", "image/webp");
        map.put(".gif", "image/gif");
        map.put(".svg", "image/svg+xml");
        map.put(".ico", "image/x-icon");

        // ---- 信创/国产图片格式 ----
        // PCX 图片格式（国产系统/老旧系统中常用）
        map.put(".pcx", "image/pcx");
        // JPEG 2000（国产文档管理系统常用）
        map.put(".jp2", "image/jp2");
        map.put(".j2k", "image/jpeg2000");
        map.put(".jpf", "image/jpeg2000");
        // WMF/EMF 矢量图（Office 文档中嵌入的矢量图形）
        map.put(".wmf", "image/wmf");
        map.put(".emf", "image/emf");
        // DJVU 扫描文档格式（国产数字图书馆系统常用）
        map.put(".djvu", "image/vnd.djvu");
        map.put(".djv", "image/vnd.djvu");
        // Linux 信创系统图片格式（X Window System）
        map.put(".xbm", "image/x-xbitmap");
        map.put(".xpm", "image/x-xpixmap");
        // Netpbm 格式（国产图像处理流程中常用）
        map.put(".pbm", "image/x-portable-bitmap");
        map.put(".pgm", "image/x-portable-graymap");
        map.put(".ppm", "image/x-portable-pixmap");
        map.put(".pnm", "image/x-portable-anymap");
        // WBMP 无线位图（嵌入式信创设备）
        map.put(".wbmp", "image/vnd.wap.wbmp");
        // HEIC/HEIF 高压缩率图片格式（国产移动端生态常用）
        map.put(".heic", "image/heic");
        map.put(".heif", "image/heif");
        // AVIF 现代图片格式
        map.put(".avif", "image/avif");

        // ---- 纯文本 / 代码 ----
        map.put(".txt", "text/plain");
        map.put(".md", "text/markdown");
        map.put(".json", "application/json");
        map.put(".xml", "application/xml");
        map.put(".yaml", "text/yaml");
        map.put(".yml", "text/yaml");
        map.put(".log", "text/plain");
        map.put(".properties", "text/plain");
        map.put(".ini", "text/plain");
        map.put(".cfg", "text/plain");
        map.put(".conf", "text/plain");
        map.put(".sql", "text/plain");
        map.put(".sh", "text/plain");
        map.put(".bat", "text/plain");
        map.put(".py", "text/plain");
        map.put(".js", "text/plain");
        map.put(".ts", "text/plain");
        map.put(".java", "text/plain");
        map.put(".html", "text/html");
        map.put(".htm", "text/html");
        map.put(".css", "text/css");

        // ---- RTF ----
        map.put(".rtf", "application/rtf");

        return map;
    }
}