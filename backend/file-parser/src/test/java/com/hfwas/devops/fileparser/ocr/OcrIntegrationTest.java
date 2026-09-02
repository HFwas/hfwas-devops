package com.hfwas.devops.fileparser.ocr;

import com.hfwas.devops.fileparser.config.FileParserConfig;
import io.github.mymonstercat.ocr.config.ParamConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.File;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OCR 集成测试：使用真实图片验证 ParamConfig 优化效果
 * <p>
 * 需要 RapidOCR 引擎和 ONNX 模型文件，在无 GPU 或首次运行时会自动下载模型。
 * 使用 {@code -DrunIntegrationTests=true} 启用此测试类。
 *
 * <h3>测试图片说明</h3>
 * <ul>
 *   <li><b>20260712/</b> (9 张): 终端日志输出截图，包含 grep 命令、TESSDATA 诊断等</li>
 *   <li><b>20260713/</b> (1 张): 终端操作截图，包含 ps 命令、wc 统计等</li>
 *   <li><b>20260719/</b> (5 张): 项目管理/终端截图混合</li>
 * </ul>
 *
 * <h3>验证重点</h3>
 * <ul>
 *   <li>recognizeWithConfig 方法可正常调用（ParamConfig 被正确接受）</li>
 *   <li>OCR 引擎返回非空结果和有效置信度</li>
 *   <li>后处理管线（correctBlockText、postProcessTextBlocks）正常衔接</li>
 *   <li>默认配置与优化配置均可正常输出</li>
 * </ul>
 */
@Slf4j
@Tag("integration")
@EnabledIfSystemProperty(named = "runIntegrationTests", matches = "true", disabledReason = "Skipped: set -DrunIntegrationTests=true to enable")
class OcrIntegrationTest {

    private static OcrService ocrService;
    private static boolean engineAvailable = false;

    /**
     * 优化版 ParamConfig：针对终端截图类型图片
     * <ul>
     *   <li>unClipRatio=1.2：较小扩张系数，防止文本块粘连</li>
     *   <li>boxThresh=0.25：较低检测阈值，拾取低对比度文本区域</li>
     *   <li>boxScoreThresh=0.4：较低得分阈值，保留更多文本块</li>
     *   <li>maxSideLen=2048：限制最大边长，平衡速度和精度</li>
     *   <li>padding=50：增加边距，检测边缘文本</li>
     *   <li>doAngle=true：启用文本方向检测</li>
     * </ul>
     */
    private static final ParamConfig OPTIMIZED_CONFIG = new ParamConfig(
            50,    // padding
            2048,  // maxSideLen
            0.4f,  // boxScoreThresh
            0.25f, // boxThresh
            1.2f,  // unClipRatio
            true,  // doAngle
            false  // mostAngle
    );

    /**
     * 宽松版 ParamConfig：针对低质量/低对比度图片
     * <ul>
     *   <li>boxThresh=0.2：更低检测阈值，拾取模糊文本</li>
     *   <li>boxScoreThresh=0.3：更低得分阈值</li>
     *   <li>unClipRatio=1.6：较大扩张系数，合并碎片化文本块</li>
     * </ul>
     */
    private static final ParamConfig RELAXED_CONFIG = new ParamConfig(
            50,    // padding
            2048,  // maxSideLen
            0.3f,  // boxScoreThresh
            0.2f,  // boxThresh
            1.6f,  // unClipRatio
            true,  // doAngle
            false  // mostAngle
    );

    @BeforeAll
    static void init() {
        try {
            FileParserConfig config = new FileParserConfig();
            ocrService = new OcrService(config);
            ocrService.init();

            // 等待引擎初始化完成
            if (!ocrService.isAvailable()) {
                log.warn("RapidOCR engine not available after init, will wait briefly...");
                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(5));
                } catch (InterruptedException ignored) {
                }
            }

            engineAvailable = ocrService.isAvailable();
            if (engineAvailable) {
                log.info("RapidOCR engine is available, running integration tests");
            } else {
                log.warn("RapidOCR engine is NOT available, all tests will be skipped");
            }
        } catch (Exception e) {
            log.warn("Failed to initialize OcrService: {}", e.getMessage());
            engineAvailable = false;
        }
    }

    // ========== 基础功能验证 ==========

    @Test
    void testEngineAvailable() {
        assertTrue(engineAvailable, "RapidOCR engine should be available");
    }

    @Test
    void testRecognizeWithConfig_ReturnsResult() throws Exception {
        if (!engineAvailable) return;

        File image = getTestImage("20260712/Snipaste_2026-07-12_22-52-45.png");
        OcrService.OcrResult result = ocrService.recognizeWithConfig(image, OPTIMIZED_CONFIG);

        log.info("testRecognizeWithConfig: text={}", truncate(result.text(), 200));
        assertTrue(result.confidence() > 0, "Confidence should be > 0");
        assertFalse(result.text().isEmpty(), "Text should not be empty");
    }

    @Test
    void testDefaultVsOptimized_BothWork() throws Exception {
        if (!engineAvailable) return;

        File image = getTestImage("20260712/Snipaste_2026-07-12_22-52-45.png");
        OcrService.OcrResult defaultResult = ocrService.recognizeWithConfidence(image);
        OcrService.OcrResult optimizedResult = ocrService.recognizeWithConfig(image, OPTIMIZED_CONFIG);

        log.info("Default:   confidence={:.4f}, text={}", defaultResult.confidence(), truncate(defaultResult.text(), 100));
        log.info("Optimized: confidence={:.4f}, text={}", optimizedResult.confidence(), truncate(optimizedResult.text(), 100));

        // 两种配置均应返回有效结果
        assertTrue(defaultResult.confidence() > 0, "Default config confidence should be > 0");
        assertTrue(optimizedResult.confidence() > 0, "Optimized config confidence should be > 0");
        assertFalse(defaultResult.text().isEmpty(), "Default config text should not be empty");
        assertFalse(optimizedResult.text().isEmpty(), "Optimized config text should not be empty");
    }

    @Test
    void testRelaxedConfig_Works() throws Exception {
        if (!engineAvailable) return;

        File image = getTestImage("20260712/Snipaste_2026-07-12_22-52-45.png");
        OcrService.OcrResult result = ocrService.recognizeWithConfig(image, RELAXED_CONFIG);

        log.info("Relaxed config: confidence={:.4f}, text={}", result.confidence(), truncate(result.text(), 200));
        assertTrue(result.confidence() > 0, "Confidence should be > 0");
        assertFalse(result.text().isEmpty(), "Text should not be empty");
    }

    // ========== 20260712: 终端日志输出（9 images） ==========

    @Test
    void test20260712_TerminalLog1() throws Exception {
        runOcrTest("20260712/Snipaste_2026-07-12_22-52-45.png");
    }

    @Test
    void test20260712_TerminalLog2() throws Exception {
        runOcrTest("20260712/Snipaste_2026-07-12_22-53-12.png");
    }

    @Test
    void test20260712_TerminalLog3() throws Exception {
        runOcrTest("20260712/Snipaste_2026-07-12_22-53-31.png");
    }

    @Test
    void test20260712_TerminalLog4() throws Exception {
        runOcrTest("20260712/Snipaste_2026-07-12_22-54-03.png");
    }

    @Test
    void test20260712_TerminalLog5() throws Exception {
        runOcrTest("20260712/Snipaste_2026-07-12_22-54-36.png");
    }

    @Test
    void test20260712_TerminalLog6() throws Exception {
        runOcrTest("20260712/Snipaste_2026-07-12_22-54-51.png");
    }

    @Test
    void test20260712_TerminalLog7() throws Exception {
        runOcrTest("20260712/Snipaste_2026-07-12_22-55-42.png");
    }

    @Test
    void test20260712_TerminalLog8() throws Exception {
        runOcrTest("20260712/Snipaste_2026-07-12_22-55-56.png");
    }

    @Test
    void test20260712_TerminalLog9() throws Exception {
        runOcrTest("20260712/Snipaste_2026-07-12_22-56-18.png");
    }

    // ========== 20260713: 终端操作截图 ==========

    @Test
    void test20260713_StackLog() throws Exception {
        if (!engineAvailable) return;

        File image = getTestImage("20260713/Snipaste_2026-07-13_20-16-29.png");
        OcrService.OcrResult defaultResult = ocrService.recognizeWithConfidence(image);
        OcrService.OcrResult optimizedResult = ocrService.recognizeWithConfig(image, OPTIMIZED_CONFIG);

        log.info("20260713-20-16-29 (default):   text={}", truncate(defaultResult.text(), 300));
        log.info("20260713-20-16-29 (optimized): text={}", truncate(optimizedResult.text(), 300));

        // 两种配置均应返回有效结果
        assertTrue(defaultResult.confidence() > 0, "Default config confidence should be > 0");
        assertTrue(optimizedResult.confidence() > 0, "Optimized config confidence should be > 0");
        assertFalse(optimizedResult.text().isEmpty(), "Optimized config text should not be empty");
    }

    // ========== 20260719: 混合截图 ==========

    @Test
    void test20260719_Image1() throws Exception {
        runOcrTest("20260719/Snipaste_2026-07-19_18-15-04.png");
    }

    @Test
    void test20260719_Image2() throws Exception {
        runOcrTest("20260719/Snipaste_2026-07-19_18-18-50.png");
    }

    @Test
    void test20260719_Image3() throws Exception {
        runOcrTest("20260719/Snipaste_2026-07-19_18-21-41.png");
    }

    @Test
    void test20260719_Image4() throws Exception {
        runOcrTest("20260719/Snipaste_2026-07-19_19-36-40.png");
    }

    @Test
    void test20260719_Image5() throws Exception {
        runOcrTest("20260719/Snipaste_2026-07-19_19-45-38.png");
    }

    // ========== 工具方法 ==========

    /**
     * 对指定图片运行 OCR 识别（优化配置），验证基本功能正常
     */
    private void runOcrTest(String relativePath) throws Exception {
        if (!engineAvailable) return;

        File image = getTestImage(relativePath);
        OcrService.OcrResult result = ocrService.recognizeWithConfig(image, OPTIMIZED_CONFIG);

        log.info("{}: confidence={:.4f}, text={}",
                relativePath, result.confidence(), truncate(result.text(), 200));

        assertTrue(result.confidence() > 0,
                "Confidence should be > 0 for " + relativePath);
        assertFalse(result.text().isEmpty(),
                "Text should not be empty for " + relativePath);
    }

    private File getTestImage(String relativePath) {
        URL url = getClass().getClassLoader().getResource("files/" + relativePath);
        assertNotNull(url, "Test image not found: files/" + relativePath);
        return new File(url.getFile());
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "null";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
}