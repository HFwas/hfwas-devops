package com.hfwas.devops.pipeline.tekton;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pipeline.graph.PipelineGraphSpec;
import com.hfwas.devops.pipeline.graph.PipelineJobKind;
import com.hfwas.devops.pipeline.graph.PipelineJobSpec;
import com.hfwas.devops.pipeline.graph.PipelineStageSpec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TektonCompiler {

    public static final String CLONE_IMAGE = "alpine/git:2.45.2";
    public static final String WORKSPACE = "source";
    public static final String SOURCE_DIR = "src";

    private TektonCompiler() {
    }

    public static CompiledTekton compile(CompileRequest request) {
        List<PipelineStageSpec> stages = request.graph().stages().stream()
                .sorted(Comparator.comparingInt(PipelineStageSpec::sortOrder))
                .toList();
        boolean serial = stages.stream().allMatch(stage -> stage.jobs() != null && stage.jobs().size() == 1);
        String name = DnsNames.objectName(request.runId());
        GitRemote remote = GitRemote.parse(request.repoUrl());
        if (serial) {
            List<CompiledStep> steps = new ArrayList<>();
            for (PipelineStageSpec stage : stages) {
                steps.add(toStep(stage.jobs().getFirst(), request, remote, uniqueStepNames(steps)));
            }
            CompiledTask task = new CompiledTask(name, steps);
            return new CompiledTekton(name, TektonMode.TASK, List.of(task), List.of(), false);
        }
        List<CompiledTask> tasks = new ArrayList<>();
        List<CompiledPipelineTask> pipeline = new ArrayList<>();
        List<String> previous = List.of();
        int index = 0;
        for (PipelineStageSpec stage : stages) {
            List<PipelineJobSpec> jobs = stage.jobs().stream()
                    .sorted(Comparator.comparingInt(PipelineJobSpec::sortOrder))
                    .toList();
            List<String> current = new ArrayList<>();
            for (PipelineJobSpec job : jobs) {
                String taskName = uniqueName(DnsNames.stepName(job.name()), tasks.stream().map(CompiledTask::name).toList());
                CompiledStep step = toStep(job, request, remote, List.of());
                tasks.add(new CompiledTask(taskName, List.of(step)));
                pipeline.add(new CompiledPipelineTask(taskName, taskName, previous));
                current.add(taskName);
                index++;
            }
            previous = List.copyOf(current);
        }
        if (index == 0) {
            throw BizException.of(ResultCode.BAD_REQUEST, "流水线没有可执行任务");
        }
        return new CompiledTekton(name, TektonMode.PIPELINE, tasks, pipeline, true);
    }

    public static boolean isSerial(PipelineGraphSpec graph) {
        return graph.stages().stream().allMatch(stage -> stage.jobs() != null && stage.jobs().size() == 1);
    }

    private static CompiledStep toStep(
            PipelineJobSpec job,
            CompileRequest request,
            GitRemote remote,
            List<String> usedNames
    ) {
        String stepName = uniqueName(DnsNames.stepName(job.name()), usedNames);
        Map<String, String> env = new LinkedHashMap<>();
        env.put("GOTOOLCHAIN", "local");
        if (job.kind() == PipelineJobKind.CLONE) {
            env.put("GIT_SCHEME", remote.scheme());
            env.put("GIT_HOST", remote.host());
            env.put("GIT_PATH", remote.path());
            env.put("GIT_REF", request.gitRef() == null || request.gitRef().isBlank() ? "main" : request.gitRef());
            String script = """
                    set -eu
                    cd "$(workspaces.source.path)"
                    AUTH=""
                    if [ -n "${GIT_USERNAME:-}" ]; then
                      AUTH="${GIT_USERNAME}:${GIT_PASSWORD}@"
                    fi
                    git clone "${GIT_SCHEME}://${AUTH}${GIT_HOST}/${GIT_PATH}" src
                    git -C src checkout "${GIT_REF}"
                    """.stripIndent();
            return new CompiledStep(stepName, CLONE_IMAGE, script, env, request.hasCredential());
        }
        String command = job.command() == null ? "" : job.command();
        String script = """
                set -eu
                cd "$(workspaces.source.path)/src"
                %s
                """.formatted(command).stripIndent();
        return new CompiledStep(stepName, request.stackImage(), script, env, false);
    }

    private static List<String> uniqueStepNames(List<CompiledStep> steps) {
        return steps.stream().map(CompiledStep::name).toList();
    }

    private static String uniqueName(String base, List<String> used) {
        if (!used.contains(base)) {
            return base;
        }
        int i = 2;
        while (used.contains(base + "-" + i)) {
            i++;
        }
        return base + "-" + i;
    }
}
