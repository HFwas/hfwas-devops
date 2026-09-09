package com.hfwas.devops.pipeline.graph;

public enum PipelineJobKind {
    CLONE, LINT_SEMGREP, LINT_SONAR, BUILD, TEST,
    SCAN, PACKAGE, CUSTOM, IMAGE, PUBLISH,
    UPLOAD, DEPLOY, APPROVAL, NOTIFY;

    /**
     * 不需要命令的任务类型（命令由平台生成或无需命令）。
     * 用于 PipelineGraphValidator 校验。
     */
    public boolean requiresCommand() {
        return this != CLONE && this != APPROVAL;
    }

    public static boolean isValid(String kind) {
        if (kind == null) return false;
        try {
            PipelineJobKind.valueOf(kind);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}