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
public class ImageHistoryVO {
    private Long id;
    private String sessionId;
    private String fileName;
    private String sourceMime;
    private String targetFormat;
    private String resultFileName;
    private Long resultSize;
    private Integer width;
    private Integer height;
    private boolean strippedGps;
    private String status;
    private Instant createTime;
}
