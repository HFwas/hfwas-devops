package com.hfwas.devops.image.service.transform;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.image.dto.ImageConvertRequest;
import com.hfwas.devops.image.service.ImageWorkLimiter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;

@Slf4j
@Component
public class ImageIoTransformer {

    static {
        ImageIO.scanForPlugins();
        ImageIO.setUseCache(true);
    }

    private final ImageWorkLimiter limiter;

    public ImageIoTransformer() {
        this(null);
    }

    @Autowired
    public ImageIoTransformer(ImageWorkLimiter limiter) {
        this.limiter = limiter;
    }

    public Result convert(Path source, Path target, String targetFormat, ImageConvertRequest request) {
        return gated(() -> convertUngated(source, target, targetFormat, request));
    }

    public Result writePreviewJpeg(Path source, Path target, int maxSide, int quality) {
        return gated(() -> writePreviewUngated(source, target, maxSide, quality));
    }

    private Result convertUngated(Path source, Path target, String targetFormat, ImageConvertRequest request) {
        int subsample = 1;
        if (isIdentityGeometry(request.getGeometry()) && request.getMaxSide() != null && request.getMaxSide() > 0) {
            subsample = subsampleForFile(source, request.getMaxSide());
        }
        BufferedImage image = readRaster(source, subsample);
        if (image == null) {
            throw new BizException(ResultCode.FILE_INVALID, "无法解码图片");
        }
        try {
            image = replace(image, GeometryOps.toSrgb(image));
            if (request.isApplyOrientation()) {
                image = replace(image, GeometryOps.applyOrientation(image, readOrientation(source)));
            }
            image = replace(image, GeometryOps.applyGeometry(image, request.getGeometry()));
            image = replace(image, GeometryOps.scaleMaxSide(image, request.getMaxSide()));
            String format = targetFormat.toLowerCase(Locale.ROOT);
            if ("jpg".equals(format) || "jpeg".equals(format)) {
                format = "jpeg";
                image = replace(image, GeometryOps.flattenForJpeg(image));
            }
            write(image, target, format, request.getQuality());
            return new Result(image.getWidth(), image.getHeight());
        } finally {
            if (image != null) {
                image.flush();
            }
        }
    }

    private Result writePreviewUngated(Path source, Path target, int maxSide, int quality) {
        BufferedImage image = readRaster(source, subsampleForFile(source, maxSide));
        if (image == null) {
            throw new BizException(ResultCode.FILE_INVALID, "无法生成预览图");
        }
        try {
            image = replace(image, GeometryOps.toSrgb(image));
            image = replace(image, GeometryOps.applyOrientation(image, readOrientation(source)));
            image = replace(image, GeometryOps.scaleMaxSide(image, maxSide));
            image = replace(image, GeometryOps.flattenForJpeg(image));
            write(image, target, "jpeg", quality);
            return new Result(image.getWidth(), image.getHeight());
        } finally {
            if (image != null) {
                image.flush();
            }
        }
    }

    static int subsampleFactor(int width, int height, int maxSide) {
        if (maxSide <= 0) {
            return 1;
        }
        int longest = Math.max(width, height);
        if (longest <= maxSide) {
            return 1;
        }
        return Math.max(1, longest / maxSide);
    }

    private int subsampleForFile(Path source, int maxSide) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(source.toFile())) {
            if (iis == null) {
                return 1;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return 1;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                return subsampleFactor(reader.getWidth(0), reader.getHeight(0), maxSide);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            return 1;
        }
    }

    private BufferedImage readRaster(Path source, int subsample) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(source.toFile())) {
            if (iis == null) {
                return ImageIO.read(source.toFile());
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return ImageIO.read(source.toFile());
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                ImageReadParam param = reader.getDefaultReadParam();
                if (subsample > 1) {
                    param.setSourceSubsampling(subsample, subsample, 0, 0);
                }
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new BizException(ResultCode.FILE_INVALID, "无法解码图片");
        }
    }

    private <T> T gated(java.util.function.Supplier<T> work) {
        if (limiter == null) {
            return work.get();
        }
        return limiter.call(work);
    }

    private static boolean isIdentityGeometry(ImageConvertRequest.Geometry geometry) {
        if (geometry == null) {
            return true;
        }
        int rotate = ((geometry.getRotate() % 360) + 360) % 360;
        ImageConvertRequest.Crop crop = geometry.getCrop();
        boolean hasCrop = crop != null && crop.getWidth() > 0 && crop.getHeight() > 0;
        return rotate == 0 && !geometry.isFlipX() && !geometry.isFlipY() && !hasCrop;
    }

    private static BufferedImage replace(BufferedImage previous, BufferedImage next) {
        if (previous != null && next != null && next != previous) {
            previous.flush();
        }
        return next;
    }

    private void write(BufferedImage image, Path target, String format, Integer quality) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) {
            throw new BizException(ResultCode.OPERATION_FAILED,
                    "webp".equals(format)
                            ? "WebP 导出需要 ImageMagick（当前环境无 WebP 编码器）"
                            : "不支持导出格式: " + format);
        }
        ImageWriter writer = writers.next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(target.toFile())) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (quality != null && param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                String[] types = param.getCompressionTypes();
                if (types != null && types.length > 0 && param.getCompressionType() == null) {
                    param.setCompressionType(types[0]);
                }
                float q = Math.min(100, Math.max(1, quality)) / 100f;
                try {
                    param.setCompressionQuality(q);
                } catch (UnsupportedOperationException ignored) {
                    // some writers advertise compression but reject quality
                }
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new BizException(ResultCode.OPERATION_FAILED, "写入图片失败");
        } finally {
            writer.dispose();
        }
    }

    static Integer readOrientation(Path source) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(source.toFile());
            ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (ifd0 != null && ifd0.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                return ifd0.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            }
        } catch (Exception e) {
            log.debug("read orientation failed", e);
        }
        return null;
    }

    @Value
    public static class Result {
        int width;
        int height;
    }
}
