package com.hfwas.devops.fileparser.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文件解析配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "file-parser")
public class FileParserConfig {

    /** 临时文件存储目录 */
    private String uploadDir = "./data/uploads";

    /** 单文件大小限制（字节），默认 50MB */
    private long maxFileSize = 50 * 1024 * 1024;

    /** 单次请求总大小限制（字节），默认 200MB */
    private long maxTotalSize = 200 * 1024 * 1024;

    /** 临时文件清理间隔（小时），默认 24h */
    private int cleanupHours = 24;

    /** OCR 配置 */
    private OcrConfig ocr = new OcrConfig();

    @Data
    public static class OcrConfig {
        /** 是否启用 OCR */
        private boolean enabled = true;

        /** OCR 语言 */
        private String lang = "chi_sim+eng";

        /** 是否启用图片预处理 */
        private boolean preprocessing = true;

        /** 是否缓存 OCR 结果 */
        private boolean cacheResults = true;

        /** 并行 OCR 页数 */
        private int parallelPages = 4;
    }
}