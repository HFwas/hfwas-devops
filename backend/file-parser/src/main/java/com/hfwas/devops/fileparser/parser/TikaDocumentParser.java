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
            // WPS Office 格式
            "application/wps-office.",
            // 国产办公格式（基于 XML 的格式）
            "application/uof",
            // ===== 兼容类型 =====
            // WPS Office 兼容类型（application/wps-office.* 已有，此处补充）
            // 旧版 Word 兼容
            "application/x-msword",
            // CSV 兼容
            "application/csv",
            // 旧版 Excel 兼容
            "application/msexcel",
            "application/x-msexcel",
            "application/x-excel",
            "application/excel",
            // 旧版 PowerPoint 兼容
            "application/mspowerpoint",
            "application/powerpoint",
            "application/x-mspowerpoint",
            // Markdown 兼容
            "application/markdown",
            // OFD 兼容
            "application/vnd.ofd",
            // 音频格式（Tika 可提取元数据）
            "audio/",
            // 压缩包格式（Tika 可列出目录结构）
            "application/x-rar",
            "application/rar",
    };

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
            String msg = e.getMessage();
            if (msg != null && msg.contains("max character limit")) {
                log.warn("Tika parse exceeded text length limit for {}: {}", fileName, msg);
                return FileParseResultVO.builder()
                        .success(false)
                        .fileName(fileName)
                        .fileSize(file.length())
                        .errorMessage("文档内容过长，超过最大提取限制（"
                                + (config.getTika().getMaxTextLength() / 1024 / 1024) + "MB），请减小文件后重试")
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