package com.hfwas.devops.fileparser.parser;

import cn.hutool.core.util.StrUtil;
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
import java.util.Map;

/**
 * Tika 文档解析器
 * 支持格式：DOCX、PPTX、XLSX、文本 PDF
 * 使用 Apache Tika 自动检测并解析
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
    };

    @Override
    public boolean supports(String mimeType) {
        if (mimeType == null) return false;
        String lower = mimeType.toLowerCase();
        // 排除图片类型（由 ImageOcrParser 处理）
        if (lower.startsWith("image/")) return false;
        for (String prefix : SUPPORTED_TYPES) {
            if (lower.startsWith(prefix)) return true;
        }
        return false;
    }

    @Override
    public FileParseResultVO parse(File file, String fileName) {
        long start = System.currentTimeMillis();

        try (InputStream input = new FileInputStream(file)) {
            Parser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(-1);
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