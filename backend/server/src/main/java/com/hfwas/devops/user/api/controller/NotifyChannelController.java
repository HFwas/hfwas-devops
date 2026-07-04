package com.hfwas.devops.user.api.controller;

import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.user.model.NotifyChannelSaveRequest;
import com.hfwas.devops.user.model.NotifyChannelVO;
import com.hfwas.devops.user.model.NotifyTestResult;
import com.hfwas.devops.user.notify.NotifyChannelService;
import com.hfwas.devops.user.operlog.annotation.OperLog;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/message-notify")
@RequiredArgsConstructor
public class NotifyChannelController {

    private final NotifyChannelService notifyChannelService;

    @GetMapping("/channels")
    public BaseResult<List<NotifyChannelVO>> listChannels() {
        return BaseResult.ok(notifyChannelService.listAll());
    }

    @OperLog(module = "user", action = "save", bizType = "notify_channel", summary = "保存消息通知渠道")
    @PostMapping("/save")
    public BaseResult<Void> save(@RequestBody NotifyChannelSaveRequest request) {
        notifyChannelService.save(request);
        return BaseResult.ok();
    }

    @PostMapping("/test")
    public BaseResult<NotifyTestResult> test(@RequestBody NotifyChannelSaveRequest request) {
        if (request.getChannel() != null && request.getConfigJson() == null) {
            return BaseResult.ok(notifyChannelService.test(request.getChannel()));
        }
        return BaseResult.ok(notifyChannelService.testDraft(request));
    }
}
