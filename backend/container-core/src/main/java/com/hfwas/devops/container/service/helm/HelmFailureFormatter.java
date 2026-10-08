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

    static String summarize(HelmProcessResult result, String... secrets) {
        String stderr = result.stderr() == null ? "" : result.stderr();
        String stdout = result.stdout() == null ? "" : result.stdout();
        String text = stderr.isBlank() ? stdout : stderr;
        String redacted = redact(text, secrets);
        String[] lines = redacted.split("\\R");
        List<String> kept = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                kept.add(trimmed);
            }
        }
        int from = Math.max(0, kept.size() - 12);
        String joined = String.join("\n", kept.subList(from, kept.size()));
        if (joined.length() > 800) {
            joined = joined.substring(joined.length() - 800);
        }
        return joined;
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
