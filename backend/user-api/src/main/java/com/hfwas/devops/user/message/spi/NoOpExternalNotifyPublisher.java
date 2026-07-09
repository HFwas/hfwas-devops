package com.hfwas.devops.user.message.spi;

import com.hfwas.devops.user.message.model.SiteMessageCommand;

public final class NoOpExternalNotifyPublisher implements ExternalNotifyPublisher {

    public static final NoOpExternalNotifyPublisher INSTANCE = new NoOpExternalNotifyPublisher();

    private NoOpExternalNotifyPublisher() {
    }

    @Override
    public void dispatch(SiteMessageCommand command) {
        // no-op
    }
}
