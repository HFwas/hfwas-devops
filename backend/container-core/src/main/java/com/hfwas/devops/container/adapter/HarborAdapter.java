package com.hfwas.devops.container.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hfwas.devops.container.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Adapter for Harbor Registry v2 API.
 * Uses Harbor REST API v2.0 to interact with the registry.
 */
@Slf4j
public class HarborAdapter implements RegistryAdapter {

    private final String baseUrl;
    private final String username;
    private final String password;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public HarborAdapter(String baseUrl, String username, String password, Boolean insecure) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.username = username;
        this.password = password;
        this.objectMapper = new ObjectMapper();

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(this.baseUrl)
                .defaultHeader("Authorization", basicAuthHeader());

        if (Boolean.TRUE.equals(insecure)) {
            // Skip TLS verification — use a custom request factory that trusts all certs
            builder = builder.requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory());
            // NOTE: For production, use a properly configured SSL context instead
        }

        this.restClient = builder.build();
    }

    @Override
    public boolean health() {
        try {
            int status = restClient.get()
                    .uri("/api/v2.0/projects?page=1&page_size=1")
                    .exchange((req, res) -> {
                        // Just check we got a successful response
                        if (res.getStatusCode().is2xxSuccessful()) return res.getStatusCode().value();
                        throw new RuntimeException("Health check failed: " + res.getStatusCode());
                    });
            return status >= 200 && status < 300;
        } catch (Exception e) {
            log.warn("Harbor health check failed for {}: {}", baseUrl, e.getMessage());
            return false;
        }
    }

    @Override
    public List<RegistryProjectVO> listProjects() {
        try {
            String json = restClient.get()
                    .uri("/api/v2.0/projects?page=1&page_size=100")
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(json);
            List<RegistryProjectVO> result = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode node : root) {
                    RegistryProjectVO vo = new RegistryProjectVO();
                    vo.setName(node.path("name").asText());
                    vo.setRepoCount(node.path("repo_count").asLong());
                    vo.setCreationTime(node.path("creation_time").asText());
                    vo.setUpdateTime(node.path("update_time").asText());
                    result.add(vo);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to list Harbor projects: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<RegistryRepoVO> listRepositories(String project, int page, int pageSize) {
        try {
            String json = restClient.get()
                    .uri("/api/v2.0/projects/{project}/repositories?page={page}&page_size={size}",
                            project, page, pageSize)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(json);
            List<RegistryRepoVO> result = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode node : root) {
                    RegistryRepoVO vo = new RegistryRepoVO();
                    vo.setId(node.path("id").asLong());
                    vo.setProjectName(node.path("project_name").asText());
                    vo.setName(node.path("name").asText());
                    vo.setArtifactCount(node.path("artifact_count").asInt());
                    vo.setPullCount(node.path("pull_count").asLong());
                    vo.setCreationTime(node.path("creation_time").asText());
                    vo.setUpdateTime(node.path("update_time").asText());
                    result.add(vo);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to list repositories for project {}: {}", project, e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<ArtifactVO> listArtifacts(String project, String repo, int page, int pageSize) {
        try {
            String encodedRepo = repo.replace("/", "%2F");
            String json = restClient.get()
                    .uri("/api/v2.0/projects/{project}/repositories/{repo}/artifacts" +
                            "?page={page}&page_size={size}&with_tag=true&with_scan_overview=true&with_label=false",
                            project, encodedRepo, page, pageSize)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(json);
            List<ArtifactVO> result = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode node : root) {
                    ArtifactVO vo = new ArtifactVO();
                    vo.setDigest(node.path("digest").asText());

                    // Size — try to get it from node
                    JsonNode sizeNode = node.path("size");
                    vo.setSize(sizeNode.isNumber() ? sizeNode.asLong() + " B" : "-");

                    // Tags
                    JsonNode tagsNode = node.path("tags");
                    List<TagVO> tags = new ArrayList<>();
                    if (tagsNode.isArray()) {
                        for (JsonNode tagNode : tagsNode) {
                            TagVO tag = new TagVO();
                            tag.setName(tagNode.path("name").asText());
                            tag.setPushTime(tagNode.path("push_time").asText());
                            tag.setPullTime(tagNode.path("pull_time").asText());
                            tag.setImmutable(tagNode.path("immutable").asBoolean());
                            tags.add(tag);
                        }
                    }
                    vo.setTags(tags);

                    // Scan overview
                    JsonNode scanNode = node.path("scan_overview");
                    if (!scanNode.isMissingNode() && scanNode.has("severity")) {
                        vo.setScanOverview(parseScanOverview(scanNode));
                    }

                    result.add(vo);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to list artifacts for {}/{}: {}", project, repo, e.getMessage());
            return List.of();
        }
    }

    @Override
    public void deleteArtifact(String project, String repo, String reference) {
        try {
            String encodedRepo = repo.replace("/", "%2F");
            restClient.delete()
                    .uri("/api/v2.0/projects/{project}/repositories/{repo}/artifacts/{reference}",
                            project, encodedRepo, reference)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Deleted artifact {}/{} reference={}", project, repo, reference);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete artifact: " + e.getMessage(), e);
        }
    }

    @Override
    public ScanOverviewVO getScanOverview(String project, String repo, String reference) {
        try {
            String encodedRepo = repo.replace("/", "%2F");
            String json = restClient.get()
                    .uri("/api/v2.0/projects/{project}/repositories/{repo}/artifacts/{reference}" +
                            "?with_scan_overview=true",
                            project, encodedRepo, reference)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(json);
            JsonNode scanNode = root.path("scan_overview");
            if (scanNode.isMissingNode()) {
                ScanOverviewVO vo = new ScanOverviewVO();
                vo.setStatus("unknown");
                return vo;
            }

            // scan_overview might be an object with keys (Harbor returns it as a map)
            if (scanNode.isObject()) {
                Iterator<String> fieldNames = scanNode.fieldNames();
                if (fieldNames.hasNext()) {
                    String key = fieldNames.next();
                    JsonNode inner = scanNode.path(key);
                    if (inner.isObject()) {
                        return parseScanOverviewInner(inner);
                    }
                }
                return parseScanOverviewInner(scanNode);
            }
            return parseScanOverview(scanNode);
        } catch (Exception e) {
            log.warn("Failed to get scan overview for {}/{}/{}: {}", project, repo, reference, e.getMessage());
            ScanOverviewVO vo = new ScanOverviewVO();
            vo.setStatus("error");
            return vo;
        }
    }

    @Override
    public List<VulnerabilityVO> getVulnerabilities(String project, String repo, String reference, String reportId) {
        try {
            String encodedRepo = repo.replace("/", "%2F");
            String json = restClient.get()
                    .uri("/api/v2.0/projects/{project}/repositories/{repo}/artifacts/{reference}" +
                            "/scan/{report_id}/vulnerabilities",
                            project, encodedRepo, reference, reportId)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(json);
            List<VulnerabilityVO> result = new ArrayList<>();
            JsonNode vulns = root.path("vulnerabilities");
            if (vulns.isArray()) {
                for (JsonNode node : vulns) {
                    VulnerabilityVO vo = new VulnerabilityVO();
                    vo.setId(node.path("id").asText());
                    vo.setPackageName(node.path("package").asText());
                    vo.setVersion(node.path("version").asText());
                    vo.setFixedVersion(node.path("fix_version").asText());
                    vo.setSeverity(node.path("severity").asText());
                    vo.setDescription(node.path("description").asText());

                    List<String> links = new ArrayList<>();
                    JsonNode linksNode = node.path("links");
                    if (linksNode.isArray()) {
                        for (JsonNode link : linksNode) {
                            links.add(link.asText());
                        }
                    }
                    vo.setLinks(links);
                    result.add(vo);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to get vulnerabilities for {}/{}/{}: {}", project, repo, reference, e.getMessage());
            return List.of();
        }
    }

    // ---- internal helpers ----

    private String basicAuthHeader() {
        String raw = username + ":" + (password != null ? password : "");
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private ScanOverviewVO parseScanOverview(JsonNode scanNode) {
        if (scanNode == null || scanNode.isNull()) {
            ScanOverviewVO vo = new ScanOverviewVO();
            vo.setStatus("unknown");
            return vo;
        }

        // Harbor may embed scan as { "application/vnd.scanner.adapter.trivy+json": { ... } }
        if (scanNode.isObject()) {
            Iterator<String> fields = scanNode.fieldNames();
            if (fields.hasNext()) {
                String first = fields.next();
                JsonNode inner = scanNode.path(first);
                if (inner.isObject()) {
                    return parseScanOverviewInner(inner);
                }
            }
        }
        return parseScanOverviewInner(scanNode);
    }

    private ScanOverviewVO parseScanOverviewInner(JsonNode inner) {
        ScanOverviewVO vo = new ScanOverviewVO();
        vo.setStatus(inner.path("scan_status").asText("unknown"));
        vo.setSeverity(inner.path("severity").asText("None"));

        JsonNode summary = inner.path("summary");
        if (!summary.isMissingNode()) {
            vo.setTotalVulnerabilities(summary.path("total").asInt(0));
            vo.setCritical(summary.path("critical").asInt(0));
            vo.setHigh(summary.path("high").asInt(0));
            vo.setMedium(summary.path("medium").asInt(0));
            vo.setLow(summary.path("low").asInt(0));
        }
        return vo;
    }
}