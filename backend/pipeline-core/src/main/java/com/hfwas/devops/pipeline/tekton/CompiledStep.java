package com.hfwas.devops.pipeline.tekton;

import java.util.Map;

public record CompiledStep(
        String name,
        String image,
        String script,
        Map<String, String> env,
        boolean usesGitSecret,
        boolean usesKanikoCache,
        boolean usesKubeconfig,
        String cpuRequest,
        String cpuLimit,
        String memoryRequest,
        String memoryLimit
) {
    public CompiledStep(String name, String image, String script, Map<String, String> env, boolean usesGitSecret) {
        this(name, image, script, env, usesGitSecret, false, false, "", "", "", "");
    }

    public CompiledStep(String name, String image, String script, Map<String, String> env, boolean usesGitSecret, boolean usesKanikoCache) {
        this(name, image, script, env, usesGitSecret, usesKanikoCache, false, "", "", "", "");
    }

    public CompiledStep(String name, String image, String script, Map<String, String> env, boolean usesGitSecret, boolean usesKanikoCache, boolean usesKubeconfig) {
        this(name, image, script, env, usesGitSecret, usesKanikoCache, usesKubeconfig, "", "", "", "");
    }
}
