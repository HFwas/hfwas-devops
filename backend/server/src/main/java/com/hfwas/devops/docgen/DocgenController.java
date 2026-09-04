package com.hfwas.devops.docgen;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.common.docgen.DocgenUtil;
import com.hfwas.devops.user.operlog.annotation.OperLog;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/docgen")
@RequiredArgsConstructor
public class DocgenController {

    private final DocgenUtil docgenUtil;

    @Value("${docgen.output-dir:../../files}")
    private String defaultOutputDir;

    private static final Map<String, String> EXT_MAP = Map.of(
            "word", ".docx", "excel", ".xlsx", "ppt", ".pptx",
            "image", ".png", "md", ".md", "pdf", ".pdf"
    );
    private static final DateTimeFormatter DATE_PREFIX = DateTimeFormatter.BASIC_ISO_DATE;

    @OperLog(module = "docgen", action = "generate", bizType = "document", summary = "生成文档")
    @PostMapping(value = "/generate", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> generate(@RequestBody DocgenRequest request) {
        byte[] bytes = docgenUtil.generate(
                request.getFormat(),
                request.getData(),
                request.getFilename()
        );

        String contentType = switch (request.getFormat()) {
            case "word" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "excel" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "image" -> "image/png";
            case "md" -> "text/markdown; charset=utf-8";
            case "pdf" -> "application/pdf";
            default -> "application/octet-stream";
        };

        String encodedFilename = URLEncoder.encode(request.getFilename(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedFilename)
                .body(bytes);
    }

    @OperLog(module = "docgen", action = "generate", bizType = "document", summary = "生成文档到目录")
    @PostMapping("/generate-to-dir")
    public BaseResult<Map<String, Object>> generateToDir(@RequestBody DocgenDirRequest request) {
        // 空目录时使用默认目录
        String dir = request.getDirectory();
        if (dir == null || dir.trim().isEmpty()) {
            dir = new File(defaultOutputDir).getAbsolutePath();
        }

        Map<String, Object> data = new HashMap<>(request.getData() != null ? request.getData() : new HashMap<>());
        data.put("output_dir", dir);

        Path dirPath = Paths.get(dir);
        dirPath.toFile().mkdirs();
        String outputPath = dirPath.resolve(request.getFilename()).toString();

        docgenUtil.generateToFile(
                request.getFormat(),
                data,
                outputPath
        );

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("directory", dir);
        result.put("filename", request.getFilename());
        result.put("message", "文件已生成到: " + Paths.get(dir, request.getFilename()));

        return BaseResult.ok(result);
    }

    @OperLog(module = "docgen", action = "batch_generate", bizType = "document", summary = "批量生成文档")
    @PostMapping("/batch-generate")
    public BaseResult<Map<String, Object>> batchGenerate(@RequestBody BatchGenerateRequest request) {
        List<String> formats = request.getFormats();
        List<Long> sizes = request.getSizes();
        int fileCount = request.getFileCount();

        if (formats == null || formats.isEmpty()) {
            return BaseResult.failed(400, "请至少选择一个格式");
        }
        if (sizes == null || sizes.isEmpty()) {
            sizes = List.of(0L);
        }

        // 解析目标目录（空则用默认目录：项目根目录下的 files/）
        String dir = request.getDirectory();
        if (dir == null || dir.trim().isEmpty()) {
            dir = new File(defaultOutputDir).getAbsolutePath();
        }
        Path dirPath = Paths.get(dir);
        dirPath.toFile().mkdirs();

        String baseName = request.getFilename();
        // 去掉扩展名
        if (baseName.contains(".")) {
            baseName = baseName.substring(0, baseName.lastIndexOf('.'));
        }

        List<Map<String, Object>> generatedFiles = new ArrayList<>();
        int total = 0;

        String datePrefix = LocalDate.now().format(DATE_PREFIX);
        for (String fmt : formats) {
            String ext = EXT_MAP.getOrDefault(fmt, ".bin");

            for (long size : sizes) {
                for (int n = 0; n < fileCount; n++) {
                    String fileName = buildOutputFileName(datePrefix, baseName, fmt, ext, size, n, fileCount, request);
                    String outputPath = dirPath.resolve(fileName).toString();

                    Map<String, Object> data = new HashMap<>();
                    data.put("format", fmt);
                    data.put("file_count", fileCount);
                    data.put("file_size", size);
                    if (request.getColumnCount() != null) {
                        data.put("column_count", request.getColumnCount());
                    }
                    if (request.getRowSize() != null) {
                        data.put("row_size", request.getRowSize());
                    }
                    if (request.getRowCount() != null) {
                        data.put("row_count", request.getRowCount());
                    }
                    if (request.getPageCount() != null) {
                        data.put("page_count", request.getPageCount());
                    }
                    if (request.getEncrypt() != null) {
                        data.put("encrypt", request.getEncrypt());
                    }
                    if (request.getPdfPassword() != null && !request.getPdfPassword().isBlank()) {
                        data.put("pdf_password", request.getPdfPassword());
                    }
                    if (request.getEmptyContent() != null) {
                        data.put("empty_content", request.getEmptyContent());
                    }
                    if (request.getEmptyPageCount() != null) {
                        data.put("empty_page_count", request.getEmptyPageCount());
                    }

                    docgenUtil.generateToFile(fmt, data, outputPath);

                    File f = new File(outputPath);
                    Map<String, Object> fileInfo = new HashMap<>();
                    fileInfo.put("filename", fileName);
                    fileInfo.put("size", f.exists() ? f.length() : 0);
                    generatedFiles.add(fileInfo);
                    total++;
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("directory", dir);
        result.put("files", generatedFiles);
        result.put("total", total);
        result.put("message", "已生成 " + total + " 个文件到: " + dir);
        return BaseResult.ok(result);
    }

    /**
     * {日期}_{基础名}[_{大小}][_{页数|空页数}][_空内容][_加密][_{序号}].{ext}
     * 例：20260903_文档_5页_加密.pdf
     */
    private static String buildOutputFileName(
            String datePrefix,
            String baseName,
            String fmt,
            String ext,
            long size,
            int index,
            int fileCount,
            BatchGenerateRequest request
    ) {
        List<String> parts = new ArrayList<>();
        parts.add(datePrefix);
        parts.add(sanitizeBaseName(baseName));
        if (size > 0) {
            parts.add(formatSizeLabel(size));
        }
        if ("pdf".equals(fmt)) {
            if (Boolean.TRUE.equals(request.getEmptyPageCount())) {
                parts.add("空页数");
            } else if (request.getPageCount() != null && request.getPageCount() > 0) {
                parts.add(request.getPageCount() + "页");
            }
            if (Boolean.TRUE.equals(request.getEmptyContent())) {
                parts.add("空内容");
            }
            if (Boolean.TRUE.equals(request.getEncrypt())) {
                parts.add("加密");
            }
        }
        if (fileCount > 1) {
            parts.add(String.valueOf(index + 1));
        }
        return String.join("_", parts) + ext;
    }

    private static String sanitizeBaseName(String name) {
        if (name == null || name.isBlank()) {
            return "文档";
        }
        String cleaned = name.trim().replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        cleaned = cleaned.replaceAll("_+", "_");
        cleaned = cleaned.replaceAll("^_|_$", "");
        return cleaned.isBlank() ? "文档" : cleaned;
    }

    /** 仅用于有目标大小时：102400 → 100KB，1048576 → 1MB */
    private static String formatSizeLabel(long bytes) {
        if (bytes < 1024 * 1024) {
            return (bytes / 1024) + "KB";
        }
        return (bytes / (1024 * 1024)) + "MB";
    }

    @Data
    public static class DocgenRequest {
        private String format;       // word / excel / ppt / image / md / pdf
        private String filename;     // 文件名，如 report.docx
        private Map<String, Object> data;  // 填充数据
    }

    @Data
    public static class DocgenDirRequest {
        private String format;
        private String filename;
        private String directory;    // 目标目录
        private Map<String, Object> data;
    }

    @Data
    public static class BatchGenerateRequest {
        private List<String> formats;    // ["word", "excel"]
        private String filename;         // 基础文件名
        private List<Long> sizes;        // [0, 102400, 1048576]
        private int fileCount = 1;
        private String directory;        // 可选，空则用临时目录
        private Integer columnCount;     // 可选，Excel 列数
        private Integer rowSize;         // 可选，每行数据量（字符数）
        private Integer rowCount;        // 可选，行数覆盖
        private Integer pageCount;       // 可选，PDF 页数
        private Boolean encrypt;         // 可选，PDF 是否加密
        private String pdfPassword;      // 可选，PDF 打开密码
        private Boolean emptyContent;    // 可选，PDF 是否空内容
        private Boolean emptyPageCount;  // 可选，true 时不指定页数
    }
}