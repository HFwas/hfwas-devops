package com.hfwas.devops.fileparser.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 文件解析结果 VO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileParseResultVO {
    /** 是否成功 */
    private boolean success;
    /** 错误信息 */
    private String errorMessage;

    /** 文件名 */
    private String fileName;
    /** 文件大小（字节） */
    private long fileSize;
    /** MIME 类型 */
    private String mimeType;
    /** 解析方式: tika | ocr | plain */
    private String parseMethod;
    /** 解析耗时（毫秒） */
    private long parseTimeMs;

    /** 解析内容 */
    private Content content;

    /** 警告信息 */
    private List<String> warnings;

    /** OCR 信息 */
    private OcrInfo ocrInfo;

    /** 图片压缩信息（仅图片 OCR 解析时存在） */
    private CompressionInfo compressionInfo;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Content {
        /** 全文文本 */
        private String text;
        /** 分页内容（PDF 专用） */
        private List<PageContent> pages;
        /** 表格数据（Excel 专用） */
        private List<TableContent> tables;
        /** 幻灯片内容（PPTX 专用） */
        private List<SlideContent> slides;
        /** 元数据 */
        private Map<String, String> metadata;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageContent {
        private int pageNum;
        private String text;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableContent {
        private String sheetName;
        private List<List<String>> rows;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlideContent {
        private int slideNum;
        private String text;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OcrInfo {
        private String engine;
        private int pagesProcessed;
        private double confidence;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompressionInfo {
        /** 原始文件大小（字节） */
        private long originalSize;
        /** 压缩后文件大小（字节） */
        private long compressedSize;
        /** 压缩比 (original - compressed) / original */
        private double compressionRatio;
        /** 压缩质量配置 */
        private float quality;
        /** 原始图片宽度（像素） */
        private int originalWidth;
        /** 原始图片高度（像素） */
        private int originalHeight;
        /** 压缩后图片宽度（像素） */
        private int compressedWidth;
        /** 压缩后图片高度（像素） */
        private int compressedHeight;
    }
}