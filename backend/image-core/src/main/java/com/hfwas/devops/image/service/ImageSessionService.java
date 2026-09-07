package com.hfwas.devops.image.service;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.image.config.ImageProcessorConfig;
import com.hfwas.devops.image.dto.ImageConvertRequest;
import com.hfwas.devops.image.dto.ImageConvertVO;
import com.hfwas.devops.image.dto.ImageHistoryVO;
import com.hfwas.devops.image.dto.ImageMetadataVO;
import com.hfwas.devops.image.dto.ImageSessionVO;
import com.hfwas.devops.image.history.service.ImageHistoryService;
import com.hfwas.devops.image.service.identify.ImageIdentifyService;
import com.hfwas.devops.image.service.metadata.ExifToolReader;
import com.hfwas.devops.image.service.metadata.ImageMetadataService;
import com.hfwas.devops.image.service.metadata.MetadataExtractorReader;
import com.hfwas.devops.image.service.transform.ImageTransformService;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ImageSessionService {

    private final ImageProcessorConfig config;
    private final ImageStorageService storageService;
    private final ImageIdentifyService identifyService;
    private final ImageMetadataService metadataService;
    private final ImageTransformService transformService;
    private final MetadataExtractorReader extractorReader;
    private final ExifToolReader exifToolReader;
    private final ImageConvertJobService jobService;
    private final ImageHistoryService historyService;
    private final CurrentUserAccessor currentUserAccessor;
    private final Map<String, ImageSession> sessions = new ConcurrentHashMap<>();

    public ImageSessionService(ImageProcessorConfig config,
                               ImageStorageService storageService,
                               ImageIdentifyService identifyService,
                               ImageMetadataService metadataService,
                               ImageTransformService transformService,
                               MetadataExtractorReader extractorReader,
                               ExifToolReader exifToolReader,
                               ImageConvertJobService jobService,
                               @Autowired(required = false) ImageHistoryService historyService,
                               @Autowired(required = false) CurrentUserAccessor currentUserAccessor) {
        this.config = config;
        this.storageService = storageService;
        this.identifyService = identifyService;
        this.metadataService = metadataService;
        this.transformService = transformService;
        this.extractorReader = extractorReader;
        this.exifToolReader = exifToolReader;
        this.jobService = jobService;
        this.historyService = historyService;
        this.currentUserAccessor = currentUserAccessor;
    }

    public ImageSessionVO create(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.FILE_INVALID, "上传文件为空");
        }
        if (file.getSize() > config.maxFileSizeBytes()) {
            throw new BizException(ResultCode.FILE_INVALID, "文件大小超过限制");
        }
        String sessionId = Ulids.next();
        Path dir;
        try {
            dir = storageService.createSessionDir(sessionId);
        } catch (IOException e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "无法创建会话目录");
        }
        Path original;
        try {
            original = storageService.saveOriginal(dir, file, "bin");
        } catch (IOException e) {
            storageService.deleteSessionDir(dir);
            throw new BizException(ResultCode.FILE_READ_FAILED, "保存上传文件失败");
        }
        ImageIdentifyService.IdentifyResult identified;
        try {
            identified = identifyService.identify(original);
        } catch (BizException e) {
            storageService.deleteSessionDir(dir);
            throw e;
        } catch (IOException e) {
            storageService.deleteSessionDir(dir);
            throw new BizException(ResultCode.FILE_INVALID, "无法识别图片");
        }
        Path renamed = dir.resolve("original." + identified.getExt());
        try {
            if (!original.equals(renamed)) {
                Files.move(original, renamed);
                original = renamed;
            }
        } catch (IOException e) {
            storageService.deleteSessionDir(dir);
            throw new BizException(ResultCode.INTERNAL_ERROR, "无法整理会话文件");
        }
        Instant expiresAt = Instant.now().plus(config.getSessionTtl());
        ImageSession session = ImageSession.builder()
                .sessionId(sessionId)
                .originalFileName(file.getOriginalFilename() == null ? "image." + identified.getExt() : file.getOriginalFilename())
                .fileSize(file.getSize())
                .mimeType(identified.getMimeType())
                .ext(identified.getExt())
                .width(identified.getWidth())
                .height(identified.getHeight())
                .frames(identified.getFrames())
                .colorSpace(identified.getColorSpace())
                .hasIcc(identified.isHasIcc())
                .orientation(identified.getOrientation())
                .needsServerPreview(identified.isNeedsServerPreview())
                .userId(currentUserAccessor == null ? null : currentUserAccessor.currentUserId())
                .tenantId(currentUserAccessor == null ? null : currentUserAccessor.currentTenantId())
                .expiresAt(expiresAt)
                .directory(dir)
                .originalPath(original)
                .build();
        if (session.isNeedsServerPreview()) {
            generatePreview(session);
        }
        try {
            session.setMetadata(overlayIdentify(metadataService.read(session), session));
        } catch (Exception e) {
            log.warn("Metadata read failed for {}: {}", sessionId, e.getMessage());
        }
        Integer orientation = sessionOrientation(session);
        session.setOrientation(orientation);
        if (shouldMarkOrientationApplied(orientation, session.getPreviewPath() != null)
                || (orientation != null && orientation != 1 && session.getPreviewPath() == null)) {
            if (session.getPreviewPath() == null) {
                session.setNeedsServerPreview(true);
                generatePreview(session);
            }
            session.setOrientationApplied(session.getPreviewPath() != null);
            if (session.getMetadata() != null) {
                session.getMetadata().setOrientationApplied(session.isOrientationApplied());
            }
        }
        sessions.put(sessionId, session);
        return toVo(session);
    }

    public ImageSessionVO get(String sessionId) {
        return toVo(require(sessionId));
    }

    public ImageMetadataVO metadata(String sessionId) {
        ImageSession session = require(sessionId);
        if (session.getMetadata() == null) {
            session.setMetadata(overlayIdentify(metadataService.read(session), session));
        }
        session.getMetadata().setOrientationApplied(session.isOrientationApplied());
        return session.getMetadata();
    }

    public Path previewFile(String sessionId) {
        ImageSession session = require(sessionId);
        if (session.getPreviewPath() != null && Files.exists(session.getPreviewPath())) {
            return session.getPreviewPath();
        }
        return session.getOriginalPath();
    }

    public String previewMime(String sessionId) {
        ImageSession session = require(sessionId);
        if (session.getPreviewPath() != null && Files.exists(session.getPreviewPath())) {
            return "image/jpeg";
        }
        return session.getMimeType();
    }

    public ImageConvertVO convert(String sessionId, ImageConvertRequest request) {
        ImageSession session = require(sessionId);
        if (shouldAsync(session)) {
            return jobService.enqueue(sessionId, () -> convertSync(require(sessionId), request));
        }
        return convertSync(session, request);
    }

    public ImageConvertVO job(String sessionId, String jobId) {
        require(sessionId);
        return jobService.require(sessionId, jobId);
    }

    public List<ImageConvertVO> batchConvert(List<String> sessionIds, ImageConvertRequest request) {
        List<ImageConvertVO> results = new ArrayList<>();
        for (String sessionId : sessionIds) {
            results.add(convert(sessionId, request));
        }
        return results;
    }

    public List<ImageHistoryVO> history(int limit) {
        if (historyService == null) {
            return List.of();
        }
        return historyService.listRecent(limit);
    }

    public Path resultFile(String sessionId) {
        ImageSession session = require(sessionId);
        if (session.getResultPath() == null || !Files.exists(session.getResultPath())) {
            throw new BizException(ResultCode.NOT_FOUND, "尚无转换结果");
        }
        return session.getResultPath();
    }

    public String resultFileName(String sessionId) {
        return require(sessionId).getResultFileName();
    }

    public String resultMime(String sessionId) {
        ImageSession session = require(sessionId);
        return session.getResultMimeType() == null ? "application/octet-stream" : session.getResultMimeType();
    }

    public void delete(String sessionId) {
        ImageSession session = sessions.remove(sessionId);
        if (session != null) {
            jobService.evictSession(sessionId);
            storageService.deleteSessionDir(session.getDirectory());
        }
    }

    @Scheduled(fixedDelay = 30_000)
    public void evictExpired() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, ImageSession>> it = sessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, ImageSession> entry = it.next();
            if (entry.getValue().getExpiresAt().isBefore(now)) {
                jobService.evictSession(entry.getKey());
                storageService.deleteSessionDir(entry.getValue().getDirectory());
                it.remove();
            }
        }
    }

    ImageSession require(String sessionId) {
        ImageSession session = sessions.get(sessionId);
        if (session == null || session.getExpiresAt().isBefore(Instant.now())) {
            if (session != null) {
                jobService.evictSession(sessionId);
                sessions.remove(sessionId);
                storageService.deleteSessionDir(session.getDirectory());
            }
            throw new BizException(ResultCode.NOT_FOUND, "会话不存在或已过期");
        }
        return session;
    }

    static boolean shouldMarkOrientationApplied(Integer orientation, boolean previewGenerated) {
        return previewGenerated && orientation != null && orientation != 1;
    }

    private boolean shouldAsync(ImageSession session) {
        long pixels = (long) session.getWidth() * (long) session.getHeight();
        return pixels >= config.getAsyncMinPixels();
    }

    private ImageConvertVO convertSync(ImageSession session, ImageConvertRequest request) {
        String mime = ImageIdentifyService.mimeForTarget(ImageTransformService.normalizeFormat(request.getTargetFormat()));
        if (mime == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "不支持的导出格式");
        }
        String ext = ImageIdentifyService.extForMime(mime);
        Path result = storageService.resultPath(session.getDirectory(), ext);
        try {
            Files.deleteIfExists(result);
        } catch (IOException ignored) {
            // overwrite
        }
        ImageTransformService.Result transformed = transformService.convert(session.getOriginalPath(), result, request);
        session.setResultPath(result);
        session.setResultMimeType(mime);
        session.setResultFileName(baseName(session.getOriginalFileName()) + "." + ext);
        session.setResultWidth(transformed.getWidth());
        session.setResultHeight(transformed.getHeight());
        session.setStrippedGps(request.isStripMetadata() && !hasGps(result));
        long size;
        try {
            size = Files.size(result);
        } catch (IOException e) {
            throw new BizException(ResultCode.OPERATION_FAILED, "无法读取转换结果");
        }
        ImageConvertVO vo = ImageConvertVO.builder()
                .sessionId(session.getSessionId())
                .resultFileName(session.getResultFileName())
                .resultSize(size)
                .mimeType(mime)
                .width(transformed.getWidth())
                .height(transformed.getHeight())
                .downloadUrl("/api/image/sessions/" + session.getSessionId() + "/result")
                .strippedGps(session.isStrippedGps())
                .status("completed")
                .build();
        if (historyService != null) {
            historyService.record(session, vo);
        }
        return vo;
    }

    private void generatePreview(ImageSession session) {
        try {
            Path preview = storageService.previewPath(session.getDirectory());
            transformService.writePreviewJpeg(session.getOriginalPath(), preview);
            session.setPreviewPath(preview);
        } catch (Exception e) {
            log.warn("Server preview generation failed for {}: {}", session.getSessionId(), e.getMessage());
        }
    }

    private ImageMetadataVO overlayIdentify(ImageMetadataVO vo, ImageSession session) {
        if (vo == null) {
            return null;
        }
        if (vo.getPixel() != null) {
            if ((vo.getPixel().getColorSpace() == null || vo.getPixel().getColorSpace().isBlank())
                    && session.getColorSpace() != null) {
                vo.getPixel().setColorSpace(session.getColorSpace());
            }
            vo.getPixel().setHasIcc(session.isHasIcc());
        }
        vo.setOrientationApplied(session.isOrientationApplied());
        return vo;
    }

    private static Integer sessionOrientation(ImageSession session) {
        if (session.getOrientation() != null) {
            return session.getOrientation();
        }
        if (session.getMetadata() != null && session.getMetadata().getCapture() != null) {
            return session.getMetadata().getCapture().getOrientation();
        }
        return null;
    }

    private ImageSessionVO toVo(ImageSession session) {
        return ImageSessionVO.builder()
                .sessionId(session.getSessionId())
                .fileName(session.getOriginalFileName())
                .fileSize(session.getFileSize())
                .mimeType(session.getMimeType())
                .width(session.getWidth())
                .height(session.getHeight())
                .needsServerPreview(session.isNeedsServerPreview())
                .previewUrl("/api/image/sessions/" + session.getSessionId() + "/preview")
                .expiresAt(session.getExpiresAt())
                .build();
    }

    private boolean hasGps(Path file) {
        if (exifToolReader.available()) {
            try {
                return exifToolReader.hasGps(file);
            } catch (Exception e) {
                log.debug("exiftool strip check failed", e);
            }
        }
        try {
            ImageMetadataVO vo = extractorReader.read(file, "image/jpeg", "jpg", 1, 1, 1, false);
            return vo.getPrivacy() != null && vo.getPrivacy().isHasGps();
        } catch (Exception e) {
            return false;
        }
    }

    static String baseName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "image";
        }
        String name = Path.of(fileName).getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
