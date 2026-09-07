package com.hfwas.devops.image.service.transform;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.image.config.ImageProcessorConfig;
import com.hfwas.devops.image.dto.ImageConvertRequest;
import com.hfwas.devops.image.service.identify.ImageIdentifyService;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Service
public class ImageTransformService {

    private static final Set<String> OUTPUT = Set.of("jpeg", "jpg", "png", "webp", "tif", "tiff");

    private final ImageProcessorConfig config;
    private final ImageMagickTransformer magickTransformer;
    private final ImageIoTransformer imageIoTransformer;

    public ImageTransformService(ImageProcessorConfig config,
                                 ImageMagickTransformer magickTransformer,
                                 ImageIoTransformer imageIoTransformer) {
        this.config = config;
        this.magickTransformer = magickTransformer;
        this.imageIoTransformer = imageIoTransformer;
    }

    public Result convert(Path source, Path target, ImageConvertRequest request) {
        String format = normalizeFormat(request.getTargetFormat());
        if (format == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "不支持的导出格式");
        }
        if (magickTransformer.available()) {
            try {
                ImageMagickTransformer.Result r = magickTransformer.convert(source, target, format, request);
                return new Result(r.getWidth(), r.getHeight(), ImageIdentifyService.mimeForTarget(format));
            } catch (Exception e) {
                log.warn("ImageMagick convert failed, falling back to ImageIO: {}", e.getMessage());
            }
        }
        ImageIoTransformer.Result r = imageIoTransformer.convert(source, target, format, request);
        return new Result(r.getWidth(), r.getHeight(), ImageIdentifyService.mimeForTarget(format));
    }

    public Result writePreviewJpeg(Path source, Path target) {
        int maxSide = Math.max(1, config.getPreview().getMaxSide());
        int quality = Math.min(100, Math.max(1, config.getPreview().getQuality()));
        if (magickTransformer.available()) {
            try {
                ImageMagickTransformer.Result r = magickTransformer.writePreviewJpeg(source, target, maxSide, quality);
                return new Result(r.getWidth(), r.getHeight(), "image/jpeg");
            } catch (Exception e) {
                log.warn("ImageMagick preview failed, falling back to ImageIO: {}", e.getMessage());
            }
        }
        ImageIoTransformer.Result r = imageIoTransformer.writePreviewJpeg(source, target, maxSide, quality);
        return new Result(r.getWidth(), r.getHeight(), "image/jpeg");
    }

    public static String normalizeFormat(String targetFormat) {
        if (targetFormat == null) {
            return null;
        }
        String f = targetFormat.toLowerCase(Locale.ROOT).replace("image/", "");
        if (!OUTPUT.contains(f)) {
            return null;
        }
        return "jpg".equals(f) ? "jpeg" : "tif".equals(f) ? "tiff" : f;
    }

    @Value
    public static class Result {
        int width;
        int height;
        String mimeType;
    }
}
