package com.hfwas.devops.user.operlog.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import com.hfwas.devops.user.context.UserContext;
import com.hfwas.devops.user.operlog.entity.SysOperLog;
import com.hfwas.devops.user.operlog.mapper.SysOperLogMapper;
import com.hfwas.devops.user.operlog.model.OperLogEntry;
import com.hfwas.devops.user.operlog.model.OperLogPageRequest;
import com.hfwas.devops.user.operlog.model.OperLogVO;
import com.hfwas.devops.user.operlog.spi.OperLogRecorder;
import com.hfwas.devops.user.operlog.support.OperLogRequestSupport;
import com.hfwas.devops.user.util.ClientIpResolver;
import com.hfwas.devops.user.util.UserAgentUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OperLogService implements OperLogRecorder {

    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_FAIL = "fail";

    private final SysOperLogMapper operLogMapper;
    private final CurrentUserAccessor currentUserAccessor;

    @Override
    public void record(OperLogEntry entry) {
        SysOperLog log = new SysOperLog();
        currentUserAccessor.current().ifPresent(user -> fillUser(log, user));
        log.setModule(entry.getModule());
        log.setAction(entry.getAction());
        log.setBizType(StringUtils.defaultString(entry.getBizType()));
        log.setBizId(entry.getBizId());
        log.setSummary(entry.getSummary());
        log.setStatus(StringUtils.defaultIfBlank(entry.getStatus(), STATUS_SUCCESS));
        log.setFailReason(entry.getFailReason());
        log.setExtraJson(entry.getExtraJson());
        log.setCreateTime(LocalDateTime.now());

        OperLogRequestSupport.currentRequest().ifPresent(request -> fillRequest(log, request));
        operLogMapper.insert(log);
    }

    public IPage<OperLogVO> page(OperLogPageRequest request) {
        requireAdmin();
        int pageNo = request.getPageNo() == null || request.getPageNo() < 1 ? 1 : request.getPageNo();
        int pageSize = request.getPageSize() == null || request.getPageSize() < 1 ? 20 : request.getPageSize();
        String keyword = StringUtils.trimToEmpty(request.getKeyword());
        String module = StringUtils.defaultIfBlank(request.getModule(), "all");
        String action = StringUtils.trimToNull(request.getAction());

        Page<SysOperLog> page = operLogMapper.selectPage(new Page<>(pageNo, pageSize),
                Wrappers.<SysOperLog>lambdaQuery()
                        .and(StringUtils.isNotBlank(keyword), w -> w
                                .like(SysOperLog::getUsername, keyword)
                                .or().like(SysOperLog::getDisplayName, keyword)
                                .or().like(SysOperLog::getSummary, keyword)
                                .or().like(SysOperLog::getBizId, keyword))
                        .eq(!"all".equalsIgnoreCase(module), SysOperLog::getModule, module)
                        .eq(StringUtils.isNotBlank(action), SysOperLog::getAction, action)
                        .orderByDesc(SysOperLog::getCreateTime));
        return page.convert(this::toVo);
    }

    private void fillUser(SysOperLog log, UserContext user) {
        log.setUserId(user.getUserId());
        log.setUsername(user.getUsername());
        log.setDisplayName(user.getDisplayName());
    }

    private void fillRequest(SysOperLog log, HttpServletRequest request) {
        log.setRequestIp(ClientIpResolver.resolve(request));
        String userAgent = UserAgentUtils.trim(request.getHeader("User-Agent"));
        log.setUserAgent(userAgent);
        log.setClientInfo(UserAgentUtils.simplify(userAgent));
    }

    private OperLogVO toVo(SysOperLog log) {
        OperLogVO vo = new OperLogVO();
        vo.setId(log.getId());
        vo.setUserId(log.getUserId());
        vo.setUsername(log.getUsername());
        vo.setDisplayName(log.getDisplayName());
        vo.setModule(log.getModule());
        vo.setAction(log.getAction());
        vo.setBizType(log.getBizType());
        vo.setBizId(log.getBizId());
        vo.setSummary(log.getSummary());
        vo.setStatus(log.getStatus());
        vo.setFailReason(log.getFailReason());
        vo.setRequestIp(ClientIpResolver.normalize(StringUtils.defaultIfBlank(log.getRequestIp(), "-")));
        vo.setClientInfo(log.getClientInfo());
        vo.setUserAgent(log.getUserAgent());
        vo.setExtraJson(log.getExtraJson());
        vo.setCreateTime(log.getCreateTime());
        return vo;
    }

    private void requireAdmin() {
        UserContext user = currentUserAccessor.current()
                .orElseThrow(() -> new IllegalArgumentException("未登录或登录已过期"));
        if (!"admin".equalsIgnoreCase(user.getRole())) {
            throw new IllegalArgumentException("需要管理员权限");
        }
    }
}
