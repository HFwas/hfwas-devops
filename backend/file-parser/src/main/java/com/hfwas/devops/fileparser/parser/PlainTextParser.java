package com.hfwas.devops.fileparser.parser;

import cn.hutool.core.util.StrUtil;
import com.hfwas.devops.fileparser.config.FileParserConfig;
import com.hfwas.devops.fileparser.dto.FileParseResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * 纯文本解析器
 * 支持格式：TXT、CSV、MD 等纯文本文件。
 * 自动检测文件编码（UTF-8、GBK、ISO-8859-1 等）。
 */
@Slf4j
@Component
public class PlainTextParser implements DocumentParser {

    private static final String[] SUPPORTED_EXTENSIONS = {".txt", ".csv", ".md", ".log", ".json", ".xml", ".yaml", ".yml", ".properties", ".ini", ".cfg", ".conf"};

    private static final Charset[] DETECT_CHARSETS = {
            StandardCharsets.UTF_8,
            Charset.forName("GBK"),
            Charset.forName("GB2312"),
            Charset.forName("ISO-8859-1"),
            StandardCharsets.UTF_16LE,
            StandardCharsets.UTF_16BE,
    };

    private final FileParserConfig config;

    public PlainTextParser(FileParserConfig config) {
        this.config = config;
    }

    @Override
    public boolean supports(String mimeType) {
        if (mimeType == null) return false;
        String lower = mimeType.toLowerCase();
        return lower.startsWith("text/");
    }

    @Override
    public FileParseResultVO parse(File file, String fileName) {
        long start = System.currentTimeMillis();

        try {
            // 检测文件编码（只读取文件头部采样，而非整个文件）
            Charset detectedCharset = detectCharset(file);
            log.info("Detected charset for {}: {}", fileName, detectedCharset.name());

            String text = Files.readString(file.toPath(), detectedCharset);
            long fileSize = file.length();

            // 检测是否为 CSV（含表头）
            List<FileParseResultVO.TableContent> tables = null;
            if (fileName != null && fileName.toLowerCase().endsWith(".csv")) {
                tables = parseCsv(text);
            }

            long elapsed = System.currentTimeMillis() - start;
            log.info("Plain text parsed {} in {}ms, text length={}, charset={}",
                    fileName, elapsed, text.length(), detectedCharset.name());

            FileParseResultVO.Content content = FileParseResultVO.Content.builder()
                    .text(text)
                    .tables(tables)
                    .build();

            return FileParseResultVO.builder()
                    .success(true)
                    .fileName(fileName)
                    .fileSize(fileSize)
                    .mimeType(detectMimeType(fileName))
                    .parseMethod("plain")
                    .parseTimeMs(elapsed)
                    .content(content)
                    .build();

        } catch (IOException e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("Plain text parse failed for {}: {}", fileName, e.getMessage());
            return FileParseResultVO.builder()
                    .success(false)
                    .fileName(fileName)
                    .fileSize(file.length())
                    .errorMessage("文本解析失败: " + e.getMessage())
                    .parseTimeMs(elapsed)
                    .build();
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("Plain text parse failed for {}: {}", fileName, e.getMessage());
            return FileParseResultVO.builder()
                    .success(false)
                    .fileName(fileName)
                    .fileSize(file.length())
                    .errorMessage("文本解析失败: " + e.getMessage())
                    .parseTimeMs(elapsed)
                    .build();
        }
    }

    /**
     * 检测文件编码
     * 只读取文件头部采样字节，避免加载整个文件到内存。
     */
    private Charset detectCharset(File file) throws IOException {
        int sampleSize = config.getParser().getCharsetDetectionSampleSize();
        byte[] bytes = readFileHead(file, sampleSize);
        if (bytes.length == 0) {
            return StandardCharsets.UTF_8;
        }

        // 尝试 BOM 检测
        if (bytes.length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
            return StandardCharsets.UTF_8;
        }
        if (bytes.length >= 2 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE) {
            return StandardCharsets.UTF_16LE;
        }
        if (bytes.length >= 2 && bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF) {
            return StandardCharsets.UTF_16BE;
        }

        // 尝试各编码解码，选择最合适的
        for (Charset charset : DETECT_CHARSETS) {
            String decoded = new String(bytes, charset);
            // 检查是否包含乱码字符（ Replacement 字符）
            if (!decoded.contains("�")) {
                // 对于 GBK 和 UTF-8，进一步验证
                if (charset.equals(StandardCharsets.UTF_8)) {
                    // 验证是否为合法的 UTF-8 序列
                    if (isValidUtf8(bytes)) {
                        return charset;
                    }
                } else {
                    return charset;
                }
            }
        }

        return StandardCharsets.UTF_8;
    }

    /**
     * 读取文件头部的采样字节，避免加载整个文件。
     * UTF-16 编码的所有字符都分布在文件头部，采样 4KB 足够检测编码。
     */
    private byte[] readFileHead(File file, int sampleSize) throws IOException {
        long fileLen = file.length();
        if (fileLen == 0) {
            return new byte[0];
        }
        int readSize = (int) Math.min(fileLen, sampleSize);
        byte[] buffer = new byte[readSize];
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.readFully(buffer);
        }
        return buffer;
    }

    /**
     * 简单验证是否为合法的 UTF-8 字节序列
     */
    private boolean isValidUtf8(byte[] bytes) {
        int i = 0;
        while (i < bytes.length) {
            int b = bytes[i] & 0xFF;
            if (b < 0x80) {
                i++;
            } else if (b < 0xC0) {
                // 不应出现在多字节序列起始位置
                return false;
            } else if (b < 0xE0) {
                if (i + 1 >= bytes.length) return false;
                if ((bytes[i + 1] & 0xC0) != 0x80) return false;
                i += 2;
            } else if (b < 0xF0) {
                if (i + 2 >= bytes.length) return false;
                if ((bytes[i + 1] & 0xC0) != 0x80) return false;
                if ((bytes[i + 2] & 0xC0) != 0x80) return false;
                i += 3;
            } else if (b < 0xF8) {
                if (i + 3 >= bytes.length) return false;
                if ((bytes[i + 1] & 0xC0) != 0x80) return false;
                if ((bytes[i + 2] & 0xC0) != 0x80) return false;
                if ((bytes[i + 3] & 0xC0) != 0x80) return false;
                i += 4;
            } else {
                return false;
            }
        }
        return true;
    }

    /**
     * 解析 CSV 格式为表格数据
     */
    private List<FileParseResultVO.TableContent> parseCsv(String text) {
        String[] lines = text.split("\n");
        if (lines.length < 1) return null;

        List<List<String>> rows = new java.util.ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            // 简单 CSV 解析（逗号分隔，不考虑引号转义）
            List<String> cells = java.util.Arrays.asList(trimmed.split(","));
            rows.add(cells);
        }

        if (rows.isEmpty()) return null;

        return List.of(FileParseResultVO.TableContent.builder()
                .sheetName("CSV")
                .rows(rows)
                .build());
    }

    private String detectMimeType(String fileName) {
        if (fileName == null) return "text/plain";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".csv")) return "text/csv";
        if (lower.endsWith(".md")) return "text/markdown";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".xml")) return "application/xml";
        if (lower.endsWith(".yaml") || lower.endsWith(".yml")) return "text/yaml";
        return "text/plain";
    }
}