package com.hfwas.devops.user.message;

public final class MessageCategories {

    public static final String SYSTEM = "system";
    public static final String OPERATION = "operation";
    public static final String ANNOUNCEMENT = "announcement";

    private MessageCategories() {
    }

    public static String label(String category) {
        if (category == null) {
            return "消息";
        }
        return switch (category) {
            case SYSTEM -> "系统通知";
            case OPERATION -> "操作通知";
            case ANNOUNCEMENT -> "公告";
            default -> category;
        };
    }
}
