package com.hfwas.devops.pipeline.param;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 版本号只走 semver 三段。省略时补丁位 +1，没有当前版本时从 1.0.0 起。 */
public final class TaskKindVersions {

    private static final Pattern SEMVER = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$");

    private TaskKindVersions() {}

    public static String resolve(String current, String requested) {
        if (requested != null && !requested.isBlank()) {
            String trimmed = requested.trim();
            if (!SEMVER.matcher(trimmed).matches()) {
                throw BizException.of(ResultCode.BAD_REQUEST, "版本号须为 semver，例如 1.2.0");
            }
            return trimmed;
        }
        if (current == null || current.isBlank()) {
            return "1.0.0";
        }
        Matcher matcher = SEMVER.matcher(current.trim());
        if (!matcher.matches()) {
            return "1.0.0";
        }
        int patch = Integer.parseInt(matcher.group(3)) + 1;
        return matcher.group(1) + "." + matcher.group(2) + "." + patch;
    }
}
