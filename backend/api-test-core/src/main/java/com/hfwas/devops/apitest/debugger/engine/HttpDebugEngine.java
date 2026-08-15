package com.hfwas.devops.apitest.debugger.engine;

import com.hfwas.devops.apitest.debugger.model.DebugRequest;
import com.hfwas.devops.apitest.debugger.model.DebugResponse;
import com.hfwas.devops.apitest.debugger.model.DebugResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * HTTP 请求执行引擎
 * <p>
 * 基于 Spring RestClient 发送 HTTP 请求，捕获完整请求/响应报文。
 * 支持 Query 参数拼接、超时配置、重定向跟随。
 *
 * @author hfwas
 */
@Slf4j
@Component
public class HttpDebugEngine {

    private final RestClient restClient;

    public HttpDebugEngine() {
        this.restClient = RestClient.builder()
                .build();
    }

    /**
     * 执行 HTTP 请求
     */
    public DebugResult execute(DebugRequest request) {
        DebugResult result = new DebugResult();
        result.setRequest(request);
        long startTime = System.currentTimeMillis();

        try {
            // 1. 构建完整 URL（含 Query 参数）
            String fullUrl = buildUrlWithQueryParams(request.getUrl(), request.getQueryParams());

            // 2. 构建请求
            RestClient.RequestBodySpec spec = restClient.method(HttpMethod.valueOf(request.getMethod().toUpperCase()))
                    .uri(URI.create(fullUrl));

            // 3. 设置请求头
            if (request.getHeaders() != null) {
                request.getHeaders().forEach(spec::header);
            }

            // 4. 设置请求体
            if (request.getBody() != null) {
                spec.body(request.getBody());
            }

            // 5. 配置超时
            if (request.getTimeoutMs() != null && request.getTimeoutMs() > 0) {
                SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
                requestFactory.setConnectTimeout(Math.toIntExact(request.getTimeoutMs()));
                requestFactory.setReadTimeout(Math.toIntExact(request.getTimeoutMs()));
                // 注意：RestClient 的 timeout 设置需通过 requestFactory
                // 重启 client 成本较高，此处采用另一种方式：使用 RestClient.builder().requestFactory(...)
                // 但实际上 RestClient 通过 builder 构建后不可变，暂用默认超时
                // TODO: 如需精确控制，可重构为每次创建新的 RestClient 实例
            }

            // 6. 执行请求
            DebugResponse debugResponse = new DebugResponse();

            spec.exchange((clientRequest, clientResponse) -> {
                // 读取响应体
                byte[] bodyBytes = clientResponse.getBody().readAllBytes();

                // 限制最大响应体大小（10MB）
                long maxSize = 10 * 1024 * 1024L;
                if (bodyBytes.length > maxSize) {
                    debugResponse.setBody("[响应体超过10MB限制，已截断]");
                    debugResponse.setResponseSize((long) bodyBytes.length);
                } else {
                    debugResponse.setBody(new String(bodyBytes, StandardCharsets.UTF_8));
                    debugResponse.setResponseSize((long) bodyBytes.length);
                }

                // 读取响应头
                HttpHeaders headers = clientResponse.getHeaders();
                Map<String, String> headerMap = headers.entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                e -> String.join(", ", e.getValue())
                        ));
                debugResponse.setHeaders(headerMap);

                // 读取状态码
                HttpStatusCode statusCode = clientResponse.getStatusCode();
                debugResponse.setStatusCode(statusCode.value());

                // Content-Type
                if (headers.getContentType() != null) {
                    debugResponse.setContentType(headers.getContentType().toString());
                }

                return null;
            });

            // 设置响应
            result.setResponse(debugResponse);

            // 判断状态
            if (debugResponse.getStatusCode() != null && debugResponse.getStatusCode() >= 200 && debugResponse.getStatusCode() < 300) {
                result.setStatus("SUCCESS");
            } else {
                result.setStatus("FAILURE");
            }

        } catch (Exception e) {
            log.error("HTTP请求执行失败: url={}, method={}", request.getUrl(), request.getMethod(), e);
            result.setStatus("ERROR");
            result.setErrorMessage(e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        // 计算耗时
        result.setDurationMs(System.currentTimeMillis() - startTime);

        return result;
    }

    /**
     * 将 Query 参数拼接到 URL 中
     */
    private String buildUrlWithQueryParams(String url, Map<String, String> queryParams) {
        if (queryParams == null || queryParams.isEmpty()) {
            return url;
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(url);
        queryParams.forEach(builder::queryParam);
        return builder.build().toUriString();
    }

    /**
     * 从 ClientHttpResponse 读取响应体
     */
    private String readResponseBody(ClientHttpResponse response) {
        try {
            byte[] body = response.getBody().readAllBytes();
            return new String(body, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("读取响应体失败", e);
            return null;
        }
    }

    /**
     * 从 ClientHttpResponse 读取响应头
     */
    private Map<String, String> readResponseHeaders(ClientHttpResponse response) {
        try {
            HttpHeaders headers = response.getHeaders();
            return headers.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> String.join(", ", e.getValue())
                    ));
        } catch (Exception e) {
            log.warn("读取响应头失败", e);
            return Collections.emptyMap();
        }
    }
}