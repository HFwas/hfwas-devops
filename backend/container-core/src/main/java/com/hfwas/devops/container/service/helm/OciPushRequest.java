package com.hfwas.devops.container.service.helm;

import java.nio.file.Path;

/**
 * Push request. {@link #toString()} omits the registry password.
 */
public final class OciPushRequest {

    private final Path archive;
    private final OciChartReference reference;
    private final String username;
    private final String password;
    private final String chartName;
    private final String version;

    public OciPushRequest(Path archive, OciChartReference reference, String username, String password,
                          String chartName, String version) {
        this.archive = archive;
        this.reference = reference;
        this.username = username == null ? "" : username;
        this.password = password == null ? "" : password;
        this.chartName = chartName;
        this.version = version;
    }

    public Path archive() {
        return archive;
    }

    public OciChartReference reference() {
        return reference;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    public String chartName() {
        return chartName;
    }

    public String version() {
        return version;
    }

    @Override
    public String toString() {
        return "OciPushRequest{chart=" + chartName + ":" + version
                + ", registry=" + (reference == null ? "" : reference.registryBase()) + "}";
    }
}
