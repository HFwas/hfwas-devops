package com.hfwas.devops.user.notify.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hfwas.devops.user.model.NotifyTestResult;
import com.hfwas.devops.user.model.WebhookChannelConfig;
import com.hfwas.devops.user.notify.NotifyChannels;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WebhookNotifyClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public NotifyTestResult test(String channel, WebhookChannelConfig config) {
        return send(channel, config, "【测试】消息通知", "这是一条测试消息，用于验证 Webhook 配置是否正确。");
    }

    public NotifyTestResult send(String channel, WebhookChannelConfig config, String title, String content) {
        if (config == null || StringUtils.isBlank(config.getWebhookUrl())) {
            return NotifyTestResult.builder().success(false).message("Webhook 地址不能为空").build();
        }
        try {
            String body = buildBody(channel, title, content);
            String url = signedUrl(channel, config);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return NotifyTestResult.builder().success(true).message("发送成功").build();
            }
            return NotifyTestResult.builder()
                    .success(false)
                    .message("HTTP " + response.statusCode() + ": " + StringUtils.left(response.body(), 200))
                    .build();
        } catch (Exception e) {
            return NotifyTestResult.builder().success(false).message("发送失败: " + rootMessage(e)).build();
        }
    }

    private String buildBody(String channel, String title, String content) throws Exception {
        String text = "### " + title + "\n\n" + StringUtils.defaultString(content);
        if (NotifyChannels.DINGTALK.equals(channel)) {
            Map<String, Object> markdown = new LinkedHashMap<>();
            markdown.put("title", title);
            markdown.put("text", text);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("msgtype", "markdown");
            payload.put("markdown", markdown);
            return objectMapper.writeValueAsString(payload);
        }
        Map<String, Object> contentNode = new LinkedHashMap<>();
        contentNode.put("text", title + "\n" + StringUtils.defaultString(content));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("msg_type", "text");
        payload.put("content", contentNode);
        return objectMapper.writeValueAsString(payload);
    }

    private String signedUrl(String channel, WebhookChannelConfig config) throws Exception {
        String url = config.getWebhookUrl().trim();
        if (StringUtils.isBlank(config.getSecret())) {
            return url;
        }
        long timestamp = System.currentTimeMillis();
        String sign = sign(channel, timestamp, config.getSecret().trim());
        String connector = url.contains("?") ? "&" : "?";
        return url + connector + "timestamp=" + timestamp + "&sign=" + URLEncoder.encode(sign, StandardCharsets.UTF_8);
    }

    private String sign(String channel, long timestamp, String secret) throws Exception {
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        if (NotifyChannels.FEISHU.equals(channel)) {
            return Base64.getEncoder().encodeToString(signData);
        }
        return URLEncoder.encode(Base64.getEncoder().encodeToString(signData), StandardCharsets.UTF_8);
    }

    private static String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null) {
            cur = cur.getCause();
        }
        return StringUtils.defaultIfBlank(cur.getMessage(), cur.getClass().getSimpleName());
    }
}
