package com.hfwas.devops.common.core.requestid;

import org.slf4j.MDC;

import java.util.UUID;

/**
 * RequestId 上下文持有者。
 * 基于 SLF4J MDC，在请求线程中传递 requestId。
 * 所有日志通过 {@code [%X{requestId}]} 自动输出，支持 grep 检索完整链路。
 */
public class RequestIdHolder {

    private static final String REQUEST_ID_KEY = "requestId";
    public static final String HEADER_NAME = "X-Request-Id";
    public static final String ATTRIBUTE_NAME = "requestId";

    /**
     * 获取或生成 requestId，写入 MDC。
     * 优先使用传入的 ID（来自请求头），为空时自动生成 UUID。
     *
     * @param requestId 外部传入的 requestId，可为 null
     * @return 最终生效的 requestId
     */
    public static String setIfAbsent(String requestId) {
        String existing = MDC.get(REQUEST_ID_KEY);
        if (existing != null) {
            return existing;
        }
        String id = (requestId != null && !requestId.isEmpty()) ? requestId : generateId();
        MDC.put(REQUEST_ID_KEY, id);
        return id;
    }

    /**
     * 获取当前线程的 requestId
     */
    public static String get() {
        return MDC.get(REQUEST_ID_KEY);
    }

    /**
     * 清理当前线程的 requestId
     */
    public static void clear() {
        MDC.remove(REQUEST_ID_KEY);
    }

    private static String generateId() {
        return UUID.randomUUID().toString();
    }
}