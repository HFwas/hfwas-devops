package com.hfwas.devops.pipeline.tekton;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public record GitRemote(String scheme, String host, String path) {

    public static GitRemote parse(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) {
            throw BizException.of(ResultCode.BAD_REQUEST, "仓库地址不能为空");
        }
        String trimmed = repoUrl.trim();
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw BizException.of(ResultCode.BAD_REQUEST, "仓库地址无法解析: " + trimmed);
        }
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw BizException.of(ResultCode.BAD_REQUEST, "仓库地址需要 http 或 https 协议: " + trimmed);
        }
        String host = uri.getHost();
        String path = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");
        if (host == null || path.isBlank()) {
            throw BizException.of(ResultCode.BAD_REQUEST, "仓库地址缺少主机或路径: " + trimmed);
        }
        return new GitRemote(scheme.toLowerCase(Locale.ROOT), host, path);
    }
}
