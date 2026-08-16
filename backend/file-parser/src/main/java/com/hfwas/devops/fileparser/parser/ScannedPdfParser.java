package com.hfwas.devops.fileparser.parser;

import com.hfwas.devops.fileparser.dto.FileParseResultVO;
import com.hfwas.devops.fileparser.ocr.OcrService;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 扫描版 PDF 解析器
 * 使用 PDFBox 提取页面图片，再通过 RapidOCR 进行文字识别。
 * 适用于无法直接提取文本的扫描件 PDF。
 */
@Slf4j
@Component
public class ScannedPdfParser implements DocumentParser {

    private static final int OCR_DPI = 300;
    private static final int MAX_PAGES = 50;

    private final OcrService ocrService;

    public ScannedPdfParser(OcrService ocrService) {
        this.ocrService = ocrService;
    }

    @Override
    public boolean supports(String mimeType) {
        return "application/pdf".equalsIgnoreCase(mimeType);
    }

    @Override
    public FileParseResultVO parse(File file, String fileName) {
        long start = System.currentTimeMillis();

        try (PDDocument document = Loader.loadPDF(file)) {
            int totalPages = document.getNumberOfPages();
            int pagesToProcess = Math.min(totalPages, MAX_PAGES);

            log.info("Processing scanned PDF: {} ({} pages, processing {})",
                    fileName, totalPages, pagesToProcess);

            PDFRenderer renderer = new PDFRenderer(document);
            List<FileParseResultVO.PageContent> pages = new ArrayList<>();
            int ocrPages = 0;
            double totalConfidence = 0;

            for (int i = 0; i < pagesToProcess; i++) {
                BufferedImage pageImage = renderer.renderImageWithDPI(i, OCR_DPI, ImageType.RGB);

                // 将页面图片保存到临时文件进行 OCR
                Path tempImage = Files.createTempFile("pdf-page-", ".png");
                try {
                    ImageIO.write(pageImage, "png", tempImage.toFile());
                    OcrService.OcrResult result = ocrService.recognizeWithConfidence(tempImage.toFile());

                    pages.add(FileParseResultVO.PageContent.builder()
                            .pageNum(i + 1)
                            .text(result.text())
                            .build());

                    if (!result.text().isEmpty()) {
                        ocrPages++;
                        totalConfidence += result.confidence();
                    }
                } catch (Exception e) {
                    log.warn("OCR failed for page {} of {}: {}", i + 1, fileName, e.getMessage());
                    pages.add(FileParseResultVO.PageContent.builder()
                            .pageNum(i + 1)
                            .text("")
                            .build());
                } finally {
                    try {
                        Files.deleteIfExists(tempImage);
                    } catch (IOException ignored) {
                    }
                }
            }

            // 组装全文
            StringBuilder fullText = new StringBuilder();
            for (FileParseResultVO.PageContent page : pages) {
                fullText.append(page.getText()).append("\n");
            }

            long elapsed = System.currentTimeMillis() - start;
            log.info("Scanned PDF parsed {} in {}ms, {} pages OCR'd", fileName, elapsed, ocrPages);

            double avgConfidence = ocrPages > 0 ? totalConfidence / ocrPages : 0;

            return FileParseResultVO.builder()
                    .success(true)
                    .fileName(fileName)
                    .fileSize(file.length())
                    .mimeType("application/pdf")
                    .parseMethod("ocr")
                    .parseTimeMs(elapsed)
                    .content(FileParseResultVO.Content.builder()
                            .text(fullText.toString())
                            .pages(pages)
                            .build())
                    .ocrInfo(FileParseResultVO.OcrInfo.builder()
                            .engine("rapidocr")
                            .pagesProcessed(ocrPages)
                            .confidence(avgConfidence)
                            .build())
                    .build();

        } catch (IOException e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("Scanned PDF parse failed for {}: {}", fileName, e.getMessage());
            return FileParseResultVO.builder()
                    .success(false)
                    .fileName(fileName)
                    .fileSize(file.length())
                    .errorMessage("扫描 PDF 解析失败: " + e.getMessage())
                    .parseTimeMs(elapsed)
                    .build();
        }
    }
}