package com.hfwas.devops.user.message.spi;

import com.hfwas.devops.user.message.model.SiteMessageCommand;

/**
 * Default no-op when user-core is not on classpath.
 */
public final class NoOpSiteMessagePublisher implements SiteMessagePublisher {

    public static final NoOpSiteMessagePublisher INSTANCE = new NoOpSiteMessagePublisher();

    private NoOpSiteMessagePublisher() {
    }

    @Override
    public void sendToUser(Long userId, SiteMessageCommand command) {
    }
}
