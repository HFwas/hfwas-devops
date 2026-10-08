package com.hfwas.devops.container.service.helm;

import java.nio.file.Path;
import java.time.Duration;

public record OciPullRequest(
        String chartRef,
        boolean plainHttp,
        String username,
        String password,
        Path destinationDir,
        Duration timeout
) {
    @Override
    public String toString() {
        return "OciPullRequest[chartRef=" + chartRef + ", plainHttp=" + plainHttp + "]";
    }
}
