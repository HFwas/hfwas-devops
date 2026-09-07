package com.hfwas.devops.image.service.transform;

import com.hfwas.devops.image.config.ImageProcessorConfig;
import com.hfwas.devops.image.dto.ImageConvertRequest;
import com.hfwas.devops.image.service.EngineProbe;
import com.hfwas.devops.image.service.NativeProcessRunner;
import lombok.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class ImageMagickTransformer {

    private final ImageProcessorConfig config;
    private final EngineProbe engineProbe;
    private final NativeProcessRunner processRunner;

    public ImageMagickTransformer(ImageProcessorConfig config,
                                  EngineProbe engineProbe,
                                  NativeProcessRunner processRunner) {
        this.config = config;
        this.engineProbe = engineProbe;
        this.processRunner = processRunner;
    }

    public boolean available() {
        return engineProbe.isMagickReady();
    }

    public Result convert(Path source, Path target, String targetFormat, ImageConvertRequest request) {
        List<String> cmd = buildConvertCommand(
                config.getEngines().getMagickPath(),
                source.getFileName().toString(),
                target.getFileName().toString(),
                targetFormat,
                request);
        processRunner.run(cmd, source.getParent(), config.getEngines().getConvertTimeout());
        return readSize(target);
    }

    static List<String> buildConvertCommand(String magickPath, String sourceName, String targetName,
                                            String targetFormat, ImageConvertRequest request) {
        List<String> cmd = new ArrayList<>();
        cmd.add(magickPath);
        addResourceLimits(cmd);
        cmd.add(sourceName);
        if (request.isApplyOrientation()) {
            cmd.add("-auto-orient");
        }
        cmd.add("-colorspace");
        cmd.add("sRGB");
        ImageConvertRequest.Geometry geometry = request.getGeometry();
        if (geometry != null) {
            int rotate = ((geometry.getRotate() % 360) + 360) % 360;
            if (rotate != 0) {
                cmd.add("-rotate");
                cmd.add(String.valueOf(rotate));
            }
            if (geometry.isFlipX()) {
                cmd.add("-flop");
            }
            if (geometry.isFlipY()) {
                cmd.add("-flip");
            }
            ImageConvertRequest.Crop crop = geometry.getCrop();
            if (crop != null && crop.getWidth() > 0 && crop.getHeight() > 0) {
                cmd.add("-crop");
                cmd.add(crop.getWidth() + "x" + crop.getHeight() + "+" + crop.getX() + "+" + crop.getY());
                cmd.add("+repage");
            }
        }
        if (request.getMaxSide() != null && request.getMaxSide() > 0) {
            cmd.add("-resize");
            cmd.add(request.getMaxSide() + "x" + request.getMaxSide() + ">");
        }
        String format = targetFormat.toLowerCase(Locale.ROOT);
        if ("jpg".equals(format) || "jpeg".equals(format) || "webp".equals(format)) {
            int q = request.getQuality() == null ? 85 : request.getQuality();
            cmd.add("-quality");
            cmd.add(String.valueOf(q));
        }
        if ("jpg".equals(format) || "jpeg".equals(format)) {
            cmd.add("-background");
            cmd.add("white");
            cmd.add("-alpha");
            cmd.add("remove");
            cmd.add("-alpha");
            cmd.add("off");
        }
        if (request.isStripMetadata()) {
            cmd.add("-strip");
        }
        cmd.add(targetName);
        return cmd;
    }

    public Result writePreviewJpeg(Path source, Path target, int maxSide, int quality) {
        List<String> cmd = buildPreviewCommand(
                config.getEngines().getMagickPath(),
                source.getFileName().toString(),
                target.getFileName().toString(),
                maxSide,
                quality);
        processRunner.run(cmd, source.getParent(), config.getEngines().getPreviewTimeout());
        return readSize(target);
    }

    static List<String> buildPreviewCommand(String magickPath, String sourceName, String targetName,
                                            int maxSide, int quality) {
        List<String> cmd = new ArrayList<>();
        cmd.add(magickPath);
        addResourceLimits(cmd);
        cmd.add(sourceName);
        cmd.add("-auto-orient");
        cmd.add("-colorspace");
        cmd.add("sRGB");
        cmd.add("-resize");
        cmd.add(maxSide + "x" + maxSide + ">");
        cmd.add("-quality");
        cmd.add(String.valueOf(quality));
        cmd.add("-background");
        cmd.add("white");
        cmd.add("-alpha");
        cmd.add("remove");
        cmd.add(targetName);
        return cmd;
    }

    static void addResourceLimits(List<String> cmd) {
        cmd.add("-limit");
        cmd.add("memory");
        cmd.add("256MiB");
        cmd.add("-limit");
        cmd.add("map");
        cmd.add("512MiB");
        cmd.add("-limit");
        cmd.add("area");
        cmd.add("40MP");
    }

    private Result readSize(Path target) {
        String out = processRunner.runUnlocked(List.of(
                config.getEngines().getMagickPath(),
                "identify",
                "-limit", "memory", "32MiB",
                "-format", "%w %h",
                target.getFileName().toString()
        ), target.getParent(), config.getEngines().getProcessTimeout());
        String[] parts = out.trim().split("\\s+");
        return new Result(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    @Value
    public static class Result {
        int width;
        int height;
    }
}
