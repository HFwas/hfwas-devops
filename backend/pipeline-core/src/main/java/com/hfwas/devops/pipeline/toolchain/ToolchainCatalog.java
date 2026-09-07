package com.hfwas.devops.pipeline.toolchain;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ToolchainCatalog {

    public record ToolchainOption(
            PipelineStack stack,
            String runtimeVersion,
            String toolVersion,
            String image,
            String buildCommand,
            String testCommand
    ) {
    }

    private static final List<ToolchainOption> OPTIONS = List.of(
            java("17", "3.8", "maven:3.8.8-eclipse-temurin-17"),
            java("17", "3.9", "maven:3.9.9-eclipse-temurin-17"),
            java("21", "3.9", "maven:3.9.9-eclipse-temurin-21"),
            node("20", "NPM", "node:20-bookworm", "npm ci", "npm test"),
            node("20", "PNPM", "node:20-bookworm",
                    "corepack enable && pnpm i --frozen-lockfile", "pnpm test"),
            node("20", "YARN", "node:20-bookworm",
                    "corepack enable && yarn install --frozen-lockfile", "yarn test"),
            node("22", "NPM", "node:22-bookworm", "npm ci", "npm test"),
            node("22", "PNPM", "node:22-bookworm",
                    "corepack enable && pnpm i --frozen-lockfile", "pnpm test"),
            node("22", "YARN", "node:22-bookworm",
                    "corepack enable && yarn install --frozen-lockfile", "yarn test"),
            new ToolchainOption(PipelineStack.GO, "1.22", null, "golang:1.22",
                    "go build ./...", "go test ./..."),
            new ToolchainOption(PipelineStack.GO, "1.23", null, "golang:1.23",
                    "go build ./...", "go test ./..."),
            new ToolchainOption(PipelineStack.PYTHON, "3.11", null, "python:3.11-bookworm",
                    "pip install -r requirements.txt", "pytest"),
            new ToolchainOption(PipelineStack.PYTHON, "3.12", null, "python:3.12-bookworm",
                    "pip install -r requirements.txt", "pytest")
    );

    private static ToolchainOption java(String jdk, String maven, String image) {
        return new ToolchainOption(PipelineStack.JAVA_MAVEN, jdk, maven, image,
                "mvn -B -DskipTests package", "mvn -B test");
    }

    private static ToolchainOption node(String runtime, String tool, String image,
                                        String build, String test) {
        return new ToolchainOption(PipelineStack.NODE, runtime, tool, image, build, test);
    }

    public List<ToolchainOption> list() {
        return new ArrayList<>(OPTIONS);
    }

    public ToolchainResolved resolve(PipelineStack stack, String runtimeVersion, String toolVersion) {
        return OPTIONS.stream()
                .filter(item -> item.stack() == stack)
                .filter(item -> Objects.equals(item.runtimeVersion(), runtimeVersion))
                .filter(item -> Objects.equals(blankToNull(item.toolVersion()), blankToNull(toolVersion)))
                .findFirst()
                .map(item -> new ToolchainResolved(
                        item.stack(),
                        item.runtimeVersion(),
                        item.toolVersion(),
                        item.image(),
                        item.buildCommand(),
                        item.testCommand()))
                .orElseThrow(() -> BizException.of(
                        ResultCode.BAD_REQUEST,
                        "不支持的工具链组合: " + stack + " " + runtimeVersion + " " + toolVersion));
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
