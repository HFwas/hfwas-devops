package com.hfwas.devops.pipeline.toolchain;

public record ToolchainResolved(
        PipelineStack stack,
        String runtimeVersion,
        String toolVersion,
        String image,
        String buildCommand,
        String testCommand
) {
}
