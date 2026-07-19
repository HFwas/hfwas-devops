package com.hfwas.devops.pm.field.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.hfwas.devops.common.http.OutboundHttpUrlValidator;
import com.hfwas.devops.pm.field.model.FieldRemoteOptionsConfig;
import com.hfwas.devops.pm.field.model.RemoteOptionFetchResult;
import com.hfwas.devops.pm.field.model.ResolvedFieldOption;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class FieldOptionRemoteService {

    private static final int MAX_OPTIONS = 500;
    private static final int MAX_RESPONSE_BYTES = 512 * 1024;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final Cache<String, CachedEntry> cache = CacheBuilder.newBuilder()
            .maximumSize(200)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .build();

    private record CachedEntry(List<ResolvedFieldOption> options, long expiresAt) {
    }

    public RemoteOptionFetchResult fetch(FieldRemoteOptionsConfig config) {
        return fetch(config, null);
    }

    public RemoteOptionFetchResult fetch(FieldRemoteOptionsConfig config, Long fieldId) {
        if (config == null || StringUtils.isBlank(config.getUrl())) {
            return RemoteOptionFetchResult.builder()
                    .success(false)
                    .message("远程接口地址不能为空")
                    .build();
        }
        if (StringUtils.isBlank(config.getValueField()) || StringUtils.isBlank(config.getLabelField())) {
            return RemoteOptionFetchResult.builder()
                    .success(false)
                    .message("值字段与显示字段不能为空")
                    .build();
        }
        String cacheKey = fieldId != null ? "field:" + fieldId : null;
        int cacheSeconds = config.getCacheSeconds() != null && config.getCacheSeconds() > 0
                ? config.getCacheSeconds()
                : 300;
        if (cacheKey != null) {
            CachedEntry cached = cache.getIfPresent(cacheKey);
            if (cached != null && cached.expiresAt > System.currentTimeMillis()) {
                return RemoteOptionFetchResult.builder().success(true).options(cached.options).build();
            }
        }
        try {
            String body = executeRequest(config);
            List<ResolvedFieldOption> options = parseOptions(body, config);
            if (cacheKey != null && !options.isEmpty()) {
                cache.put(cacheKey, new CachedEntry(options, System.currentTimeMillis() + cacheSeconds * 1000L));
            }
            return RemoteOptionFetchResult.builder().success(true).options(options).build();
        } catch (Exception e) {
            return RemoteOptionFetchResult.builder()
                    .success(false)
                    .message(rootMessage(e))
                    .build();
        }
    }

    public void invalidateCache(Long fieldId) {
        if (fieldId != null) {
            cache.invalidate("field:" + fieldId);
        }
    }

    private String executeRequest(FieldRemoteOptionsConfig config) throws Exception {
        String method = StringUtils.defaultIfBlank(config.getMethod(), "GET").trim().toUpperCase();
        if (!"GET".equals(method) && !"POST".equals(method)) {
            throw new IllegalArgumentException("仅支持 GET 或 POST 请求");
        }
        URI uri = OutboundHttpUrlValidator.toUri(config.getUrl());
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(15));
        Map<String, String> headers = config.getHeaders();
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (StringUtils.isNotBlank(entry.getKey()) && entry.getValue() != null) {
                    builder.header(entry.getKey().trim(), entry.getValue());
                }
            }
        }
        if ("POST".equals(method)) {
            String body = StringUtils.defaultString(config.getBody());
            if (!hasContentType(headers)) {
                builder.header("Content-Type", "application/json; charset=utf-8");
            }
            builder.POST(HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.GET();
        }
        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + response.statusCode() + ": "
                    + StringUtils.left(response.body(), 200));
        }
        String body = response.body();
        if (body != null && body.length() > MAX_RESPONSE_BYTES) {
            throw new IllegalStateException("响应体过大，超过 " + MAX_RESPONSE_BYTES + " 字节");
        }
        return body;
    }

    private static boolean hasContentType(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return false;
        }
        return headers.keySet().stream().anyMatch(k -> "content-type".equalsIgnoreCase(k));
    }

    List<ResolvedFieldOption> parseOptions(String body, FieldRemoteOptionsConfig config) throws Exception {
        if (StringUtils.isBlank(body)) {
            return List.of();
        }
        JsonNode root = objectMapper.readTree(body);
        JsonNode arrayNode = resolveDataNode(root, config.getDataPath());
        if (arrayNode == null || !arrayNode.isArray()) {
            throw new IllegalArgumentException("未找到选项数组，请检查 dataPath 配置");
        }
        String valueField = config.getValueField().trim();
        String labelField = config.getLabelField().trim();
        List<ResolvedFieldOption> result = new ArrayList<>();
        Iterator<JsonNode> it = arrayNode.elements();
        while (it.hasNext() && result.size() < MAX_OPTIONS) {
            JsonNode item = it.next();
            String value = readField(item, valueField);
            String label = readField(item, labelField);
            if (StringUtils.isNotBlank(value) && StringUtils.isNotBlank(label)) {
                result.add(new ResolvedFieldOption(value, label));
            }
        }
        return result;
    }

    private JsonNode resolveDataNode(JsonNode root, String dataPath) {
        if (StringUtils.isBlank(dataPath)) {
            return root.isArray() ? root : null;
        }
        JsonNode cur = root;
        for (String segment : dataPath.split("\\.")) {
            if (StringUtils.isBlank(segment)) {
                continue;
            }
            if (cur == null || cur.isNull()) {
                return null;
            }
            cur = cur.get(segment.trim());
        }
        return cur;
    }

    private String readField(JsonNode item, String field) {
        if (item == null || item.isNull()) {
            return null;
        }
        if (item.isObject() && item.has(field)) {
            JsonNode node = item.get(field);
            return node == null || node.isNull() ? null : node.asText();
        }
        if (item.isValueNode()) {
            return item.asText();
        }
        return null;
    }

    private static String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null) {
            cur = cur.getCause();
        }
        return StringUtils.defaultIfBlank(cur.getMessage(), cur.getClass().getSimpleName());
    }
}
