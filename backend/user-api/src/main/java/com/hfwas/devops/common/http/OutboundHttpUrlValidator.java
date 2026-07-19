package com.hfwas.devops.common.http;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Validates outbound HTTP(S) URLs to reduce SSRF risk before {@link URI} is used in requests.
 */
public final class OutboundHttpUrlValidator {

    private OutboundHttpUrlValidator() {
    }

    public static URI toUri(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("URL 不能为空");
        }
        URI uri = parseUri(url.trim());
        validate(uri);
        return uri;
    }

    private static URI parseUri(String url) {
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("无效的 URL", e);
        }
    }

    private static void validate(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("仅支持 http 或 https 协议");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("无效的 URL");
        }
        InternalHostGuard.validateHost(host);
    }
}
