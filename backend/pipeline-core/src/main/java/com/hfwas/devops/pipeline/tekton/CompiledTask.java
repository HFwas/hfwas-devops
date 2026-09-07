package com.hfwas.devops.pipeline.tekton;

import java.util.List;

public record CompiledTask(
        String name,
        List<CompiledStep> steps
) {
}
