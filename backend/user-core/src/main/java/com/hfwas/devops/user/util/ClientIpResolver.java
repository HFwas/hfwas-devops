package com.hfwas.devops.user.util;

import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;

import java.net.Inet6Address;
import java.net.InetAddress;

public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        String ip = firstHeader(request, "X-Forwarded-For");
        if (ip == null) {
            ip = firstHeader(request, "X-Forwarded-IP");
        }
        if (ip == null) {
            ip = firstHeader(request, "X-Real-IP");
        }
        if (ip == null) {
            ip = request.getRemoteAddr();
        }
        return normalize(StringUtils.defaultIfBlank(ip, "-"));
    }

    public static String normalize(String ip) {
        if (StringUtils.isBlank(ip) || "-".equals(ip)) {
            return "-";
        }
        String raw = ip.trim();
        int zoneIndex = raw.indexOf('%');
        if (zoneIndex > 0) {
            raw = raw.substring(0, zoneIndex);
        }
        try {
            InetAddress address = InetAddress.getByName(raw);
            if (address.isLoopbackAddress()) {
                return "127.0.0.1";
            }
            if (address instanceof Inet6Address inet6 && isIpv4Mapped(inet6.getAddress())) {
                byte[] b = inet6.getAddress();
                return String.format("%d.%d.%d.%d", b[12] & 0xff, b[13] & 0xff, b[14] & 0xff, b[15] & 0xff);
            }
            String hostAddress = address.getHostAddress();
            return hostAddress != null ? hostAddress : raw;
        } catch (Exception ignored) {
            if ("::1".equals(raw) || "0:0:0:0:0:0:0:1".equalsIgnoreCase(raw)) {
                return "127.0.0.1";
            }
            return raw;
        }
    }

    private static String firstHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return value.split(",")[0].trim();
    }

    private static boolean isIpv4Mapped(byte[] bytes) {
        if (bytes.length != 16) {
            return false;
        }
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return (bytes[10] & 0xff) == 0xff && (bytes[11] & 0xff) == 0xff;
    }
}
