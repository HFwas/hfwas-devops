package com.hfwas.devops.pipeline.tekton;

import com.hfwas.devops.pipeline.graph.PipelineGraphSpec;

public record CompileRequest(
        long runId,
        long pipelineId,
        String repoUrl,
        String gitRef,
        boolean hasCredential,
        PipelineGraphSpec graph,
        String gitHttpProxy
) {
}
