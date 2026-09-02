package com.hfwas.devops.fileparser.ocr.onnx;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;

/**
 * PaddleOCR 推理引擎
 * 基于 ONNX Runtime Java 加载 PP-OCRv6 检测和识别模型。
 *
 * <h3>模型管理</h3>
 * <ul>
 *   <li>模型文件在首次调用时自动从 HuggingFace 镜像站下载到本地缓存目录</li>
 *   <li>支持检测模型（det）和识别模型（rec）的独立加载</li>
 *   <li>线程安全：每个推理调用创建独立的张量，Session 可共享</li>
 * </ul>
 */
@Slf4j
public class PaddleOcrEngine implements AutoCloseable {

    private final OrtEnvironment env;
    private final OrtSession detSession;
    private final OrtSession recSession;

    /** 检测模型输入名称 */
    private final String detInputName;
    /** 识别模型输入名称 */
    private final String recInputName;

    /**
     * 创建 PaddleOCR 推理引擎
     *
     * @param modelDir  模型存放目录
     * @param modelTier 模型档次: tiny | small | medium
     * @throws IOException  模型文件下载/读取失败
     * @throws OrtException ONNX Runtime 推理初始化失败
     */
    public PaddleOcrEngine(Path modelDir, String modelTier) throws IOException, OrtException {
        this.env = OrtEnvironment.getEnvironment();

        // 1. 确保模型目录存在，并写入与当前档次匹配的官方字典
        Files.createDirectories(modelDir);
        ensureCharacterDict(modelDir, modelTier);

        // 2. 下载/加载检测模型
        Path detModelPath = modelDir.resolve("det_" + modelTier + ".onnx");
        downloadIfNeeded(detModelPath, modelTier, "det");
        log.info("Loading detection model: {}", detModelPath);
        try (OrtSession.SessionOptions detOptions = cpuSessionOptions()) {
            this.detSession = env.createSession(detModelPath.toString(), detOptions);
        }
        this.detInputName = detSession.getInputNames().iterator().next();
        log.info("Detection model loaded: {} (input: {})", detModelPath.getFileName(), detInputName);

        // 3. 下载/加载识别模型
        Path recModelPath = modelDir.resolve("rec_" + modelTier + ".onnx");
        downloadIfNeeded(recModelPath, modelTier, "rec");
        log.info("Loading recognition model: {}", recModelPath);
        try (OrtSession.SessionOptions recOptions = cpuSessionOptions()) {
            this.recSession = env.createSession(recModelPath.toString(), recOptions);
        }
        this.recInputName = recSession.getInputNames().iterator().next();
        log.info("Recognition model loaded: {} (input: {}, outputs: {})",
                recModelPath.getFileName(), recInputName, recSession.getOutputInfo().keySet());
    }

    private static OrtSession.SessionOptions cpuSessionOptions() throws OrtException {
        OrtSession.SessionOptions options = new OrtSession.SessionOptions();
        options.setIntraOpNumThreads(1);
        options.setInterOpNumThreads(1);
        options.setMemoryPatternOptimization(true);
        return options;
    }

    /**
     * 将官方 PP-OCRv6 字典写入模型目录。
     * 本地 dict.txt 若是从 inference.yml 错误抽取（丢失 U+3000、单引号变成 {@code ''}），
     * 会导致所有汉字错位成生僻字。
     */
    public static Path ensureCharacterDict(Path modelDir, String modelTier) throws IOException {
        Files.createDirectories(modelDir);
        Path dictPath = modelDir.resolve("dict.txt");
        String resource = isTiny(modelTier) ? "ocr/ppocrv6_tiny_dict.txt" : "ocr/ppocrv6_dict.txt";
        int expectedChars = isTiny(modelTier) ? 6904 : 18708;
        if (isValidDict(dictPath, expectedChars)) {
            return dictPath;
        }

        try (InputStream in = PaddleOcrEngine.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Missing classpath OCR dictionary: " + resource);
            }
            Files.copy(in, dictPath, StandardCopyOption.REPLACE_EXISTING);
        }
        if (!isValidDict(dictPath, expectedChars)) {
            throw new IOException("OCR dictionary invalid after install: " + dictPath);
        }
        log.info("Installed PP-OCRv6 dictionary ({} chars) to {}", expectedChars, dictPath);
        return dictPath;
    }

    static boolean isValidDict(Path dictPath, int expectedChars) throws IOException {
        if (dictPath == null || !Files.exists(dictPath) || Files.size(dictPath) == 0) {
            return false;
        }
        List<String> lines = Files.readAllLines(dictPath, StandardCharsets.UTF_8);
        List<String> chars = new java.util.ArrayList<>(lines.size());
        for (String line : lines) {
            if (!line.isEmpty() && line.charAt(line.length() - 1) == '\r') {
                line = line.substring(0, line.length() - 1);
            }
            if (!line.isEmpty()) {
                chars.add(line);
            }
        }
        if (chars.size() != expectedChars) {
            log.warn("OCR dict {} has {} chars, expected {}", dictPath, chars.size(), expectedChars);
            return false;
        }
        if (chars.size() > 6 && !"'".equals(chars.get(6))) {
            log.warn("OCR dict {} quote entry corrupted: {}", dictPath, chars.get(6));
            return false;
        }
        boolean hasIdeographicSpace = chars.stream().anyMatch(c -> "\u3000".equals(c));
        if (!hasIdeographicSpace) {
            log.warn("OCR dict {} missing ideographic space U+3000; Chinese indices would shift", dictPath);
            return false;
        }
        return true;
    }

    private static boolean isTiny(String modelTier) {
        return modelTier != null && "tiny".equalsIgnoreCase(modelTier.trim());
    }

    /**
     * 运行检测模型推理
     *
     * @param input 预处理后的图像张量 [1, 3, H, W]
     * @return 检测输出，通常为 [1, 1, H, W] 的概率图（已含 sigmoid）
     * @throws OrtException 推理失败
     */
    public float[][][][] runDetection(float[][][][] input) throws OrtException {
        long[] shape = {1, 3, input[0][0].length, input[0][0][0].length};
        try (OnnxTensor tensor = floatTensorToOnnx(input, shape);
             OrtSession.Result result = detSession.run(Collections.singletonMap(detInputName, tensor))) {

            // 获取第一个输出（PP-OCRv6 检测模型输出已含 sigmoid）
            var output = (OnnxTensor) result.get(0);
            return onnxToFloatArray4D(output);
        }
    }

    /**
     * 运行识别模型推理
     *
     * @param input 预处理后的文本区域张量 [1, 3, 48, W]
     * @return 识别输出 logits [1, time, num_classes]
     * @throws OrtException 推理失败
     */
    public float[][][] runRecognition(float[][][][] input) throws OrtException {
        long[] shape = {1, 3, input[0][0].length, input[0][0][0].length};
        try (OnnxTensor tensor = floatTensorToOnnx(input, shape);
             OrtSession.Result result = recSession.run(Collections.singletonMap(recInputName, tensor))) {

            var output = (OnnxTensor) result.get(0);
            return onnxToFloatArray3D(output);
        }
    }

    /**
     * 检查引擎是否可用
     */
    public boolean isAvailable() {
        return detSession != null && recSession != null;
    }

    @Override
    public void close() {
        try {
            if (detSession != null) detSession.close();
        } catch (OrtException e) {
            log.warn("Failed to close detection session: {}", e.getMessage());
        }
        try {
            if (recSession != null) recSession.close();
        } catch (OrtException e) {
            log.warn("Failed to close recognition session: {}", e.getMessage());
        }
    }

    // ========== 模型下载 ==========

    private void downloadIfNeeded(Path modelPath, String modelTier, String type) throws IOException {
        if (Files.exists(modelPath) && Files.size(modelPath) > 0) {
            log.debug("Model already exists: {}", modelPath);
            return;
        }

        String modelName = "PP-OCRv6_" + modelTier + "_" + type + "_onnx";
        // 尝试多个镜像源
        String[] urls = {
                "https://hf-mirror.com/PaddlePaddle/" + modelName + "/resolve/main/inference.onnx",
                "https://huggingface.co/PaddlePaddle/" + modelName + "/resolve/main/inference.onnx"
        };

        IOException lastException = null;
        for (String url : urls) {
            try {
                log.info("Downloading model from: {}", url);
                var connection = new java.net.URL(url).openConnection();
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(120000);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0");

                try (var inputStream = connection.getInputStream();
                     var outputStream = Files.newOutputStream(modelPath)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalBytes = 0;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        totalBytes += bytesRead;
                    }
                    log.info("Model downloaded: {} ({} bytes)", modelPath.getFileName(), totalBytes);
                    return;
                }
            } catch (IOException e) {
                lastException = e;
                log.warn("Failed to download from {}: {}", url, e.getMessage());
            }
        }
        throw lastException != null ? lastException : new IOException("No download URLs available");
    }

    // ========== 张量转换 ==========

    /**
     * 将 float 4D 数组转换为 ONNX Runtime 张量
     */
    private OnnxTensor floatTensorToOnnx(float[][][][] data, long[] shape) throws OrtException {
        int total = 1;
        for (long s : shape) total *= s;

        FloatBuffer buffer = FloatBuffer.allocate(total);
        for (int n = 0; n < shape[0]; n++) {
            for (int c = 0; c < shape[1]; c++) {
                for (int h = 0; h < shape[2]; h++) {
                    for (int w = 0; w < shape[3]; w++) {
                        buffer.put(data[n][c][h][w]);
                    }
                }
            }
        }
        buffer.flip();

        return OnnxTensor.createTensor(env, buffer, shape);
    }

    /**
     * 将 ONNX Runtime 张量转换为 float 4D 数组
     */
    private float[][][][] onnxToFloatArray4D(OnnxTensor tensor) throws OrtException {
        Object value = tensor.getValue();
        if (value instanceof float[][][][] arr) {
            return arr;
        }
        var shape = tensor.getInfo().getShape();
        int n = (int) shape[0];
        int c = (int) shape[1];
        int h = (int) shape[2];
        int w = (int) shape[3];
        float[][][][] result = new float[n][c][h][w];
        FloatBuffer floatBuffer = tensor.getFloatBuffer();
        if (floatBuffer == null) {
            throw new OrtException("Detection tensor has no float buffer");
        }
        floatBuffer.rewind();
        for (int in = 0; in < n; in++) {
            for (int ic = 0; ic < c; ic++) {
                for (int ih = 0; ih < h; ih++) {
                    for (int iw = 0; iw < w; iw++) {
                        result[in][ic][ih][iw] = floatBuffer.get();
                    }
                }
            }
        }
        return result;
    }

    /**
     * 将 ONNX Runtime 张量转换为 float 3D 数组 [batch, time, classes]
     */
    private float[][][] onnxToFloatArray3D(OnnxTensor tensor) throws OrtException {
        Object value = tensor.getValue();
        if (value instanceof float[][][] arr) {
            return arr;
        }
        if (value instanceof float[][][][] arr4 && arr4.length > 0 && arr4[0].length == 1) {
            return arr4[0];
        }
        var shape = tensor.getInfo().getShape();
        if (shape.length == 4 && shape[1] == 1) {
            float[][][][] squeezed = onnxToFloatArray4D(tensor);
            return squeezed[0];
        }
        int n = (int) shape[0];
        int h = (int) shape[1];
        int w = (int) shape[2];
        float[][][] result = new float[n][h][w];
        FloatBuffer floatBuffer = tensor.getFloatBuffer();
        if (floatBuffer == null) {
            throw new OrtException("Recognition tensor has no float buffer");
        }
        floatBuffer.rewind();
        for (int in = 0; in < n; in++) {
            for (int ih = 0; ih < h; ih++) {
                for (int iw = 0; iw < w; iw++) {
                    result[in][ih][iw] = floatBuffer.get();
                }
            }
        }
        return result;
    }
}