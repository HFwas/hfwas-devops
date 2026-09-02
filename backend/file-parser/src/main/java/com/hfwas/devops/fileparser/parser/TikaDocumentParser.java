package com.hfwas.devops.fileparser.parser;

import cn.hutool.core.util.StrUtil;
import com.hfwas.devops.fileparser.config.FileParserConfig;
import com.hfwas.devops.fileparser.dto.FileParseResultVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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
    /** 分隔线整行：3 个以上 = 或 - */
    private static final Pattern SEPARATOR_LINE = Pattern.compile("^[=]{3,}.*$|^[-]{3,}.*$", Pattern.MULTILINE);
    /** 内部协同批注 @xxx */
    private static final Pattern AT_MENTION = Pattern.compile("@\\S+[ \\t]*\\S*");
    /** 图片文件名残留 */
    private static final Pattern IMAGE_FILE = Pattern.compile("^image\\d+\\.(png|jpe?g|gif|bmp|svg|webp)\\s*$", Pattern.MULTILINE);
    /** 研发备注 to研发/开发/产品 */
    private static final Pattern TO_DEV = Pattern.compile("^[ 　]*to(研发|开发|产品)[：:].*$", Pattern.MULTILINE);
    /** 待研发/开发/产品确认 */
    private static final Pattern PENDING_DEV = Pattern.compile("待(研发|开发|产品)确认.*?(?:\n|$)");
    /** 待确认括号内容 */
    private static final Pattern PENDING_PAREN = Pattern.compile("\\(待确认[^)]*\\)");
    /** 待确认句尾 */
    private static final Pattern PENDING_END = Pattern.compile("待确认[。，]");
    /** URL 内链 */
    private static final Pattern URL_LINK = Pattern.compile("http[s]?://\\S+");
    /** 占位文本 xxx岗位/选择/菜单/功能 */
    private static final Pattern PLACEHOLDER = Pattern.compile("^xxx(岗位|选择|菜单|功能).*$", Pattern.MULTILINE);
    /** 后续待拆分占位 */
    private static final Pattern PLACEHOLDER_PENDING = Pattern.compile("【后续待[^】]*】");
    /** 单独一行数字（页码残留） */
    private static final Pattern PAGE_NUMBER = Pattern.compile("^\\d{1,4}$", Pattern.MULTILINE);
    /** 孤立逗号 */
    private static final Pattern LONELY_COMMA = Pattern.compile("[，,]\\s*\n");

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

        try (InputStream input = new FileInputStream(file)) {
            Parser parser = new AutoDetectParser();
            // 使用配置的文本长度上限，防止超大文档撑爆堆内存
            BodyContentHandler handler = new BodyContentHandler(config.getTika().getMaxTextLength());
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();

            parser.parse(input, handler, metadata, context);

            String text = handler.toString();
            String mimeType = metadata.get(Metadata.CONTENT_TYPE);
            // 文本清洗：根据配置策略执行文档内容清洗
            text = cleanupText(text, mimeType);

            FileParseResultVO.Content content = FileParseResultVO.Content.builder()
                    .text(text)
                    .metadata(extractMetadata(metadata))
                    .build();

            long elapsed = System.currentTimeMillis() - start;
            log.info("Tika parsed {} in {}ms", fileName, elapsed);
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

        } catch (TikaException | SAXException | IOException e) {
            // Tika 文本超出限制时抛出 SAXException，转成友好的错误消息
            // BodyContentHandler/WriteOutContentHandler 的异常消息格式：
            // "Your document contained more than X characters, and so your requested limit has been reached."
            String msg = e.getMessage();
            if (msg != null && (msg.contains("more than") && msg.contains("limit has been reached"))
                    || msg.contains("max character limit")) {
                log.warn("Tika parse exceeded text length limit for {}: {}", fileName, msg);
                return FileParseResultVO.builder()
                        .success(false)
                        .fileName(fileName)
                        .fileSize(file.length())
                        .errorMessage("文档内容过长，超过最大提取限制（"
                                + (config.getTika().getMaxTextLength() / 1024 / 1024) + "MB），可增大 "
                                + "file-parser.tika.max-text-length 配置后重试")
                        .parseTimeMs(System.currentTimeMillis() - start)
                        .build();
            }
            log.error("Tika parse failed for {}: {}", fileName, e.getMessage());
            return FileParseResultVO.builder()
                    .success(false)
                    .fileName(fileName)
                    .fileSize(file.length())
                    .errorMessage("Tika 解析失败: " + e.getMessage())
                    .parseTimeMs(System.currentTimeMillis() - start)
                    .build();
        }
    }

    /**
     * 文本清洗：根据配置策略对 Tika 解析结果进行清洗。
     * <p>
     * 支持三种策略：
     * <ul>
     *   <li>{@code none} — 不进行清洗，保留 Tika 原始输出</li>
     *   <li>{@code basic} — 基础清洗：统一换行符、去除行首行尾空白、去除空白行、压缩连续空行、
     *       删除图片文件名残留（如 {@code image1.png}）</li>
     *   <li>{@code docx} — DOCX 文档深度清洗：在 basic 基础上，额外去除文档内部协同标记、
     *       分隔线、URL 内链、研发备注等，适用于正式交付场景</li>
     * </ul>
     *
     * <h4>深度清洗规则（docx 策略）</h4>
     * <ul>
     *   <li>删除 {@code @xxx} 内部协同标记及同行后续内容</li>
     *   <li>删除由 3 个以上 {@code =} 或 {@code -} 组成的分隔线整行</li>
     *   <li>删除 URL 内链（{@code http://}、{@code https://}）</li>
     *   <li>删除研发内部备注（{@code to研发}、{@code 待研发确认}、{@code 待确认} 等）</li>
     *   <li>删除常见占位文本（如 {@code xxx岗位选择xxx菜单} 等模板占位符）</li>
     *   <li>删除单独一行的数字（文档页脚页码残留）</li>
     * </ul>
     *
     * @param raw      原始文本
     * @param mimeType 文档 MIME 类型（用于判断是否为文档类格式）
     * @return 清洗后的文本
     */
    private String cleanupText(String raw, String mimeType) {
        if (raw == null || raw.isEmpty()) return raw;

        String strategy = config.getTika().getCleanupStrategy();
        if ("none".equals(strategy)) {
            return raw;
        }

        // ========== 基础清洗（basic & docx 共用） ==========

        // 1. 统一换行符（简单字符串替换，无需正则）
        String text = raw.replace("\r\n", "\n").replace("\r", "\n");

        // 2-4. 行首/行尾/纯空白行清理（一并通过预编译 Pattern 执行）
        text = TRAILING_WS.matcher(text).replaceAll("\n");
        text = LEADING_WS.matcher(text).replaceAll("\n");
        text = BLANK_LINE.matcher(text).replaceAll("\n");

        // 5. 删除图片文件名残留（所有文档类型通用，图片非业务内容）
        text = IMAGE_FILE.matcher(text).replaceAll("");

        // 6. 压缩连续 3+ 空行
        text = MANY_NEWLINES.matcher(text).replaceAll("\n\n");

        if (!"docx".equals(strategy)) {
            return text.trim();
        }

        // ========== DOCX 深度清洗 ==========
        boolean isSpreadsheet = mimeType != null
                && (mimeType.contains("spreadsheet") || mimeType.contains("excel") || mimeType.contains("csv"));
        if (isSpreadsheet) {
            return text.trim();
        }

        // 6. 分隔线整行（预编译 Pattern 单次编译，复用匹配）
        text = SEPARATOR_LINE.matcher(text).replaceAll("");

        // 7. @xxx 内部协同批注
        text = AT_MENTION.matcher(text).replaceAll("");

        // 8. 研发备注
        text = TO_DEV.matcher(text).replaceAll("");
        text = PENDING_DEV.matcher(text).replaceAll("\n");
        text = PENDING_PAREN.matcher(text).replaceAll("");
        text = PENDING_END.matcher(text).replaceAll("");

        // 9. URL 内链
        text = URL_LINK.matcher(text).replaceAll("");

        // 10. 占位文本
        text = PLACEHOLDER.matcher(text).replaceAll("");
        text = PLACEHOLDER_PENDING.matcher(text).replaceAll("");

        // 11. 页码残留
        text = PAGE_NUMBER.matcher(text).replaceAll("");

        // 12. 孤立逗号
        text = LONELY_COMMA.matcher(text).replaceAll("\n");

        // 14. 再次压缩连续空行 + 去首尾空白
        text = MANY_NEWLINES.matcher(text).replaceAll("\n\n");
        text = text.trim();

        return text;
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