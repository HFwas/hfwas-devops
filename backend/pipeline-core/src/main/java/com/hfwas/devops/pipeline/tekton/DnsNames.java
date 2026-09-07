package com.hfwas.devops.pipeline.tekton;

public final class DnsNames {

    private DnsNames() {
    }

    public static String objectName(long runId) {
        String raw = "hfwas-" + Long.toUnsignedString(runId);
        return clip(raw);
    }

    public static String stepName(String raw) {
        String value = raw == null ? "step" : raw.toLowerCase().replaceAll("[^a-z0-9]+", "-");
        value = value.replaceAll("^-+", "").replaceAll("-+$", "");
        if (value.isBlank()) {
            value = "step";
        }
        if (value.length() == 1) {
            return value;
        }
        return clip(value);
    }

    private static String clip(String value) {
        if (value.length() <= 63) {
            return value;
        }
        String clipped = value.substring(0, 63);
        return clipped.replaceAll("-+$", "");
    }
}
