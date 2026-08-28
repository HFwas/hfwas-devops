package com.hfwas.devops.common.docgen;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.util.Map;

/**
 * 文档生成工具类 - 调用 Python 脚本生成文档
 */
@Slf4j
@Component
public class DocgenUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${docgen.script-path:backend/scripts/generate_doc.py}")
    private String scriptPath;

    @Value("${docgen.python-path:python3}")
    private String pythonPath;

    /**
     * 生成文档，返回文件字节数组
     */
    public byte[] generate(String format, Map<String, Object> data, String outputName) {
        File outputFile = new File(getTempDir(), outputName);
        File dataFile = null;
        try {
            dataFile = File.createTempFile("docgen_", ".json");
            MAPPER.writeValue(dataFile, data);

            String scriptAbsPath = new File(scriptPath).getAbsolutePath();
            ProcessBuilder pb = new ProcessBuilder(
                    pythonPath, scriptAbsPath, format,
                    dataFile.getAbsolutePath(), outputFile.getAbsolutePath()
            );
            pb.redirectErrorStream(true);

            log.info("执行文档生成: {} -> {}", format, outputName);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("文档生成失败，退出码: " + exitCode + "\n" + output);
            }

            log.info("文档生成成功: {}", outputName);
            return Files.readAllBytes(outputFile.toPath());

        } catch (IOException e) {
            throw new RuntimeException("文档生成失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("文档生成被中断", e);
        } finally {
            if (dataFile != null) dataFile.delete();
            if (outputFile.exists()) outputFile.delete();
        }
    }

    /**
     * 生成文档并保存到指定路径
     */
    public void generateToFile(String format, Map<String, Object> data, String outputPath) {
        File dataFile = null;
        try {
            dataFile = File.createTempFile("docgen_", ".json");
            MAPPER.writeValue(dataFile, data);

            String scriptAbsPath = new File(scriptPath).getAbsolutePath();
            ProcessBuilder pb = new ProcessBuilder(
                    pythonPath, scriptAbsPath, format,
                    dataFile.getAbsolutePath(), outputPath
            );
            pb.redirectErrorStream(true);

            log.info("执行文档生成: {} -> {}", format, outputPath);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("文档生成失败，退出码: " + exitCode + "\n" + output);
            }
            log.info("文档生成成功: {}", outputPath);

        } catch (IOException e) {
            throw new RuntimeException("文档生成失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("文档生成被中断", e);
        } finally {
            if (dataFile != null) dataFile.delete();
        }
    }

    private String getTempDir() {
        return System.getProperty("java.io.tmpdir");
    }
}