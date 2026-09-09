package com.hfwas.devops.pipeline.tekton;

import com.hfwas.devops.pipeline.graph.PipelineGraphSpec;

import java.util.Map;

public record CompileRequest(
        long runId,
        long pipelineId,
        String repoUrl,
        String gitRef,
        boolean hasCredential,
        PipelineGraphSpec graph,
        String gitHttpProxy,
        Map<String, String> taskImages
) {
}
