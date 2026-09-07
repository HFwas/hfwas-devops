package com.hfwas.devops.pipeline.tekton;

import java.util.Map;

public record CompiledStep(
        String name,
        String image,
        String script,
        Map<String, String> env,
        boolean usesGitSecret
) {
}
