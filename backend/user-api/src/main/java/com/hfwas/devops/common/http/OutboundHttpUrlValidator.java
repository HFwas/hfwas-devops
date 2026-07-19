package com.hfwas.devops.common.http;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

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
        if (isBlockedHost(host)) {
            throw new IllegalArgumentException("不允许访问内网或本地地址");
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isBlockedAddress(address)) {
                    throw new IllegalArgumentException("不允许访问内网或本地地址");
                }
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("无法解析主机: " + host, e);
        }
    }

    private static boolean isBlockedHost(String host) {
        String h = host.toLowerCase();
        return "localhost".equals(h)
                || h.endsWith(".local")
                || h.endsWith(".internal");
    }

    private static boolean isBlockedAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()) {
            return true;
        }
        if (address.isSiteLocalAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;
            if (b0 == 169 && b1 == 254) {
                return true;
            }
            if (b0 == 127 || b0 == 10 || b0 == 0) {
                return true;
            }
            if (b0 == 172 && b1 >= 16 && b1 <= 31) {
                return true;
            }
            if (b0 == 192 && b1 == 168) {
                return true;
            }
        }
        return false;
    }
}
