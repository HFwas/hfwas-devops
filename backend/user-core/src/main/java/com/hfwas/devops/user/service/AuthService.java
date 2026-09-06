package com.hfwas.devops.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hfwas.devops.user.context.UserContext;
import com.hfwas.devops.user.context.UserContextHolder;
import com.hfwas.devops.user.entity.SysTenant;
import com.hfwas.devops.user.entity.SysUser;
import com.hfwas.devops.user.mapper.SysTenantMapper;
import com.hfwas.devops.user.mapper.SysUserMapper;
import com.hfwas.devops.user.message.SiteMessageNotifier;
import com.hfwas.devops.user.model.SwitchTenantRequest;
import com.hfwas.devops.user.model.TenantOptionVO;
import com.hfwas.devops.user.model.UserPageRequest;
import com.hfwas.devops.user.model.UserProfile;
import com.hfwas.devops.user.model.UserSaveRequest;
import com.hfwas.devops.user.security.KeycloakUserProvisioningService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final SysTenantMapper tenantMapper;
    private final PasswordEncoder passwordEncoder;
    private final TenantService tenantService;
    private final TenantMemberService tenantMemberService;
    private final SiteMessageNotifier messageNotifier;

    public List<TenantOptionVO> listMyTenants() {
        UserContext ctx = UserContextHolder.require();
        if ("admin".equalsIgnoreCase(ctx.getRole())) {
            return tenantService.listEnabledOptions();
        }
        return tenantMemberService.listEnabledTenantsByUser(ctx.getUserId());
    }

    public UserProfile switchTenant(SwitchTenantRequest request) {
        UserContext ctx = UserContextHolder.require();
        if (request.getTenantId() == null) {
            throw new IllegalArgumentException("租户 ID 不能为空");
        }
        SysUser user = loadUser(ctx.getUserId());
        if (request.getTenantId().equals(ctx.getTenantId())) {
            SysTenant tenant = tenantMapper.selectById(request.getTenantId());
            return toProfile(user, tenant);
        }
        SysTenant tenant = tenantService.requireEnabled(request.getTenantId());
        boolean platformAdmin = "admin".equalsIgnoreCase(ctx.getRole());
        if (!platformAdmin && !tenantMemberService.isActiveMember(tenant.getId(), ctx.getUserId())) {
            throw new IllegalArgumentException("您尚未加入该租户");
        }
        return toProfile(user, tenant);
    }

    public UserProfile me() {
        UserContext ctx = UserContextHolder.require();
        SysUser user = loadUser(ctx.getUserId());
        SysTenant tenant = ctx.getTenantId() != null ? tenantMapper.selectById(ctx.getTenantId()) : null;
        return toProfile(user, tenant);
    }

    private SysUser loadUser(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        return user;
    }

    public IPage<UserProfile> page(UserPageRequest request) {
        requireAdmin();
        String keyword = StringUtils.trimToEmpty(request.getKeyword());
        Long tenantFilter = request.getTenantId();
        List<Long> memberUserIds = null;
        if (tenantFilter != null) {
            memberUserIds = tenantMemberService.listUserIdsByTenant(tenantFilter);
            if (memberUserIds.isEmpty()) {
                return new Page<>(request.resolvePageNo(), request.resolvePageSize(), 0);
            }
        }
        Page<SysUser> page = userMapper.selectPage(
                new Page<>(request.resolvePageNo(), request.resolvePageSize()),
                Wrappers.<SysUser>lambdaQuery()
                        .in(memberUserIds != null, SysUser::getId, memberUserIds)
                        .and(StringUtils.isNotBlank(keyword), w -> w
                                .like(SysUser::getUsername, keyword)
                                .or().like(SysUser::getDisplayName, keyword)
                                .or().like(SysUser::getEmail, keyword))
                        .orderByDesc(SysUser::getCreateTime));
        return page.convert(user -> {
            UserProfile profile = toProfile(user, null);
            profile.setTenantNames(tenantMemberService.listTenantNamesByUser(user.getId()));
            return profile;
        });
    }

    public List<UserProfile> listEnabled() {
        Long tenantId = UserContextHolder.current()
                .map(UserContext::getTenantId)
                .orElse(TenantService.DEFAULT_TENANT_ID);
        List<SysUser> users = tenantMemberService.listEnabledMembers(tenantId);
        List<UserProfile> list = new ArrayList<>();
        SysTenant tenant = tenantMapper.selectById(tenantId);
        for (SysUser user : users) {
            list.add(toProfile(user, tenant));
        }
        return list;
    }

    @Transactional
    public Long save(UserSaveRequest request) {
        requireAdmin();
        if (StringUtils.isBlank(request.getUsername()) || StringUtils.isBlank(request.getDisplayName())) {
            throw new IllegalArgumentException("用户名和显示名称不能为空");
        }
        String username = request.getUsername().trim();
        String role = StringUtils.defaultIfBlank(request.getRole(), "user");
        if (!List.of("admin", "user").contains(role)) {
            throw new IllegalArgumentException("无效的角色");
        }
        Long dup = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getUsername, username)
                .ne(request.getId() != null, SysUser::getId, request.getId()));
        if (dup != null && dup > 0) {
            throw new IllegalArgumentException("用户名已存在");
        }
        SysUser user;
        if (request.getId() == null) {
            user = new SysUser();
            user.setUsername(username);
            user.setEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
            user.setAuthSource(KeycloakUserProvisioningService.AUTH_SOURCE);
            if (StringUtils.isNotBlank(request.getPassword())) {
                user.setPassword(passwordEncoder.encode(request.getPassword()));
            }
        } else {
            user = userMapper.selectById(request.getId());
            if (user == null) {
                throw new IllegalArgumentException("用户不存在");
            }
            user.setUsername(username);
            if (StringUtils.isNotBlank(request.getPassword())
                    && !KeycloakUserProvisioningService.AUTH_SOURCE.equalsIgnoreCase(user.getAuthSource())) {
                user.setPassword(passwordEncoder.encode(request.getPassword()));
            }
            if (request.getEnabled() != null) {
                user.setEnabled(request.getEnabled());
            }
        }
        user.setDisplayName(request.getDisplayName().trim());
        user.setEmail(StringUtils.trimToNull(request.getEmail()));
        user.setPhone(StringUtils.trimToNull(request.getPhone()));
        user.setRole(role);
        if (request.getId() == null) {
            userMapper.insert(user);
            messageNotifier.notifyAccountCreated(user.getId(), user.getUsername());
        } else {
            userMapper.updateById(user);
        }
        return user.getId();
    }

    @Transactional
    public void delete(Long id) {
        requireAdmin();
        UserContext current = UserContextHolder.require();
        if (current.getUserId().equals(id)) {
            throw new IllegalArgumentException("不能删除当前登录用户");
        }
        userMapper.deleteById(id);
    }

    private void requireAdmin() {
        UserContext ctx = UserContextHolder.require();
        if (!"admin".equalsIgnoreCase(ctx.getRole())) {
            throw new IllegalArgumentException("需要管理员权限");
        }
    }

    private UserProfile toProfile(SysUser user, SysTenant tenant) {
        UserProfile profile = new UserProfile();
        profile.setId(user.getId());
        profile.setUsername(user.getUsername());
        profile.setDisplayName(user.getDisplayName());
        profile.setEmail(user.getEmail());
        profile.setPhone(user.getPhone());
        profile.setRole(user.getRole());
        profile.setEnabled(user.getEnabled());
        profile.setAuthSource(StringUtils.defaultIfBlank(user.getAuthSource(), "local"));
        if (tenant != null) {
            profile.setTenantId(tenant.getId());
            profile.setTenantCode(tenant.getCode());
            profile.setTenantName(tenant.getName());
        }
        return profile;
    }
}
