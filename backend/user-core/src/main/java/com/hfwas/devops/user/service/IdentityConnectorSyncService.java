package com.hfwas.devops.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hfwas.devops.user.context.UserContext;
import com.hfwas.devops.user.context.UserContextHolder;
import com.hfwas.devops.user.entity.SysIdentityConnector;
import com.hfwas.devops.user.entity.SysUser;
import com.hfwas.devops.user.integration.engine.IdentityConnectorRegistry;
import com.hfwas.devops.user.integration.model.ConnectorSyncResult;
import com.hfwas.devops.user.integration.model.ExternalUserSnapshot;
import com.hfwas.devops.user.integration.spi.IdentityConnectorHandler;
import com.hfwas.devops.user.mapper.SysIdentityConnectorMapper;
import com.hfwas.devops.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdentityConnectorSyncService {

    public static final String AUTH_SOURCE_LOCAL = "local";
    public static final String AUTH_SOURCE_LDAP = "ldap";

    private final SysIdentityConnectorMapper connectorMapper;
    private final SysUserMapper userMapper;
    private final IdentityConnectorRegistry connectorRegistry;
    private final IdentityConnectorService connectorService;
    private final TenantMemberService tenantMemberService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public ConnectorSyncResult sync(Long connectorId) {
        requireAdmin();
        SysIdentityConnector connector = connectorService.requireConnector(connectorId);
        if (connector.getEnabled() == null || connector.getEnabled() != 1) {
            throw new IllegalArgumentException("对接已停用，无法同步");
        }

        IdentityConnectorHandler handler = connectorRegistry.require(connector.getType());
        String configJson = connectorService.resolveConfigForSync(connector);
        List<ExternalUserSnapshot> externalUsers = handler.fetchUsers(configJson);

        int created = 0;
        int updated = 0;
        int skipped = 0;

        for (ExternalUserSnapshot ext : externalUsers) {
            if (StringUtils.isBlank(ext.getUsername())) {
                skipped++;
                continue;
            }
            SysUser existing = findExistingUser(connector, ext);
            if (existing == null) {
                SysUser local = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                        .eq(SysUser::getUsername, ext.getUsername()));
                if (local != null && !connector.getId().equals(local.getConnectorId())) {
                    skipped++;
                    continue;
                }
                SysUser user = new SysUser();
                user.setUsername(ext.getUsername());
                user.setPassword(passwordEncoder.encode("LDAP-" + UUID.randomUUID()));
                user.setDisplayName(StringUtils.defaultIfBlank(ext.getDisplayName(), ext.getUsername()));
                user.setEmail(ext.getEmail());
                user.setPhone(ext.getPhone());
                user.setRole("user");
                user.setEnabled(ext.isEnabled() ? 1 : 0);
                user.setAuthSource(mapAuthSource(connector.getType()));
                user.setExternalId(ext.getExternalId());
                user.setConnectorId(connector.getId());
                userMapper.insert(user);
                ensureTenantMembership(connector, user.getId());
                created++;
            } else {
                existing.setDisplayName(StringUtils.defaultIfBlank(ext.getDisplayName(), existing.getDisplayName()));
                existing.setEmail(StringUtils.defaultIfBlank(ext.getEmail(), existing.getEmail()));
                existing.setPhone(StringUtils.defaultIfBlank(ext.getPhone(), existing.getPhone()));
                existing.setEnabled(ext.isEnabled() ? 1 : 0);
                existing.setExternalId(ext.getExternalId());
                userMapper.updateById(existing);
                ensureTenantMembership(connector, existing.getId());
                updated++;
            }
        }

        ConnectorSyncResult result = ConnectorSyncResult.builder()
                .success(true)
                .message(String.format("同步完成：新增 %d，更新 %d，跳过 %d", created, updated, skipped))
                .fetched(externalUsers.size())
                .created(created)
                .updated(updated)
                .skipped(skipped)
                .disabled(0)
                .build();

        connector.setLastSyncTime(LocalDateTime.now());
        connector.setLastSyncStatus("success");
        connector.setLastSyncMessage(result.getMessage());
        connector.setLastSyncCount(externalUsers.size());
        connectorMapper.updateById(connector);

        handler.afterSync(configJson, result);
        return result;
    }

    private SysUser findExistingUser(SysIdentityConnector connector, ExternalUserSnapshot ext) {
        if (StringUtils.isNotBlank(ext.getExternalId())) {
            SysUser byExternal = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                    .eq(SysUser::getConnectorId, connector.getId())
                    .eq(SysUser::getExternalId, ext.getExternalId()));
            if (byExternal != null) {
                return byExternal;
            }
        }
        return userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getConnectorId, connector.getId())
                .eq(SysUser::getUsername, ext.getUsername()));
    }

    private void ensureTenantMembership(SysIdentityConnector connector, Long userId) {
        if (connector.getAutoCreateMember() != null && connector.getAutoCreateMember() == 1
                && connector.getDefaultTenantId() != null) {
            tenantMemberService.ensureMember(connector.getDefaultTenantId(), userId, "member");
        }
    }

    private String mapAuthSource(String type) {
        if ("ldap".equalsIgnoreCase(type)) {
            return AUTH_SOURCE_LDAP;
        }
        return type;
    }

    private void requireAdmin() {
        UserContext ctx = UserContextHolder.require();
        if (!"admin".equalsIgnoreCase(ctx.getRole())) {
            throw new IllegalArgumentException("需要管理员权限");
        }
    }
}
