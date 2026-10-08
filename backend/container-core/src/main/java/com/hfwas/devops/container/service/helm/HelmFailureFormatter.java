package com.hfwas.devops.container.service.helm;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Turns helm stderr into a short client-facing message. Secrets and kubeconfig material are removed.
 */
final class HelmFailureFormatter {

    private static final Pattern SECRET_ASSIGNMENT = Pattern.compile(
            "(?i)(password|passwd|token|secret|authorization|kubeconfig)\\s*[:=]\\s*\\S+");
    private static final Pattern CERT_DATA = Pattern.compile(
            "(?i)(client-certificate-data|client-key-data|certificate-authority-data)\\s*:\\s*\\S+");

    private HelmFailureFormatter() {
    }

    /**
     * One client-facing line. Pull/digest progress is dropped. Known release conflicts and
     * missing releases are mapped by the caller to a stable message instead of this summary.
     */
    static String summarize(HelmProcessResult result, String... secrets) {
        String stderr = result.stderr() == null ? "" : result.stderr();
        String stdout = result.stdout() == null ? "" : result.stdout();
        String text = stderr.isBlank() ? stdout : stderr;
        String redacted = redact(text, secrets);
        List<String> lines = new ArrayList<>();
        String errorLine = "";
        for (String line : redacted.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || isPullNoise(trimmed)) {
                continue;
            }
            lines.add(trimmed);
            if (trimmed.regionMatches(true, 0, "Error:", 0, 6)) {
                errorLine = trimmed.substring(6).trim();
            }
        }
        String chosen = errorLine.isEmpty() && !lines.isEmpty() ? lines.get(lines.size() - 1) : errorLine;
        chosen = chosen.replaceAll("\\s+", " ").trim();
        if (chosen.length() > 240) {
            chosen = chosen.substring(0, 240).trim();
        }
        return chosen;
    }

    private static boolean isPullNoise(String line) {
        return line.regionMatches(true, 0, "Pulled:", 0, 7)
                || line.regionMatches(true, 0, "Digest:", 0, 7)
                || line.regionMatches(true, 0, "Size:", 0, 5)
                || line.regionMatches(true, 0, "Status: Downloaded", 0, 18);
    }

    static String redact(String text, String... secrets) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String value = text;
        if (secrets != null) {
            for (String secret : secrets) {
                if (secret != null && secret.length() >= 3) {
                    value = value.replace(secret, "******");
                }
            }
        }
        value = SECRET_ASSIGNMENT.matcher(value).replaceAll("$1: ******");
        value = CERT_DATA.matcher(value).replaceAll("$1: ******");
        return value;
    }

    static boolean mentions(HelmProcessResult result, String needle) {
        String blob = (result.combined() == null ? "" : result.combined()).toLowerCase(Locale.ROOT);
        return blob.contains(needle.toLowerCase(Locale.ROOT));
    }
}
