package com.hfwas.devops.container.service.helm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.container.dto.HelmReleaseHistoryVO;
import com.hfwas.devops.container.dto.HelmReleaseResourceVO;
import com.hfwas.devops.container.dto.HelmReleaseVO;
import com.hfwas.devops.container.error.ContainerErrorCode;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses helm JSON/YAML. Malformed output becomes a short error and is not echoed back.
 */
final class HelmOutputParser {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern CHART = Pattern.compile("^(.*)-(\\d+\\.\\d+\\.\\d+(?:-[0-9A-Za-z.-]+)?)$");
    private static final DateTimeFormatter HELM_LIST_TIME = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
            .optionalEnd()
            .appendPattern(" Z")
            .optionalStart()
            .appendLiteral(' ')
            .appendZoneText(java.time.format.TextStyle.SHORT)
            .optionalEnd()
            .toFormatter(Locale.ENGLISH);

    private HelmOutputParser() {
    }

    static List<HelmReleaseVO> releases(String json) {
        JsonNode root = readJson(json, true);
        if (root == null || root.isNull() || root.isMissingNode()) {
            return List.of();
        }
        if (!root.isArray()) {
            throw unreadable();
        }
        List<HelmReleaseVO> releases = new ArrayList<>();
        for (JsonNode item : root) {
            HelmReleaseVO vo = new HelmReleaseVO();
            vo.setName(text(item, "name"));
            vo.setNamespace(text(item, "namespace"));
            vo.setRevision(revision(item.get("revision")));
            vo.setStatus(text(item, "status"));
            vo.setUpdatedAt(timestamp(text(item, "updated")));
            vo.setAppVersion(firstText(item, "app_version", "appVersion"));
            ChartName chart = splitChart(text(item, "chart"));
            vo.setChartName(chart.name());
            vo.setChartVersion(chart.version());
            vo.setChartRef("");
            vo.setValuesYaml("");
            vo.setManifest("");
            releases.add(vo);
        }
        return releases;
    }

    static HelmReleaseVO status(String json) {
        JsonNode root = readJson(json, false);
        if (root == null || !root.isObject()) {
            throw unreadable();
        }
        HelmReleaseVO vo = new HelmReleaseVO();
        vo.setName(text(root, "name"));
        vo.setNamespace(text(root, "namespace"));
        vo.setRevision(revision(root.get("version")));
        JsonNode info = root.path("info");
        vo.setStatus(text(info, "status"));
        vo.setNotes(emptyToNull(text(info, "notes")));
        vo.setUpdatedAt(timestamp(firstText(info, "last_deployed", "lastDeployed")));
        JsonNode metadata = root.path("chart").path("metadata");
        // helm status -o json (v3.18) has name/info/config/manifest/hooks/version/namespace
        // and no chart object. Callers fill chart identity from history when these stay empty.
        vo.setChartName(text(metadata, "name"));
        vo.setChartVersion(text(metadata, "version"));
        vo.setAppVersion(firstText(metadata, "appVersion", "app_version"));
        vo.setManifest(text(root, "manifest"));
        vo.setChartRef("");
        vo.setResources(resources(vo.getManifest()));
        return vo;
    }

    static List<HelmReleaseHistoryVO> history(String json) {
        JsonNode root = readJson(json, true);
        if (root == null || root.isNull() || root.isMissingNode()) {
            return List.of();
        }
        if (!root.isArray()) {
            throw unreadable();
        }
        List<HelmReleaseHistoryVO> items = new ArrayList<>();
        for (JsonNode item : root) {
            HelmReleaseHistoryVO vo = new HelmReleaseHistoryVO();
            vo.setRevision(revision(item.get("revision")));
            vo.setStatus(text(item, "status"));
            vo.setDescription(text(item, "description"));
            vo.setUpdatedAt(timestamp(text(item, "updated")));
            vo.setAppVersion(firstText(item, "app_version", "appVersion"));
            ChartName chart = splitChart(text(item, "chart"));
            vo.setChartName(chart.name());
            vo.setChartVersion(chart.version());
            vo.setValuesYaml("");
            items.add(vo);
        }
        return items;
    }

    static String userValues(String stdout) {
        if (stdout == null) {
            return "";
        }
        String text = stdout.replace("\r\n", "\n").trim();
        if (text.startsWith("USER-SUPPLIED VALUES:")) {
            text = text.substring("USER-SUPPLIED VALUES:".length()).trim();
        }
        while (text.startsWith("#")) {
            int nl = text.indexOf('\n');
            if (nl < 0) {
                return "";
            }
            text = text.substring(nl + 1).trim();
        }
        if (text.isEmpty() || "null".equals(text) || "{}".equals(text)) {
            return "";
        }
        return text.endsWith("\n") ? text : text + "\n";
    }

    static String dryRunManifest(String stdout) {
        if (stdout == null || stdout.isBlank()) {
            return "";
        }
        String trimmed = stdout.trim();
        if (trimmed.startsWith("{")) {
            JsonNode root = readJson(trimmed, false);
            if (root == null || !root.isObject()) {
                throw unreadable();
            }
            return text(root, "manifest");
        }
        int marker = stdout.indexOf("MANIFEST:");
        if (marker < 0) {
            return stdout.trim();
        }
        int content = stdout.indexOf('\n', marker);
        if (content < 0) {
            return "";
        }
        int notes = stdout.indexOf("\nNOTES:", content);
        String body = notes < 0 ? stdout.substring(content + 1) : stdout.substring(content + 1, notes);
        return body.trim();
    }

    static List<HelmReleaseResourceVO> resources(String manifest) {
        return resources(manifest, null);
    }

    /**
     * @param releaseNamespace used when a document omits {@code metadata.namespace}
     *                         (charts from {@code helm create} do this)
     */
    static List<HelmReleaseResourceVO> resources(String manifest, String releaseNamespace) {
        if (manifest == null || manifest.isBlank()) {
            return List.of();
        }
        String fallback = releaseNamespace == null || releaseNamespace.isBlank() ? null : releaseNamespace.trim();
        List<HelmReleaseResourceVO> resources = new ArrayList<>();
        try {
            for (Object document : yaml().loadAll(manifest)) {
                if (!(document instanceof Map<?, ?> map)) {
                    continue;
                }
                String kind = scalar(map.get("kind"));
                if (kind.isEmpty()) {
                    continue;
                }
                HelmReleaseResourceVO vo = new HelmReleaseResourceVO();
                vo.setApiVersion(scalar(map.get("apiVersion")));
                vo.setKind(kind);
                vo.setStatus("unknown");
                Object metadata = map.get("metadata");
                if (metadata instanceof Map<?, ?> meta) {
                    vo.setName(scalar(meta.get("name")));
                    String namespace = scalar(meta.get("namespace"));
                    vo.setNamespace(namespace.isEmpty() ? fallback : namespace);
                } else {
                    vo.setName("");
                    vo.setNamespace(fallback);
                }
                resources.add(vo);
            }
        } catch (RuntimeException e) {
            throw new BizException(ContainerErrorCode.HELM_RELEASE_FAILED, "无法解析 Helm 清单");
        }
        return resources;
    }

    static ChartName splitChart(String chart) {
        if (chart == null || chart.isBlank()) {
            return new ChartName("", "");
        }
        Matcher matcher = CHART.matcher(chart.trim());
        if (!matcher.matches()) {
            return new ChartName(chart.trim(), "");
        }
        return new ChartName(matcher.group(1), matcher.group(2));
    }

    static String timestamp(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String text = raw.trim();
        try {
            return OffsetDateTime.parse(text).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            // helm list uses a custom timestamp
        }
        try {
            return Instant.parse(text).toString();
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return java.time.ZonedDateTime.parse(text, HELM_LIST_TIME)
                    .toOffsetDateTime()
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (DateTimeParseException ignored) {
            return text;
        }
    }

    private static JsonNode readJson(String json, boolean emptyAsNull) {
        if (json == null || json.isBlank()) {
            if (emptyAsNull) {
                return null;
            }
            throw unreadable();
        }
        try {
            return JSON.readTree(json);
        } catch (Exception e) {
            throw unreadable();
        }
    }

    private static BizException unreadable() {
        return new BizException(ContainerErrorCode.HELM_RELEASE_FAILED, "无法解析 helm 输出");
    }

    private static int revision(JsonNode node) {
        if (node == null || node.isNull()) {
            return 0;
        }
        if (node.isNumber()) {
            return node.asInt();
        }
        try {
            return Integer.parseInt(node.asText("0").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return "";
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        return value.asText("");
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String scalar(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static Yaml yaml() {
        LoaderOptions loader = new LoaderOptions();
        loader.setCodePointLimit(2 * 1024 * 1024);
        loader.setMaxAliasesForCollections(20);
        loader.setNestingDepthLimit(50);
        DumperOptions dumper = new DumperOptions();
        return new Yaml(new SafeConstructor(loader), new Representer(dumper), dumper, loader);
    }

    record ChartName(String name, String version) {
    }
}
