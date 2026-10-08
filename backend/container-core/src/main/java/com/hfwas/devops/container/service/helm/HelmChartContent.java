package com.hfwas.devops.container.service.helm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hfwas.devops.container.entity.HelmChartArtifactEntity;

import java.util.List;

final class HelmChartContent {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> STRINGS = new TypeReference<>() {
    };

    private HelmChartContent() {
    }

    static void apply(HelmChartArtifactEntity entity, HelmChartPackageMeta meta) {
        entity.setKeywords(keywordsJson(meta.keywords()));
        entity.setReadme(meta.readme());
        entity.setValuesYaml(meta.valuesYaml());
        entity.setContentCached(true);
    }

    static String keywordsJson(List<String> keywords) {
        try {
            return JSON.writeValueAsString(keywords == null ? List.of() : keywords);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    static List<String> keywords(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            List<String> parsed = JSON.readValue(raw, STRINGS);
            return parsed == null ? List.of() : List.copyOf(parsed);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
