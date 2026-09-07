package com.hfwas.devops.pipeline.tekton;

import com.hfwas.devops.pipeline.graph.PipelineGraphSpec;

public record CompileRequest(
        long runId,
        String repoUrl,
        String gitRef,
        String stackImage,
        boolean hasCredential,
        PipelineGraphSpec graph
) {
}
