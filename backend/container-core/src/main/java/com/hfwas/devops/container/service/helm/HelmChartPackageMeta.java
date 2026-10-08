package com.hfwas.devops.container.service.helm;

import java.util.List;

public record HelmChartPackageMeta(
        String name,
        String version,
        String description,
        String appVersion,
        String apiVersion,
        List<String> keywords,
        String readme,
        String valuesYaml
) {
    public HelmChartPackageMeta {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        readme = readme == null ? "" : readme;
        valuesYaml = valuesYaml == null ? "" : valuesYaml;
    }
}
