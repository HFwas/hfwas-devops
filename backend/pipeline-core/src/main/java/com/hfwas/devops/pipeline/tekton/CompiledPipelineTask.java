package com.hfwas.devops.pipeline.tekton;

import java.util.List;

public record CompiledPipelineTask(
        String name,
        String taskRef,
        List<String> runAfter
) {
}
