package com.hfwas.devops.user.model;

import lombok.Data;

@Data
public class WebhookChannelConfig {
    private String webhookUrl;
    /** DingTalk/Feishu signing secret; optional. */
    private String secret;
}
