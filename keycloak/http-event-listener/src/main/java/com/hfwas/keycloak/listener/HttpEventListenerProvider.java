package com.hfwas.keycloak.listener;

import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.util.JsonSerialization;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class HttpEventListenerProvider implements EventListenerProvider {

    private static final Set<EventType> FORWARDED = EnumSet.of(
            EventType.LOGIN, EventType.LOGIN_ERROR, EventType.LOGOUT);

    private final HttpClient httpClient;
    private final String url;
    private final String secret;

    public HttpEventListenerProvider(HttpClient httpClient, String url, String secret) {
        this.httpClient = httpClient;
        this.url = url;
        this.secret = secret;
    }

    @Override
    public void onEvent(Event event) {
        if (event == null || event.getType() == null || !FORWARDED.contains(event.getType())) {
            return;
        }
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("id", event.getId());
            body.put("type", event.getType().name());
            body.put("time", event.getTime());
            body.put("userId", event.getUserId());
            body.put("ipAddress", event.getIpAddress());
            body.put("error", event.getError());
            body.put("details", event.getDetails());
            String json = JsonSerialization.writeValueAsString(body);
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            if (secret != null && !secret.isBlank()) {
                builder.header("X-Webhook-Secret", secret);
            }
            httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
            // Listener must not fail the login flow.
        }
    }

    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {
        // unused
    }

    @Override
    public void close() {
        // no-op
    }
}
