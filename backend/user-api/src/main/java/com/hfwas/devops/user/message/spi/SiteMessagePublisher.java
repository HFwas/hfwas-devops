package com.hfwas.devops.user.message.spi;

import com.hfwas.devops.user.message.model.SiteMessageCommand;

import java.util.Collection;

/**
 * SPI for sending in-site messages from any module (user-core, pm-core, etc.).
 */
public interface SiteMessagePublisher {

    void sendToUser(Long userId, SiteMessageCommand command);

    /** Inbox + external channels (dingtalk/feishu) when enabled. */
    default void publishToUser(Long userId, SiteMessageCommand command) {
        sendToUser(userId, command);
    }

    default void sendToUsers(Collection<Long> userIds, SiteMessageCommand command) {
        if (userIds == null) {
            return;
        }
        for (Long userId : userIds) {
            if (userId != null) {
                sendToUser(userId, command);
            }
        }
    }
}
