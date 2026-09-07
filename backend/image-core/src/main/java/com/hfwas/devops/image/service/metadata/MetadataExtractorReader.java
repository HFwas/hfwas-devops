package com.hfwas.devops.image.service.metadata;

import com.drew.imaging.ImageMetadataReader;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.hfwas.devops.image.dto.ImageMetadataVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;

@Slf4j
@Component
public class MetadataExtractorReader {

    public ImageMetadataVO read(Path file, String mime, String ext, int width, int height, int frames, boolean hasAlpha) {
        ImageMetadataVO.ImageMetadataVOBuilder builder = ImageMetadataVO.builder()
                .pixel(ImageMetadataVO.Pixel.builder()
                        .width(width)
                        .height(height)
                        .colorSpace(null)
                        .hasAlpha(hasAlpha)
                        .frames(Math.max(1, frames))
                        .build())
                .format(ImageMetadataVO.Format.builder().mime(mime).ext(ext).build())
                .privacy(ImageMetadataVO.Privacy.builder().hasGps(false).hasFaceRegions(false).build())
                .rawTags(new LinkedHashMap<>())
                .orientationApplied(false);
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file.toFile());
            applyExif(builder, metadata);
        } catch (Exception e) {
            log.debug("metadata-extractor failed for {}", file, e);
        }
        return builder.build();
    }

    private void applyExif(ImageMetadataVO.ImageMetadataVOBuilder builder, Metadata metadata) {
        ImageMetadataVO.Capture.CaptureBuilder capture = ImageMetadataVO.Capture.builder();
        String artist = null;
        String copyright = null;
        ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
        if (ifd0 != null) {
            capture.make(ifd0.getString(ExifIFD0Directory.TAG_MAKE));
            capture.model(ifd0.getString(ExifIFD0Directory.TAG_MODEL));
            artist = ifd0.getString(ExifIFD0Directory.TAG_ARTIST);
            copyright = ifd0.getString(ExifIFD0Directory.TAG_COPYRIGHT);
            if (ifd0.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                try {
                    capture.orientation(ifd0.getInt(ExifIFD0Directory.TAG_ORIENTATION));
                } catch (Exception ignored) {
                    // skip
                }
            }
        }
        ExifSubIFDDirectory sub = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
        if (sub != null) {
            Date original = sub.getDateOriginal();
            if (original != null) {
                SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
                fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
                capture.takenAt(fmt.format(original));
            }
        }
        builder.capture(capture.build());
        if (artist != null || copyright != null) {
            builder.copyright(ImageMetadataVO.Copyright.builder().artist(artist).copyright(copyright).build());
        }

        GpsDirectory gpsDir = metadata.getFirstDirectoryOfType(GpsDirectory.class);
        GeoLocation geo = gpsDir != null ? gpsDir.getGeoLocation() : null;
        boolean hasGps = geo != null && !geo.isZero();
        if (hasGps) {
            builder.gps(ImageMetadataVO.Gps.builder().lat(geo.getLatitude()).lng(geo.getLongitude()).build());
        }
        boolean hasFace = false;
        Map<String, String> rawTags = new LinkedHashMap<>();
        for (Directory dir : metadata.getDirectories()) {
            for (Tag tag : dir.getTags()) {
                if (rawTags.size() >= 80) {
                    break;
                }
                String desc = tag.getDescription();
                if (desc == null || desc.isBlank()) {
                    continue;
                }
                String key = dir.getName() + ":" + tag.getTagName();
                rawTags.put(key, desc.length() > 500 ? desc.substring(0, 500) : desc);
                String lower = key.toLowerCase();
                if (lower.contains("face") || lower.contains("region")) {
                    hasFace = true;
                }
            }
        }
        builder.rawTags(rawTags);
        builder.privacy(ImageMetadataVO.Privacy.builder().hasGps(hasGps).hasFaceRegions(hasFace).build());
    }
}
