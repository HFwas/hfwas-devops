package com.hfwas.devops.image.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ImageConvertRequest {

    @NotBlank
    private String targetFormat;

    @Min(1)
    @Max(100)
    private Integer quality = 85;

    /** 最长边；null 表示不缩放 */
    @Min(1)
    private Integer maxSide;

    private boolean stripMetadata = true;

    private boolean applyOrientation = true;

    @Valid
    private Geometry geometry;

    @Data
    public static class Geometry {
        private int rotate;
        private boolean flipX;
        private boolean flipY;
        @Valid
        private Crop crop;
    }

    @Data
    public static class Crop {
        private int x;
        private int y;
        @Min(1)
        private int width;
        @Min(1)
        private int height;
    }
}
