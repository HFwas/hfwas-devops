package com.hfwas.keycloak.listener;

import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

import java.net.http.HttpClient;
import java.time.Duration;

public class HttpEventListenerProviderFactory implements EventListenerProviderFactory {

    public static final String ID = "hfwas-http";

    private HttpClient httpClient;
    private String url;
    private String secret;

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        return new HttpEventListenerProvider(httpClient, url, secret);
    }

    @Override
    public void init(Config.Scope config) {
        url = firstNonBlank(
                config == null ? null : config.get("url"),
                System.getenv("KEYCLOAK_HTTP_LISTENER_URL"));
        secret = firstNonBlank(
                config == null ? null : config.get("secret"),
                System.getenv("KEYCLOAK_HTTP_LISTENER_SECRET"));
        httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // no-op
    }

    @Override
    public void close() {
        // no-op
    }

    @Override
    public String getId() {
        return ID;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return null;
    }
}
