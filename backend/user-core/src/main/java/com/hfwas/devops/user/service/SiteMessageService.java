package com.hfwas.devops.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hfwas.devops.user.context.UserContext;
import com.hfwas.devops.user.context.UserContextHolder;
import com.hfwas.devops.user.entity.SysTenant;
import com.hfwas.devops.user.entity.SysTenantMember;
import com.hfwas.devops.user.entity.SysUser;
import com.hfwas.devops.user.entity.SysUserMessage;
import com.hfwas.devops.user.mapper.SysTenantMapper;
import com.hfwas.devops.user.mapper.SysTenantMemberMapper;
import com.hfwas.devops.user.mapper.SysUserMapper;
import com.hfwas.devops.user.mapper.SysUserMessageMapper;
import com.hfwas.devops.user.message.MessageCategories;
import com.hfwas.devops.user.message.model.SiteMessageCommand;
import com.hfwas.devops.user.message.spi.SiteMessagePublisher;
import com.hfwas.devops.user.notify.MessageNotifyDispatcher;
import com.hfwas.devops.user.notify.NotifyChannelService;
import com.hfwas.devops.user.model.*;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Primary
@RequiredArgsConstructor
public class SiteMessageService implements SiteMessagePublisher {

    private final SysUserMessageMapper messageMapper;
    private final SysUserMapper userMapper;
    private final SysTenantMapper tenantMapper;
    private final SysTenantMemberMapper memberMapper;
    private final NotifyChannelService notifyChannelService;
    private final MessageNotifyDispatcher notifyDispatcher;

    @Override
    @Transactional
    public void sendToUser(Long userId, SiteMessageCommand command) {
        if (userId == null || command == null || StringUtils.isBlank(command.getTitle())) {
            return;
        }
        if (!notifyChannelService.isSiteEnabled()) {
            return;
        }
        SysUserMessage msg = new SysUserMessage();
        msg.setUserId(userId);
        msg.setTenantId(command.getTenantId());
        msg.setCategory(StringUtils.defaultIfBlank(command.getCategory(), MessageCategories.OPERATION));
        msg.setTitle(command.getTitle().trim());
        msg.setContent(StringUtils.defaultString(command.getContent()));
        msg.setReadFlag(0);
        msg.setSenderId(command.getSenderId());
        msg.setSenderName(command.getSenderName());
        msg.setBizType(command.getBizType());
        msg.setBizId(command.getBizId());
        msg.setLinkUrl(command.getLinkUrl());
        messageMapper.insert(msg);
    }

    @Override
    public void publishToUser(Long userId, SiteMessageCommand command) {
        sendToUser(userId, command);
        notifyDispatcher.dispatchExternal(command);
    }

    @Transactional
    public void sendBroadcast(MessageSendRequest request) {
        requireAdmin();
        validateSendRequest(request);
        UserContext sender = UserContextHolder.require();
        SiteMessageCommand template = SiteMessageCommand.builder()
                .category(StringUtils.defaultIfBlank(request.getCategory(), MessageCategories.ANNOUNCEMENT))
                .title(request.getTitle().trim())
                .content(StringUtils.defaultString(request.getContent()))
                .linkUrl(request.getLinkUrl())
                .senderId(sender.getUserId())
                .senderName(StringUtils.defaultIfBlank(sender.getDisplayName(), sender.getUsername()))
                .build();

        List<Long> userIds = resolveTargetUserIds(request);
        sendToUsers(userIds, template);
        notifyDispatcher.dispatchExternal(template);
    }

    public long unreadCount() {
        Long userId = UserContextHolder.require().getUserId();
        Long count = messageMapper.selectCount(Wrappers.<SysUserMessage>lambdaQuery()
                .eq(SysUserMessage::getUserId, userId)
                .eq(SysUserMessage::getReadFlag, 0));
        return count == null ? 0 : count;
    }

    public IPage<MessageVO> pageInbox(MessagePageRequest request) {
        Long userId = UserContextHolder.require().getUserId();
        return pageMessages(request, userId, null);
    }

    public IPage<MessageVO> pageAdmin(MessageAdminPageRequest request) {
        requireAdmin();
        MessagePageRequest inboxReq = new MessagePageRequest();
        inboxReq.setPageNo(request.getPageNo());
        inboxReq.setPageSize(request.getPageSize());
        inboxReq.setCategory(request.getCategory());
        inboxReq.setKeyword(request.getKeyword());
        inboxReq.setReadFlag("all");
        return pageMessages(inboxReq, request.getUserId(), true);
    }

    public MessageVO getDetail(Long id) {
        Long userId = UserContextHolder.require().getUserId();
        SysUserMessage msg = messageMapper.selectById(id);
        if (msg == null) {
            throw new IllegalArgumentException("消息不存在");
        }
        if (!UserContextHolder.require().getRole().equalsIgnoreCase("admin")
                && !userId.equals(msg.getUserId())) {
            throw new IllegalArgumentException("无权查看该消息");
        }
        return toVo(msg, loadUserMap(List.of(msg)), loadTenantMap(List.of(msg)));
    }

    @Transactional
    public void markRead(Long id) {
        SysUserMessage msg = requireOwnedMessage(id);
        if (msg.getReadFlag() != null && msg.getReadFlag() == 1) {
            return;
        }
        msg.setReadFlag(1);
        msg.setReadTime(LocalDateTime.now());
        messageMapper.updateById(msg);
    }

    @Transactional
    public void markAllRead() {
        Long userId = UserContextHolder.require().getUserId();
        messageMapper.update(null, Wrappers.<SysUserMessage>lambdaUpdate()
                .eq(SysUserMessage::getUserId, userId)
                .eq(SysUserMessage::getReadFlag, 0)
                .set(SysUserMessage::getReadFlag, 1)
                .set(SysUserMessage::getReadTime, LocalDateTime.now()));
    }

    @Transactional
    public void markReadBatch(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        Long userId = UserContextHolder.require().getUserId();
        messageMapper.update(null, Wrappers.<SysUserMessage>lambdaUpdate()
                .in(SysUserMessage::getId, ids)
                .eq(SysUserMessage::getUserId, userId)
                .set(SysUserMessage::getReadFlag, 1)
                .set(SysUserMessage::getReadTime, LocalDateTime.now()));
    }

    @Transactional
    public void deleteMessage(Long id) {
        SysUserMessage msg = requireOwnedMessage(id);
        messageMapper.deleteById(msg.getId());
    }

    public List<MessageVO> listRecent(int limit) {
        Long userId = UserContextHolder.require().getUserId();
        List<SysUserMessage> list = messageMapper.selectList(Wrappers.<SysUserMessage>lambdaQuery()
                .eq(SysUserMessage::getUserId, userId)
                .orderByDesc(SysUserMessage::getCreateTime)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 20)));
        Map<Long, SysUser> userMap = loadUserMap(list);
        Map<Long, SysTenant> tenantMap = loadTenantMap(list);
        return list.stream().map(m -> toVo(m, userMap, tenantMap)).toList();
    }

    private IPage<MessageVO> pageMessages(MessagePageRequest request, Long filterUserId, Boolean adminView) {
        int pageNo = request.resolvePageNo();
        int pageSize = request.resolvePageSize();
        String keyword = StringUtils.trimToEmpty(request.getKeyword());
        String category = StringUtils.defaultIfBlank(request.getCategory(), "all");
        String readFlag = StringUtils.defaultIfBlank(request.getReadFlag(), "all");

        Page<SysUserMessage> page = messageMapper.selectPage(new Page<>(pageNo, pageSize),
                Wrappers.<SysUserMessage>lambdaQuery()
                        .eq(filterUserId != null, SysUserMessage::getUserId, filterUserId)
                        .eq(!"all".equalsIgnoreCase(category), SysUserMessage::getCategory, category)
                        .eq("unread".equalsIgnoreCase(readFlag), SysUserMessage::getReadFlag, 0)
                        .eq("read".equalsIgnoreCase(readFlag), SysUserMessage::getReadFlag, 1)
                        .and(StringUtils.isNotBlank(keyword), w -> w
                                .like(SysUserMessage::getTitle, keyword)
                                .or().like(SysUserMessage::getContent, keyword))
                        .orderByDesc(SysUserMessage::getCreateTime));

        Map<Long, SysUser> userMap = Boolean.TRUE.equals(adminView) ? loadUserMap(page.getRecords()) : Map.of();
        Map<Long, SysTenant> tenantMap = loadTenantMap(page.getRecords());
        return page.convert(m -> toVo(m, userMap, tenantMap));
    }

    private SysUserMessage requireOwnedMessage(Long id) {
        Long userId = UserContextHolder.require().getUserId();
        SysUserMessage msg = messageMapper.selectById(id);
        if (msg == null) {
            throw new IllegalArgumentException("消息不存在");
        }
        if (!userId.equals(msg.getUserId())) {
            throw new IllegalArgumentException("无权操作该消息");
        }
        return msg;
    }

    private List<Long> resolveTargetUserIds(MessageSendRequest request) {
        return switch (StringUtils.defaultString(request.getTargetType())) {
            case "all" -> userMapper.selectList(Wrappers.<SysUser>lambdaQuery()
                            .eq(SysUser::getEnabled, 1)
                            .select(SysUser::getId))
                    .stream().map(SysUser::getId).toList();
            case "tenant" -> {
                if (request.getTenantId() == null) {
                    throw new IllegalArgumentException("请选择目标租户");
                }
                yield memberMapper.selectList(Wrappers.<SysTenantMember>lambdaQuery()
                                .eq(SysTenantMember::getTenantId, request.getTenantId())
                                .eq(SysTenantMember::getStatus, 1)
                                .select(SysTenantMember::getUserId))
                        .stream().map(SysTenantMember::getUserId).distinct().toList();
            }
            case "users" -> {
                if (request.getUserIds() == null || request.getUserIds().isEmpty()) {
                    throw new IllegalArgumentException("请选择目标用户");
                }
                yield request.getUserIds();
            }
            default -> throw new IllegalArgumentException("无效的发送目标类型");
        };
    }

    private void validateSendRequest(MessageSendRequest request) {
        if (StringUtils.isBlank(request.getTitle())) {
            throw new IllegalArgumentException("消息标题不能为空");
        }
        if (StringUtils.isBlank(request.getTargetType())) {
            throw new IllegalArgumentException("请选择发送目标");
        }
    }

    private MessageVO toVo(SysUserMessage msg, Map<Long, SysUser> userMap, Map<Long, SysTenant> tenantMap) {
        MessageVO vo = new MessageVO();
        vo.setId(msg.getId());
        vo.setUserId(msg.getUserId());
        SysUser user = userMap.get(msg.getUserId());
        if (user != null) {
            vo.setUsername(user.getUsername());
            vo.setDisplayName(user.getDisplayName());
        }
        vo.setTenantId(msg.getTenantId());
        if (msg.getTenantId() != null && tenantMap.get(msg.getTenantId()) != null) {
            vo.setTenantName(tenantMap.get(msg.getTenantId()).getName());
        }
        vo.setCategory(msg.getCategory());
        vo.setCategoryLabel(MessageCategories.label(msg.getCategory()));
        vo.setTitle(msg.getTitle());
        vo.setContent(msg.getContent());
        vo.setReadFlag(msg.getReadFlag());
        vo.setSenderId(msg.getSenderId());
        vo.setSenderName(msg.getSenderName());
        vo.setBizType(msg.getBizType());
        vo.setBizId(msg.getBizId());
        vo.setLinkUrl(msg.getLinkUrl());
        vo.setCreateTime(msg.getCreateTime());
        vo.setReadTime(msg.getReadTime());
        return vo;
    }

    private Map<Long, SysUser> loadUserMap(List<SysUserMessage> messages) {
        Set<Long> ids = messages.stream().map(SysUserMessage::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectList(Wrappers.<SysUser>lambdaQuery().in(SysUser::getId, ids))
                .stream().collect(Collectors.toMap(SysUser::getId, u -> u));
    }

    private Map<Long, SysTenant> loadTenantMap(List<SysUserMessage> messages) {
        Set<Long> ids = messages.stream().map(SysUserMessage::getTenantId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return tenantMapper.selectList(Wrappers.<SysTenant>lambdaQuery().in(SysTenant::getId, ids))
                .stream().collect(Collectors.toMap(SysTenant::getId, t -> t));
    }

    private void requireAdmin() {
        UserContext ctx = UserContextHolder.require();
        if (!"admin".equalsIgnoreCase(ctx.getRole())) {
            throw new IllegalArgumentException("需要管理员权限");
        }
    }
}
