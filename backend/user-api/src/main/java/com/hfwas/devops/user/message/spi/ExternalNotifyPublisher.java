package com.hfwas.devops.user.message.spi;

import com.hfwas.devops.user.message.model.SiteMessageCommand;

/**
 * SPI for dispatching external notifications (DingTalk / Feishu webhook) without inbox.
 */
public interface ExternalNotifyPublisher {

    void dispatch(SiteMessageCommand command);
}
