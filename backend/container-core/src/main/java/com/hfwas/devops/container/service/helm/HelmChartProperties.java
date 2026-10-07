package com.hfwas.devops.container.service.helm;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Default Harbor OCI target for chart upload. Credentials stay in config/env.
 */
@Data
@ConfigurationProperties(prefix = "helm.chart")
public class HelmChartProperties {

    private int maxUploadMb = 50;
    private int maxEntries = 2000;
    private int maxUncompressedMb = 200;
    private String helmBinary = "helm";
    /** Wall clock for one helm invocation (login, show, or push). */
    private int pushTimeoutSeconds = 120;
    private Oci oci = new Oci();

    public HelmChartArchiveLimits archiveLimits() {
        int entries = maxEntries > 0 ? maxEntries : 2000;
        long uncompressed = (maxUncompressedMb > 0 ? maxUncompressedMb : 200) * 1024L * 1024L;
        return new HelmChartArchiveLimits(entries, uncompressed, 1024 * 1024);
    }

    public long maxUploadBytes() {
        int mb = maxUploadMb > 0 ? maxUploadMb : 50;
        return mb * 1024L * 1024L;
    }

    @Data
    public static class Oci {
        /** oci://host/project, or http(s)://host/project */
        private String url = "";
        private String username = "";
        @ToString.Exclude
        private String password = "";
        /** Plain HTTP / insecure TLS. Also implied by an http:// url. */
        private boolean insecure = false;
        /** Row name of the default repository for the current tenant. */
        private String repositoryName = "default";
    }
}
