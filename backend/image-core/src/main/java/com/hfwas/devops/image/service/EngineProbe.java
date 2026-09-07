package com.hfwas.devops.image.service;

import com.hfwas.devops.image.config.ImageProcessorConfig;
import com.hfwas.devops.image.dto.ImageHealthVO;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Slf4j
@Getter
@Component
public class EngineProbe {

    private final ImageProcessorConfig config;
    private volatile boolean magickReady;
    private volatile boolean exiftoolReady;
    private volatile boolean heicDelegate;

    public EngineProbe(ImageProcessorConfig config) {
        this.config = config;
    }

    @PostConstruct
    public void probe() {
        ImageProcessorConfig.Engines engines = config.getEngines();
        if (engines.isMagickEnabled()) {
            magickReady = commandSucceeds(engines.getMagickPath(), "-version");
            if (magickReady) {
                String formats = commandOutput(engines.getMagickPath(), "-list", "format");
                heicDelegate = formats != null && formats.toUpperCase(Locale.ROOT).contains("HEIC");
            }
        } else {
            magickReady = false;
            heicDelegate = false;
        }
        exiftoolReady = engines.isExiftoolEnabled() && commandSucceeds(engines.getExiftoolPath(), "-ver");
        log.info("image-processor engines.magick={} heicDelegate={} exiftool={}",
                magickReady, heicDelegate, exiftoolReady);
    }

    public ImageHealthVO health() {
        return ImageHealthVO.builder()
                .magick(magickReady)
                .exiftool(exiftoolReady)
                .heicDelegate(heicDelegate)
                .build();
    }

    private boolean commandSucceeds(String bin, String... args) {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            pb.command().add(bin);
            pb.command().addAll(java.util.List.of(args));
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    private String commandOutput(String bin, String... args) {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            pb.command().add(bin);
            pb.command().addAll(java.util.List.of(args));
            pb.redirectErrorStream(true);
            Process process = pb.start();
            byte[] bytes = process.getInputStream().readAllBytes();
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return "";
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return "";
        }
    }
}
