package com.hfwas.devops.image.service.metadata;

import com.hfwas.devops.image.dto.ImageMetadataVO;
import com.hfwas.devops.image.service.ImageSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Slf4j
@Service
public class ImageMetadataService {

    private final ExifToolReader exifToolReader;
    private final MetadataExtractorReader extractorReader;

    public ImageMetadataService(ExifToolReader exifToolReader, MetadataExtractorReader extractorReader) {
        this.exifToolReader = exifToolReader;
        this.extractorReader = extractorReader;
    }

    public ImageMetadataVO read(ImageSession session) {
        return read(session.getOriginalPath(), session.getMimeType(), session.getExt(),
                session.getWidth(), session.getHeight(), session.getFrames(), session.isHasAlpha());
    }

    public ImageMetadataVO read(Path file, String mime, String ext, int width, int height, int frames, boolean hasAlpha) {
        if (exifToolReader.available()) {
            try {
                ImageMetadataVO vo = exifToolReader.read(file, mime, ext, width, height, frames, hasAlpha);
                vo.setOrientationApplied(false);
                return vo;
            } catch (Exception e) {
                log.warn("ExifTool metadata failed, falling back to metadata-extractor: {}", e.getMessage());
            }
        }
        return extractorReader.read(file, mime, ext, width, height, frames, hasAlpha);
    }
}
