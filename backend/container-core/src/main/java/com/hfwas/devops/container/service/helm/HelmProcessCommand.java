package com.hfwas.devops.container.service.helm;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public record HelmProcessCommand(
        List<String> argv,
        byte[] stdin,
        Map<String, String> environment,
        Path workDir,
        Duration timeout,
        int maxCaptureBytes,
        boolean failIfTooLarge
) {
    /** Push and other short commands keep the historical 64 KiB capture and truncate quietly. */
    public HelmProcessCommand(
            List<String> argv,
            byte[] stdin,
            Map<String, String> environment,
            Path workDir,
            Duration timeout) {
        this(argv, stdin, environment, workDir, timeout, DefaultHelmProcessRunner.MAX_CAPTURE_BYTES, false);
    }

    /** Release and chart-content commands fail instead of returning a cut-off manifest. */
    public static HelmProcessCommand captured(
            List<String> argv,
            byte[] stdin,
            Map<String, String> environment,
            Path workDir,
            Duration timeout) {
        return new HelmProcessCommand(argv, stdin, environment, workDir, timeout, 2 * 1024 * 1024, true);
    }
}
