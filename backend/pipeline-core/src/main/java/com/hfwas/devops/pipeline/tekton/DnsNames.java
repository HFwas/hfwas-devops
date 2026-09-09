package com.hfwas.devops.pipeline.tekton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DnsNames {

    private DnsNames() {
    }

    public static String objectName(long runId) {
        String raw = "hfwas-" + Long.toUnsignedString(runId);
        return clip(raw);
    }

    public static String stepName(String raw) {
        return stepName(raw, null);
    }

    public static String stepName(String raw, String kind) {
        String value = sanitize(raw);
        if (!value.equals("step")) {
            return clip(value);
        }
        String fromKind = sanitize(kind == null ? "" : kind.toLowerCase(Locale.ROOT));
        if (!fromKind.isBlank() && !fromKind.equals("step")) {
            return clip(fromKind);
        }
        return "step";
    }

    public static String uniqueName(String base, List<String> used) {
        if (used == null || !used.contains(base)) {
            return base;
        }
        int i = 2;
        while (used.contains(base + "-" + i)) {
            i++;
        }
        return base + "-" + i;
    }

    public static List<String> assignJobStepNames(List<String> jobNames, List<String> kinds) {
        List<String> assigned = new ArrayList<>();
        List<String> used = new ArrayList<>();
        int n = jobNames == null ? 0 : jobNames.size();
        for (int i = 0; i < n; i++) {
            String kind = kinds != null && i < kinds.size() ? kinds.get(i) : null;
            if ("APPROVAL".equalsIgnoreCase(kind)) {
                assigned.add("");
                continue;
            }
            String name = uniqueName(stepName(jobNames.get(i), kind), used);
            used.add(name);
            assigned.add(name);
        }
        return assigned;
    }

    /**
     * IMAGE 会生成 {@code base-crane} 这类后缀 step；不能用 {@code startsWith(base + "-")}
     * 直接匹配，否则 {@code step} 会吞掉 {@code step-2}。
     */
    public static boolean stepBelongsTo(String stepName, String assigned, List<String> allAssigned) {
        if (stepName == null || assigned == null || assigned.isBlank()) {
            return false;
        }
        String best = null;
        if (allAssigned != null) {
            for (String name : allAssigned) {
                if (name == null || name.isBlank()) {
                    continue;
                }
                if (stepName.equals(name) || stepName.startsWith(name + "-")) {
                    if (best == null || name.length() > best.length()) {
                        best = name;
                    }
                }
            }
        }
        return assigned.equals(best);
    }

    public static String parseCommitSha(String logText) {
        if (logText == null || logText.isBlank()) {
            return null;
        }
        for (String line : logText.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("HFWAS_COMMIT=")) {
                String sha = trimmed.substring("HFWAS_COMMIT=".length()).trim();
                if (sha.matches("[0-9a-fA-F]{7,40}")) {
                    return sha.toLowerCase(Locale.ROOT);
                }
            }
        }
        return null;
    }

    private static String sanitize(String raw) {
        String value = raw == null ? "step" : raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        value = value.replaceAll("^-+", "").replaceAll("-+$", "");
        if (value.isBlank()) {
            return "step";
        }
        if (value.length() == 1) {
            return value;
        }
        return value;
    }

    private static String clip(String value) {
        if (value.length() <= 63) {
            return value;
        }
        String clipped = value.substring(0, 63);
        return clipped.replaceAll("-+$", "");
    }
}
