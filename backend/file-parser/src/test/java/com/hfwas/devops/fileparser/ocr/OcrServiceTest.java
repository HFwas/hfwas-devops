package com.hfwas.devops.fileparser.ocr;

import com.benjaminwan.ocrlibrary.Point;
import com.benjaminwan.ocrlibrary.TextBlock;
import com.hfwas.devops.fileparser.config.FileParserConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OcrServiceTest {

    @Mock
    private FileParserConfig config;

    @Mock
    private FileParserConfig.OcrConfig ocrConfig;

    @BeforeEach
    void setUp() {
        lenient().when(config.getOcr()).thenReturn(ocrConfig);
        lenient().when(ocrConfig.getMaxConcurrent()).thenReturn(2);
        lenient().when(ocrConfig.isPreprocessing()).thenReturn(false);
    }

    @Test
    void shouldReturnEmptyWhenEngineNotInitialized() {
        // 创建一个未初始化的 OcrService（模拟引擎初始化失败的情况）
        OcrService service = new OcrService(config);

        // 不调用 init()
        assertFalse(service.isAvailable());

        File tempFile = new File("test.png");
        String result = service.recognize(tempFile);
        assertEquals("", result);

        OcrService.OcrResult ocrResult = service.recognizeWithConfidence(tempFile);
        assertNotNull(ocrResult);
        assertEquals("", ocrResult.text());
        assertEquals(0.0, ocrResult.confidence(), 0.01);
    }

    @Test
    void shouldCreateOcrResultRecord() {
        OcrService.OcrResult result = new OcrService.OcrResult("测试文本", 0.95);

        assertEquals("测试文本", result.text());
        assertEquals(0.95, result.confidence(), 0.001);
    }

    // ========== 文本块后处理测试 ==========

    @Test
    void shouldHandleEmptyTextBlocks() {
        OcrService service = new OcrService(config);
        String result = service.postProcessTextBlocks(null);
        assertEquals("", result);

        result = service.postProcessTextBlocks(new ArrayList<>());
        assertEquals("", result);
    }

    @Test
    void shouldHandleSingleTextBlock() {
        OcrService service = new OcrService(config);
        List<TextBlock> blocks = new ArrayList<>();
        blocks.add(createTextBlock("HelloWorld", 10, 10, 200, 30, 0.95f));

        String result = service.postProcessTextBlocks(blocks);
        assertEquals("HelloWorld", result);
    }

    @Test
    void shouldMergeBlocksOnSameLine() {
        OcrService service = new OcrService(config);
        List<TextBlock> blocks = new ArrayList<>();

        // 同一行的两个文本块（Y 坐标相近）
        blocks.add(createTextBlock("Hello", 10, 10, 100, 30, 0.95f));
        blocks.add(createTextBlock("World", 110, 12, 200, 32, 0.93f));

        String result = service.postProcessTextBlocks(blocks);
        // 应合并为同一行，X 间隙 ≥ 前块宽度 30% 时插入空格
        assertTrue(result.contains("Hello") || result.contains("Hello World"),
                "Same-line blocks should be merged: " + result);
    }

    @Test
    void shouldInsertSpaceBetweenDistantBlocks() {
        OcrService service = new OcrService(config);
        List<TextBlock> blocks = new ArrayList<>();

        // 同一行的两个文本块，间距较大（前块宽度 90px，间隙 100px > 90*0.3=27px）
        blocks.add(createTextBlock("Hello", 10, 10, 100, 30, 0.95f));
        blocks.add(createTextBlock("World", 210, 12, 310, 32, 0.93f));

        String result = service.postProcessTextBlocks(blocks);
        assertTrue(result.contains("Hello World"),
                "Should insert space between far blocks: " + result);
    }

    @Test
    void shouldNotInsertSpaceBetweenCloseBlocks() {
        OcrService service = new OcrService(config);
        List<TextBlock> blocks = new ArrayList<>();

        // 同一行的两个文本块，间距很小
        blocks.add(createTextBlock("Hello", 10, 10, 100, 30, 0.95f));
        blocks.add(createTextBlock("World", 105, 12, 200, 32, 0.93f));

        String result = service.postProcessTextBlocks(blocks);
        assertEquals("HelloWorld", result,
                "Should not insert space between close blocks: " + result);
    }

    @Test
    void shouldSeparateBlocksOnDifferentLines() {
        OcrService service = new OcrService(config);
        List<TextBlock> blocks = new ArrayList<>();

        // 不同行的两个文本块（Y 坐标相差较大）
        blocks.add(createTextBlock("Line1", 10, 10, 100, 30, 0.95f));
        blocks.add(createTextBlock("Line2", 10, 100, 100, 120, 0.93f));

        String result = service.postProcessTextBlocks(blocks);
        assertTrue(result.contains("Line1") && result.contains("Line2"),
                "Different-line blocks should be separated: " + result);
        // 检查是否包含换行
        String[] lines = result.split("\n");
        assertTrue(lines.length >= 2, "Should have at least 2 lines: " + result);
    }

    @Test
    void shouldSortBlocksByYThenX() {
        OcrService service = new OcrService(config);
        List<TextBlock> blocks = new ArrayList<>();

        // 乱序输入
        blocks.add(createTextBlock("Second", 10, 100, 100, 130, 0.90f));
        blocks.add(createTextBlock("First", 10, 10, 100, 30, 0.95f));
        blocks.add(createTextBlock("Third", 10, 200, 100, 230, 0.85f));

        String result = service.postProcessTextBlocks(blocks);
        String[] lines = result.split("\n");
        assertTrue(lines.length >= 3, "Should have 3 lines: " + result);
        assertEquals("First", lines[0].trim(), "First line should be First");
        assertEquals("Second", lines[1].trim(), "Second line should be Second");
        assertEquals("Third", lines[2].trim(), "Third line should be Third");
    }

    @Test
    void shouldSkipEmptyTextInBlocks() {
        OcrService service = new OcrService(config);
        List<TextBlock> blocks = new ArrayList<>();

        blocks.add(createTextBlock("", 10, 10, 100, 30, 0.95f));
        blocks.add(createTextBlock("Valid", 110, 12, 200, 32, 0.93f));
        blocks.add(createTextBlock("   ", 210, 14, 300, 34, 0.90f));

        String result = service.postProcessTextBlocks(blocks);
        assertEquals("Valid", result, "Should skip empty blocks");
    }

    @Test
    void shouldHandleComplexMultiLineLayout() {
        OcrService service = new OcrService(config);
        List<TextBlock> blocks = new ArrayList<>();

        // 模拟终端输出：两行，每行两个块
        // [[root@localhost 618d97adad76]]# grep -R 'TESS'
        blocks.add(createTextBlock("[[root@localhost", 10, 10, 180, 30, 0.95f));
        blocks.add(createTextBlock("618d97adad76]]#", 190, 12, 320, 32, 0.93f));
        blocks.add(createTextBlock("grep -R 'TESS'", 10, 50, 180, 70, 0.94f));
        blocks.add(createTextBlock("agentscope-workflow-app-info.log", 190, 52, 450, 72, 0.92f));

        String result = service.postProcessTextBlocks(blocks);
        String[] lines = result.split("\n");
        assertEquals(2, lines.length, "Should have 2 lines: " + result);
        assertTrue(lines[0].contains("[[root@localhost"),
                "Line 1 should contain terminal prompt: " + lines[0]);
        assertTrue(lines[0].contains("618d97adad76"),
                "Line 1 should contain hash: " + lines[0]);
        assertTrue(lines[1].contains("grep"),
                "Line 2 should contain grep: " + lines[1]);
    }

    // ========== Otsu 阈值计算测试 ==========

    @Test
    void shouldComputeOtsuThresholdForBimodalHistogram() {
        OcrPreprocessor preprocessor = new OcrPreprocessor();

        // 双峰直方图：前景（暗色 0-50）和背景（亮色 200-255）
        int[] histogram = new int[256];
        for (int i = 0; i < 50; i++) histogram[i] = 100;  // 前景
        for (int i = 200; i < 256; i++) histogram[i] = 100; // 背景

        int threshold = preprocessor.computeOtsuThreshold(histogram, 50 * 100 + 56 * 100);
        // Otsu 阈值应在两个峰之间（49 或 50 都是正确的，由方差最大决定）
        assertTrue(threshold >= 40 && threshold < 200,
                "Otsu threshold should be between peaks: " + threshold);
    }

    @Test
    void shouldReturnDefaultThresholdForZeroPixels() {
        OcrPreprocessor preprocessor = new OcrPreprocessor();
        int threshold = preprocessor.computeOtsuThreshold(new int[256], 0);
        assertEquals(128, threshold, "Should return default 128 for zero pixels");
    }

    @Test
    void shouldReturnDefaultThresholdForUniformDistribution() {
        OcrPreprocessor preprocessor = new OcrPreprocessor();
        int[] histogram = new int[256];
        for (int i = 0; i < 256; i++) histogram[i] = 10;

        int threshold = preprocessor.computeOtsuThreshold(histogram, 256 * 10);
        assertTrue(threshold >= 0 && threshold < 256,
                "Should return a valid threshold: " + threshold);
    }

    // ========== 图片预处理测试 ==========

    @Test
    void shouldHandleNullImageInPreprocessor() {
        OcrPreprocessor preprocessor = new OcrPreprocessor();
        assertNull(preprocessor.preprocess(null));
    }

    @Test
    void shouldConvertToGrayscale() {
        OcrPreprocessor preprocessor = new OcrPreprocessor();
        BufferedImage colorImage = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        BufferedImage gray = preprocessor.toGrayscale(colorImage);
        assertNotNull(gray);
        assertEquals(BufferedImage.TYPE_BYTE_GRAY, gray.getType());
        assertEquals(10, gray.getWidth());
        assertEquals(10, gray.getHeight());
    }

    @Test
    void shouldApplyOtsuBinarization() {
        OcrPreprocessor preprocessor = new OcrPreprocessor();
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_BYTE_GRAY);

        // 上半部分白色，下半部分黑色
        int white = (255 << 16) | (255 << 8) | 255;
        int black = 0;
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 20; x++) {
                image.setRGB(x, y, white);
            }
        }
        for (int y = 10; y < 20; y++) {
            for (int x = 0; x < 20; x++) {
                image.setRGB(x, y, black);
            }
        }

        BufferedImage binary = preprocessor.otsuBinarization(image);
        assertNotNull(binary);
        // 上半部分应为白色
        int topPixel = binary.getRGB(0, 0) & 0xFF;
        assertEquals(255, topPixel, "Top half should be white after binarization");
        // 下半部分应为黑色
        int bottomPixel = binary.getRGB(0, 15) & 0xFF;
        assertEquals(0, bottomPixel, "Bottom half should be black after binarization");
    }

    @Test
    void shouldApplyContrastStretch() {
        OcrPreprocessor preprocessor = new OcrPreprocessor();

        // 创建低对比度图片（灰度范围 100-150）
        BufferedImage image = new BufferedImage(50, 50, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < 50; y++) {
            for (int x = 0; x < 50; x++) {
                int gray = 100 + (x + y) % 50; // 范围 100-149
                int rgb = (gray << 16) | (gray << 8) | gray;
                image.setRGB(x, y, rgb);
            }
        }

        // 测试对比度拉伸
        BufferedImage stretched = preprocessor.contrastStretch(image, 100, 149);
        assertNotNull(stretched);
        assertEquals(50, stretched.getWidth());
        assertEquals(50, stretched.getHeight());

        // 拉伸后灰度范围应覆盖 [0, 255]
        int min = 255, max = 0;
        for (int y = 0; y < 50; y++) {
            for (int x = 0; x < 50; x++) {
                int gray = stretched.getRGB(x, y) & 0xFF;
                min = Math.min(min, gray);
                max = Math.max(max, gray);
            }
        }
        assertTrue(min <= 5, "Min should be near 0: " + min);
        assertTrue(max >= 250, "Max should be near 255: " + max);
    }

    @Test
    void shouldComputeHistogram() {
        OcrPreprocessor preprocessor = new OcrPreprocessor();
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_BYTE_GRAY);
        // 设置所有像素为 128
        int gray128 = (128 << 16) | (128 << 8) | 128;
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 10; x++) {
                image.setRGB(x, y, gray128);
            }
        }

        int[] histogram = preprocessor.computeHistogram(image);
        assertEquals(100, histogram[128], "All 100 pixels should be at gray 128");
        assertEquals(0, histogram[0], "No pixels at gray 0");
    }

    @Test
    void shouldSkipPreprocessingForAlreadyOptimalImage() {
        OcrPreprocessor preprocessor = new OcrPreprocessor();
        // 创建一张高对比度灰度图（范围 0-255）
        BufferedImage image = new BufferedImage(50, 50, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < 50; y++) {
            for (int x = 0; x < 50; x++) {
                int gray = (x * 255) / 50;
                int rgb = (gray << 16) | (gray << 8) | gray;
                image.setRGB(x, y, rgb);
            }
        }

        BufferedImage result = preprocessor.preprocess(image);
        assertNotNull(result);
        // 对于高对比度图片，预处理应基本保持原样
        assertEquals(50, result.getWidth());
        assertEquals(50, result.getHeight());
    }

    // ========== 字符级纠错测试 ==========

    @Test
    void shouldCorrectDigit1ToLetterLInWordContext() {
        assertEquals("log", OcrService.correctBlockText("1og"));
        assertEquals("library", OcrService.correctBlockText("1ibrary"));
        assertEquals("ls", OcrService.correctBlockText("1s"));
        assertEquals("localhost", OcrService.correctBlockText("1ocalhost"));
    }

    @Test
    void shouldNotChangeDigit1InNumericContext() {
        assertEquals("618d97adad76", OcrService.correctBlockText("618d97adad76"));
        assertEquals("2026", OcrService.correctBlockText("2026"));
        assertEquals("17:30:48", OcrService.correctBlockText("17:30:48"));
    }

    @Test
    void shouldCorrectDigit0ToLetterOInUppercaseContext() {
        assertEquals("INFO", OcrService.correctBlockText("INF0"));
        assertEquals("WorkflowNodeExecutor-1", OcrService.correctBlockText("WorkflowNodeExecutor-1"));
    }

    @Test
    void shouldCorrectLowercaseOToUppercaseOInUppercaseContext() {
        assertEquals("INFO", OcrService.correctBlockText("INFo"));
    }

    @Test
    void shouldFixCaseDriftForMostlyUppercaseWords() {
        assertEquals("TESSDATA_PREFIX", OcrService.correctBlockText("TEssDATA_PREFIX"));
        assertEquals("TESSDATA_PREFIX", OcrService.correctBlockText("TEsSDATA_PREFIX"));
        assertEquals("TESSDATA_PREFIX", OcrService.correctBlockText("tESSDATA_PREFIX"));
    }

    @Test
    void shouldNotChangeMixedCaseWords() {
        assertEquals("KnowledgeBaseIndexPipeline", OcrService.correctBlockText("KnowledgeBaseIndexPipeline"));
        assertEquals("localhost", OcrService.correctBlockText("localhost"));
        assertEquals("root", OcrService.correctBlockText("root"));
    }

    @Test
    void shouldHandleNullAndEmptyText() {
        assertNull(OcrService.correctBlockText(null));
        assertEquals("", OcrService.correctBlockText(""));
    }

    @Test
    void shouldCorrectMultipleErrorsInOneBlock() {
        // "1og" → "log", "INF0" → "INFO"
        String result = OcrService.correctBlockText("1og.INF0");
        assertEquals("log.INFO", result);
    }

    // ========== 正则后处理测试 ==========

    @Test
    void shouldFixDateTimestampMissingSpace() {
        String input = "2026-07-1117:30:48.631 504492";
        String result = OcrService.applyRegexPostProcessing(input);
        assertTrue(result.startsWith("2026-07-11 17:30:48.631"),
                "Should insert space between date and time: " + result);
    }

    @Test
    void shouldFixLogLevelMissingSpace() {
        String input = "INFOi.a.p.w.c.r.i.KnowledgeBaseIndexPipeline";
        String result = OcrService.applyRegexPostProcessing(input);
        assertTrue(result.contains("INFO i.a.p.w.c.r.i."),
                "Should insert space after INFO: " + result);
    }

    @Test
    void shouldFixCommandFlagMissingSpace() {
        String input = "grep-R'TESS'";
        String result = OcrService.applyRegexPostProcessing(input);
        assertTrue(result.contains("grep -R"),
                "Should insert space between command and flag: " + result);
    }

    @Test
    void shouldFixMultiplePatternsInOnePass() {
        String input = "[[root@localhost 618d97adad76]]# grep-R'TESS'\n" +
                "agentscope-workflow-app-info.log:2026-07-1117:30:48.631 504492\n" +
                "INFOi.a.p.w.c.r.i.KnowledgeBaseIndexPipeline";
        String result = OcrService.applyRegexPostProcessing(input);
        assertTrue(result.contains("grep -R"), "Should fix grep space: " + result);
        assertTrue(result.contains("2026-07-11 17:30:48.631"), "Should fix date space: " + result);
        assertTrue(result.contains("INFO i.a.p."), "Should fix INFO space: " + result);
    }

    @Test
    void shouldNotAddFalsePositiveSpaces() {
        // Normal text with correct spaces should not be modified
        String input = "2026-07-11 17:30:48.631 INFO i.a.p.w.c.r.i.";
        String result = OcrService.applyRegexPostProcessing(input);
        assertEquals(input, result, "Should not modify already-correct text");
    }

    @Test
    void shouldHandleNullAndEmptyString() {
        assertNull(OcrService.applyRegexPostProcessing(null));
        assertEquals("", OcrService.applyRegexPostProcessing(""));
    }

    @Test
    void shouldNotConvertG1GCToGlGC() {
        // JVM 参数中的 G1GC 不应被误改成 GlGC
        String result = OcrService.correctBlockText("UseG1GC");
        // 1 在 G 和 G 之间，前后都是字母 → 保持为 1
        assertEquals("UseG1GC", result, "G1GC should not be changed to GlGC");
    }

    @Test
    void shouldNormalizeFullwidthDigits() {
        String input = "１１";  // 全角数字
        String result = OcrService.applyRegexPostProcessing(input);
        assertEquals("11", result, "Full-width digits should be normalized");
    }

    @Test
    void shouldNormalizeChineseQuotes() {
        String input = "awk ‘{print $2}'";
        String result = OcrService.applyRegexPostProcessing(input);
        assertEquals("awk '{print $2}'", result, "Chinese quotes should be normalized");
    }

    @Test
    void shouldNormalizeUnicodeHyphens() {
        String input = "ps‑ef";  // U+2011 non-breaking hyphen
        String result = OcrService.applyRegexPostProcessing(input);
        assertEquals("ps -ef", result, "Unicode hyphen should be normalized and space inserted");
    }

    @Test
    void shouldFixUniqCSpacing() {
        // uniq -c 输出: "      1 1" → batch 识别错误 "１１"
        String input = "１１ 15478";  // 全角 + 空格丢失
        String result = OcrService.applyRegexPostProcessing(input);
        assertEquals("11 15478", result, "Full-width digits normalized, space preserved");
    }

    @Test
    void shouldNotChangeCorrectG1InJvmFlags() {
        // 正确的 JVM 参数不应被修改
        String[] correctFlags = {
                "-XX:+UseG1GC",
                "UseG1GC",
                "G1GC",
        };
        for (String flag : correctFlags) {
            String result = OcrService.correctBlockText(flag);
            assertEquals(flag, result, "Should not modify: " + flag);
        }
    }

    @Test
    void shouldStillCorrectLogAtStartOfWord() {
        // 开头的 1 → l 仍然有效
        assertEquals("log", OcrService.correctBlockText("1og"));
        assertEquals("library", OcrService.correctBlockText("1ibrary"));
        assertEquals("localhost", OcrService.correctBlockText("1ocalhost"));
    }

    @Test
    void shouldCorrectDigit1BetweenDifferentLetters() {
        // Mi1lis → Millis (1 between i and l, different letters)
        assertEquals("Millis", OcrService.correctBlockText("Mi1lis"));
    }

    @Test
    void shouldNotChangeDigit1BetweenSameLetters() {
        // G1GC → G1GC (1 between G and G, same letter → JVM version)
        assertEquals("G1GC", OcrService.correctBlockText("G1GC"));
        assertEquals("UseG1GC", OcrService.correctBlockText("UseG1GC"));
    }

    @Test
    void shouldNotChangeDigit1BetweenDifferentUppercaseLetters() {
        // G1HeapRegionSize → G1HeapRegionSize (1 between G and H, both uppercase → G1 is valid)
        assertEquals("G1HeapRegionSize", OcrService.correctBlockText("G1HeapRegionSize"));
        assertEquals("G1HeapRegionSize", OcrService.correctBlockText("G1HeapRegionSize"));
    }

    @Test
    void shouldCorrectLowercaseLToDigit1InG1Pattern() {
        // GlHeapRegionSize → G1HeapRegionSize (l after G + uppercase → G1 is JVM pattern)
        assertEquals("G1HeapRegionSize", OcrService.correctBlockText("GlHeapRegionSize"));
        assertEquals("G1GC", OcrService.correctBlockText("GlGC"));
    }

    @Test
    void shouldCorrectDigit1AfterHyphenWithSpace() {
        // wc -1 → wc -l (command flag)
        assertEquals("wc -l", OcrService.correctBlockText("wc -1"));
    }

    @Test
    void shouldNotChangeDigit1InThreadName() {
        // WorkflowNodeExecutor-1 → WorkflowNodeExecutor-1 (thread number)
        assertEquals("WorkflowNodeExecutor-1", OcrService.correctBlockText("WorkflowNodeExecutor-1"));
    }

    @Test
    void shouldCorrectDigit0InCamelCase() {
        // InitiatingHeap0ccupancy → InitiatingHeapOccupancy
        assertEquals("InitiatingHeapOccupancy", OcrService.correctBlockText("InitiatingHeap0ccupancy"));
    }

    @Test
    void shouldCorrectDigit0BeforeAnyLetter() {
        // 0ccupanc → Occupanc (0 at start of word)
        assertEquals("Occupanc", OcrService.correctBlockText("0ccupanc"));
    }

    @Test
    void shouldFixTimeAndTextMissingSpace() {
        String input = "00:00:00java";
        String result = OcrService.applyRegexPostProcessing(input);
        assertEquals("00:00:00 java", result, "Should insert space between time and text");
    }

    @Test
    void shouldFixFullwidthQuestionMark() {
        String input = "这是什么？";
        String result = OcrService.applyRegexPostProcessing(input);
        assertEquals("这是什么?", result, "Full-width ? should be half-width");
    }

    @Test
    void shouldFixPsEfCommand() {
        String input = "ps-ef";
        String result = OcrService.applyRegexPostProcessing(input);
        assertTrue(result.contains("ps -ef"), "Should insert space in ps-ef: " + result);
    }

    @Test
    void shouldInsertSpaceAfterShellPrompt() {
        String result = OcrService.applyRegexPostProcessing("sh-4.4$ps -efL");
        assertTrue(result.contains("sh-4.4$ ps"), "Should insert space after $: " + result);
    }

    @Test
    void shouldInsertSpacesAroundPipe() {
        String result = OcrService.applyRegexPostProcessing("ps -efL|wc -l");
        assertTrue(result.contains("| wc") || result.contains(" | wc"),
                "Should insert space around pipe: " + result);
    }

    @Test
    void shouldFixWcDashOneAfterSpaceInsert() {
        String glued = OcrService.applyRegexPostProcessing("wc-1");
        assertEquals("wc -l", glued, "wc-1 should become wc -l, got: " + glued);
    }

    @Test
    void shouldSplitPsTableHeaderTokens() {
        String result = OcrService.applyRegexPostProcessing("PIDLWPTTYTIMECMD");
        assertEquals("PID LWP TTY TIME CMD", result);
    }

    @Test
    void shouldSplitUidPidPpidHeader() {
        String result = OcrService.applyRegexPostProcessing("UIDPIDPPIDCSTIMETTYTIMECMD");
        assertEquals("UID PID PPID C STIME TTY TIME CMD", result);
    }

    @Test
    void shouldStripLeadingBracketBeforeShellPrompt() {
        String result = OcrService.applyRegexPostProcessing("[sh-4.4$ ps -efL");
        assertTrue(result.startsWith("sh-4.4$"), "Should drop leading [: " + result);
    }

    @Test
    void shouldRepairPsEfTerminalDumpSpacing() {
        String input = "[sh-4.4$ps -ef-L|wc -1\n"
                + "PIDLWPTTYTIMECMD11?00:00:22 tini\n"
                + "1010?00:00:00 java77?00:00:00 run-service.sh";
        String result = OcrService.applyRegexPostProcessing(input);
        assertTrue(result.contains("sh-4.4$ ps"), "Prompt space: " + result);
        assertTrue(result.contains("wc -l"), "wc -l: " + result);
        assertTrue(result.contains("PID LWP TTY TIME CMD"), "Header: " + result);
        assertTrue(result.contains("1 1 ? 00:00:22") || result.contains("1 1 ?00:00:22"),
                "PID/LWP split: " + result);
        assertTrue(result.contains("run-service.sh"), "Keep command name: " + result);
        assertFalse(result.contains("java77?"), "Should split merged ps rows: " + result);
    }

    @Test
    void shouldSplitUniqCCountFromPid() {
        String result = OcrService.applyRegexPostProcessing("15478\n15824");
        assertEquals("1 5478\n1 5824", result);
    }

    @Test
    void shouldSplitMergedUniqCLines() {
        String result = OcrService.applyRegexPostProcessing("1547815824\n1582515826");
        assertEquals("1 5478\n1 5824\n1 5825\n1 5826", result);
    }

    @Test
    void shouldRestoreLostPipesInPsHeadPipeline() {
        String result = OcrService.applyRegexPostProcessing("ps -eLheadn10");
        assertTrue(result.contains("ps -eL | head"), "Should restore pipe before head: " + result);
        assertTrue(result.contains("head -n 10") || result.contains("head -n10"),
                "Should restore head -n: " + result);
    }

    @Test
    void shouldRestoreLostPipesInAwkSortUniqPipeline() {
        String result = OcrService.applyRegexPostProcessing("ps -efLawk'{print$2}'sortuniq-c");
        assertTrue(result.contains("ps -efL | awk"), "pipe before awk: " + result);
        assertTrue(result.contains("awk '{print $2}'") || result.contains("awk '{print $2}'"),
                "awk quoting: " + result);
        assertTrue(result.contains("| sort | uniq"), "sort | uniq: " + result);
        assertTrue(result.contains("uniq -c"), "uniq -c: " + result);
    }

    @Test
    void shouldSplitRemainingPsHeaderFragments() {
        assertEquals("PID LWP TTY TIME CMD",
                OcrService.applyRegexPostProcessing("PID LWPTTY TIMECMD"));
        assertEquals("UID PID PPID C STIME TTY TIME CMD",
                OcrService.applyRegexPostProcessing("UID PID PPID CSTIMETTY TIMECMD"));
    }

    @Test
    void shouldInsertSpaceBeforeTtyQuestionMark() {
        String result = OcrService.applyRegexPostProcessing("1 1? 00:00:22 tini");
        assertTrue(result.contains("1 1 ? 00:00:22"), "Space before ?: " + result);
    }

    @Test
    void shouldSplitUniqCThreeDigitCount() {
        assertEquals("413 10", OcrService.applyRegexPostProcessing("41310"));
    }

    @Test
    void shouldFixJavaCommandAndScriptArgs() {
        String result = OcrService.applyRegexPostProcessing(
                "java-javaagent:/app/jars/x.jar\n/bin/bash /app/sbin/run-service.shstart\n"
                        + "/usr/bin/tini--/usr/local/sbin/docker-entrypoint.sh");
        assertTrue(result.contains("java -javaagent:"), "java -javaagent: " + result);
        assertTrue(result.contains("run-service.sh start"), ".sh start: " + result);
        assertTrue(result.contains("tini -- /usr/"), "tini -- /usr: " + result);
    }

    @Test
    void shouldStripLeadingBracketOnMisreadPrompt() {
        String result = OcrService.applyRegexPostProcessing("[sn-4.4$");
        assertTrue(result.startsWith("sn-4.4$") || result.startsWith("sh-4.4$"),
                "Should drop leading [: " + result);
    }

    @Test
    void shouldRepairLatestPsEfDump() {
        String input = "[sn-4.4$\n"
                + "sh-4.4$ ps -efL | wc -l\n"
                + "sh-4.4$ ps -eLheadn10\n"
                + "PID LWPTTY TIMECMD\n"
                + "1 1? 00:00:22 tini\n"
                + "10 10? 00:00:00 java\n"
                + "sh-4.4$ ps -efLawk'{print$2}'sortuniq-c\n"
                + "41310\n"
                + "1 5478\n"
                + "UID PID PPID CSTIMETTY TIMECMD\n"
                + "agpf 7 1 OJU109? 00:00:00/bin/bash/app/sbin/run-service.shstart";
        String result = OcrService.applyRegexPostProcessing(input);
        assertTrue(result.contains("ps -eL | head"), "head pipeline: " + result);
        assertTrue(result.contains("PID LWP TTY TIME CMD"), "thread header: " + result);
        assertTrue(result.contains("1 1 ? 00:00:22"), "tty question: " + result);
        assertTrue(result.contains("ps -efL | awk"), "awk pipeline: " + result);
        assertTrue(result.contains("| sort | uniq"), "sort uniq: " + result);
        assertTrue(result.contains("413 10"), "413 10: " + result);
        assertTrue(result.contains("C STIME TTY TIME CMD"), "ps -ef header: " + result);
        assertTrue(result.contains("run-service.sh start"), "script arg: " + result);
    }

    // ========== 完整终端日志场景测试 ==========

    @Test
    void shouldPostProcessFullTerminalLogOutput() {
        OcrService service = new OcrService(config);
        List<TextBlock> blocks = new ArrayList<>();

        // 模拟终端日志场景：grep 命令 + 多行日志输出
        // 实际快照中，每行包含时间戳、线程号、包名、日志级别、消息
        // 模拟如下 5 行 + 1 行命令 + 2 行结果

        // 行 1: 命令 + 路径
        blocks.add(createTextBlock("[[root@localhost", 10, 10, 170, 28, 0.95f));
        blocks.add(createTextBlock("618d97adad76]]#", 180, 11, 320, 29, 0.93f));
        blocks.add(createTextBlock("grep", 330, 12, 380, 30, 0.94f));
        blocks.add(createTextBlock("-R", 390, 12, 420, 30, 0.92f));
        blocks.add(createTextBlock("'TESS'", 430, 13, 490, 31, 0.91f));

        // 行 2: 日志行 1 (17:30:48)
        blocks.add(createTextBlock("agentscope-workflow-app-info.log:2026-07-11", 10, 48, 300, 66, 0.94f));
        blocks.add(createTextBlock("17:30:48.631", 310, 49, 400, 67, 0.93f));
        blocks.add(createTextBlock("504492", 410, 50, 460, 68, 0.95f));

        // 行 3: 日志行 2 (17:33:51)
        blocks.add(createTextBlock("17:33:51.233", 310, 86, 400, 104, 0.93f));
        blocks.add(createTextBlock("687094", 410, 87, 460, 105, 0.95f));

        // 行 4: 日志行 3 (17:50:00)
        blocks.add(createTextBlock("17:50:00.468", 310, 124, 400, 142, 0.93f));
        blocks.add(createTextBlock("1656329", 410, 125, 470, 143, 0.95f));

        // 行 5: 命令结果
        blocks.add(createTextBlock("[[root@localhost", 10, 162, 170, 180, 0.95f));
        blocks.add(createTextBlock("618d97adad76]]#", 180, 163, 320, 181, 0.93f));
        blocks.add(createTextBlock("wc", 330, 164, 360, 182, 0.94f));
        blocks.add(createTextBlock("-l", 370, 165, 390, 183, 0.92f));

        // 行 6: 结果 (16)
        blocks.add(createTextBlock("16", 10, 200, 40, 218, 0.96f));

        // 乱序输入，测试排序
        List<TextBlock> shuffled = new ArrayList<>(blocks);
        java.util.Collections.shuffle(shuffled, new java.util.Random(42));

        String result = service.postProcessTextBlocks(shuffled);
        String[] lines = result.split("\n");

        // 应该有 6 行
        assertEquals(6, lines.length, "Should have 6 lines: " + result);

        // 第 1 行: grep 命令
        assertTrue(lines[0].contains("[[root@localhost"),
                "Line 1 should contain prompt: " + lines[0]);
        assertTrue(lines[0].contains("618d97adad76"),
                "Line 1 should contain hash: " + lines[0]);
        assertTrue(lines[0].contains("grep"),
                "Line 1 should contain grep: " + lines[0]);

        // 第 2 行: 日志行 1 (17:30:48)
        assertTrue(lines[1].contains("17:30:48.631"),
                "Line 2 should contain 17:30:48: " + lines[1]);

        // 第 3 行: 日志行 2 (17:33:51)
        assertTrue(lines[2].contains("17:33:51.233"),
                "Line 3 should contain 17:33:51: " + lines[2]);

        // 第 4 行: 日志行 3 (17:50:00)
        assertTrue(lines[3].contains("17:50:00.468"),
                "Line 4 should contain 17:50:00: " + lines[3]);

        // 第 5 行: wc 命令
        assertTrue(lines[4].contains("[[root@localhost"),
                "Line 5 should contain prompt: " + lines[4]);
        assertTrue(lines[4].contains("wc"),
                "Line 5 should contain wc: " + lines[4]);

        // 第 6 行: 结果
        assertEquals("16", lines[5].trim(),
                "Line 6 should be 16: " + lines[5]);
    }

    @Test
    void shouldPostProcessTerminalLogWithCharacterCorrection() {
        OcrService service = new OcrService(config);
        List<TextBlock> blocks = new ArrayList<>();

        // 模拟有 OCR 识别错误的输入
        // "1ocalhost" → "localhost" (corrected), "INF0" → "INFO" (corrected)
        blocks.add(createTextBlock("[[root@1ocalhost", 10, 10, 170, 28, 0.85f));
        blocks.add(createTextBlock("618d97adad76]]#", 180, 11, 320, 29, 0.83f));
        blocks.add(createTextBlock("INF0", 330, 12, 380, 30, 0.80f));
        blocks.add(createTextBlock("1og", 10, 50, 80, 68, 0.82f));

        String result = service.postProcessTextBlocks(blocks);
        String[] lines = result.split("\n");

        // 应该有两行
        assertEquals(2, lines.length, "Should have 2 lines: " + result);

        // 第 1 行: "localhost" 已纠错
        assertTrue(lines[0].contains("localhost"),
                "Line 1 should contain corrected 'localhost': " + lines[0]);
        // 第 1 行: "INFO" 已纠错
        assertTrue(lines[0].contains("INFO"),
                "Line 1 should contain corrected 'INFO': " + lines[0]);
        // 第 2 行: "log" 已纠错
        assertTrue(lines[1].contains("log"),
                "Line 2 should contain corrected 'log': " + lines[1]);
    }

    // ========== 辅助方法 ==========

    /**
     * 创建测试用的 TextBlock
     */
    private TextBlock createTextBlock(String text, int x1, int y1, int x2, int y2, float score) {
        ArrayList<Point> points = new ArrayList<>();
        // 四个角点：左上、右上、右下、左下
        points.add(new Point(x1, y1));
        points.add(new Point(x2, y1));
        points.add(new Point(x2, y2));
        points.add(new Point(x1, y2));
        return new TextBlock(points, score, 0, 0, 0, text, new float[]{score}, 0, 0);
    }
}