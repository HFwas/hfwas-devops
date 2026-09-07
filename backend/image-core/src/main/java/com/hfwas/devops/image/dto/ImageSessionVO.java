package com.hfwas.devops.image.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageSessionVO {
    private String sessionId;
    private String fileName;
    private long fileSize;
    private String mimeType;
    private int width;
    private int height;
    private int orientedWidth;
    private int orientedHeight;
    private int previewWidth;
    private int previewHeight;
    private boolean needsServerPreview;
    private String previewUrl;
    private Instant expiresAt;
}
