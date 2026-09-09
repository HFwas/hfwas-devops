package com.hfwas.devops.pipeline.graph;

public record PipelineJobSpec(
        Long id,
        String name,
        PipelineJobKind kind,
        String command,
        String stack,
        String runtimeVersion,
        String toolVersion,
        int sortOrder
) {
}
