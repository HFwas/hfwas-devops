package com.hfwas.devops.container.service.helm;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.container.error.ContainerErrorCode;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Parsed Harbor OCI chart repository. chartRef is {@code oci://host/project/name:version}.
 */
public record OciChartReference(String host, String project, boolean plainHttp) {

    private static final Pattern HOST = Pattern.compile(
            "^[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?(?::[0-9]{1,5})?$");
    private static final Pattern SEGMENT = Pattern.compile("[a-z0-9]+(?:[._-][a-z0-9]+)*");

    public static OciChartReference parse(String raw, boolean insecure) {
        if (raw == null || raw.isBlank()) {
            throw new BizException(ContainerErrorCode.HELM_CHART_REPO_NOT_CONFIGURED,
                    "默认 OCI 仓库地址未配置");
        }
        String rest = raw.trim();
        boolean plain = insecure;
        int scheme = rest.indexOf("://");
        if (scheme >= 0) {
            String proto = rest.substring(0, scheme).toLowerCase(Locale.ROOT);
            rest = rest.substring(scheme + 3);
            switch (proto) {
                case "http" -> plain = true;
                case "https", "oci" -> {
                    // https still uses TLS; insecure=true opts into plain HTTP / skipped verify
                }
                default -> throw new BizException(ContainerErrorCode.HELM_CHART_INVALID,
                        "不支持的仓库协议: " + proto);
            }
        }
        if (rest.contains("@") || rest.contains(" ") || rest.contains("\\")) {
            throw new BizException(ContainerErrorCode.HELM_CHART_INVALID,
                    "仓库地址不能包含用户信息或空白");
        }
        while (rest.endsWith("/")) {
            rest = rest.substring(0, rest.length() - 1);
        }
        int slash = rest.indexOf('/');
        if (slash <= 0 || slash == rest.length() - 1) {
            throw new BizException(ContainerErrorCode.HELM_CHART_REPO_NOT_CONFIGURED,
                    "OCI 仓库地址需要包含 host 和 project，例如 oci://harbor.example/charts");
        }
        String host = rest.substring(0, slash).toLowerCase(Locale.ROOT);
        String project = rest.substring(slash + 1).toLowerCase(Locale.ROOT);
        if (!HOST.matcher(host).matches() || !validPort(host)) {
            throw new BizException(ContainerErrorCode.HELM_CHART_INVALID, "OCI 仓库 host 无效");
        }
        if (!validProject(project)) {
            throw new BizException(ContainerErrorCode.HELM_CHART_INVALID, "OCI project 路径无效");
        }
        return new OciChartReference(host, project, plain);
    }

    public String registryBase() {
        return "oci://" + host + "/" + project;
    }

    public String chartLocator(String chartName) {
        return registryBase() + "/" + chartName;
    }

    public String chartRef(String chartName, String version) {
        return chartLocator(chartName) + ":" + version;
    }

    private static boolean validPort(String host) {
        int colon = host.lastIndexOf(':');
        if (colon < 0) {
            return true;
        }
        int port;
        try {
            port = Integer.parseInt(host.substring(colon + 1));
        } catch (NumberFormatException e) {
            return false;
        }
        return port >= 1 && port <= 65535;
    }

    private static boolean validProject(String project) {
        if (project.isEmpty() || project.length() > 256) {
            return false;
        }
        for (String segment : project.split("/", -1)) {
            if (!SEGMENT.matcher(segment).matches() || segment.length() > 128) {
                return false;
            }
        }
        return true;
    }
}
