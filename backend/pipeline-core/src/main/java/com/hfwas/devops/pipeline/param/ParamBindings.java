package com.hfwas.devops.pipeline.param;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hfwas.devops.pipeline.dto.JobParamBindingDTO;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ParamBindings {

    private static final ObjectMapper JSON = new ObjectMapper();

    private ParamBindings() {}

    public static Map<String, JobParamBindingDTO> parse(String json) {
        if (json == null || json.isBlank() || "{}".equals(json.trim())) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, JobParamBindingDTO> parsed = JSON.readValue(json, new TypeReference<>() {});
            return parsed != null ? parsed : new LinkedHashMap<>();
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    public static String toJson(Map<String, JobParamBindingDTO> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return "{}";
        }
        try {
            return JSON.writeValueAsString(bindings);
        } catch (Exception e) {
            return "{}";
        }
    }

    public static boolean isRuntime(JobParamBindingDTO binding) {
        return binding != null && binding.getMode() != null && "runtime".equalsIgnoreCase(binding.getMode().trim());
    }

    public static Map<String, JobParamBindingDTO> empty() {
        return Collections.emptyMap();
    }
}
