package com.hfwas.devops.pm.workitem.service;

import com.hfwas.devops.pm.field.model.FieldDefinition;
import com.hfwas.devops.pm.workitem.entity.PmWorkItem;
import com.hfwas.devops.pm.workitem.model.TransitionPostFunctionType;
import com.hfwas.devops.pm.workitem.model.TransitionPostFunctionVO;
import com.hfwas.devops.pm.workitem.model.TransitionVO;
import com.hfwas.devops.user.context.CurrentUserAccessor;
import com.hfwas.devops.user.message.MessageCategories;
import com.hfwas.devops.user.message.model.SiteMessageCommand;
import com.hfwas.devops.user.message.spi.ExternalNotifyPublisher;
import com.hfwas.devops.user.message.spi.SiteMessagePublisher;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TransitionPostFunctionExecutor {

    private final StatusDefinitionService statusDefinitionService;
    private final WorkItemFieldApplicator fieldApplicator;
    private final SiteMessagePublisher siteMessagePublisher;
    private final ExternalNotifyPublisher externalNotifyPublisher;
    private final CurrentUserAccessor currentUserAccessor;

    public void execute(PmWorkItem item, String fromStatus, String transitionId, List<FieldDefinition> definitions) {
        if (item == null || StringUtils.isBlank(transitionId)) {
            return;
        }
        TransitionVO transition = statusDefinitionService.findTransition(
                item.getProjectId(), item.getTypeCode(), fromStatus, transitionId);
        String toStatus = transition.getToStatus();
        List<TransitionPostFunctionVO> functions = statusDefinitionService.resolvePostFunctions(
                item.getProjectId(), item.getTypeCode(), fromStatus, transitionId);
        if (functions.isEmpty()) {
            return;
        }
        Map<String, String> labels = statusDefinitionService.statusLabelMap(
                item.getProjectId(), item.getTypeCode());
        for (TransitionPostFunctionVO fn : functions) {
            if (fn == null || StringUtils.isBlank(fn.getType())) {
                continue;
            }
            String type = fn.getType().trim();
            switch (type) {
                case TransitionPostFunctionType.SET_FIELD -> applySetField(item, fn, definitions);
                case TransitionPostFunctionType.NOTIFY_ASSIGNEE -> notifyAssignee(item, fn, fromStatus, toStatus, labels);
                case TransitionPostFunctionType.NOTIFY_USER -> notifyUser(item, fn, fromStatus, toStatus, labels);
                case TransitionPostFunctionType.WEBHOOK -> dispatchWebhook(item, fn, fromStatus, toStatus, labels);
                default -> throw new IllegalArgumentException("不支持的后置函数类型: " + type);
            }
        }
    }

    private void applySetField(PmWorkItem item, TransitionPostFunctionVO fn, List<FieldDefinition> definitions) {
        if (StringUtils.isBlank(fn.getFieldKey())) {
            throw new IllegalArgumentException("SET_FIELD 后置函数缺少 fieldKey");
        }
        fieldApplicator.apply(item, fn.getFieldKey(), fn.getValue(), definitions);
    }

    private void notifyAssignee(PmWorkItem item, TransitionPostFunctionVO fn,
                                String fromStatus, String toStatus, Map<String, String> labels) {
        if (item.getAssigneeId() == null) {
            return;
        }
        Long currentUserId = currentUserAccessor.currentUserId();
        if (item.getAssigneeId().equals(currentUserId)) {
            return;
        }
        SiteMessageCommand command = buildMessageCommand(item, fn, fromStatus, toStatus, labels);
        siteMessagePublisher.publishToUser(item.getAssigneeId(), command);
    }

    private void notifyUser(PmWorkItem item, TransitionPostFunctionVO fn,
                            String fromStatus, String toStatus, Map<String, String> labels) {
        if (fn.getUserId() == null) {
            throw new IllegalArgumentException("NOTIFY_USER 后置函数缺少 userId");
        }
        SiteMessageCommand command = buildMessageCommand(item, fn, fromStatus, toStatus, labels);
        siteMessagePublisher.publishToUser(fn.getUserId(), command);
    }

    private void dispatchWebhook(PmWorkItem item, TransitionPostFunctionVO fn,
                                 String fromStatus, String toStatus, Map<String, String> labels) {
        SiteMessageCommand command = buildMessageCommand(item, fn, fromStatus, toStatus, labels);
        externalNotifyPublisher.dispatch(command);
    }

    private SiteMessageCommand buildMessageCommand(PmWorkItem item, TransitionPostFunctionVO fn,
                                                   String fromStatus, String toStatus,
                                                   Map<String, String> labels) {
        String fromLabel = labels.getOrDefault(fromStatus, fromStatus);
        String toLabel = labels.getOrDefault(toStatus, toStatus);
        String defaultTitle = "工作项状态已更新";
        String defaultContent = "工作项「" + displayTitle(item) + "」已从「" + fromLabel + "」流转到「" + toLabel + "」。";
        return SiteMessageCommand.builder()
                .category(MessageCategories.OPERATION)
                .title(renderTemplate(StringUtils.defaultIfBlank(fn.getTitle(), defaultTitle),
                        item, fromLabel, toLabel))
                .content(renderTemplate(StringUtils.defaultIfBlank(fn.getContent(), defaultContent),
                        item, fromLabel, toLabel))
                .tenantId(currentUserAccessor.currentTenantId())
                .bizType("work_item")
                .bizId(String.valueOf(item.getId()))
                .linkUrl("/pm/projects/" + item.getProjectId() + "/items/" + item.getId()
                        + "?type=" + item.getTypeCode())
                .senderId(currentUserAccessor.currentUserId())
                .senderName(currentUserAccessor.currentDisplayName())
                .build();
    }

    private String renderTemplate(String template, PmWorkItem item, String fromLabel, String toLabel) {
        if (template == null) {
            return "";
        }
        return template
                .replace("{title}", displayTitle(item))
                .replace("{itemKey}", StringUtils.defaultIfBlank(item.getItemKey(), "#" + item.getId()))
                .replace("{fromStatus}", fromLabel)
                .replace("{toStatus}", toLabel);
    }

    private String displayTitle(PmWorkItem item) {
        return StringUtils.defaultIfBlank(item.getTitle(), "#" + item.getId());
    }
}
