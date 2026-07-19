package com.hfwas.devops.common.ldap;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

/**
 * Validates admin-configured LDAP connector settings before JNDI / search use.
 */
public final class LdapConfigValidator {

    private static final Pattern DN_PATTERN = Pattern.compile("^[A-Za-z0-9=,+_.\\- /]+$");
    private static final Pattern FILTER_PATTERN = Pattern.compile("^[()&|!=*~><$0-9a-zA-Z._\\-:; /]+$");

    private LdapConfigValidator() {
    }

    public static String toProviderUrl(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("LDAP 地址不能为空");
        }
        URI uri = parseUri(url.trim());
        String scheme = uri.getScheme();
        if (scheme == null || (!"ldap".equalsIgnoreCase(scheme) && !"ldaps".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("仅支持 ldap 或 ldaps 协议");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("LDAP 地址缺少主机名");
        }
        return uri.toString();
    }

    public static String validateBaseDn(String baseDn) {
        String dn = requireNonBlank(baseDn, "Base DN 不能为空").trim();
        if (!dn.contains("=") || !DN_PATTERN.matcher(dn).matches()) {
            throw new IllegalArgumentException("Base DN 格式无效");
        }
        return dn;
    }

    public static String validateUserFilter(String userFilter) {
        String filter = requireNonBlank(userFilter, "用户过滤条件不能为空").trim();
        if (!filter.startsWith("(") || !isBalancedParentheses(filter)) {
            throw new IllegalArgumentException("LDAP 过滤条件格式无效");
        }
        if (!FILTER_PATTERN.matcher(filter).matches()) {
            throw new IllegalArgumentException("LDAP 过滤条件包含非法字符");
        }
        return filter;
    }

    private static URI parseUri(String url) {
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("无效的 LDAP 地址", e);
        }
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static boolean isBalancedParentheses(String filter) {
        int depth = 0;
        for (char ch : filter.toCharArray()) {
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
                if (depth < 0) {
                    return false;
                }
            }
        }
        return depth == 0;
    }
}
