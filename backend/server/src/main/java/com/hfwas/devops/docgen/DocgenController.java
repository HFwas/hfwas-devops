package com.hfwas.devops.docgen;

import com.hfwas.devops.common.docgen.DocgenUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@RestController
@RequestMapping("/api/docgen")
@RequiredArgsConstructor
public class DocgenController {

    private final DocgenUtil docgenUtil;

    @Value("${docgen.output-dir:../../files}")
    private String defaultOutputDir;

    // 格式 → 扩展名映射
    private static final Map<String, String> EXT_MAP = Map.of(
            "word", ".docx", "excel", ".xlsx", "ppt", ".pptx",
            "image", ".png", "md", ".md", "pdf", ".pdf"
    );
    // 格式 → 标签映射
    private static final Map<String, String> LABEL_MAP = Map.of(
            "word", "Word", "excel", "Excel", "ppt", "PPT",
            "image", "图片", "md", "MD", "pdf", "PDF"
    );

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

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + request.getFilename() + "\"")
                .body(bytes);
    }

    @PostMapping("/generate-to-dir")
    public ResponseEntity<Map<String, Object>> generateToDir(@RequestBody DocgenDirRequest request) {
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

        return ResponseEntity.ok(result);
    }

    @PostMapping("/batch-generate")
    public ResponseEntity<Map<String, Object>> batchGenerate(@RequestBody BatchGenerateRequest request) {
        List<String> formats = request.getFormats();
        List<Long> sizes = request.getSizes();
        int fileCount = request.getFileCount();

        if (formats == null || formats.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "请至少选择一个格式"));
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

        for (String fmt : formats) {
            String ext = EXT_MAP.getOrDefault(fmt, ".bin");
            String label = LABEL_MAP.getOrDefault(fmt, fmt);

            for (long size : sizes) {
                String sizeLabel = formatSizeLabel(size);

                for (int n = 0; n < fileCount; n++) {
                    String suffix = fileCount > 1 ? "_" + (n + 1) : "";
                    String fileName = label + "_" + sizeLabel + "_" + baseName + suffix + ext;
                    String outputPath = dirPath.resolve(fileName).toString();

                    Map<String, Object> data = new HashMap<>();
                    data.put("format", fmt);
                    data.put("file_count", fileCount);
                    data.put("file_size", size);

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
        return ResponseEntity.ok(result);
    }

    /** 格式化文件大小标签：0 → "不限", 102400 → "100KB", 1048576 → "1MB" */
    private static String formatSizeLabel(long bytes) {
        if (bytes <= 0) return "不限";
        if (bytes < 1024 * 1024) return (bytes / 1024) + "KB";
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
    }
}