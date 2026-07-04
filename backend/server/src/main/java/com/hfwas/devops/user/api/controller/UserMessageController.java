package com.hfwas.devops.user.api.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hfwas.devops.common.core.base.BaseResult;
import com.hfwas.devops.user.model.*;
import com.hfwas.devops.user.operlog.annotation.OperLog;
import com.hfwas.devops.user.service.SiteMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/user/messages")
@RequiredArgsConstructor
public class UserMessageController {

    private final SiteMessageService messageService;

    @GetMapping("/unread-count")
    public BaseResult<Long> unreadCount() {
        return BaseResult.ok(messageService.unreadCount());
    }

    @GetMapping("/recent")
    public BaseResult<List<MessageVO>> recent(@RequestParam(defaultValue = "5") int limit) {
        return BaseResult.ok(messageService.listRecent(limit));
    }

    @PostMapping("/page")
    public BaseResult<IPage<MessageVO>> page(@RequestBody MessagePageRequest request) {
        return BaseResult.ok(messageService.pageInbox(request));
    }

    @GetMapping("/{id}")
    public BaseResult<MessageVO> detail(@PathVariable Long id) {
        return BaseResult.ok(messageService.getDetail(id));
    }

    @PostMapping("/mark-read")
    public BaseResult<Void> markRead(@RequestParam("id") Long id) {
        messageService.markRead(id);
        return BaseResult.ok();
    }

    @PostMapping("/mark-all-read")
    public BaseResult<Void> markAllRead() {
        messageService.markAllRead();
        return BaseResult.ok();
    }

    @PostMapping("/mark-read-batch")
    public BaseResult<Void> markReadBatch(@RequestBody List<Long> ids) {
        messageService.markReadBatch(ids);
        return BaseResult.ok();
    }

    @PostMapping("/delete")
    public BaseResult<Void> delete(@RequestParam("id") Long id) {
        messageService.deleteMessage(id);
        return BaseResult.ok();
    }

    @PostMapping("/admin/page")
    public BaseResult<IPage<MessageVO>> adminPage(@RequestBody MessageAdminPageRequest request) {
        return BaseResult.ok(messageService.pageAdmin(request));
    }

    @OperLog(module = "user", action = "save", bizType = "message", summary = "发送消息")
    @PostMapping("/admin/send")
    public BaseResult<Void> adminSend(@RequestBody MessageSendRequest request) {
        messageService.sendBroadcast(request);
        return BaseResult.ok();
    }
}
