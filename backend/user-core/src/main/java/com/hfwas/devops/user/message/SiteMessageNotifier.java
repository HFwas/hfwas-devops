package com.hfwas.devops.user.message;

import com.hfwas.devops.user.context.UserContext;
import com.hfwas.devops.user.context.UserContextHolder;
import com.hfwas.devops.user.message.model.SiteMessageCommand;
import com.hfwas.devops.user.message.spi.SiteMessagePublisher;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SiteMessageNotifier {

    private final SiteMessagePublisher messagePublisher;

    public void notifyTenantJoined(Long userId, Long tenantId, String tenantName) {
        publish(userId, SiteMessageCommand.builder()
                .category(MessageCategories.OPERATION)
                .title("已加入租户")
                .content("您已被加入租户「" + tenantName + "」，可在租户切换器中选择该租户。")
                .tenantId(tenantId)
                .bizType("tenant_member")
                .linkUrl("/pm/projects")
                .senderId(currentUserId())
                .senderName(currentUserName())
                .build());
    }

    public void notifyTenantRemoved(Long userId, String tenantName) {
        publish(userId, SiteMessageCommand.builder()
                .category(MessageCategories.OPERATION)
                .title("已移出租户")
                .content("您已被移出租户「" + tenantName + "」。")
                .bizType("tenant_member")
                .senderId(currentUserId())
                .senderName(currentUserName())
                .build());
    }

    public void notifySessionRevoked(Long userId) {
        publish(userId, SiteMessageCommand.builder()
                .category(MessageCategories.SYSTEM)
                .title("会话已强制下线")
                .content("您的在线会话已被管理员强制下线，如非本人操作请联系管理员。")
                .bizType("session")
                .senderId(currentUserId())
                .senderName(currentUserName())
                .build());
    }

    public void notifyAccountCreated(Long userId, String username) {
        publish(userId, SiteMessageCommand.builder()
                .category(MessageCategories.SYSTEM)
                .title("平台账号已创建")
                .content("您的平台账号「" + username + "」已创建，请联系管理员加入租户后即可使用。")
                .bizType("user")
                .senderId(currentUserId())
                .senderName(currentUserName())
                .build());
    }

    public void notifyLdapSyncComplete(Long userId, String summary) {
        publish(userId, SiteMessageCommand.builder()
                .category(MessageCategories.OPERATION)
                .title("LDAP 用户同步完成")
                .content(summary)
                .bizType("identity_connector")
                .linkUrl("/user/integrations")
                .senderId(userId)
                .senderName("系统")
                .build());
    }

    private void publish(Long userId, SiteMessageCommand command) {
        messagePublisher.publishToUser(userId, command);
    }

    private Long currentUserId() {
        return UserContextHolder.current().map(UserContext::getUserId).orElse(null);
    }

    private String currentUserName() {
        return UserContextHolder.current()
                .map(u -> StringUtils.defaultIfBlank(u.getDisplayName(), u.getUsername()))
                .orElse("系统");
    }
}
