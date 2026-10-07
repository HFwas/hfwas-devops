package com.hfwas.devops.container.service.helm;

public record HelmChartPackageMeta(
        String name,
        String version,
        String description,
        String appVersion,
        String apiVersion
) {
}
