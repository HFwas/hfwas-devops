package com.hfwas.devops.image.service;

import com.hfwas.devops.image.dto.ImageMetadataVO;
import lombok.Builder;
import lombok.Data;

import java.nio.file.Path;
import java.time.Instant;

@Data
@Builder
public class ImageSession {
    private String sessionId;
    private String originalFileName;
    private long fileSize;
    private String mimeType;
    private String ext;
    private int width;
    private int height;
    private int frames;
    private String colorSpace;
    private boolean hasIcc;
    private Integer orientation;
    private boolean needsServerPreview;
    private boolean orientationApplied;
    private Long userId;
    private Long tenantId;
    private Instant expiresAt;
    private Path directory;
    private Path originalPath;
    private Path previewPath;
    private Path resultPath;
    private String resultMimeType;
    private String resultFileName;
    private int resultWidth;
    private int resultHeight;
    private boolean strippedGps;
    private ImageMetadataVO metadata;
}
