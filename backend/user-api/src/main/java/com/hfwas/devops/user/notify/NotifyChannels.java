package com.hfwas.devops.user.notify;

public final class NotifyChannels {

    public static final String SITE = "site";
    public static final String DINGTALK = "dingtalk";
    public static final String FEISHU = "feishu";

    private NotifyChannels() {
    }

    public static String label(String channel) {
        if (channel == null) {
            return "未知";
        }
        return switch (channel) {
            case SITE -> "站内消息";
            case DINGTALK -> "钉钉";
            case FEISHU -> "飞书";
            default -> channel;
        };
    }
}
