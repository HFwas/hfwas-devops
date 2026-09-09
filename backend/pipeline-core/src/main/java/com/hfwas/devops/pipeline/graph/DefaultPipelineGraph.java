package com.hfwas.devops.pipeline.graph;

import java.util.ArrayList;
import java.util.List;

public final class DefaultPipelineGraph {

    private DefaultPipelineGraph() {
    }

    public static PipelineGraphSpec create() {
        List<PipelineStageSpec> stages = new ArrayList<>();
        stages.add(stage("clone", 0, List.of(job("clone", PipelineJobKind.CLONE, "", 0))));
        stages.add(stage("build", 1, List.of(job("build", PipelineJobKind.BUILD, "", 0))));
        stages.add(stage("test", 2, List.of(job("test", PipelineJobKind.TEST, "", 0))));
        return new PipelineGraphSpec(stages);
    }

    private static PipelineStageSpec stage(String name, int order, List<PipelineJobSpec> jobs) {
        return new PipelineStageSpec(null, name, order, jobs);
    }

    private static PipelineJobSpec job(String name, PipelineJobKind kind, String command, int order) {
        return new PipelineJobSpec(null, name, kind, command, null, null, null, order);
    }
}
