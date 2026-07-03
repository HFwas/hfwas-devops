package com.hfwas.devops.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hfwas.devops.user.entity.SysTenant;
import com.hfwas.devops.user.entity.SysUser;
import com.hfwas.devops.user.mapper.SysTenantMapper;
import com.hfwas.devops.user.mapper.SysUserMapper;
import com.hfwas.devops.user.model.*;
import com.hfwas.devops.user.context.UserContext;
import com.hfwas.devops.user.context.UserContextHolder;
import com.hfwas.devops.user.security.IssuedToken;
import com.hfwas.devops.user.security.JwtTokenService;
import jakarta.servlet.http.HttpServletRequest;
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
    private final JwtTokenService jwtTokenService;
    private final UserSessionService userSessionService;
    private final LoginLogService loginLogService;
    private final TenantService tenantService;

    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String username = StringUtils.trimToEmpty(request.getUsername());
        String tenantCode = StringUtils.defaultIfBlank(request.getTenantCode(), "default").trim().toLowerCase();
        if (StringUtils.isBlank(username) || StringUtils.isBlank(request.getPassword())) {
            loginLogService.recordLoginFail(username, "用户名和密码不能为空", httpRequest);
            throw new IllegalArgumentException("用户名和密码不能为空");
        }
        SysTenant tenant = tenantService.findByCode(tenantCode);
        if (tenant == null) {
            loginLogService.recordLoginFail(username, "租户不存在", httpRequest);
            throw new IllegalArgumentException("租户不存在");
        }
        if (tenant.getStatus() == null || tenant.getStatus() != 1) {
            loginLogService.recordLoginFail(username, "租户已停用", httpRequest);
            throw new IllegalArgumentException("租户已停用");
        }
        SysUser user = userMapper.selectOne(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getTenantId, tenant.getId())
                .eq(SysUser::getUsername, username));
        if (user == null || user.getEnabled() == null || user.getEnabled() != 1) {
            loginLogService.recordLoginFail(username, "用户名或密码错误", httpRequest);
            throw new IllegalArgumentException("用户名或密码错误");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            loginLogService.recordLoginFail(username, "用户名或密码错误", httpRequest);
            throw new IllegalArgumentException("用户名或密码错误");
        }
        IssuedToken issued = jwtTokenService.issueToken(user);
        userSessionService.createSession(user, issued.jti(), issued.expireAt(), httpRequest);
        loginLogService.recordLoginSuccess(user, httpRequest);
        LoginResponse response = new LoginResponse();
        response.setToken(issued.token());
        response.setUser(toProfile(user));
        return response;
    }

    public void logout(String token, HttpServletRequest httpRequest) {
        userSessionService.revokeByJti(jwtTokenService.resolveSessionKey(token));
        UserContextHolder.current().ifPresent(user -> loginLogService.recordLogout(user, httpRequest));
    }

    public UserProfile me() {
        UserContext ctx = UserContextHolder.require();
        return toProfile(loadUser(ctx.getUserId()));
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
        int pageNo = request.getPageNo() == null || request.getPageNo() < 1 ? 1 : request.getPageNo();
        int pageSize = request.getPageSize() == null || request.getPageSize() < 1 ? 20 : request.getPageSize();
        String keyword = StringUtils.trimToEmpty(request.getKeyword());
        Long tenantFilter = request.getTenantId();
        Page<SysUser> page = userMapper.selectPage(new Page<>(pageNo, pageSize),
                Wrappers.<SysUser>lambdaQuery()
                        .eq(tenantFilter != null, SysUser::getTenantId, tenantFilter)
                        .and(StringUtils.isNotBlank(keyword), w -> w
                                .like(SysUser::getUsername, keyword)
                                .or().like(SysUser::getDisplayName, keyword)
                                .or().like(SysUser::getEmail, keyword))
                        .orderByDesc(SysUser::getCreateTime));
        return page.convert(this::toProfile);
    }

    public List<UserProfile> listEnabled() {
        Long tenantId = UserContextHolder.current()
                .map(UserContext::getTenantId)
                .orElse(TenantService.DEFAULT_TENANT_ID);
        List<SysUser> users = userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getTenantId, tenantId)
                .eq(SysUser::getEnabled, 1)
                .orderByAsc(SysUser::getDisplayName));
        List<UserProfile> list = new ArrayList<>();
        for (SysUser user : users) {
            list.add(toProfile(user));
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
        Long tenantId = request.getTenantId();
        if (tenantId == null) {
            tenantId = TenantService.DEFAULT_TENANT_ID;
        }
        tenantService.requireEnabled(tenantId);
        Long dup = userMapper.selectCount(Wrappers.<SysUser>lambdaQuery()
                .eq(SysUser::getTenantId, tenantId)
                .eq(SysUser::getUsername, username)
                .ne(request.getId() != null, SysUser::getId, request.getId()));
        if (dup != null && dup > 0) {
            throw new IllegalArgumentException("该租户下用户名已存在");
        }
        SysUser user;
        if (request.getId() == null) {
            if (StringUtils.isBlank(request.getPassword())) {
                throw new IllegalArgumentException("新建用户必须设置密码");
            }
            user = new SysUser();
            user.setTenantId(tenantId);
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            user.setEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
        } else {
            user = userMapper.selectById(request.getId());
            if (user == null) {
                throw new IllegalArgumentException("用户不存在");
            }
            user.setUsername(username);
            if (request.getTenantId() != null) {
                tenantService.requireEnabled(request.getTenantId());
                user.setTenantId(request.getTenantId());
            }
            if (StringUtils.isNotBlank(request.getPassword())) {
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

    private UserProfile toProfile(SysUser user) {
        UserProfile profile = new UserProfile();
        profile.setId(user.getId());
        profile.setUsername(user.getUsername());
        profile.setDisplayName(user.getDisplayName());
        profile.setEmail(user.getEmail());
        profile.setPhone(user.getPhone());
        profile.setRole(user.getRole());
        profile.setEnabled(user.getEnabled());
        profile.setTenantId(user.getTenantId());
        if (user.getTenantId() != null) {
            SysTenant tenant = tenantMapper.selectById(user.getTenantId());
            if (tenant != null) {
                profile.setTenantCode(tenant.getCode());
                profile.setTenantName(tenant.getName());
            }
        }
        return profile;
    }
}
