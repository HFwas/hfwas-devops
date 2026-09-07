package com.hfwas.devops.image.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageHealthVO {
    private boolean magick;
    private boolean exiftool;
    private boolean heicDelegate;
}
