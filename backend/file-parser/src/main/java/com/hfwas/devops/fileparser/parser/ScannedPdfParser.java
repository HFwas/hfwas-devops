package com.hfwas.devops.fileparser.parser;

import com.hfwas.devops.fileparser.config.FileParserConfig;
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
 *
 * 内存安全说明：
 * - 每页渲染出的 BufferedImage 在循环内被 GC 回收
 * - 通过 maxImageDimension 限制超大页面的渲染尺寸，防止巨幅 BufferedImage
 * - 通过 maxPages 限制总页数，防止无限循环
 */
@Slf4j
@Component
public class ScannedPdfParser implements DocumentParser {

    private static final int OCR_DPI = 300;

    private final OcrService ocrService;
    private final FileParserConfig config;

    public ScannedPdfParser(OcrService ocrService, FileParserConfig config) {
        this.ocrService = ocrService;
        this.config = config;
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
            int maxPages = config.getScannedPdf().getMaxPages();
            int maxDimension = config.getScannedPdf().getMaxImageDimension();
            int pagesToProcess = Math.min(totalPages, maxPages);

            log.info("Processing scanned PDF: {} ({} pages, processing {}, maxImageDimension={})",
                    fileName, totalPages, pagesToProcess, maxDimension);

            PDFRenderer renderer = new PDFRenderer(document);
            List<FileParseResultVO.PageContent> pages = new ArrayList<>();
            int ocrPages = 0;
            double totalConfidence = 0;

            for (int i = 0; i < pagesToProcess; i++) {
                BufferedImage pageImage = renderer.renderImageWithDPI(i, OCR_DPI, ImageType.RGB);

                // 缩放超大页面，防止巨幅 BufferedImage 撑爆堆内存
                pageImage = scaleIfNeeded(pageImage, maxDimension);

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

    /**
     * 如果图片尺寸超过最大限制，等比例缩放。
     * 防止超大页面（如工程图纸）渲染出巨幅 BufferedImage 撑爆堆内存。
     */
    private BufferedImage scaleIfNeeded(BufferedImage image, int maxDimension) {
        int width = image.getWidth();
        int height = image.getHeight();
        int maxSide = Math.max(width, height);

        if (maxSide <= maxDimension) {
            return image;
        }

        double scale = (double) maxDimension / maxSide;
        int newWidth = (int) (width * scale);
        int newHeight = (int) (height * scale);

        BufferedImage scaled = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = scaled.createGraphics();
        try {
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image, 0, 0, newWidth, newHeight, null);
        } finally {
            g.dispose();
        }

        log.debug("Scaled page image from {}x{} to {}x{} (maxDimension={})",
                width, height, newWidth, newHeight, maxDimension);
        return scaled;
    }
}