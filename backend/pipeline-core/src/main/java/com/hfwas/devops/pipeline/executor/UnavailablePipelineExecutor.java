package com.hfwas.devops.pipeline.executor;

public class UnavailablePipelineExecutor implements PipelineExecutor {

    @Override
    public boolean isReady() {
        return false;
    }

    @Override
    public void submit(Long runId) {
        throw new IllegalStateException("未配置执行集群（pipeline.kubeconfig）");
    }

    @Override
    public void cancel(Long runId) {
        // no cluster object to cancel
    }
}
