package com.hfwas.devops.pipeline.graph;

import java.util.List;

public record PipelineStageSpec(
        Long id,
        String name,
        int sortOrder,
        List<PipelineJobSpec> jobs
) {
}
