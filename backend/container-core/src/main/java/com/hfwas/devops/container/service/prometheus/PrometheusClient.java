package com.hfwas.devops.container.service.prometheus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PrometheusClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Query Prometheus instant query.
     *
     * @param baseUrl Prometheus HTTP base URL, e.g. "http://host:30090"
     * @param promql  PromQL expression
     * @return raw JSON response, or empty if error
     */
    public Optional<JsonNode> query(String baseUrl, String promql) {
        try {
            String url = baseUrl + "/api/v1/query?query={promql}";
            String json = restTemplate.getForObject(url, String.class, promql);
            if (json == null) return Optional.empty();
            JsonNode root = objectMapper.readTree(json);
            String status = root.path("status").asText();
            if (!"success".equals(status)) {
                log.warn("Prometheus query not successful: status={}, promql={}", status, promql);
                return Optional.empty();
            }
            return Optional.of(root.path("data"));
        } catch (Exception e) {
            log.error("Prometheus query failed: promql={}, error={}", promql, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Query Prometheus range query.
     *
     * @param baseUrl Prometheus HTTP base URL
     * @param promql  PromQL expression
     * @param start   start unix timestamp (seconds)
     * @param end     end unix timestamp (seconds)
     * @param step    step, e.g. "15s", "1m", "5m"
     * @return raw JSON data node, or empty if error
     */
    public Optional<JsonNode> queryRange(String baseUrl, String promql, long start, long end, String step) {
        try {
            String url = baseUrl + "/api/v1/query_range?query={promql}&start={start}&end={end}&step={step}";
            String json = restTemplate.getForObject(url, String.class, promql, start, end, step);
            if (json == null) return Optional.empty();
            JsonNode root = objectMapper.readTree(json);
            String status = root.path("status").asText();
            if (!"success".equals(status)) {
                log.warn("Prometheus range query not successful: status={}, promql={}", status, promql);
                return Optional.empty();
            }
            return Optional.of(root.path("data"));
        } catch (Exception e) {
            log.error("Prometheus range query failed: promql={}, error={}", promql, e.getMessage());
            return Optional.empty();
        }
    }
}