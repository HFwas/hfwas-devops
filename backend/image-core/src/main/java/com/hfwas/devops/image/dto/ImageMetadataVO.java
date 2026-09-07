package com.hfwas.devops.image.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageMetadataVO {

    private Pixel pixel;
    private Format format;
    private Capture capture;
    private Gps gps;
    private Privacy privacy;
    private Copyright copyright;
    @Builder.Default
    private Map<String, String> rawTags = new LinkedHashMap<>();
    /** 预览画面是否已按 Orientation 摆正 */
    private boolean orientationApplied;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Pixel {
        private int width;
        private int height;
        private String colorSpace;
        private boolean hasAlpha;
        private int frames;
        private boolean hasIcc;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Format {
        private String mime;
        private String ext;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Capture {
        private String make;
        private String model;
        private String takenAt;
        private Integer orientation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Gps {
        private Double lat;
        private Double lng;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Privacy {
        private boolean hasGps;
        private boolean hasFaceRegions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Copyright {
        private String artist;
        private String copyright;
    }
}
