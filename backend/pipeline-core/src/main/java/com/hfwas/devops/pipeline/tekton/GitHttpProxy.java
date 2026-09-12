package com.hfwas.devops.pipeline.tekton;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Git HTTPS 在执行集群里常常到不了 GitHub；宿主机 git 走的 127.0.0.1 代理
 * 对 Pod 不可达，需要改写成 host.docker.internal 对应 IP。
 */
public final class GitHttpProxy {

    private GitHttpProxy() {
    }

    public static String dockerHostAddress(String fallback) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName("host.docker.internal");
            for (InetAddress address : addresses) {
                if (address instanceof java.net.Inet4Address) {
                    return address.getHostAddress();
                }
            }
            // 仅有 IPv6 时保留主机名，避免 URL 里裸 IPv6 缺括号
            return "host.docker.internal";
        } catch (UnknownHostException e) {
            return fallback == null ? "" : fallback.trim();
        }
    }

    public static String rewrite(String raw, String dockerHostIp) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String value = raw.trim();
        if (!value.contains("://")) {
            value = "http://" + value;
        }
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException e) {
            return value;
        }
        String host = uri.getHost();
        if (host == null) {
            return value;
        }
        if (!needsRewrite(host) || dockerHostIp == null || dockerHostIp.isBlank()) {
            return value;
        }
        StringBuilder out = new StringBuilder();
        out.append(uri.getScheme()).append("://").append(dockerHostIp.trim());
        if (uri.getPort() > 0) {
            out.append(':').append(uri.getPort());
        }
        String path = uri.getRawPath();
        if (path != null && !path.isEmpty() && !"/".equals(path)) {
            out.append(path);
        }
        if (uri.getRawQuery() != null) {
            out.append('?').append(uri.getRawQuery());
        }
        return out.toString();
    }

    static boolean needsRewrite(String host) {
        return "127.0.0.1".equals(host)
                || "localhost".equalsIgnoreCase(host)
                || "host.docker.internal".equalsIgnoreCase(host)
                || "::1".equals(host);
    }
}
