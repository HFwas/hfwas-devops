package com.hfwas.devops.user.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hfwas.devops.user.entity.SysUser;
import com.hfwas.devops.user.mapper.SysUserMapper;
import com.hfwas.devops.user.service.TenantMemberService;
import com.hfwas.devops.user.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KeycloakUserProvisioningService {

    public static final String AUTH_SOURCE = "keycloak";

    private final SysUserMapper userMapper;
    private final TenantMemberService tenantMemberService;

    public SysUser findByExternalId(String sub) {
        if (StringUtils.isBlank(sub)) {
            return null;
        }
        return userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getExternalId, sub.trim()));
    }

    @Transactional
    public SysUser ensureUser(String sub, String preferredUsername, String email, String displayName) {
        if (StringUtils.isBlank(sub)) {
            throw new IllegalArgumentException("缺少 Keycloak 用户标识");
        }
        String externalId = sub.trim();
        SysUser existing = findByExternalId(externalId);
        if (existing != null) {
            return refreshProfile(existing, email, displayName);
        }

        Long existingCount = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery());
        boolean firstUser = existingCount == null || existingCount == 0;
        String username = uniqueUsername(preferredUsername, externalId);
        SysUser user = new SysUser();
        user.setExternalId(externalId);
        user.setUsername(username);
        user.setPassword(null);
        user.setDisplayName(StringUtils.defaultIfBlank(StringUtils.trimToNull(displayName), username));
        user.setEmail(StringUtils.trimToNull(email));
        user.setRole(firstUser || "admin".equalsIgnoreCase(username) ? "admin" : "user");
        user.setAuthSource(AUTH_SOURCE);
        user.setEnabled(1);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            SysUser raced = findByExternalId(externalId);
            if (raced != null) {
                return raced;
            }
            throw ex;
        }
        String tenantRole = "admin".equalsIgnoreCase(user.getRole()) ? "tenant_admin" : "member";
        tenantMemberService.ensureMember(TenantService.DEFAULT_TENANT_ID, user.getId(), tenantRole);
        return user;
    }

    private SysUser refreshProfile(SysUser user, String email, String displayName) {
        boolean dirty = false;
        if (StringUtils.isBlank(user.getEmail()) && StringUtils.isNotBlank(email)) {
            user.setEmail(email.trim());
            dirty = true;
        }
        if (StringUtils.isBlank(user.getDisplayName()) && StringUtils.isNotBlank(displayName)) {
            user.setDisplayName(displayName.trim());
            dirty = true;
        }
        if (dirty) {
            userMapper.updateById(user);
        }
        return user;
    }

    private String uniqueUsername(String preferredUsername, String externalId) {
        String base = StringUtils.trimToEmpty(preferredUsername);
        if (base.isBlank()) {
            base = "kc-" + shortId(externalId);
        }
        SysUser collision = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, base));
        if (collision == null || externalId.equals(collision.getExternalId())) {
            return base;
        }
        return base + "-" + shortId(externalId);
    }

    private static String shortId(String externalId) {
        String compact = externalId.replace("-", "");
        return compact.length() <= 8 ? compact : compact.substring(0, 8);
    }
}
