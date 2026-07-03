package com.hfwas.devops.user.operlog.support;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

public final class OperLogRequestSupport {

    private OperLogRequestSupport() {
    }

    public static Optional<HttpServletRequest> currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return Optional.ofNullable(attrs.getRequest());
        }
        return Optional.empty();
    }

    public static String trimUserAgent(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() > 512 ? userAgent.substring(0, 512) : userAgent;
    }
}
