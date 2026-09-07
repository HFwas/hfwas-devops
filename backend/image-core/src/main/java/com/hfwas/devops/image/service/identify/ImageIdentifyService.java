package com.hfwas.devops.image.service.identify;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.image.config.ImageProcessorConfig;
import com.hfwas.devops.image.service.EngineProbe;
import com.hfwas.devops.image.service.NativeProcessRunner;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.stream.ImageInputStream;
import java.awt.color.ColorSpace;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
public class ImageIdentifyService {

    private static final Set<String> SUPPORTED = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif",
            "image/bmp", "image/heic", "image/heif", "image/tiff");

    private static final Set<String> SERVER_PREVIEW = Set.of(
            "image/heic", "image/heif", "image/tiff");

    private final ImageProcessorConfig config;
    private final EngineProbe engineProbe;
    private final NativeProcessRunner processRunner;
    private final Tika tika = new Tika();

    public ImageIdentifyService(ImageProcessorConfig config,
                                EngineProbe engineProbe,
                                NativeProcessRunner processRunner) {
        this.config = config;
        this.engineProbe = engineProbe;
        this.processRunner = processRunner;
    }

    public IdentifyResult identify(Path file) throws IOException {
        byte[] header = readHeader(file);
        String mime = MagicBytes.detectMime(header);
        if (mime == null) {
            try {
                mime = tika.detect(file);
            } catch (Exception e) {
                log.debug("Tika detect failed", e);
            }
        }
        mime = normalizeMime(mime);
        if (mime == null || !SUPPORTED.contains(mime)) {
            throw new BizException(ResultCode.FILE_INVALID, "不支持的图片格式");
        }

        Dimensions dims = readWithImageIo(file, mime);
        boolean imageIoFailed = dims == null;
        if (dims == null && engineProbe.isMagickReady()) {
            dims = readWithMagick(file);
        }
        if (dims == null) {
            throw new BizException(ResultCode.FILE_INVALID, "无法读取图片尺寸");
        }
        long pixels = (long) dims.width() * (long) dims.height();
        if (pixels > config.getMaxPixels()) {
            throw new BizException(ResultCode.FILE_INVALID, "图片像素超过限制");
        }
        boolean needsServerPreview = needsServerPreview(
                mime, dims.colorSpace(), dims.hasIcc(), dims.orientation(), imageIoFailed);
        return IdentifyResult.builder()
                .mimeType(mime)
                .ext(extForMime(mime))
                .width(dims.width())
                .height(dims.height())
                .frames(dims.frames())
                .colorSpace(dims.colorSpace())
                .hasIcc(dims.hasIcc())
                .orientation(dims.orientation())
                .needsServerPreview(needsServerPreview)
                .build();
    }

    static boolean needsServerPreview(String mime, String colorSpace, boolean hasIcc,
                                      Integer orientation, boolean imageIoFailed) {
        if (imageIoFailed) {
            return true;
        }
        if (SERVER_PREVIEW.contains(mime)) {
            return true;
        }
        if (isCmykOrNonSrgb(colorSpace)) {
            return true;
        }
        if (hasIcc && colorSpace != null && isCmykOrNonSrgb(colorSpace)) {
            return true;
        }
        return orientation != null && orientation != 1;
    }

    static boolean isCmykOrNonSrgb(String colorSpace) {
        if (colorSpace == null || colorSpace.isBlank()) {
            return false;
        }
        String c = colorSpace.toLowerCase(Locale.ROOT);
        if (c.contains("cmyk")) {
            return true;
        }
        if (c.contains("srgb") || c.equals("rgb") || c.contains("gray") || c.contains("grey")) {
            return false;
        }
        return true;
    }

    private Dimensions readWithImageIo(Path file, String mime) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(file.toFile())) {
            if (iis == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                int frames = 1;
                try {
                    frames = Math.max(1, reader.getNumImages(true));
                } catch (Exception ignored) {
                    frames = 1;
                }
                String colorSpace = colorSpaceFromReader(reader);
                return new Dimensions(width, height, frames, colorSpace, false, null);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            log.debug("ImageIO identify failed for {}", file, e);
            return null;
        }
    }

    private static String colorSpaceFromReader(ImageReader reader) {
        try {
            Iterator<ImageTypeSpecifier> types = reader.getImageTypes(0);
            if (!types.hasNext()) {
                return null;
            }
            ColorSpace cs = types.next().getColorModel().getColorSpace();
            if (cs == null) {
                return null;
            }
            return switch (cs.getType()) {
                case ColorSpace.TYPE_CMYK -> "CMYK";
                case ColorSpace.TYPE_GRAY -> "Gray";
                case ColorSpace.TYPE_RGB -> "sRGB";
                default -> cs.getType() == ColorSpace.TYPE_3CLR ? "Unknown" : null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    private Dimensions readWithMagick(Path file) {
        String out = processRunner.run(
                List.of(config.getEngines().getMagickPath(), "identify", "-format",
                        "%w\t%h\t%n\t%[colorspace]\t%[profiles]\t%[EXIF:Orientation]",
                        file.getFileName().toString()),
                file.getParent());
        return parseMagickIdentify(out);
    }

    static Dimensions parseMagickIdentify(String out) {
        if (out == null || out.isBlank()) {
            return null;
        }
        String line = out.trim().split("\\R")[0];
        String[] tabs = line.split("\t", -1);
        if (tabs.length >= 4) {
            int width = Integer.parseInt(tabs[0].trim());
            int height = Integer.parseInt(tabs[1].trim());
            int frames = parsePositive(tabs[2], 1);
            String colorSpace = blankToNull(tabs[3]);
            boolean hasIcc = tabs.length > 4 && tabs[4].toLowerCase(Locale.ROOT).contains("icc");
            Integer orientation = tabs.length > 5 ? parseOrientation(tabs[5]) : null;
            return new Dimensions(width, height, frames, colorSpace, hasIcc, orientation);
        }
        String[] parts = line.trim().split("\\s+");
        if (parts.length < 2) {
            return null;
        }
        int width = Integer.parseInt(parts[0]);
        int height = Integer.parseInt(parts[1]);
        int frames = parts.length >= 3 ? Math.max(1, Integer.parseInt(parts[2])) : 1;
        String colorSpace = parts.length >= 4 ? parts[3] : null;
        return new Dimensions(width, height, frames, colorSpace, false, null);
    }

    private static int parsePositive(String raw, int fallback) {
        try {
            return Math.max(1, Integer.parseInt(raw.trim()));
        } catch (Exception e) {
            return fallback;
        }
    }

    private static Integer parseOrientation(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim().replaceAll("[^0-9-]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String blankToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        return t.isEmpty() ? null : t;
    }

    private static byte[] readHeader(Path file) throws IOException {
        byte[] buf = new byte[32];
        try (var in = Files.newInputStream(file)) {
            int n = in.read(buf);
            if (n <= 0) {
                return new byte[0];
            }
            if (n < buf.length) {
                byte[] sliced = new byte[n];
                System.arraycopy(buf, 0, sliced, 0, n);
                return sliced;
            }
            return buf;
        }
    }

    static String normalizeMime(String mime) {
        if (mime == null) {
            return null;
        }
        String lower = mime.toLowerCase(Locale.ROOT);
        if ("image/jpg".equals(lower)) {
            return "image/jpeg";
        }
        if ("image/x-bmp".equals(lower) || "image/x-ms-bmp".equals(lower)) {
            return "image/bmp";
        }
        if ("image/heif".equals(lower)) {
            return "image/heic";
        }
        return lower;
    }

    public static String extForMime(String mime) {
        return switch (mime) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/gif" -> "gif";
            case "image/bmp" -> "bmp";
            case "image/heic", "image/heif" -> "heic";
            case "image/tiff" -> "tif";
            default -> "img";
        };
    }

    public static String mimeForTarget(String format) {
        String f = format == null ? "" : format.toLowerCase(Locale.ROOT);
        return switch (f) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "tif", "tiff" -> "image/tiff";
            default -> null;
        };
    }

    public record Dimensions(int width, int height, int frames, String colorSpace, boolean hasIcc, Integer orientation) {
    }

    @Value
    @Builder
    public static class IdentifyResult {
        String mimeType;
        String ext;
        int width;
        int height;
        int frames;
        String colorSpace;
        boolean hasIcc;
        Integer orientation;
        boolean needsServerPreview;
    }
}
