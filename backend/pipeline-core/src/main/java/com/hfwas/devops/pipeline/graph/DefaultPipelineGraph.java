package com.hfwas.devops.pipeline.graph;

import com.hfwas.devops.pipeline.toolchain.PipelineStack;
import com.hfwas.devops.pipeline.toolchain.ToolchainCatalog;
import com.hfwas.devops.pipeline.toolchain.ToolchainResolved;

import java.util.ArrayList;
import java.util.List;

public final class DefaultPipelineGraph {

    private DefaultPipelineGraph() {
    }

    public static PipelineGraphSpec create(PipelineStack stack, String runtimeVersion, String toolVersion) {
        ToolchainResolved resolved = new ToolchainCatalog().resolve(stack, runtimeVersion, toolVersion);
        List<PipelineStageSpec> stages = new ArrayList<>();
        stages.add(stage("clone", 0, List.of(job("clone", PipelineJobKind.CLONE, "", 0))));
        stages.add(stage("build", 1, List.of(job("build", PipelineJobKind.BUILD, resolved.buildCommand(), 0))));
        stages.add(stage("test", 2, List.of(job("test", PipelineJobKind.TEST, resolved.testCommand(), 0))));
        return new PipelineGraphSpec(stages);
    }

    private static PipelineStageSpec stage(String name, int order, List<PipelineJobSpec> jobs) {
        return new PipelineStageSpec(null, name, order, jobs);
    }

    private static PipelineJobSpec job(String name, PipelineJobKind kind, String command, int order) {
        return new PipelineJobSpec(null, name, kind, command, order);
    }
}
