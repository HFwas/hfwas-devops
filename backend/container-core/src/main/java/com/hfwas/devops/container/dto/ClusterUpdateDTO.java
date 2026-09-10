package com.hfwas.devops.container.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.util.Map;

@Data
public class ClusterUpdateDTO {
    private String alias;
    private String provider;
    private String kubeconfig; // null = keep existing
    private Map<String, String> labels;

    /**
     * Normalize labels from raw JSON string (null-safe).
     */
    public void setLabelsFromJson(String json, ObjectMapper mapper) {
        if (json != null && !json.isBlank()) {
            try {
                this.labels = mapper.readValue(json, new TypeReference<Map<String, String>>() {});
            } catch (Exception ignored) {
                this.labels = null;
            }
        }
    }
}