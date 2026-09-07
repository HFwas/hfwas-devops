package com.hfwas.devops.image.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageConvertVO {
    private String sessionId;
    private String resultFileName;
    private long resultSize;
    private String mimeType;
    private int width;
    private int height;
    private String downloadUrl;
    private boolean strippedGps;
    /** completed | queued | running | failed */
    @Builder.Default
    private String status = "completed";
    private String jobId;
    private String errorMessage;
}
