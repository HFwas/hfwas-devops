package com.hfwas.devops.fileparser.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

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

    /** 临时文件清理间隔（小时），默认 1h */
    private int cleanupHours = 1;

    /** 解析器通用配置 */
    private ParserConfig parser = new ParserConfig();

    /** OCR 配置 */
    private OcrConfig ocr = new OcrConfig();

    /** Tika 文档解析配置 */
    private TikaConfig tika = new TikaConfig();

    /** 扫描版 PDF 解析配置 */
    private ScannedPdfConfig scannedPdf = new ScannedPdfConfig();

    /** MIME 类型映射配置 */
    private MimeConfig mime = new MimeConfig();

    @Data
    public static class ParserConfig {
        /**
         * 字符集检测取样字节数，默认 4KB。
         * 检测编码时只需读取文件头部少量字节，避免加载整个文件。
         */
        private int charsetDetectionSampleSize = 4096;
    }

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

        /**
         * 最大并发 OCR 推理数，默认 2。
         * ONNX Runtime 推理在堆外内存（Native Memory）中执行，
         * 并发数过高会导致堆外内存膨胀，引发 OOM。
         */
        private int maxConcurrent = 2;

        /**
         * OCR 模型版本：v4 或 v6
         * v4 = PP-OCRv4 (RapidOCR)
         * v6 = PP-OCRv6（常驻 Python worker）
         */
        private String modelVersion = "v4";

        /** V6 Python 解释器 */
        private String pythonPath = "python3";

        /** V6 worker 脚本；空则从 classpath 解出 ocr_worker.py */
        private String pythonWorker = "";

        /** V6 检测模型目录；空则使用 worker 旁的 resources/ocr/models */
        private String pythonDetModelDir = "";

        /** V6 识别模型目录；空则使用 worker 旁的 resources/ocr/models */
        private String pythonRecModelDir = "";

        /** 单次识别超时（毫秒）；worker 首次就绪另有至少 60s */
        private long pythonTimeoutMs = 30_000;
    }

    @Data
    public static class TikaConfig {
        /**
         * Tika 提取文本最大长度（字符数），默认 100MB。
         * 防止超大文档解析出超长字符串挤占堆内存。
         * BodyContentHandler 使用此值限制写入，超出时抛出异常。
         * 可通过环境变量 {@code FILE_PARSER_TIKA_MAX_TEXT_LENGTH} 或配置文件
         * {@code file-parser.tika.max-text-length} 自定义。
         */
        private int maxTextLength = 100 * 1024 * 1024;

        /**
         * 文本清洗策略，默认 basic。
         * <ul>
         *   <li>{@code none}: 不进行任何清洗，保留 Tika 原始输出</li>
         *   <li>{@code basic}: 仅 Word/PPT — 去除 Tika 抽出的多余空白行、嵌入图文件名</li>
         *   <li>{@code docx}: DOCX 文档深度清洗 — 在 basic 基础上，额外去除
         *       内部协同批注（{@code @xxx}）、设计分割线、图片文件名清单、
         *       研发内部备注、占位文本等，适用于正式交付场景</li>
         * </ul>
         */
        private String cleanupStrategy = "basic";
    }

    @Data
    public static class ScannedPdfConfig {
        /**
         * 最大处理页数，默认 50 页。
         * 超过此页数的 PDF 只处理前 N 页。
         */
        private int maxPages = 50;

        /**
         * 渲染图片最大像素尺寸（宽高中较大值），默认 2048px。
         * 超过此值会自动缩放，防止超大页面渲染出巨幅 BufferedImage。
         */
        private int maxImageDimension = 2048;
    }

    @Data
    public static class MimeConfig {
        /**
         * 扩展名到 MIME 类型的映射覆盖。
         * 用于自定义/国产格式的 MIME 类型映射，优先级高于内置映射。
         * 格式: { ".ext": "application/xxx" }
         */
        private Map<String, String> extensionMappings;

        /**
         * 额外支持的 MIME 类型前缀列表。
         * 用于 TikaDocumentParser 等解析器匹配非标准 MIME 类型。
         */
        private List<String> additionalMimePrefixes;
    }
}