package com.hfwas.devops.user.notify;

import com.hfwas.devops.user.message.model.SiteMessageCommand;
import com.hfwas.devops.user.model.WebhookChannelConfig;
import com.hfwas.devops.user.notify.webhook.WebhookNotifyClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageNotifyDispatcher {

    private final NotifyChannelService notifyChannelService;
    private final WebhookNotifyClient webhookNotifyClient;

    @Async
    public void dispatchExternal(SiteMessageCommand command) {
        if (command == null) {
            return;
        }
        dispatchIfEnabled(NotifyChannels.DINGTALK, command);
        dispatchIfEnabled(NotifyChannels.FEISHU, command);
    }

    private void dispatchIfEnabled(String channel, SiteMessageCommand command) {
        try {
            WebhookChannelConfig config = notifyChannelService.resolveWebhookConfig(channel);
            if (config == null) {
                return;
            }
            var result = webhookNotifyClient.send(channel, config, command.getTitle(), command.getContent());
            if (!result.isSuccess()) {
                log.warn("External notify failed [{}]: {}", channel, result.getMessage());
            }
        } catch (Exception e) {
            log.warn("External notify error [{}]: {}", channel, e.getMessage());
        }
    }
}
