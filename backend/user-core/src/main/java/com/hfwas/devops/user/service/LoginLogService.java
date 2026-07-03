package com.hfwas.devops.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hfwas.devops.user.context.UserContext;
import com.hfwas.devops.user.context.UserContextHolder;
import com.hfwas.devops.user.entity.SysLoginLog;
import com.hfwas.devops.user.entity.SysUser;
import com.hfwas.devops.user.mapper.SysLoginLogMapper;
import com.hfwas.devops.user.model.LoginLogPageRequest;
import com.hfwas.devops.user.model.LoginLogVO;
import com.hfwas.devops.user.util.ClientIpResolver;
import com.hfwas.devops.user.util.UserAgentUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class LoginLogService {

    public static final String ACTION_LOGIN_SUCCESS = "login_success";
    public static final String ACTION_LOGIN_FAIL = "login_fail";
    public static final String ACTION_LOGOUT = "logout";

    private final SysLoginLogMapper loginLogMapper;

    public void recordLoginSuccess(SysUser user, HttpServletRequest request) {
        SysLoginLog log = baseLog(request);
        log.setUserId(user.getId());
        log.setUsername(user.getUsername());
        log.setDisplayName(user.getDisplayName());
        log.setAction(ACTION_LOGIN_SUCCESS);
        loginLogMapper.insert(log);
    }

    public void recordLoginFail(String username, String reason, HttpServletRequest request) {
        SysLoginLog log = baseLog(request);
        log.setUsername(StringUtils.defaultIfBlank(username, "-"));
        log.setAction(ACTION_LOGIN_FAIL);
        log.setFailReason(reason);
        loginLogMapper.insert(log);
    }

    public void recordLogout(UserContext user, HttpServletRequest request) {
        SysLoginLog log = baseLog(request);
        log.setUserId(user.getUserId());
        log.setUsername(user.getUsername());
        log.setDisplayName(user.getDisplayName());
        log.setAction(ACTION_LOGOUT);
        loginLogMapper.insert(log);
    }

    public IPage<LoginLogVO> page(LoginLogPageRequest request) {
        requireAdmin();
        int pageNo = request.getPageNo() == null || request.getPageNo() < 1 ? 1 : request.getPageNo();
        int pageSize = request.getPageSize() == null || request.getPageSize() < 1 ? 20 : request.getPageSize();
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

    private SysLoginLog baseLog(HttpServletRequest request) {
        SysLoginLog log = new SysLoginLog();
        log.setLoginIp(ClientIpResolver.resolve(request));
        String userAgent = UserAgentUtils.trim(request.getHeader("User-Agent"));
        log.setUserAgent(userAgent);
        log.setClientInfo(UserAgentUtils.simplify(userAgent));
        log.setCreateTime(LocalDateTime.now());
        return log;
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
