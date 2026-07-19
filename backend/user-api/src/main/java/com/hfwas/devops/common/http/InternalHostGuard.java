package com.hfwas.devops.common.http;

import java.net.InetAddress;
import java.net.UnknownHostException;

/** Blocks outbound requests to local / private network hosts (SSRF mitigation). */
public final class InternalHostGuard {

    private InternalHostGuard() {
    }

    public static void validateHost(String host) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("无效的主机名");
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
