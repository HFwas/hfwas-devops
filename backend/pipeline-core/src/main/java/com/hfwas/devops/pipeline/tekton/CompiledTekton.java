package com.hfwas.devops.pipeline.tekton;

import java.util.List;

public record CompiledTekton(
        String name,
        TektonMode mode,
        List<CompiledTask> tasks,
        List<CompiledPipelineTask> pipelineTasks,
        boolean needsPvc,
        boolean needsKanikoCache,
        long pipelineId
) {
    public CompiledTekton(
            String name,
            TektonMode mode,
            List<CompiledTask> tasks,
            List<CompiledPipelineTask> pipelineTasks,
            boolean needsPvc
    ) {
        this(name, mode, tasks, pipelineTasks, needsPvc, false, 0L);
    }

    public boolean anyKanikoCache() {
        return needsKanikoCache || tasks.stream()
                .flatMap(task -> task.steps().stream())
                .anyMatch(CompiledStep::usesKanikoCache);
    }
}
