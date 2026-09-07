package com.hfwas.devops.pipeline.graph;

import java.util.List;

public record PipelineGraphSpec(List<PipelineStageSpec> stages) {
}
