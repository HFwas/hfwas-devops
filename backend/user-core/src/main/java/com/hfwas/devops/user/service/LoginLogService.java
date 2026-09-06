package com.hfwas.devops.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hfwas.devops.user.context.UserContext;
import com.hfwas.devops.user.context.UserContextHolder;
import com.hfwas.devops.user.entity.SysLoginLog;
import com.hfwas.devops.user.entity.SysUser;
import com.hfwas.devops.user.mapper.SysLoginLogMapper;
import com.hfwas.devops.user.model.KeycloakAuthEvent;
import com.hfwas.devops.user.model.LoginLogPageRequest;
import com.hfwas.devops.user.model.LoginLogVO;
import com.hfwas.devops.user.security.KeycloakUserProvisioningService;
import com.hfwas.devops.user.util.ClientIpResolver;
import com.hfwas.devops.user.util.UserAgentUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LoginLogService {

    public static final String ACTION_LOGIN_SUCCESS = "login_success";
    public static final String ACTION_LOGIN_FAIL = "login_fail";
    public static final String ACTION_LOGOUT = "logout";

    private final SysLoginLogMapper loginLogMapper;
    private final KeycloakUserProvisioningService provisioningService;

    public void ingest(KeycloakAuthEvent event) {
        if (event == null || StringUtils.isBlank(event.getId()) || StringUtils.isBlank(event.getType())) {
            return;
        }
        String action = toAction(event.getType());
        if (action == null) {
            return;
        }
        Long existed = loginLogMapper.selectCount(Wrappers.<SysLoginLog>lambdaQuery()
                .eq(SysLoginLog::getKcEventId, event.getId()));
        if (existed != null && existed > 0) {
            return;
        }

        Map<String, String> details = event.getDetails() == null ? Map.of() : event.getDetails();
        String username = firstNonBlank(details.get("username"), details.get("preferred_username"), "-");
        String displayName = firstNonBlank(details.get("name"), details.get("username"));
        String userAgent = firstNonBlank(details.get("user_agent"), details.get("userAgent"));
        SysUser user = resolveUser(event, username, displayName, details.get("email"));

        SysLoginLog log = new SysLoginLog();
        log.setKcEventId(event.getId());
        log.setKcUserId(StringUtils.trimToNull(event.getUserId()));
        if (user != null) {
            log.setUserId(user.getId());
            log.setUsername(user.getUsername());
            log.setDisplayName(user.getDisplayName());
        } else {
            log.setUsername(username);
            log.setDisplayName(displayName);
        }
        log.setAction(action);
        log.setLoginIp(ClientIpResolver.normalize(StringUtils.defaultIfBlank(event.getIpAddress(), "-")));
        log.setUserAgent(UserAgentUtils.trim(userAgent));
        log.setClientInfo(userAgent != null ? UserAgentUtils.simplify(userAgent)
                : StringUtils.defaultIfBlank(details.get("client_id"), "-"));
        log.setFailReason(ACTION_LOGIN_FAIL.equals(action)
                ? StringUtils.defaultIfBlank(event.getError(), "login_failed")
                : null);
        log.setCreateTime(eventTime(event.getTime()));
        try {
            loginLogMapper.insert(log);
        } catch (DuplicateKeyException ignored) {
            // same kc_event_id retried
        }
    }

    public IPage<LoginLogVO> page(LoginLogPageRequest request) {
        requireAdmin();
        int pageNo = request.resolvePageNo();
        int pageSize = request.resolvePageSize();
        String keyword = StringUtils.trimToEmpty(request.getKeyword());
        String action = StringUtils.defaultIfBlank(request.getAction(), "all");

        Page<SysLoginLog> page = loginLogMapper.selectPage(new Page<>(pageNo, pageSize),
                Wrappers.<SysLoginLog>lambdaQuery()
                        .and(StringUtils.isNotBlank(keyword), w -> w
                                .like(SysLoginLog::getUsername, keyword)
                                .or().like(SysLoginLog::getDisplayName, keyword))
                        .eq(!"all".equalsIgnoreCase(action), SysLoginLog::getAction, action)
                        .orderByDesc(SysLoginLog::getCreateTime));
        return page.convert(this::toVo);
    }

    private SysUser resolveUser(KeycloakAuthEvent event, String username, String displayName, String email) {
        if (StringUtils.isBlank(event.getUserId())) {
            return null;
        }
        if ("LOGIN".equalsIgnoreCase(event.getType())) {
            return provisioningService.ensureUser(event.getUserId(), username, email, displayName);
        }
        return provisioningService.findByExternalId(event.getUserId());
    }

    private static String toAction(String type) {
        if ("LOGIN".equalsIgnoreCase(type)) {
            return ACTION_LOGIN_SUCCESS;
        }
        if ("LOGIN_ERROR".equalsIgnoreCase(type)) {
            return ACTION_LOGIN_FAIL;
        }
        if ("LOGOUT".equalsIgnoreCase(type)) {
            return ACTION_LOGOUT;
        }
        return null;
    }

    private static LocalDateTime eventTime(Long epochMillis) {
        if (epochMillis == null || epochMillis <= 0) {
            return LocalDateTime.now();
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private LoginLogVO toVo(SysLoginLog log) {
        LoginLogVO vo = new LoginLogVO();
        vo.setId(log.getId());
        vo.setUserId(log.getUserId());
        vo.setUsername(log.getUsername());
        vo.setDisplayName(log.getDisplayName());
        vo.setAction(log.getAction());
        vo.setLoginIp(ClientIpResolver.normalize(StringUtils.defaultIfBlank(log.getLoginIp(), "-")));
        vo.setClientInfo(log.getClientInfo());
        vo.setUserAgent(log.getUserAgent());
        vo.setFailReason(log.getFailReason());
        vo.setCreateTime(log.getCreateTime());
        return vo;
    }

    private void requireAdmin() {
        UserContext ctx = UserContextHolder.require();
        if (!"admin".equalsIgnoreCase(ctx.getRole())) {
            throw new IllegalArgumentException("需要管理员权限");
        }
    }
}
