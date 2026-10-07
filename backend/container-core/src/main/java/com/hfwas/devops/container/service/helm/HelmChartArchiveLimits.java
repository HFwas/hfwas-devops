package com.hfwas.devops.container.service.helm;

public record HelmChartArchiveLimits(int maxEntries, long maxUncompressedBytes, int maxChartYamlBytes) {
}
