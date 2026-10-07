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
        Duration timeout
) {
}
