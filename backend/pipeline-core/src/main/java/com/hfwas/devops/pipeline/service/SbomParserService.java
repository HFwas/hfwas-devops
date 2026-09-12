package com.hfwas.devops.pipeline.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hfwas.devops.pipeline.entity.DependencyComponentEntity;
import com.hfwas.devops.pipeline.entity.PipelineRunEntity;
import com.hfwas.devops.pipeline.mapper.DependencyComponentMapper;
import com.hfwas.devops.pipeline.mapper.PipelineRunMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 解析 CycloneDX SBOM JSON，将组件写入 dependency_component 表。
 * 同一流水线内按 identity_key（purl 或 GAV）upsert，流水线多次执行不产生重复行。
 */
@Service
public class SbomParserService {

    private static final Logger log = LoggerFactory.getLogger(SbomParserService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DependencyComponentMapper componentMapper;
    private final PipelineRunMapper runMapper;

    public SbomParserService(DependencyComponentMapper componentMapper, PipelineRunMapper runMapper) {
        this.componentMapper = componentMapper;
        this.runMapper = runMapper;
    }

    @Transactional
    public int parse(Long artifactId, Long runId, InputStream sbomStream) {
        try {
            JsonNode root = MAPPER.readTree(sbomStream);
            JsonNode components = root.get("components");
            if (components == null || !components.isArray() || components.isEmpty()) {
                log.warn("SBOM components 为空: artifactId={}, runId={}", artifactId, runId);
                return 0;
            }

            PipelineRunEntity run = runMapper.selectById(runId);
            if (run == null || run.getPipelineId() == null) {
                log.warn("SBOM 解析跳过：运行不存在或无 pipelineId, artifactId={}, runId={}", artifactId, runId);
                return 0;
            }
            Long pipelineId = run.getPipelineId();

            Map<String, DependencyComponentEntity> unique = new LinkedHashMap<>();
            for (JsonNode comp : components) {
                DependencyComponentEntity entity = toEntity(comp, artifactId, runId, pipelineId);
                unique.putIfAbsent(entity.getIdentityKey(), entity);
            }

            int inserted = 0;
            int updated = 0;
            for (DependencyComponentEntity entity : unique.values()) {
                DependencyComponentEntity existing = componentMapper.selectOne(
                        new LambdaQueryWrapper<DependencyComponentEntity>()
                                .eq(DependencyComponentEntity::getPipelineId, pipelineId)
                                .eq(DependencyComponentEntity::getIdentityKey, entity.getIdentityKey())
                                .last("LIMIT 1"));
                if (existing == null) {
                    componentMapper.insert(entity);
                    inserted++;
                } else {
                    entity.setId(existing.getId());
                    componentMapper.updateById(entity);
                    updated++;
                }
            }

            log.info("SBOM 解析完成: artifactId={}, runId={}, pipelineId={}, unique={}, inserted={}, updated={}",
                    artifactId, runId, pipelineId, unique.size(), inserted, updated);
            return unique.size();

        } catch (Exception e) {
            log.error("SBOM 解析失败: artifactId={}, runId={}", artifactId, runId, e);
            return 0;
        }
    }

    private static DependencyComponentEntity toEntity(JsonNode comp, Long artifactId, Long runId, Long pipelineId) {
        DependencyComponentEntity entity = new DependencyComponentEntity();
        entity.setArtifactId(artifactId);
        entity.setRunId(runId);
        entity.setPipelineId(pipelineId);

        JsonNode purlNode = comp.get("purl");
        String purl = purlNode != null ? purlNode.asText("") : "";
        entity.setPurl(purl);

        JsonNode groupNode = comp.get("group");
        entity.setGroupName(groupNode != null ? groupNode.asText(null) : null);

        JsonNode nameNode = comp.get("name");
        entity.setName(nameNode != null ? nameNode.asText("unknown") : "unknown");

        JsonNode versionNode = comp.get("version");
        entity.setVersion(versionNode != null ? versionNode.asText("") : "");

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

        JsonNode scopeNode = comp.get("scope");
        entity.setScope(scopeNode != null ? scopeNode.asText(null) : null);

        entity.setLanguage(detectLanguage(entity.getPurl(), comp));
        entity.setIdentityKey(identityKey(entity.getPurl(), entity.getGroupName(), entity.getName(), entity.getVersion()));
        entity.setCreateTime(LocalDateTime.now());
        return entity;
    }

    static String identityKey(String purl, String groupName, String name, String version) {
        if (purl != null && !purl.isBlank()) {
            return purl.trim();
        }
        return "gav:" + nullToEmpty(groupName) + "|" + nullToEmpty(name) + "|" + nullToEmpty(version);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String detectLanguage(String purl, JsonNode comp) {
        if (purl != null && !purl.isBlank()) {
            if (purl.startsWith("pkg:maven/")) return "java";
            if (purl.startsWith("pkg:npm/")) return "javascript";
            if (purl.startsWith("pkg:golang/")) return "go";
            if (purl.startsWith("pkg:pypi/")) return "python";
            if (purl.startsWith("pkg:gem/")) return "ruby";
            if (purl.startsWith("pkg:cargo/")) return "rust";
            if (purl.startsWith("pkg:nuget/")) return "dotnet";
            if (purl.startsWith("pkg:composer/")) return "php";
        }
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
