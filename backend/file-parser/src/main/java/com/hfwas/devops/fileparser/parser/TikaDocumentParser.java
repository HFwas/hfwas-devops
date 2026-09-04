package com.hfwas.devops.fileparser.parser;

import cn.hutool.core.util.StrUtil;
import com.hfwas.devops.fileparser.config.FileParserConfig;
import com.hfwas.devops.fileparser.dto.FileParseResultVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.ToMarkdownContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Tika 文档解析器
 * 支持格式：DOCX、PPTX、XLSX、文本 PDF、WPS Office 格式
 * 使用 Apache Tika 自动检测并解析
 *
 * <h3>WPS Office 支持</h3>
 * WPS Office 格式（.wps/.et/.dps）本质上是 OOXML（ZIP + XML），
 * Tika 可提取其文本内容，兼容性已验证。
 */
@Slf4j
@Component
public class TikaDocumentParser implements DocumentParser {

    private static final String[] SUPPORTED_TYPES = {
            "application/vnd.openxmlformats-officedocument",
            "application/vnd.ms-",
            "application/msword",
            "application/pdf",
            "text/",
            "application/wps-office.",
            "application/uof",
            "application/x-msword",
            "application/csv",
            "application/msexcel",
            "application/x-msexcel",
            "application/x-excel",
            "application/excel",
            "application/mspowerpoint",
            "application/powerpoint",
            "application/x-mspowerpoint",
            "application/markdown",
            "application/vnd.ofd",
            "audio/",
            "application/x-rar",
            "application/rar",
    };

    // ========== 文本清洗正则常量（预编译提升性能） ==========

    /** 行尾空白字符 */
    private static final Pattern TRAILING_WS = Pattern.compile("[ \\t]+\\n");
    /** 行首空白字符 */
    private static final Pattern LEADING_WS = Pattern.compile("\\n[ \\t]+");
    /** 纯空白行 */
    private static final Pattern BLANK_LINE = Pattern.compile("\\n[ \\t]+\\n");
    /** 连续 3+ 空行 */
    private static final Pattern MANY_NEWLINES = Pattern.compile("\\n{3,}");
    /** 内部协同批注 @xxx */
    private static final Pattern AT_MENTION = Pattern.compile("@\\S+[ \\t]*\\S*");
    /** 研发备注 to研发/开发/产品 */
    private static final Pattern TO_DEV = Pattern.compile("^[ 　]*to(研发|开发|产品)[：:].*$", Pattern.MULTILINE);
    /** 待研发/开发/产品确认 */
    private static final Pattern PENDING_DEV = Pattern.compile("待(研发|开发|产品)确认.*?(?:\n|$)");
    /** 待确认括号内容 */
    private static final Pattern PENDING_PAREN = Pattern.compile("\\(待确认[^)]*\\)");
    /** 待确认句尾 */
    private static final Pattern PENDING_END = Pattern.compile("待确认[。，]");
    /** 占位文本 xxx岗位/选择/菜单/功能 */
    private static final Pattern PLACEHOLDER = Pattern.compile("^xxx(岗位|选择|菜单|功能).*$", Pattern.MULTILINE);
    /** 后续待拆分占位 */
    private static final Pattern PLACEHOLDER_PENDING = Pattern.compile("【后续待[^】]*】");
    /** 单独一行数字（页码残留） */
    private static final Pattern PAGE_NUMBER = Pattern.compile("^\\d{1,4}$", Pattern.MULTILINE);
    /** 孤立逗号 */
    private static final Pattern LONELY_COMMA = Pattern.compile("[，,]\\s*\n");

    /**
     * PDFBox 堆内窗口。Tika 默认 512MB，和抽出文本、Spring 同处 1g 堆时 G1 会在抽取中反复回收。
     * 超出部分走 scratch 文件，不加大 {@code -Xmx}。
     */
    private static final long PDF_MAX_MAIN_MEMORY_BYTES = 16L * 1024 * 1024;

    /** 超过此长度不再 {@code split("\\n")} 打结构日志，避免 15MB 文本再复制一份。 */
    private static final int CONTENT_STATS_MAX_CHARS = 100_000;

    private final FileParserConfig config;

    public TikaDocumentParser(FileParserConfig config) {
        this.config = config;
    }

    @Override
    public boolean supports(String mimeType) {
        if (mimeType == null) return false;
        String lower = mimeType.toLowerCase();
        // 排除图片类型（由 ImageOcrParser 处理）
        if (lower.startsWith("image/")) return false;
        for (String prefix : SUPPORTED_TYPES) {
            if (lower.startsWith(prefix)) return true;
        }
        // 检查配置中的额外 MIME 类型前缀
        List<String> additionalPrefixes = config.getMime().getAdditionalMimePrefixes();
        if (additionalPrefixes != null) {
            for (String prefix : additionalPrefixes) {
                if (lower.startsWith(prefix.toLowerCase())) return true;
            }
        }
        return false;
    }

    @Override
    public FileParseResultVO parse(File file, String fileName) {
        long start = System.currentTimeMillis();
        int maxTextLength = config.getTika().getMaxTextLength();
        Path extractFile = null;

        try {
            extractFile = Files.createTempFile("tika-extract-", ".md");
            Metadata metadata = new Metadata();
            try (TikaInputStream input = TikaInputStream.get(file);
                 Writer writer = Files.newBufferedWriter(extractFile, StandardCharsets.UTF_8)) {
                Parser parser = TikaHolder.parser();
                ContentHandler handler = new ToMarkdownContentHandler(writer);
                ParseContext context = new ParseContext();
                PDFParserConfig pdfConfig = new PDFParserConfig();
                pdfConfig.setMaxMainMemoryBytes(PDF_MAX_MAIN_MEMORY_BYTES);
                context.set(PDFParserConfig.class, pdfConfig);
                try {
                    parser.parse(input, handler, metadata, context);
                } catch (TikaException | SAXException e) {
                    if (WriteLimitReachedException.isWriteLimitReached(e)) {
                        log.warn("Tika parse exceeded text length limit for {}: {}", fileName, e.getMessage());
                        return FileParseResultVO.builder()
                                .success(false)
                                .fileName(fileName)
                                .fileSize(file.length())
                                .errorMessage("文档内容过长，超过最大提取限制（"
                                        + (maxTextLength / 1024 / 1024) + "MB），可增大 "
                                        + "file-parser.tika.max-text-length 配置后重试")
                                .parseTimeMs(System.currentTimeMillis() - start)
                                .build();
                    }
                    log.error("Tika parse failed for {}: {}", fileName, e.getMessage());
                    return fail(file, fileName, start, "Tika 解析失败: " + e.getMessage());
                }
            }

            String text = Files.readString(extractFile, StandardCharsets.UTF_8);
            String mimeType = metadata.get(HttpHeaders.CONTENT_TYPE);
            text = cleanupText(text, mimeType);

            FileParseResultVO.Content content = FileParseResultVO.Content.builder()
                    .text(text)
                    .metadata(extractMetadata(metadata))
                    .build();

            long elapsed = System.currentTimeMillis() - start;
            log.info("Tika parsed {} in {}ms, chars={}", fileName, elapsed, text.length());
            logParsedContentDetail(fileName, text, mimeType, file.length(), elapsed);

            return FileParseResultVO.builder()
                    .success(true)
                    .fileName(fileName)
                    .fileSize(file.length())
                    .mimeType(mimeType)
                    .parseMethod("tika")
                    .parseTimeMs(elapsed)
                    .content(content)
                    .build();

        } catch (Exception e) {
            log.error("Tika parse failed for {}: {}", fileName, e.getMessage());
            return fail(file, fileName, start, "Tika 解析失败: " + e.getMessage());
        } finally {
            if (extractFile != null) {
                try {
                    Files.deleteIfExists(extractFile);
                } catch (IOException e) {
                    log.debug("Failed to delete extract temp file: {}", extractFile, e);
                }
            }
        }
    }

    private FileParseResultVO fail(File file, String fileName, long start, String errorMessage) {
        return FileParseResultVO.builder()
                .success(false)
                .fileName(fileName)
                .fileSize(file.length())
                .errorMessage(errorMessage)
                .parseTimeMs(System.currentTimeMillis() - start)
                .build();
    }

    /**
     * 文本清洗：根据配置策略对 Tika Markdown 解析结果进行清洗。
     * <p>
     * 支持三种策略：
     * <ul>
     *   <li>{@code none} — 不进行清洗，保留 Tika 原始 Markdown 输出</li>
     *   <li>{@code basic} — 统一换行符、清理多余空白行、删除 {@code @xxx} 协同标记。
     *       保留 Markdown 语法（标题、表格、列表、URL 等）。</li>
     *   <li>{@code docx} — DOCX 文档深度清洗：在 basic 基础上，额外去除
     *       研发内部备注、占位文本、页码残留等，适用于正式交付场景</li>
     * </ul>
     *
     * <h4>与纯文本输出的差异</h4>
     * Tika 4.x 使用 {@code ToMarkdownContentHandler} 输出，文档结构保留完整：
     * <ul>
     *   <li>标题 → {@code # 标题}</li>
     *   <li>加粗 → {@code **文本**}</li>
     *   <li>表格 → {@code | 列1 | 列2 |}</li>
     *   <li>图片 → {@code ![alt](image.png)}</li>
     *   <li>URL → {@code [text](http://...)}</li>
     * </ul>
     * 清洗正则已针对 Markdown 语法调整：
     * <ul>
     *   <li>不再删除 {@code ---}（Markdown 水平线）</li>
     *   <li>不再删除 {@code http://} URL（Markdown 有效语法）</li>
     *   <li>不再删除 {@code image1.png} 行（Markdown 图片语法为 {@code ![alt](image.png)}）</li>
     * </ul>
     *
     * @param raw      原始 Markdown 文本
     * @param mimeType 文档 MIME 类型（用于判断是否为文档类格式）
     * @return 清洗后的文本
     */
    private String cleanupText(String raw, String mimeType) {
        if (raw == null || raw.isEmpty()) return raw;

        String strategy = config.getTika().getCleanupStrategy();
        if ("none".equals(strategy)) {
            return raw;
        }
        // Markdown 输出已保留文档结构，基础空白清洗适用于所有文档类型
        // txt/md/xlsx/pdf 等同样受益于空白行清理

        // ========== 基础清洗（basic & docx 共用） ==========

        // 1. 统一换行符（简单字符串替换，无需正则）
        String text = raw.replace("\r\n", "\n").replace("\r", "\n");

        // 2-4. 行首/行尾/纯空白行清理（一并通过预编译 Pattern 执行）
        text = TRAILING_WS.matcher(text).replaceAll("\n");
        text = LEADING_WS.matcher(text).replaceAll("\n");
        text = BLANK_LINE.matcher(text).replaceAll("\n");

        // 5. 压缩连续 3+ 空行
        text = MANY_NEWLINES.matcher(text).replaceAll("\n\n");

        // 6. @xxx 内部协同批注（中文文档常见，Markdown 中 @ 无特殊含义）
        text = AT_MENTION.matcher(text).replaceAll("");

        if (!"docx".equals(strategy) || !isWordMime(normalizeMime(mimeType))) {
            return text.trim();
        }

        // ========== DOCX 深度清洗（协同稿，仅 Word） ==========

        // 7. 研发备注
        text = TO_DEV.matcher(text).replaceAll("");
        text = PENDING_DEV.matcher(text).replaceAll("\n");
        text = PENDING_PAREN.matcher(text).replaceAll("");
        text = PENDING_END.matcher(text).replaceAll("");

        // 8. 占位文本
        text = PLACEHOLDER.matcher(text).replaceAll("");
        text = PLACEHOLDER_PENDING.matcher(text).replaceAll("");

        // 9. 页码残留（单行数字，注意 Markdown 列表中数字会误伤，仅用于非列表上下文）
        text = PAGE_NUMBER.matcher(text).replaceAll("");

        // 10. 孤立逗号
        text = LONELY_COMMA.matcher(text).replaceAll("\n");

        // 11. 再次压缩连续空行 + 去首尾空白
        text = MANY_NEWLINES.matcher(text).replaceAll("\n\n");
        text = text.trim();

        return text;
    }

    private static boolean isWordMime(String m) {
        return m.startsWith("application/vnd.openxmlformats-officedocument.wordprocessingml")
                || m.startsWith("application/msword")
                || m.startsWith("application/x-msword")
                || m.startsWith("application/vnd.ms-word")
                || m.contains("wps-office.wps")
                || m.contains("wps-office.wpt")
                || m.contains("wps-office.doc");
    }

    private static String normalizeMime(String mimeType) {
        if (mimeType == null) {
            return "";
        }
        String m = mimeType.toLowerCase();
        int semi = m.indexOf(';');
        return semi < 0 ? m : m.substring(0, semi).trim();
    }

    /**
     * 输出解析后的内容结构详情到日志，方便肉眼验证。
     * 统计行数、列数、单元格内容长度（min/max/avg）、总数据量。
     */
    private void logParsedContentDetail(String fileName, String text, String mimeType, long fileSize, long elapsedMs) {
        if (text == null || text.isEmpty()) {
            log.info("  [{}] text=empty, fileSize={} bytes", fileName, fileSize);
            return;
        }
        if (text.length() > CONTENT_STATS_MAX_CHARS) {
            return;
        }

        boolean isSpreadsheet = mimeType != null
                && (mimeType.contains("spreadsheet") || mimeType.contains("excel") || mimeType.contains("csv"));

        String[] lines = text.split("\n");
        int dataRows = 0;
        int columns = 0;
        long totalCellChars = 0;
        int cellCount = 0;
        int minCellLen = Integer.MAX_VALUE;
        int maxCellLen = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            // 跳过 sheet 名行（Excel 第一行通常是 Sheet1）
            if (isSpreadsheet && dataRows == 0 && !trimmed.contains("\t")) {
                log.info("  [{}] sheet name: {}", fileName, trimmed);
                continue;
            }
            String[] cells = trimmed.split("\t");
            if (dataRows == 0) {
                columns = cells.length;
                log.info("  [{}] columns: {}", fileName, columns);
            }
            dataRows++;
            for (String cell : cells) {
                int len = cell.length();
                totalCellChars += len;
                cellCount++;
                if (len < minCellLen) minCellLen = len;
                if (len > maxCellLen) maxCellLen = len;
            }
        }

        double avgCellLen = cellCount > 0 ? (double) totalCellChars / cellCount : 0;
        double avgCharsPerRow = dataRows > 0 ? (double) totalCellChars / dataRows : 0;

        String typeLabel = isSpreadsheet ? "spreadsheet" : "document";
        log.info("  [{}] {} | fileSize={} bytes | textLength={} chars | rows={} | cols={} | "
                        + "cellLen: min={} max={} avg={} | chars/row={} | parseTime={}ms",
                fileName, typeLabel, fileSize, text.length(),
                dataRows, columns, minCellLen, maxCellLen,
                String.format("%.1f", avgCellLen),
                String.format("%.1f", avgCharsPerRow),
                elapsedMs);
    }

    private Map<String, String> extractMetadata(Metadata metadata) {
        Map<String, String> map = new HashMap<>();
        String[] names = metadata.names();
        if (names != null) {
            for (String name : names) {
                String value = metadata.get(name);
                if (StrUtil.isNotBlank(value)) {
                    map.put(name, value);
                }
            }
        }
        return map;
    }
}