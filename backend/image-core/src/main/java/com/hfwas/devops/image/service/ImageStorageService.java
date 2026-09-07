package com.hfwas.devops.image.service;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.image.config.ImageProcessorConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

@Slf4j
@Service
public class ImageStorageService {

    private final ImageProcessorConfig config;
    private Path root;

    public ImageStorageService(ImageProcessorConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void init() throws IOException {
        this.root = Path.of(config.getTempDir()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        log.info("Image temp directory: {}", root);
    }

    public Path createSessionDir(String sessionId) throws IOException {
        Path dir = root.resolve(sessionId).normalize();
        if (!dir.startsWith(root)) {
            throw new BizException(ResultCode.BAD_REQUEST, "非法会话路径");
        }
        Files.createDirectories(dir);
        return dir;
    }

    public Path saveOriginal(Path sessionDir, MultipartFile file, String ext) throws IOException {
        if (file.getSize() > config.maxFileSizeBytes()) {
            throw new BizException(ResultCode.FILE_INVALID, "文件大小超过限制");
        }
        String safeExt = sanitizeExt(ext);
        Path target = sessionDir.resolve("original" + safeExt).normalize();
        if (!target.startsWith(sessionDir)) {
            throw new BizException(ResultCode.BAD_REQUEST, "非法文件路径");
        }
        file.transferTo(target);
        return target;
    }

    public Path previewPath(Path sessionDir) {
        return sessionDir.resolve("preview.jpg");
    }

    public Path resultPath(Path sessionDir, String ext) {
        return sessionDir.resolve("result" + sanitizeExt(ext));
    }

    public void deleteSessionDir(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.debug("Failed to delete {}", path);
                }
            });
        } catch (IOException e) {
            log.warn("Failed to walk session dir {}", dir, e);
        }
    }

    @Scheduled(fixedDelay = 60_000)
    public void cleanupOrphans() {
        if (root == null || !Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> stream = Files.list(root)) {
            stream.filter(Files::isDirectory).forEach(dir -> {
                try {
                    long ageMs = System.currentTimeMillis() - Files.getLastModifiedTime(dir).toMillis();
                    if (ageMs > config.getSessionTtl().toMillis() * 2) {
                        deleteSessionDir(dir);
                    }
                } catch (IOException ignored) {
                    // skip
                }
            });
        } catch (IOException e) {
            log.debug("Image temp cleanup skipped", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        log.info("ImageStorageService shutting down");
    }

    static String sanitizeExt(String ext) {
        if (ext == null || ext.isBlank()) {
            return "";
        }
        String cleaned = ext.startsWith(".") ? ext.substring(1) : ext;
        cleaned = cleaned.replaceAll("[^a-zA-Z0-9]", "");
        return cleaned.isEmpty() ? "" : "." + cleaned.toLowerCase();
    }
}
