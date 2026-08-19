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
            log.info("Tika parsed {} in {}ms, text length={}", fileName, elapsed, text.length());

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