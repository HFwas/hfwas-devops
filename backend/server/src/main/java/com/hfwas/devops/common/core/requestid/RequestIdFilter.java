package com.hfwas.devops.common.core.requestid;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 全局 RequestId 过滤器。
 * 在过滤器链最前端执行，为每个请求分配唯一的 requestId：
 * <ol>
 *   <li>优先读取客户端传入的 {@code X-Request-Id} 请求头</li>
 *   <li>不存在时自动生成 UUID</li>
 *   <li>写入 MDC 供日志输出，写入响应头供调用方定位</li>
 *   <li>额外存入 request attribute（Tomcat 层错误兜底）</li>
 * </ol>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String headerId = request.getHeader(RequestIdHolder.HEADER_NAME);
        String requestId = RequestIdHolder.setIfAbsent(headerId);

        // 存入 request attribute，Tomcat 容器层错误仍可通过 attribute 获取
        request.setAttribute(RequestIdHolder.ATTRIBUTE_NAME, requestId);

        // 设置响应头，方便调用方拿到 requestId 后检索日志
        response.setHeader(RequestIdHolder.HEADER_NAME, requestId);

        try {
            log.info("=== Request start: {} {} ===", request.getMethod(), getRequestUri(request));
            filterChain.doFilter(request, response);
            log.info("=== Request end: {} {} ===", request.getMethod(), getRequestUri(request));
        } finally {
            RequestIdHolder.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/static/") || path.startsWith("/webjars/");
    }

    private static String getRequestUri(HttpServletRequest request) {
        String qs = request.getQueryString();
        return qs != null ? request.getRequestURI() + "?" + qs : request.getRequestURI();
    }
}