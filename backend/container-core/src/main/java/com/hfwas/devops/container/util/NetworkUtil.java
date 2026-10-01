package com.hfwas.devops.container.util;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;

/**
 * Network connectivity check utilities.
 * Used to provide fast, friendly error messages when target hosts are unreachable
 * (e.g. VPN disconnected, network down).
 */
@Slf4j
public class NetworkUtil {

    private NetworkUtil() {}

    /**
     * Timeout for the TCP connectivity check (milliseconds).
     * Short enough to not hang the UI, long enough for a local round-trip.
     */
    private static final int CONNECT_TIMEOUT_MS = 3000;

    /**
     * Extract host and port from a URL and check TCP reachability.
     *
     * @param url  full URL like "https://10.120.1.233:8443"
     * @return null if reachable, or an error message string if unreachable
     */
    public static String checkReachable(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            int port = uri.getPort();
            if (port <= 0) {
                port = "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
            }
            return checkReachable(host, port);
        } catch (Exception e) {
            log.debug("Failed to parse URL for reachability check: {}", url, e);
            return null; // don't block on URL parse error
        }
    }

    /**
     * Check TCP reachability for a host:port with a short timeout.
     *
     * @param host  target hostname or IP
     * @param port  target port
     * @return null if reachable, or an error message string if unreachable
     */
    public static String checkReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            long start = System.currentTimeMillis();
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            long elapsed = System.currentTimeMillis() - start;
            log.debug("TCP reachable: {}:{} ({}ms)", host, port, elapsed);
            return null; // reachable
        } catch (IOException e) {
            String msg = String.format("无法连接到 %s:%d — 请检查网络/VPN 连接 (%s)",
                    host, port, e.getMessage());
            log.warn("TCP unreachable: {}:{} - {}", host, port, e.getMessage());
            return msg;
        }
    }
}