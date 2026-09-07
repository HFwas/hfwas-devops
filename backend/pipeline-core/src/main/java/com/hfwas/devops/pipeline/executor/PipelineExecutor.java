package com.hfwas.devops.pipeline.executor;

/**
 * Tekton 提交入口。未配置 kubeconfig 时 isReady=false，运行记失败。
 */
public interface PipelineExecutor {

    boolean isReady();

    void submit(Long runId);

    void cancel(Long runId);
}
