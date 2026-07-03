package com.hfwas.devops.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hfwas.devops.user.context.UserContext;
import com.hfwas.devops.user.context.UserContextHolder;
import com.hfwas.devops.user.entity.SysUser;
import com.hfwas.devops.user.entity.SysUserSession;
import com.hfwas.devops.user.mapper.SysUserMapper;
import com.hfwas.devops.user.mapper.SysUserSessionMapper;
import com.hfwas.devops.user.model.UserSessionPageRequest;
import com.hfwas.devops.user.model.UserSessionStats;
import com.hfwas.devops.user.model.UserSessionVO;
import com.hfwas.devops.user.util.ClientIpResolver;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class UserSessionService {

    private static final int ONLINE_IDLE_SECONDS = 300;
    private static final int TOUCH_INTERVAL_SECONDS = 60;

    private final SysUserSessionMapper sessionMapper;
    private final SysUserMapper userMapper;

    @Transactional
    public void createSession(SysUser user, String jti, Instant expireAt, HttpServletRequest request) {
        LocalDateTime now = LocalDateTime.now();
        insertSession(user.getId(), jti, now, now, LocalDateTime.ofInstant(expireAt, ZoneId.systemDefault()),
                ClientIpResolver.resolve(request), trimUserAgent(request.getHeader("User-Agent")));
    }

    /**
     * Ensures a session row exists for the current token (login backfill, legacy token, or missed insert).
     */
    @Transactional
    public void ensureSession(Long userId, String sessionKey, Claims claims, HttpServletRequest request) {
        if (isSessionActive(sessionKey)) {
            return;
        }
        SysUserSession existing = sessionMapper.selectOne(Wrappers.<SysUserSession>lambdaQuery()
                .eq(SysUserSession::getJti, sessionKey));
        if (existing != null) {
            return;
        }
        if (claims.getExpiration() == null || !claims.getExpiration().toInstant().isAfter(Instant.now())) {
            return;
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null || user.getEnabled() == null || user.getEnabled() != 1) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime loginTime = toLocalDateTime(claims.getIssuedAt(), now);
        LocalDateTime expireTime = toLocalDateTime(claims.getExpiration(), now.plusSeconds(86400));
        insertSession(userId, sessionKey, loginTime, now, expireTime,
                ClientIpResolver.resolve(request), trimUserAgent(request.getHeader("User-Agent")));
    }

    public boolean isSessionActive(String sessionKey) {
        if (StringUtils.isBlank(sessionKey)) {
            return false;
        }
        SysUserSession session = sessionMapper.selectOne(Wrappers.<SysUserSession>lambdaQuery()
                .eq(SysUserSession::getJti, sessionKey)
                .eq(SysUserSession::getRevoked, 0));
        if (session == null) {
            return false;
        }
        return isNotExpired(session, LocalDateTime.now());
    }

    public void touchSession(String sessionKey) {
        if (StringUtils.isBlank(sessionKey)) {
            return;
        }
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(TOUCH_INTERVAL_SECONDS);
        sessionMapper.update(null, Wrappers.<SysUserSession>lambdaUpdate()
                .set(SysUserSession::getLastActiveTime, LocalDateTime.now())
                .eq(SysUserSession::getJti, sessionKey)
                .eq(SysUserSession::getRevoked, 0)
                .lt(SysUserSession::getLastActiveTime, threshold));
    }

    @Transactional
    public void revokeByJti(String sessionKey) {
        if (StringUtils.isBlank(sessionKey)) {
            return;
        }
        sessionMapper.update(null, Wrappers.<SysUserSession>lambdaUpdate()
                .set(SysUserSession::getRevoked, 1)
                .eq(SysUserSession::getJti, sessionKey)
                .eq(SysUserSession::getRevoked, 0));
    }

    @Transactional
    public void revokeById(Long sessionId) {
        requireAdmin();
        SysUserSession session = sessionMapper.selectById(sessionId);
        if (session == null || session.getRevoked() != null && session.getRevoked() == 1) {
            throw new IllegalArgumentException("会话不存在或已下线");
        }
        session.setRevoked(1);
        sessionMapper.updateById(session);
    }

    public UserSessionStats stats() {
        requireAdmin();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime onlineThreshold = now.minusSeconds(ONLINE_IDLE_SECONDS);
        List<SysUserSession> active = listActiveSessions(now);
        long online = active.stream()
                .filter(s -> s.getLastActiveTime() != null && !s.getLastActiveTime().isBefore(onlineThreshold))
                .count();
        UserSessionStats stats = new UserSessionStats();
        stats.setOnlineCount(online);
        stats.setIdleCount(active.size() - online);
        stats.setTotalActive(active.size());
        return stats;
    }

    public IPage<UserSessionVO> page(UserSessionPageRequest request, String currentSessionKey) {
        requireAdmin();
        int pageNo = request.getPageNo() == null || request.getPageNo() < 1 ? 1 : request.getPageNo();
        int pageSize = request.getPageSize() == null || request.getPageSize() < 1 ? 20 : request.getPageSize();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime onlineThreshold = now.minusSeconds(ONLINE_IDLE_SECONDS);
        String status = StringUtils.defaultIfBlank(request.getStatus(), "all");
        String keyword = StringUtils.trimToEmpty(request.getKeyword());

        List<SysUserSession> active = listActiveSessions(now);
        if (active.isEmpty()) {
            return emptyPage(pageNo, pageSize);
        }

        Set<Long> userIds = active.stream().map(SysUserSession::getUserId).collect(Collectors.toSet());
        Map<Long, SysUser> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));

        List<SysUserSession> filtered = active.stream()
                .filter(session -> matchesKeyword(session, userMap.get(session.getUserId()), keyword))
                .filter(session -> matchesStatus(session, status, onlineThreshold))
                .sorted(Comparator.comparing(SysUserSession::getLastActiveTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        int from = Math.min((pageNo - 1) * pageSize, filtered.size());
        int to = Math.min(from + pageSize, filtered.size());
        List<UserSessionVO> records = filtered.subList(from, to).stream()
                .map(session -> toVo(session, userMap.get(session.getUserId()), currentSessionKey, onlineThreshold))
                .toList();

        Page<UserSessionVO> page = new Page<>(pageNo, pageSize);
        page.setTotal(filtered.size());
        page.setRecords(records);
        return page;
    }

    /** Non-revoked sessions not yet expired — filtered in Java to avoid SQLite datetime format mismatch. */
    private List<SysUserSession> listActiveSessions(LocalDateTime now) {
        return sessionMapper.selectList(Wrappers.<SysUserSession>lambdaQuery()
                        .eq(SysUserSession::getRevoked, 0)
                        .orderByDesc(SysUserSession::getLastActiveTime))
                .stream()
                .filter(s -> isNotExpired(s, now))
                .toList();
    }

    private boolean isNotExpired(SysUserSession session, LocalDateTime now) {
        if (session.getExpireTime() != null) {
            return session.getExpireTime().isAfter(now);
        }
        if (session.getLoginTime() != null) {
            return session.getLoginTime().plusSeconds(86400).isAfter(now);
        }
        return true;
    }

    private boolean matchesKeyword(SysUserSession session, SysUser user, String keyword) {
        if (StringUtils.isBlank(keyword)) {
            return true;
        }
        if (user == null) {
            return false;
        }
        return StringUtils.containsIgnoreCase(user.getUsername(), keyword)
                || StringUtils.containsIgnoreCase(user.getDisplayName(), keyword);
    }

    private boolean matchesStatus(SysUserSession session, String status, LocalDateTime onlineThreshold) {
        if ("all".equalsIgnoreCase(status)) {
            return true;
        }
        boolean online = session.getLastActiveTime() != null
                && !session.getLastActiveTime().isBefore(onlineThreshold);
        if ("online".equalsIgnoreCase(status)) {
            return online;
        }
        if ("idle".equalsIgnoreCase(status)) {
            return !online;
        }
        return true;
    }

    private void insertSession(Long userId, String sessionKey, LocalDateTime loginTime,
                               LocalDateTime lastActiveTime, LocalDateTime expireTime,
                               String loginIp, String userAgent) {
        SysUserSession session = new SysUserSession();
        session.setUserId(userId);
        session.setJti(sessionKey);
        session.setLoginIp(loginIp);
        session.setUserAgent(userAgent);
        session.setLoginTime(loginTime);
        session.setLastActiveTime(lastActiveTime);
        session.setExpireTime(expireTime);
        session.setRevoked(0);
        sessionMapper.insert(session);
    }

    private LocalDateTime toLocalDateTime(Date date, LocalDateTime fallback) {
        if (date == null) {
            return fallback;
        }
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    private UserSessionVO toVo(SysUserSession session, SysUser user, String currentSessionKey,
                               LocalDateTime onlineThreshold) {
        UserSessionVO vo = new UserSessionVO();
        vo.setId(session.getId());
        vo.setUserId(session.getUserId());
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setDisplayName(user.getDisplayName());
            vo.setRole(user.getRole());
        }
        vo.setLoginIp(ClientIpResolver.normalize(StringUtils.defaultIfBlank(session.getLoginIp(), "-")));
        vo.setUserAgent(session.getUserAgent());
        vo.setClientInfo(simplifyUserAgent(session.getUserAgent()));
        vo.setLoginTime(session.getLoginTime());
        vo.setLastActiveTime(session.getLastActiveTime());
        vo.setExpireTime(session.getExpireTime());
        boolean online = session.getLastActiveTime() != null && !session.getLastActiveTime().isBefore(onlineThreshold);
        vo.setOnlineStatus(online ? "online" : "idle");
        vo.setCurrent(StringUtils.isNotBlank(currentSessionKey) && currentSessionKey.equals(session.getJti()));
        return vo;
    }

    private Page<UserSessionVO> emptyPage(int pageNo, int pageSize) {
        Page<UserSessionVO> empty = new Page<>(pageNo, pageSize);
        empty.setRecords(List.of());
        empty.setTotal(0);
        return empty;
    }

    private void requireAdmin() {
        UserContext ctx = UserContextHolder.require();
        if (!"admin".equalsIgnoreCase(ctx.getRole())) {
            throw new IllegalArgumentException("需要管理员权限");
        }
    }

    static String trimUserAgent(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() > 512 ? userAgent.substring(0, 512) : userAgent;
    }

    static String simplifyUserAgent(String userAgent) {
        if (StringUtils.isBlank(userAgent)) {
            return "-";
        }
        String ua = userAgent;
        String browser = "Unknown";
        if (ua.contains("Edg/")) {
            browser = "Edge";
        } else if (ua.contains("Chrome/")) {
            browser = "Chrome";
        } else if (ua.contains("Firefox/")) {
            browser = "Firefox";
        } else if (ua.contains("Safari/") && !ua.contains("Chrome/")) {
            browser = "Safari";
        }
        String os = "Unknown OS";
        if (ua.contains("Windows")) {
            os = "Windows";
        } else if (ua.contains("Mac OS X")) {
            os = "macOS";
        } else if (ua.contains("Linux")) {
            os = "Linux";
        } else if (ua.contains("Android")) {
            os = "Android";
        } else if (ua.contains("iPhone") || ua.contains("iPad")) {
            os = "iOS";
        }
        return browser + " / " + os;
    }
}
