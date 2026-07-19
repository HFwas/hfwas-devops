package com.hfwas.devops.user.integration.ldap;

import com.hfwas.devops.common.ldap.LdapConfigValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hfwas.devops.user.integration.model.ConnectorTestResult;
import com.hfwas.devops.user.integration.model.ExternalUserSnapshot;
import com.hfwas.devops.user.integration.spi.IdentityConnectorHandler;
import com.hfwas.devops.user.model.LdapConnectorConfig;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.directory.*;
import java.util.*;

@Component
@RequiredArgsConstructor
public class LdapConnectorHandler implements IdentityConnectorHandler {

    public static final String TYPE = "ldap";

    private final ObjectMapper objectMapper;

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String typeLabel() {
        return "LDAP / Active Directory";
    }

    @Override
    public void validateConfig(String configJson) {
        LdapConnectorConfig config = parseConfig(configJson);
        LdapConfigValidator.toProviderUrl(config.getUrl());
        LdapConfigValidator.validateBaseDn(config.getBaseDn());
        require(config.getBindDn(), "Bind DN 不能为空");
        require(config.getBindPassword(), "Bind 密码不能为空");
        LdapConfigValidator.validateUserFilter(config.getUserFilter());
    }

    @Override
    public ConnectorTestResult testConnection(String configJson) {
        LdapConnectorConfig config = parseConfig(configJson);
        validateConfig(configJson);
        try {
            List<ExternalUserSnapshot> users = searchUsers(config, 5);
            return ConnectorTestResult.builder()
                    .success(true)
                    .message("连接成功，示例检索到 " + users.size() + " 个用户")
                    .sampleCount(users.size())
                    .build();
        } catch (Exception e) {
            return ConnectorTestResult.builder()
                    .success(false)
                    .message("连接失败: " + rootMessage(e))
                    .build();
        }
    }

    @Override
    public List<ExternalUserSnapshot> fetchUsers(String configJson) {
        LdapConnectorConfig config = parseConfig(configJson);
        validateConfig(configJson);
        try {
            return searchUsers(config, 0);
        } catch (Exception e) {
            throw new IllegalArgumentException("LDAP 检索失败: " + rootMessage(e), e);
        }
    }

    private List<ExternalUserSnapshot> searchUsers(LdapConnectorConfig config, int limit) throws Exception {
        DirContext context = openContext(config);
        try {
            SearchControls controls = new SearchControls();
            controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
            controls.setReturningAttributes(new String[]{
                    config.getUsernameAttribute(),
                    config.getDisplayNameAttribute(),
                    config.getEmailAttribute(),
                    config.getPhoneAttribute(),
                    config.getExternalIdAttribute(),
                    "uid", "cn", "mail"
            });
            String baseDn = LdapConfigValidator.validateBaseDn(config.getBaseDn());
            String userFilter = LdapConfigValidator.validateUserFilter(config.getUserFilter());
            NamingEnumeration<SearchResult> results = context.search(baseDn, userFilter, controls);
            List<ExternalUserSnapshot> users = new ArrayList<>();
            while (results.hasMore()) {
                SearchResult result = results.next();
                Attributes attrs = result.getAttributes();
                String username = attr(attrs, config.getUsernameAttribute());
                if (StringUtils.isBlank(username)) {
                    continue;
                }
                String displayName = StringUtils.defaultIfBlank(
                        attr(attrs, config.getDisplayNameAttribute()), username);
                String externalId = StringUtils.defaultIfBlank(
                        attr(attrs, config.getExternalIdAttribute()), username);
                users.add(ExternalUserSnapshot.builder()
                        .externalId(externalId.trim())
                        .username(username.trim().toLowerCase())
                        .displayName(displayName.trim())
                        .email(StringUtils.trimToNull(attr(attrs, config.getEmailAttribute())))
                        .phone(StringUtils.trimToNull(attr(attrs, config.getPhoneAttribute())))
                        .enabled(true)
                        .build());
                if (limit > 0 && users.size() >= limit) {
                    break;
                }
            }
            return users;
        } finally {
            context.close();
        }
    }

    private DirContext openContext(LdapConnectorConfig config) throws Exception {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, LdapConfigValidator.toProviderUrl(config.getUrl()));
        env.put(Context.REFERRAL, "ignore");
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, config.getBindDn().trim());
        env.put(Context.SECURITY_CREDENTIALS, config.getBindPassword());
        env.put("com.sun.jndi.ldap.connect.timeout", "5000");
        env.put("com.sun.jndi.ldap.read.timeout", "15000");
        return new InitialDirContext(env);
    }

    private LdapConnectorConfig parseConfig(String configJson) {
        if (StringUtils.isBlank(configJson)) {
            throw new IllegalArgumentException("LDAP 配置不能为空");
        }
        try {
            return objectMapper.readValue(configJson, LdapConnectorConfig.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("LDAP 配置 JSON 无效", e);
        }
    }

    private static String attr(Attributes attrs, String name) {
        if (attrs == null || StringUtils.isBlank(name)) {
            return null;
        }
        try {
            Attribute attribute = attrs.get(name);
            if (attribute == null) {
                return null;
            }
            Object value = attribute.get();
            return value != null ? String.valueOf(value) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static void require(String value, String message) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null) {
            cur = cur.getCause();
        }
        return StringUtils.defaultIfBlank(cur.getMessage(), cur.getClass().getSimpleName());
    }
}
