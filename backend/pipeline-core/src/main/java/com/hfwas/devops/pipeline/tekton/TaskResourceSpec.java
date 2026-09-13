package com.hfwas.devops.pipeline.tekton;

public record TaskResourceSpec(
        String cpuRequest,
        String cpuLimit,
        String memoryRequest,
        String memoryLimit
) {
    public static final TaskResourceSpec EMPTY = new TaskResourceSpec("", "", "", "");

    public boolean isEmpty() {
        return cpuRequest.isBlank() && cpuLimit.isBlank()
                && memoryRequest.isBlank() && memoryLimit.isBlank();
    }
}