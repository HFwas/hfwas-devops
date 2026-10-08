package com.hfwas.devops.container.service.helm;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.container.error.ContainerErrorCode;

import java.util.regex.Pattern;

/**
 * {@code oci://host[:port]/project/name:version}. The version tag is split off for {@code helm --version}.
 */
public record HelmChartRef(String host, String locator, String version) {

    private static final Pattern HOST = Pattern.compile(
            "^[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?(?::[0-9]{1,5})?$");

    public static HelmChartRef parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw invalid("chartRef 为空");
        }
        String value = raw.trim();
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0 || value.indexOf(' ') >= 0
                || value.indexOf('\\') >= 0 || value.indexOf('@') >= 0) {
            throw invalid("chartRef 无效");
        }
        if (!value.startsWith("oci://")) {
            throw invalid("chartRef 必须是 oci:// 引用");
        }
        String rest = value.substring("oci://".length());
        int slash = rest.indexOf('/');
        if (slash <= 0 || slash == rest.length() - 1) {
            throw invalid("chartRef 需要包含 host 和 chart 名称");
        }
        String host = rest.substring(0, slash);
        String pathAndTag = rest.substring(slash + 1);
        int tagSep = pathAndTag.lastIndexOf(':');
        if (tagSep <= 0 || tagSep == pathAndTag.length() - 1 || pathAndTag.indexOf(':') != tagSep) {
            throw invalid("chartRef 需要包含版本");
        }
        String path = pathAndTag.substring(0, tagSep);
        String version = pathAndTag.substring(tagSep + 1);
        if (!HOST.matcher(host).matches() || path.isBlank() || path.contains("..")) {
            throw invalid("chartRef 无效");
        }
        if (!HelmVersions.isSemVer(version)) {
            throw invalid("chartRef 版本必须是 SemVer");
        }
        return new HelmChartRef(host, "oci://" + host + "/" + path, version);
    }

    public String chartRef() {
        return locator + ":" + version;
    }

    private static BizException invalid(String message) {
        return new BizException(ContainerErrorCode.HELM_RELEASE_INVALID, message);
    }
}
