package com.hfwas.devops.user.operlog.spi;

import com.hfwas.devops.user.operlog.model.OperLogEntry;

/**
 * Fluent helper for programmatic operation log recording from business services.
 */
public final class OperLogs {

    private OperLogs() {
    }

    public static OperLogEntryBuilder entry(String module, String action, String bizType) {
        return new OperLogEntryBuilder(module, action, bizType);
    }

    public static final class OperLogEntryBuilder {
        private final String module;
        private final String action;
        private final String bizType;
        private String bizId;
        private String summary;
        private String extraJson;
        private String status = "success";
        private String failReason;

        private OperLogEntryBuilder(String module, String action, String bizType) {
            this.module = module;
            this.action = action;
            this.bizType = bizType;
        }

        public OperLogEntryBuilder bizId(String bizId) {
            this.bizId = bizId;
            return this;
        }

        public OperLogEntryBuilder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public OperLogEntryBuilder extraJson(String extraJson) {
            this.extraJson = extraJson;
            return this;
        }

        public OperLogEntryBuilder fail(String reason) {
            this.status = "fail";
            this.failReason = reason;
            return this;
        }

        public OperLogEntry build() {
            return OperLogEntry.builder()
                    .module(module)
                    .action(action)
                    .bizType(bizType)
                    .bizId(bizId)
                    .summary(summary)
                    .extraJson(extraJson)
                    .status(status)
                    .failReason(failReason)
                    .build();
        }
    }
}
