package com.hfwas.devops.user.util;

import org.apache.commons.lang3.StringUtils;

public final class UserAgentUtils {

    private UserAgentUtils() {
    }

    public static String trim(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() > 512 ? userAgent.substring(0, 512) : userAgent;
    }

    public static String simplify(String userAgent) {
        if (StringUtils.isBlank(userAgent)) {
            return "-";
        }
        String ua = userAgent;
        String browser = "Unknown";
        if (ua.contains("Edg/")) {
            browser = "Edge";
        } else if (ua.contains("Chrome/")) {
            browser = "Chrome";
        } else if (ua.contains("Firefox/")) {
            browser = "Firefox";
        } else if (ua.contains("Safari/") && !ua.contains("Chrome/")) {
            browser = "Safari";
        }
        String os = "Unknown OS";
        if (ua.contains("Windows")) {
            os = "Windows";
        } else if (ua.contains("Mac OS X")) {
            os = "macOS";
        } else if (ua.contains("Linux")) {
            os = "Linux";
        } else if (ua.contains("Android")) {
            os = "Android";
        } else if (ua.contains("iPhone") || ua.contains("iPad")) {
            os = "iOS";
        }
        return browser + " / " + os;
    }
}
