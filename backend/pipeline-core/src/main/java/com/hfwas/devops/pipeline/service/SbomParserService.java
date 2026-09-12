package com.hfwas.devops.pipeline.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hfwas.devops.pipeline.entity.DependencyComponentEntity;
import com.hfwas.devops.pipeline.mapper.DependencyComponentMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 解析 CycloneDX SBOM JSON，将组件写入 dependency_component 表。
 */
@Service
public class SbomParserService {

    private static final Logger log = LoggerFactory.getLogger(SbomParserService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DependencyComponentMapper componentMapper;

    public SbomParserService(DependencyComponentMapper componentMapper) {
        this.componentMapper = componentMapper;
    }

    @Transactional
    public int parse(Long artifactId, Long runId, InputStream sbomStream) {
        try {
            JsonNode root = MAPPER.readTree(sbomStream);
            JsonNode components = root.get("components");
            if (components == null || !components.isArray()) {
                log.warn("SBOM components 为空: artifactId={}, runId={}", artifactId, runId);
                return 0;
            }

            List<DependencyComponentEntity> entities = new ArrayList<>();
            for (JsonNode comp : components) {
                DependencyComponentEntity entity = new DependencyComponentEntity();
                entity.setArtifactId(artifactId);
                entity.setRunId(runId);

                // purl (Package URL) — 最标准的组件标识
                JsonNode purlNode = comp.get("purl");
                entity.setPurl(purlNode != null ? purlNode.asText("") : "");

                // group (Maven groupId / npm scope)
                JsonNode groupNode = comp.get("group");
                entity.setGroupName(groupNode != null ? groupNode.asText(null) : null);

                // name
                JsonNode nameNode = comp.get("name");
                entity.setName(nameNode != null ? nameNode.asText("unknown") : "unknown");

                // version
                JsonNode versionNode = comp.get("version");
                entity.setVersion(versionNode != null ? versionNode.asText("") : "");

                // license
                JsonNode licenses = comp.get("licenses");
                if (licenses != null && licenses.isArray() && licenses.size() > 0) {
                    JsonNode first = licenses.get(0);
                    if (first != null) {
                        JsonNode license = first.get("license");
                        if (license != null) {
                            JsonNode id = license.get("id");
                            if (id != null) {
                                entity.setLicense(id.asText(null));
                            } else {
                                JsonNode name = license.get("name");
                                if (name != null) {
                                    entity.setLicense(name.asText(null));
                                }
                            }
                        }
                    }
                }

                // scope
                JsonNode scopeNode = comp.get("scope");
                entity.setScope(scopeNode != null ? scopeNode.asText(null) : null);

                // language — 从 purl 推断
                entity.setLanguage(detectLanguage(entity.getPurl(), comp));

                entity.setCreateTime(LocalDateTime.now());
                entities.add(entity);
            }

            // 批量插入
            for (DependencyComponentEntity entity : entities) {
                componentMapper.insert(entity);
            }

            log.info("SBOM 解析完成: artifactId={}, runId={}, components={}", artifactId, runId, entities.size());
            return entities.size();

        } catch (Exception e) {
            log.error("SBOM 解析失败: artifactId={}, runId={}", artifactId, runId, e);
            return 0;
        }
    }

    private static String detectLanguage(String purl, JsonNode comp) {
        if (purl != null && !purl.isBlank()) {
            // purl 格式: pkg:maven/group/artifact@version
            if (purl.startsWith("pkg:maven/")) return "java";
            if (purl.startsWith("pkg:npm/")) return "javascript";
            if (purl.startsWith("pkg:golang/")) return "go";
            if (purl.startsWith("pkg:pypi/")) return "python";
            if (purl.startsWith("pkg:gem/")) return "ruby";
            if (purl.startsWith("pkg:cargo/")) return "rust";
            if (purl.startsWith("pkg:nuget/")) return "dotnet";
            if (purl.startsWith("pkg:composer/")) return "php";
        }
        // fallback: 从 type/group 推断
        JsonNode typeNode = comp.get("type");
        if (typeNode != null) {
            String type = typeNode.asText("");
            if ("library".equals(type)) {
                JsonNode group = comp.get("group");
                if (group != null) {
                    String g = group.asText();
                    if (g != null) {
                        if (g.contains("com.") || g.contains("org.")) return "java";
                    }
                }
            }
        }
        return "unknown";
    }
}