package com.hfwas.devops.image.service.metadata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hfwas.devops.image.config.ImageProcessorConfig;
import com.hfwas.devops.image.dto.ImageMetadataVO;
import com.hfwas.devops.image.service.EngineProbe;
import com.hfwas.devops.image.service.NativeProcessRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class ExifToolReader {

    private static final int MAX_RAW_TAGS = 200;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern LAT = Pattern.compile("GPS Latitude\\s*:\\s*([+-]?[0-9.]+)");
    private static final Pattern LNG = Pattern.compile("GPS Longitude\\s*:\\s*([+-]?[0-9.]+)");
    private static final Pattern MAKE = Pattern.compile("^Make\\s*:\\s*(.+)$", Pattern.MULTILINE);
    private static final Pattern MODEL = Pattern.compile("^Camera Model Name\\s*:\\s*(.+)$", Pattern.MULTILINE);
    private static final Pattern DATE = Pattern.compile("^Date/Time Original\\s*:\\s*(.+)$", Pattern.MULTILINE);
    private static final Pattern ORIENT = Pattern.compile("^Orientation\\s*:\\s*.*?(\\d+)", Pattern.MULTILINE);
    private static final Pattern COLOR = Pattern.compile("^Color Space\\s*:\\s*(.+)$", Pattern.MULTILINE);

    private final ImageProcessorConfig config;
    private final EngineProbe engineProbe;
    private final NativeProcessRunner processRunner;

    public ExifToolReader(ImageProcessorConfig config, EngineProbe engineProbe, NativeProcessRunner processRunner) {
        this.config = config;
        this.engineProbe = engineProbe;
        this.processRunner = processRunner;
    }

    public boolean available() {
        return engineProbe != null && engineProbe.isExiftoolReady();
    }

    public ImageMetadataVO read(Path file, String mime, String ext, int width, int height, int frames, boolean hasAlpha) {
        String jsonOut = processRunner.run(List.of(
                config.getEngines().getExiftoolPath(),
                "-j",
                "-G",
                "-n",
                "-struct",
                "-fast2",
                "-x", "ThumbnailImage",
                "-x", "PreviewImage",
                "-x", "JpgFromRaw",
                "-x", "OtherImage",
                "-x", "PhotoshopThumbnail",
                file.getFileName().toString()
        ), file.getParent());
        try {
            return parseJson(jsonOut, mime, ext, width, height, frames, hasAlpha);
        } catch (Exception e) {
            log.warn("ExifTool JSON parse failed, falling back to text: {}", e.getMessage());
        }
        String out = processRunner.run(List.of(
                config.getEngines().getExiftoolPath(),
                "-n",
                "-GPSLatitude",
                "-GPSLongitude",
                "-Make",
                "-Model",
                "-DateTimeOriginal",
                "-Orientation",
                "-ColorSpace",
                "-s",
                file.getFileName().toString()
        ), file.getParent());
        return parse(out, mime, ext, width, height, frames, hasAlpha);
    }

    ImageMetadataVO parseJson(String json, String mime, String ext, int width, int height, int frames, boolean hasAlpha) {
        try {
            JsonNode root = JSON.readTree(json);
            JsonNode obj = root.isArray() ? (root.size() > 0 ? root.get(0) : JSON.createObjectNode()) : root;
            Map<String, String> rawTags = new LinkedHashMap<>();
            String make = null;
            String model = null;
            String takenAt = null;
            String colorSpace = null;
            String artist = null;
            String copyright = null;
            Integer orientation = null;
            Double lat = null;
            Double lng = null;
            boolean hasFace = false;
            Iterator<Map.Entry<String, JsonNode>> fields = obj.fields();
            while (fields.hasNext() && rawTags.size() < MAX_RAW_TAGS) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = entry.getKey();
                if (skipRawKey(key)) {
                    continue;
                }
                String value = stringify(entry.getValue());
                if (value == null || isBinary(value)) {
                    continue;
                }
                rawTags.put(key, truncate(value, 500));
                String local = localName(key);
                if ("Make".equals(local)) {
                    make = value;
                } else if ("Model".equals(local) || "CameraModelName".equals(local)) {
                    model = value;
                } else if ("DateTimeOriginal".equals(local) || "CreateDate".equals(local)) {
                    takenAt = normalizeDate(value);
                } else if ("Orientation".equals(local)) {
                    orientation = parseInt(value);
                } else if ("ColorSpace".equals(local) || "ColorSpaceData".equals(local)) {
                    colorSpace = normalizeColorSpace(value);
                } else if ("GPSLatitude".equals(local)) {
                    lat = parseDouble(value);
                } else if ("GPSLongitude".equals(local)) {
                    lng = parseDouble(value);
                } else if ("Artist".equals(local) || "Creator".equals(local)) {
                    artist = value;
                } else if ("Copyright".equals(local) || "CopyrightNotice".equals(local) || "Rights".equals(local)) {
                    copyright = value;
                }
                if (isFaceKey(key, value)) {
                    hasFace = true;
                }
            }
            boolean hasGps = lat != null && lng != null;
            return ImageMetadataVO.builder()
                    .pixel(ImageMetadataVO.Pixel.builder()
                            .width(width)
                            .height(height)
                            .colorSpace(colorSpace)
                            .hasAlpha(hasAlpha)
                            .frames(Math.max(1, frames))
                            .build())
                    .format(ImageMetadataVO.Format.builder().mime(mime).ext(ext).build())
                    .capture(ImageMetadataVO.Capture.builder()
                            .make(make)
                            .model(model)
                            .takenAt(takenAt)
                            .orientation(orientation)
                            .build())
                    .copyright((artist != null || copyright != null)
                            ? ImageMetadataVO.Copyright.builder().artist(artist).copyright(copyright).build()
                            : null)
                    .gps(hasGps ? ImageMetadataVO.Gps.builder().lat(lat).lng(lng).build() : null)
                    .privacy(ImageMetadataVO.Privacy.builder().hasGps(hasGps).hasFaceRegions(hasFace).build())
                    .rawTags(rawTags)
                    .orientationApplied(false)
                    .build();
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid exiftool json", e);
        }
    }

    ImageMetadataVO parse(String out, String mime, String ext, int width, int height, int frames, boolean hasAlpha) {
        Double lat = parseDoubleTag(out, "GPSLatitude");
        Double lng = parseDoubleTag(out, "GPSLongitude");
        boolean hasGps = lat != null && lng != null;
        String make = parseStringTag(out, "Make");
        String model = parseStringTag(out, "Model");
        String takenAt = normalizeDate(parseStringTag(out, "DateTimeOriginal"));
        Integer orientation = parseIntTag(out, "Orientation");
        String colorSpace = parseStringTag(out, "ColorSpace");
        if (colorSpace == null) {
            Matcher m = COLOR.matcher(out);
            if (m.find()) {
                colorSpace = m.group(1).trim();
            }
        }
        return ImageMetadataVO.builder()
                .pixel(ImageMetadataVO.Pixel.builder()
                        .width(width)
                        .height(height)
                        .colorSpace(colorSpace)
                        .hasAlpha(hasAlpha)
                        .frames(Math.max(1, frames))
                        .build())
                .format(ImageMetadataVO.Format.builder().mime(mime).ext(ext).build())
                .capture(ImageMetadataVO.Capture.builder()
                        .make(make)
                        .model(model)
                        .takenAt(takenAt)
                        .orientation(orientation)
                        .build())
                .gps(hasGps ? ImageMetadataVO.Gps.builder().lat(lat).lng(lng).build() : null)
                .privacy(ImageMetadataVO.Privacy.builder().hasGps(hasGps).hasFaceRegions(false).build())
                .rawTags(new LinkedHashMap<>())
                .orientationApplied(false)
                .build();
    }

    static boolean skipRawKey(String key) {
        if (key == null) {
            return true;
        }
        String local = localName(key);
        return "SourceFile".equals(local) || "ExifToolVersion".equals(local);
    }

    static boolean isFaceKey(String key, String value) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        if (lower.contains("face") || lower.contains("regioninfo") || lower.contains("regionlist")
                || lower.contains("mwg-rs") || lower.contains("region name")) {
            return value != null && !value.isBlank() && !"0".equals(value);
        }
        return false;
    }

    static String localName(String key) {
        int colon = key.lastIndexOf(':');
        return colon >= 0 ? key.substring(colon + 1) : key;
    }

    private static String stringify(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        try {
            return JSON.writeValueAsString(node);
        } catch (Exception e) {
            return node.toString();
        }
    }

    private static boolean isBinary(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("binary data") || lower.contains("use -b option");
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static String normalizeColorSpace(String value) {
        if (value == null) {
            return null;
        }
        if ("1".equals(value)) {
            return "sRGB";
        }
        if ("2".equals(value)) {
            return "Adobe RGB";
        }
        return value;
    }

    private static String parseStringTag(String out, String tag) {
        Pattern p = Pattern.compile("^" + Pattern.quote(tag) + "\\s*:\\s*(.+)$", Pattern.MULTILINE);
        Matcher m = p.matcher(out);
        if (!m.find()) {
            return fallbackHuman(out, tag);
        }
        String v = m.group(1).trim();
        return v.isEmpty() ? null : v;
    }

    private static String fallbackHuman(String out, String tag) {
        Pattern p = switch (tag) {
            case "Make" -> MAKE;
            case "Model" -> MODEL;
            case "DateTimeOriginal" -> DATE;
            case "Orientation" -> ORIENT;
            case "GPSLatitude" -> LAT;
            case "GPSLongitude" -> LNG;
            default -> null;
        };
        if (p == null) {
            return null;
        }
        Matcher m = p.matcher(out);
        return m.find() ? m.group(1).trim() : null;
    }

    private static Double parseDoubleTag(String out, String tag) {
        return parseDouble(parseStringTag(out, tag));
    }

    private static Integer parseIntTag(String out, String tag) {
        return parseInt(parseStringTag(out, tag));
    }

    private static Double parseDouble(String v) {
        if (v == null) {
            return null;
        }
        try {
            return Double.parseDouble(v.replaceAll("[^0-9.+-]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseInt(String v) {
        if (v == null) {
            return null;
        }
        try {
            return Integer.parseInt(v.replaceAll("[^0-9-]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String normalizeDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        if (s.length() >= 19 && s.charAt(4) == ':') {
            return s.substring(0, 4) + "-" + s.substring(5, 7) + "-" + s.substring(8, 10)
                    + "T" + s.substring(11, 19);
        }
        return s.replaceFirst(" ", "T");
    }

    public boolean hasGps(Path file) {
        try {
            String out = processRunner.run(List.of(
                    config.getEngines().getExiftoolPath(),
                    "-n",
                    "-GPSLatitude",
                    "-s",
                    file.getFileName().toString()
            ), file.getParent());
            return parseStringTag(out, "GPSLatitude") != null;
        } catch (Exception e) {
            log.debug("exiftool gps check failed", e);
            return false;
        }
    }
}
