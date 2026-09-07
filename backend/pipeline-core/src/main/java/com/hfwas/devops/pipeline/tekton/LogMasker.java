package com.hfwas.devops.pipeline.tekton;

public final class LogMasker {

    private LogMasker() {
    }

    public static String mask(String text, String secret) {
        if (text == null || secret == null || secret.isBlank()) {
            return text;
        }
        return text.replace(secret, "****");
    }

    public static String truncate(String text, int maxBytes) {
        if (text == null) {
            return null;
        }
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return text;
        }
        return new String(bytes, 0, maxBytes, java.nio.charset.StandardCharsets.UTF_8);
    }
}
