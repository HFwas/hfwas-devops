package com.hfwas.devops.fileparser.service;

import com.hfwas.devops.fileparser.config.FileParserConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 文件存储服务
 * 负责临时文件的上传存储和定期清理。
 */
@Slf4j
@Service
public class FileStorageService {

    private final FileParserConfig config;

    private Path uploadDir;

    private final AtomicLong totalBytesReceived = new AtomicLong(0);

    public FileStorageService(FileParserConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void init() throws IOException {
        this.uploadDir = Path.of(config.getUploadDir()).toAbsolutePath().normalize();
        Files.createDirectories(uploadDir);
        log.info("File upload directory initialized: {}", uploadDir);
    }

    /**
     * 保存上传的文件到临时目录
     * @param file 上传文件
     * @return 保存后的文件
     */
    public File save(MultipartFile file) throws IOException {
        // 验证文件大小
        if (file.getSize() > config.getMaxFileSize()) {
            throw new IOException("文件大小超过限制: " + file.getSize() +
                    " > " + config.getMaxFileSize());
        }

        // 生成唯一文件名，避免冲突
        String originalName = file.getOriginalFilename();
        String safeName = System.currentTimeMillis() + "_" + sanitizeFileName(originalName != null ? originalName : "unknown");
        Path targetPath = uploadDir.resolve(safeName).normalize();

        // 确保路径在 uploadDir 内（防止路径遍历攻击）
        if (!targetPath.startsWith(uploadDir)) {
            throw new IOException("Invalid file path: " + safeName);
        }

        file.transferTo(targetPath.toFile());
        totalBytesReceived.addAndGet(file.getSize());

        log.info("File saved: {} ({} bytes) -> {}", originalName, file.getSize(), targetPath);
        return targetPath.toFile();
    }

    /**
     * 删除临时文件
     */
    public void delete(File file) {
        try {
            boolean deleted = Files.deleteIfExists(file.toPath());
            if (deleted) {
                log.debug("Temp file deleted: {}", file.getName());
            }
        } catch (IOException e) {
            log.warn("Failed to delete temp file: {}", file.getName(), e);
        }
    }

    /**
     * 定时清理过期临时文件（默认每 1 小时运行一次）
     */
    @Scheduled(fixedRateString = "${file-parser.cleanup-hours:24}",
            initialDelayString = "${file-parser.cleanup-hours:24}")
    public void cleanupOldFiles() {
        long cleanupHours = config.getCleanupHours();
        Instant cutoff = Instant.now().minus(Duration.ofHours(cleanupHours));

        try {
            File[] files = uploadDir.toFile().listFiles();
            if (files == null) return;

            int deleted = 0;
            for (File file : files) {
                if (file.isFile() && file.lastModified() < cutoff.toEpochMilli()) {
                    if (file.delete()) {
                        deleted++;
                    }
                }
            }

            if (deleted > 0) {
                log.info("Cleaned up {} old temp files (older than {}h)", deleted, cleanupHours);
            }
        } catch (Exception e) {
            log.warn("Failed to cleanup temp files", e);
        }
    }

    @PreDestroy
    public void cleanup() {
        log.info("FileStorageService shutting down, total bytes received: {}",
                totalBytesReceived.get());
    }

    private String sanitizeFileName(String fileName) {
        // 移除路径分隔符等危险字符，只保留安全字符
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}