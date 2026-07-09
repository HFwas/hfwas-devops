package com.hfwas.devops.user.notify;

import com.hfwas.devops.user.message.model.SiteMessageCommand;
import com.hfwas.devops.user.message.spi.ExternalNotifyPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExternalNotifyPublisherImpl implements ExternalNotifyPublisher {

    private final MessageNotifyDispatcher notifyDispatcher;

    @Override
    public void dispatch(SiteMessageCommand command) {
        notifyDispatcher.dispatchExternal(command);
    }
}
