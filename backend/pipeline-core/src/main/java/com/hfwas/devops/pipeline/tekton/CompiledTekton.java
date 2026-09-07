package com.hfwas.devops.pipeline.tekton;

import java.util.List;

public record CompiledTekton(
        String name,
        TektonMode mode,
        List<CompiledTask> tasks,
        List<CompiledPipelineTask> pipelineTasks,
        boolean needsPvc
) {
}
