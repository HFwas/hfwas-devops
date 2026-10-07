package com.hfwas.devops.container.service.helm;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.container.error.ContainerErrorCode;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads a .tgz chart without writing it to disk. Rejects path traversal, links, and oversized archives.
 */
@Component
public class HelmChartArchiveInspector {

    private static final Pattern CHART_NAME = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");
    private static final int MAX_PATH_LENGTH = 1024;

    public HelmChartPackageMeta inspect(Path archive, HelmChartArchiveLimits limits) {
        Map<String, byte[]> topLevelChartYaml = new HashMap<>();
        List<String> chartYamlPaths = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        long uncompressed = 0;
        int entries = 0;
        try (InputStream fileIn = Files.newInputStream(archive);
             GZIPInputStream gzip = new GZIPInputStream(fileIn);
             TarArchiveInputStream tar = new TarArchiveInputStream(gzip)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                entries++;
                if (entries > limits.maxEntries()) {
                    throw tooLarge("压缩包内文件数量超过限制");
                }
                String name = normalize(entry.getName());
                if (!seen.add(name)) {
                    throw invalid("压缩包包含重复路径: " + safe(name));
                }
                if (entry.isSymbolicLink() || entry.isLink()) {
                    throw invalid("压缩包不允许符号链接或硬链接: " + safe(name));
                }
                boolean directory = entry.isDirectory();
                long room = limits.maxUncompressedBytes() - uncompressed;
                if (room < 0) {
                    throw tooLarge("解压后大小超过限制");
                }
                if (directory) {
                    uncompressed += drain(tar, room);
                    continue;
                }
                boolean chartYaml = name.equals("Chart.yaml") || name.endsWith("/Chart.yaml");
                if (chartYaml && slashCount(name) <= 1) {
                    byte[] body = readCapped(tar, limits.maxChartYamlBytes(), room, "Chart.yaml 过大");
                    uncompressed += body.length;
                    topLevelChartYaml.put(name, body);
                    chartYamlPaths.add(name);
                } else if (chartYaml) {
                    uncompressed += drain(tar, room);
                    chartYamlPaths.add(name);
                } else {
                    uncompressed += drain(tar, room);
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (IOException e) {
            throw invalid("无法读取 Helm Chart 包: " + safe(e.getMessage()));
        }
        if (entries == 0) {
            throw invalid("Helm Chart 包是空的");
        }
        String root = selectRootChartYaml(chartYamlPaths);
        byte[] rootBytes = topLevelChartYaml.get(root);
        if (rootBytes == null) {
            throw invalid("未找到 Chart.yaml");
        }
        HelmChartPackageMeta meta = parseChartYaml(rootBytes, limits.maxChartYamlBytes());
        String rootDir = root.equals("Chart.yaml") ? "" : root.substring(0, root.length() - "/Chart.yaml".length());
        if (!rootDir.isEmpty() && !rootDir.equals(meta.name())) {
            throw invalid("Chart 目录名与 Chart.yaml name 不一致");
        }
        String nestedPrefix = rootDir.isEmpty() ? "charts/" : rootDir + "/charts/";
        for (String path : chartYamlPaths) {
            if (path.equals(root)) {
                continue;
            }
            if (!path.startsWith(nestedPrefix)) {
                throw invalid("非法的 Chart.yaml 位置: " + safe(path));
            }
        }
        return meta;
    }

    private static String selectRootChartYaml(List<String> paths) {
        String root = null;
        for (String path : paths) {
            boolean top = path.equals("Chart.yaml") || slashCount(path) == 1;
            if (!top) {
                continue;
            }
            if (root != null) {
                throw invalid("压缩包包含多个顶层 Chart.yaml");
            }
            root = path;
        }
        if (root == null) {
            throw invalid("未找到 Chart.yaml");
        }
        return root;
    }

    static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalid("压缩包包含空路径");
        }
        String name = raw.replace('\\', '/');
        if (name.startsWith("/")) {
            throw invalid("压缩包包含绝对路径");
        }
        while (name.startsWith("./")) {
            name = name.substring(2);
        }
        if (name.endsWith("/")) {
            name = name.substring(0, name.length() - 1);
        }
        if (name.isBlank() || name.length() > MAX_PATH_LENGTH) {
            throw invalid("压缩包路径非法");
        }
        for (String part : name.split("/", -1)) {
            if (part.isEmpty() || ".".equals(part) || "..".equals(part) || part.indexOf('\0') >= 0) {
                throw invalid("压缩包路径非法: " + safe(name));
            }
        }
        return name;
    }

    private static HelmChartPackageMeta parseChartYaml(byte[] body, int maxBytes) {
        if (body.length == 0) {
            throw invalid("Chart.yaml 为空");
        }
        String text = new String(body, StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setCodePointLimit(maxBytes);
        loaderOptions.setMaxAliasesForCollections(10);
        loaderOptions.setNestingDepthLimit(30);
        loaderOptions.setAllowDuplicateKeys(false);
        DumperOptions dumperOptions = new DumperOptions();
        Yaml yaml = new Yaml(
                new SafeConstructor(loaderOptions),
                new Representer(dumperOptions),
                dumperOptions,
                loaderOptions,
                new LiteralResolver());
        Object loaded;
        try {
            loaded = yaml.load(text);
        } catch (RuntimeException e) {
            throw invalid("Chart.yaml 无法解析");
        }
        if (!(loaded instanceof Map<?, ?> rawMap)) {
            throw invalid("Chart.yaml 必须是映射");
        }
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw invalid("Chart.yaml 字段名无效");
            }
            map.put(key, entry.getValue());
        }
        String apiVersion = requiredString(map, "apiVersion");
        if (!"v1".equals(apiVersion) && !"v2".equals(apiVersion)) {
            throw invalid("Chart.yaml apiVersion 必须是 v1 或 v2");
        }
        String name = requiredString(map, "name");
        if (!CHART_NAME.matcher(name).matches()) {
            throw invalid("Chart 名称必须是小写字母、数字和连字符");
        }
        String version = requiredString(map, "version");
        if (version.indexOf('+') >= 0) {
            throw invalid("version 不能包含 +，OCI tag 不支持构建元数据");
        }
        if (!HelmVersions.isSemVer(version)) {
            throw invalid("version 必须是 SemVer，例如 1.2.3");
        }
        Object type = map.get("type");
        if (type != null) {
            if (!(type instanceof String typeText) || (!"application".equals(typeText) && !"library".equals(typeText))) {
                throw invalid("Chart.yaml type 必须是 application 或 library");
            }
        }
        return new HelmChartPackageMeta(
                name,
                version,
                clip(optionalString(map, "description"), 2000),
                clip(optionalString(map, "appVersion"), 128),
                apiVersion);
    }

    private static String requiredString(Map<String, Object> map, String key) {
        String value = optionalString(map, key);
        if (value.isEmpty()) {
            throw invalid("Chart.yaml 缺少 " + key);
        }
        return value;
    }

    private static String optionalString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return "";
        }
        if (!(value instanceof String text)) {
            throw invalid("Chart.yaml 字段 " + key + " 必须是字符串");
        }
        return text.trim();
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replace("\0", "");
        return cleaned.length() <= max ? cleaned : cleaned.substring(0, max);
    }

    private static byte[] readCapped(InputStream in, int fileCap, long budget, String fileTooBig) throws IOException {
        if (budget <= 0) {
            throw tooLarge("解压后大小超过限制");
        }
        long cap = Math.min(fileCap, budget);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) >= 0) {
            total += n;
            if (total > fileCap) {
                throw invalid(fileTooBig);
            }
            if (total > cap) {
                throw tooLarge("解压后大小超过限制");
            }
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }

    private static long drain(InputStream in, long budget) throws IOException {
        byte[] buf = new byte[8192];
        long total = 0;
        int n;
        while ((n = in.read(buf)) >= 0) {
            total += n;
            if (total > budget) {
                throw tooLarge("解压后大小超过限制");
            }
        }
        return total;
    }

    private static int slashCount(String name) {
        int count = 0;
        for (int i = 0; i < name.length(); i++) {
            if (name.charAt(i) == '/') {
                count++;
            }
        }
        return count;
    }

    private static BizException invalid(String message) {
        return new BizException(ContainerErrorCode.HELM_CHART_INVALID, message);
    }

    private static BizException tooLarge(String message) {
        return new BizException(ContainerErrorCode.HELM_CHART_TOO_LARGE, message);
    }

    private static String safe(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
        return cleaned.length() > 180 ? cleaned.substring(0, 180) : cleaned;
    }

    /**
     * Keeps every scalar as a string so version {@code 1.10} is not parsed as the float {@code 1.1}.
     */
    private static final class LiteralResolver extends Resolver {
        @Override
        protected void addImplicitResolvers() {
            // no implicit int/float/bool/timestamp
        }
    }
}
