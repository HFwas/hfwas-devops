package com.hfwas.devops.user.notify;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hfwas.devops.user.context.UserContext;
import com.hfwas.devops.user.context.UserContextHolder;
import com.hfwas.devops.user.entity.SysNotifyChannel;
import com.hfwas.devops.user.mapper.SysNotifyChannelMapper;
import com.hfwas.devops.user.model.*;
import com.hfwas.devops.user.notify.support.NotifyConfigSupport;
import com.hfwas.devops.user.notify.webhook.WebhookNotifyClient;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotifyChannelService {

    private final SysNotifyChannelMapper channelMapper;
    private final NotifyConfigSupport configSupport;
    private final WebhookNotifyClient webhookNotifyClient;
    private final ObjectMapper objectMapper;

    public List<NotifyChannelVO> listAll() {
        requireAdmin();
        ensureDefaults();
        List<SysNotifyChannel> channels = channelMapper.selectList(Wrappers.<SysNotifyChannel>lambdaQuery()
                .orderByAsc(SysNotifyChannel::getId));
        List<NotifyChannelVO> list = new ArrayList<>();
        for (SysNotifyChannel channel : channels) {
            list.add(toVo(channel));
        }
        return list;
    }

    @Transactional
    public void save(NotifyChannelSaveRequest request) {
        requireAdmin();
        if (StringUtils.isBlank(request.getChannel())) {
            throw new IllegalArgumentException("渠道类型不能为空");
        }
        String channelType = request.getChannel().trim().toLowerCase();
        validateChannelType(channelType);
        ensureDefaults();

        SysNotifyChannel channel = channelMapper.selectOne(Wrappers.<SysNotifyChannel>lambdaQuery()
                .eq(SysNotifyChannel::getChannel, channelType));
        if (channel == null) {
            channel = new SysNotifyChannel();
            channel.setChannel(channelType);
        }

        if (NotifyChannels.SITE.equals(channelType)) {
            channel.setEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
            channel.setRemark(StringUtils.trimToNull(request.getRemark()));
        } else {
            validateWebhookConfig(request.getConfigJson(), channelType);
            String configJson = configSupport.mergeWebhookConfig(request.getConfigJson(), channel.getConfigJson());
            channel.setConfigJson(configJson);
            channel.setEnabled(request.getEnabled() == null ? 0 : request.getEnabled());
            channel.setRemark(StringUtils.trimToNull(request.getRemark()));
        }
        if (channel.getId() == null) {
            channelMapper.insert(channel);
        } else {
            channelMapper.updateById(channel);
        }
    }

    public NotifyTestResult test(String channelType) {
        requireAdmin();
        SysNotifyChannel channel = requireChannel(channelType);
        WebhookChannelConfig config = parseConfig(channel.getConfigJson());
        return webhookNotifyClient.test(channelType, config);
    }

    public NotifyTestResult testDraft(NotifyChannelSaveRequest request) {
        requireAdmin();
        String channelType = request.getChannel();
        validateChannelType(channelType);
        if (NotifyChannels.SITE.equals(channelType)) {
            return NotifyTestResult.builder().success(true).message("站内消息无需 Webhook 测试").build();
        }
        SysNotifyChannel existing = channelMapper.selectOne(Wrappers.<SysNotifyChannel>lambdaQuery()
                .eq(SysNotifyChannel::getChannel, channelType));
        String configJson = existing == null
                ? request.getConfigJson()
                : configSupport.mergeWebhookConfig(request.getConfigJson(), existing.getConfigJson());
        validateWebhookConfig(configJson, channelType);
        return webhookNotifyClient.test(channelType, parseConfig(configJson));
    }

    public boolean isSiteEnabled() {
        SysNotifyChannel channel = channelMapper.selectOne(Wrappers.<SysNotifyChannel>lambdaQuery()
                .eq(SysNotifyChannel::getChannel, NotifyChannels.SITE));
        return channel == null || channel.getEnabled() == null || channel.getEnabled() == 1;
    }

    public WebhookChannelConfig resolveWebhookConfig(String channelType) {
        SysNotifyChannel channel = channelMapper.selectOne(Wrappers.<SysNotifyChannel>lambdaQuery()
                .eq(SysNotifyChannel::getChannel, channelType));
        if (channel == null || channel.getEnabled() == null || channel.getEnabled() != 1) {
            return null;
        }
        WebhookChannelConfig config = parseConfig(channel.getConfigJson());
        if (config == null || StringUtils.isBlank(config.getWebhookUrl())) {
            return null;
        }
        return config;
    }

    private SysNotifyChannel requireChannel(String channelType) {
        SysNotifyChannel channel = channelMapper.selectOne(Wrappers.<SysNotifyChannel>lambdaQuery()
                .eq(SysNotifyChannel::getChannel, channelType));
        if (channel == null) {
            throw new IllegalArgumentException("通知渠道不存在");
        }
        return channel;
    }

    private void ensureDefaults() {
        seedIfMissing(NotifyChannels.SITE, 1, null, "平台站内收件箱");
        seedIfMissing(NotifyChannels.DINGTALK, 0, "{}", "钉钉群机器人 Webhook");
        seedIfMissing(NotifyChannels.FEISHU, 0, "{}", "飞书群机器人 Webhook");
    }

    private void seedIfMissing(String channel, int enabled, String configJson, String remark) {
        Long count = channelMapper.selectCount(Wrappers.<SysNotifyChannel>lambdaQuery()
                .eq(SysNotifyChannel::getChannel, channel));
        if (count != null && count > 0) {
            return;
        }
        SysNotifyChannel row = new SysNotifyChannel();
        row.setChannel(channel);
        row.setEnabled(enabled);
        row.setConfigJson(configJson);
        row.setRemark(remark);
        channelMapper.insert(row);
    }

    private void validateChannelType(String channelType) {
        if (!List.of(NotifyChannels.SITE, NotifyChannels.DINGTALK, NotifyChannels.FEISHU).contains(channelType)) {
            throw new IllegalArgumentException("不支持的通知渠道: " + channelType);
        }
    }

    private void validateWebhookConfig(String configJson, String channelType) {
        if (NotifyChannels.SITE.equals(channelType)) {
            return;
        }
        WebhookChannelConfig config = parseConfig(configJson);
        if (config == null || StringUtils.isBlank(config.getWebhookUrl())) {
            throw new IllegalArgumentException("Webhook 地址不能为空");
        }
    }

    private WebhookChannelConfig parseConfig(String configJson) {
        if (StringUtils.isBlank(configJson)) {
            return new WebhookChannelConfig();
        }
        try {
            return objectMapper.readValue(configJson, WebhookChannelConfig.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("配置 JSON 无效");
        }
    }

    private NotifyChannelVO toVo(SysNotifyChannel channel) {
        NotifyChannelVO vo = new NotifyChannelVO();
        vo.setId(channel.getId());
        vo.setChannel(channel.getChannel());
        vo.setChannelLabel(NotifyChannels.label(channel.getChannel()));
        vo.setEnabled(channel.getEnabled());
        vo.setRemark(channel.getRemark());
        vo.setUpdateTime(channel.getUpdateTime());
        if (NotifyChannels.SITE.equals(channel.getChannel())) {
            vo.setConfigJson(null);
        } else {
            vo.setConfigJson(configSupport.maskWebhookSecret(channel.getConfigJson()));
        }
        return vo;
    }

    private void requireAdmin() {
        UserContext ctx = UserContextHolder.require();
        if (!"admin".equalsIgnoreCase(ctx.getRole())) {
            throw new IllegalArgumentException("需要管理员权限");
        }
    }
}
