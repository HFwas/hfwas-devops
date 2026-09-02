package com.hfwas.devops.fileparser.ocr.onnx;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 识别模型后处理
 * 实现 CTC（Connectionist Temporal Classification）贪心解码，
 * 将识别模型输出的 logits 转换为文本字符串。
 *
 * <h3>字典约定（与 PaddleOCR / RapidOCR 一致）</h3>
 * <ul>
 *   <li>index 0 为 CTC blank，不输出</li>
 *   <li>字典文件每行一个字符，只去掉换行，保留 ASCII 空格和全角空格 U+3000</li>
 *   <li>若字典不含 ASCII 空格，则追加到末尾（{@code use_space_char=true}）</li>
 * </ul>
 */
@Slf4j
public class RecognitionPostProcessor {

    /** 字符列表（index 0 = blank） */
    private final List<String> charList;

    /** blank 字符索引 */
    private final int blankIndex;

    /**
     * 创建 CTC 解码器
     *
     * @param dictPath 字典文件路径
     * @throws IOException 字典文件读取失败
     */
    public RecognitionPostProcessor(Path dictPath) throws IOException {
        this.charList = loadDict(dictPath);
        this.blankIndex = 0;
        log.info("CTC decoder loaded: {} classes (including blank), blank index={}",
                charList.size(), blankIndex);
    }

    /**
     * 默认字典（不含 PP-OCRv6 数据时的备用字典）
     * 包含常见中英文、数字、标点符号
     */
    public RecognitionPostProcessor() {
        List<String> chars = buildDefaultCharList();
        chars.add(0, "");
        this.charList = chars;
        this.blankIndex = 0;
        log.warn("Using default fallback dictionary ({} classes), PP-OCRv6 dictionary not loaded",
                charList.size());
    }

    /**
     * CTC 贪心解码，并计算置信度
     *
     * @param logits 识别模型输出 [1, time, num_classes]
     * @return 解码结果，包含文本和置信度
     */
    public DecodeResult decodeWithConfidence(float[][][] logits) {
        if (logits == null || logits.length == 0 || logits[0].length == 0) {
            return new DecodeResult("", 0.0);
        }

        int timeSteps = logits[0].length;
        int numClasses = logits[0][0].length;

        int[] bestPath = new int[timeSteps];
        float[] maxProbs = new float[timeSteps];
        for (int t = 0; t < timeSteps; t++) {
            int maxIdx = 0;
            float maxVal = logits[0][t][0];
            for (int c = 1; c < numClasses; c++) {
                if (logits[0][t][c] > maxVal) {
                    maxVal = logits[0][t][c];
                    maxIdx = c;
                }
            }
            bestPath[t] = maxIdx;
            maxProbs[t] = maxVal;
        }

        List<Integer> merged = new ArrayList<>();
        List<Float> charConfidences = new ArrayList<>();
        Integer prev = null;
        for (int t = 0; t < timeSteps; t++) {
            int idx = bestPath[t];
            if (idx == blankIndex) {
                prev = null;
                continue;
            }
            if (prev == null || idx != prev) {
                merged.add(idx);
                charConfidences.add(sigmoid(maxProbs[t]));
                prev = idx;
            }
        }

        StringBuilder text = new StringBuilder();
        for (int idx : merged) {
            appendChar(text, idx);
        }

        double confidence = 0.0;
        if (!charConfidences.isEmpty()) {
            double sum = 0;
            for (float c : charConfidences) sum += c;
            confidence = sum / charConfidences.size();
        }

        return new DecodeResult(text.toString(), confidence);
    }

    /**
     * CTC 解码结果
     */
    public record DecodeResult(String text, double confidence) {
    }

    /**
     * Sigmoid 激活函数
     */
    private static float sigmoid(float x) {
        return (float) (1.0 / (1.0 + Math.exp(-x)));
    }

    /**
     * CTC 贪心解码
     *
     * @param logits 识别模型输出 [1, time, num_classes]
     * @return 解码后的文本字符串
     */
    public String decode(float[][][] logits) {
        return decodeWithConfidence(logits).text();
    }

    /**
     * 获取字典大小（含 blank）
     */
    public int getDictSize() {
        return charList.size();
    }

    // ========== 工具方法 ==========

    /**
     * 加载字典文件。
     * 每行一个字符；只去掉 {@code \n}/{@code \r}，保留空格和全角空格。
     * index 0 插入 CTC blank，必要时在末尾追加 ASCII 空格。
     */
    static List<String> loadDict(Path dictPath) throws IOException {
        List<String> lines = Files.readAllLines(dictPath, StandardCharsets.UTF_8);
        List<String> dict = new ArrayList<>(lines.size() + 2);
        dict.add("");
        boolean hasAsciiSpace = false;
        for (String line : lines) {
            if (!line.isEmpty() && line.charAt(line.length() - 1) == '\r') {
                line = line.substring(0, line.length() - 1);
            }
            if (line.isEmpty()) {
                continue;
            }
            dict.add(line);
            if (" ".equals(line)) {
                hasAsciiSpace = true;
            }
        }
        if (!hasAsciiSpace) {
            dict.add(" ");
        }
        return dict;
    }

    private void appendChar(StringBuilder text, int idx) {
        if (idx == blankIndex) {
            return;
        }
        if (idx >= 0 && idx < charList.size()) {
            text.append(charList.get(idx));
        } else {
            log.warn("Character index out of range: idx={} (max {})", idx, charList.size() - 1);
        }
    }

    /**
     * 构建默认基础字符集（fallback）
     */
    private List<String> buildDefaultCharList() {
        List<String> chars = new ArrayList<>();

        for (char c = '一'; c <= '鿿'; c++) {
            chars.add(String.valueOf(c));
        }

        for (char c = 'a'; c <= 'z'; c++) chars.add(String.valueOf(c));
        for (char c = 'A'; c <= 'Z'; c++) chars.add(String.valueOf(c));

        for (char c = '0'; c <= '9'; c++) chars.add(String.valueOf(c));

        String punctuation = "、。，！？：；“”‘’" +
                "（）【】《》—…·～｜" +
                "＂＇［］｛｝" +
                "!@#$%^&*()_+-=[]{}|;':\",./<>?~`·";
        for (char c : punctuation.toCharArray()) {
            chars.add(String.valueOf(c));
        }

        chars.add(" ");
        return chars;
    }
}
