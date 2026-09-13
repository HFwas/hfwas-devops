package com.hfwas.devops.pipeline.tekton;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import com.hfwas.devops.pipeline.graph.PipelineGraphSpec;
import com.hfwas.devops.pipeline.graph.PipelineJobKind;
import com.hfwas.devops.pipeline.graph.PipelineJobSpec;
import com.hfwas.devops.pipeline.graph.PipelineStageSpec;
import com.hfwas.devops.pipeline.toolchain.PipelineStack;
import com.hfwas.devops.pipeline.toolchain.ToolchainCatalog;
import com.hfwas.devops.pipeline.toolchain.ToolchainResolved;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TektonCompiler {

    public static final String CLONE_IMAGE = "alpine/git:2.45.2";
    public static final String SCAN_IMAGE = "aquasec/trivy:0.66.0";
    public static final String UPLOAD_IMAGE = "rclone/rclone:1.68.2";
    public static final String DEPLOY_IMAGE = "bitnami/kubectl:1.31.4";
    public static final String NOTIFY_IMAGE = "curlimages/curl:8.11.1";
    public static final String BUILDAH_IMAGE = "quay.io/containers/buildah:v1.37.0";
    public static final String COSIGN_IMAGE = "ghcr.io/sigstore/cosign:v2.4.3";
    public static final String SEMGREP_IMAGE = "semgrep/semgrep:1.97.0";
    public static final String SONAR_IMAGE = "sonarsource/sonar-scanner-cli:11.2";
    public static final String CDXGEN_IMAGE = "ghcr.io/cyclonedx/cdxgen:v11.0.0";
    public static final String WORKSPACE = "source";
    public static final String CACHE_WORKSPACE = "cache";
    public static final String SOURCE_DIR = "src";

    /**
     * 缺省通用模板 — 仅当数据库 command_template 为空时使用。
     * 不含任何任务特定逻辑。
     */
    private static final String DEFAULT_TEMPLATE = """
            set -eu
            mkdir -p "$(workspaces.source.path)/src"
            cd "$(workspaces.source.path)/src"
            ${COMMAND}
            """.stripIndent();

    private static final ToolchainCatalog TOOLCHAIN = new ToolchainCatalog();

    private TektonCompiler() {
    }

    public static CompiledTekton compile(CompileRequest request) {
        List<PipelineStageSpec> stages = request.graph().stages().stream()
                .sorted(Comparator.comparingInt(PipelineStageSpec::sortOrder))
                .toList();
        boolean serial = stages.stream().allMatch(stage -> stage.jobs() != null && stage.jobs().size() == 1);
        String name = DnsNames.objectName(request.runId());
        GitRemote remote = hasClone(stages) ? GitRemote.parse(request.repoUrl()) : null;
        if (serial) {
            List<CompiledStep> steps = new ArrayList<>();
            for (PipelineStageSpec stage : stages) {
                steps.addAll(toSteps(stage.jobs().getFirst(), request, remote, uniqueStepNames(steps)));
            }
            if (steps.isEmpty()) {
                throw BizException.of(ResultCode.BAD_REQUEST, "流水线没有可执行任务");
            }
            boolean cache = steps.stream().anyMatch(CompiledStep::usesKanikoCache);
            CompiledTask task = new CompiledTask(name, steps);
            return new CompiledTekton(name, TektonMode.TASK, List.of(task), List.of(), false, cache, request.pipelineId());
        }
        List<CompiledTask> tasks = new ArrayList<>();
        List<CompiledPipelineTask> pipeline = new ArrayList<>();
        List<String> previous = List.of();
        int index = 0;
        boolean cache = false;
        for (PipelineStageSpec stage : stages) {
            List<PipelineJobSpec> jobs = stage.jobs().stream()
                    .sorted(Comparator.comparingInt(PipelineJobSpec::sortOrder))
                    .toList();
            List<String> current = new ArrayList<>();
            for (PipelineJobSpec job : jobs) {
                List<CompiledStep> steps = toSteps(job, request, remote, List.of());
                if (steps.isEmpty()) {
                    continue;
                }
                cache = cache || steps.stream().anyMatch(CompiledStep::usesKanikoCache);
                String taskName = DnsNames.uniqueName(DnsNames.stepName(job.name(), job.kind().name()), tasks.stream().map(CompiledTask::name).toList());
                tasks.add(new CompiledTask(taskName, steps));
                pipeline.add(new CompiledPipelineTask(taskName, taskName, previous));
                current.add(taskName);
                index++;
            }
            previous = List.copyOf(current);
        }
        if (index == 0) {
            throw BizException.of(ResultCode.BAD_REQUEST, "流水线没有可执行任务");
        }
        return new CompiledTekton(name, TektonMode.PIPELINE, tasks, pipeline, true, cache, request.pipelineId());
    }

    public static boolean isSerial(PipelineGraphSpec graph) {
        return graph.stages().stream().allMatch(stage -> stage.jobs() != null && stage.jobs().size() == 1);
    }

    private static boolean hasClone(List<PipelineStageSpec> stages) {
        return stages.stream()
                .flatMap(stage -> stage.jobs() == null ? java.util.stream.Stream.empty() : stage.jobs().stream())
                .anyMatch(job -> job.kind() == PipelineJobKind.CLONE);
    }

    // ========================================================================
    //  Step 生成 & 模板替换
    // ========================================================================

    private static List<CompiledStep> toSteps(
            PipelineJobSpec job,
            CompileRequest request,
            GitRemote remote,
            List<String> usedNames
    ) {
        if (job.kind() == PipelineJobKind.APPROVAL) {
            return List.of();
        }
        Map<String, String> env = new LinkedHashMap<>();
        env.put("GOTOOLCHAIN", "local");
        String base = DnsNames.uniqueName(DnsNames.stepName(job.name(), job.kind().name()), usedNames);
        String command = job.command() == null ? "" : job.command();

        // ---- 分支：CLONE — 保留 env 注入，脚本走模板 ----
        if (job.kind() == PipelineJobKind.CLONE) {
            if (remote == null) {
                throw BizException.of(ResultCode.BAD_REQUEST, "仓库地址不能为空");
            }
            env.put("GIT_SCHEME", remote.scheme());
            env.put("GIT_HOST", remote.hostAuthority());
            env.put("GIT_PATH", remote.path());
            env.put("GIT_REF", request.gitRef() == null || request.gitRef().isBlank() ? "main" : request.gitRef());
            if (remote.hasEmbeddedCredentials()) {
                env.put("GIT_EMBEDDED_AUTH", remote.userInfo());
            }
            if (request.gitHttpProxy() != null && !request.gitHttpProxy().isBlank()) {
                env.put("GIT_HTTP_PROXY", request.gitHttpProxy().trim());
            }
            String script = resolveScript("CLONE", request.taskScripts(), "");
            return List.of(new CompiledStep(base, resolveImage("CLONE", request.taskImages(), CLONE_IMAGE), script, env, request.hasCredential(), false));
        }

        // ---- 分支：IMAGE — 保留多步骤编排，每个 Step 脚本走模板 ----
        if (job.kind() == PipelineJobKind.IMAGE) {
            String buildahScript = resolveScript("IMAGE", request.taskScripts(), command);
            String cosignScript = resolveScript("IMAGE_COSIGN", request.taskScripts(), command);
            return List.of(
                    new CompiledStep(base, resolveImage("IMAGE", request.taskImages(), BUILDAH_IMAGE), buildahScript, env, false, false),
                    new CompiledStep(base + "-cosign", resolveImage("IMAGE", request.taskImages(), COSIGN_IMAGE), cosignScript, env, false, false)
            );
        }

        // ---- 分支：DEPENDENCY_ANALYSIS — 保留 API_ENDPOINT env 注入，脚本走模板 ----
        if (job.kind() == PipelineJobKind.DEPENDENCY_ANALYSIS) {
            if (request.apiEndpoint() != null && !request.apiEndpoint().isBlank()) {
                env.put("API_ENDPOINT", request.apiEndpoint());
                env.put("RUN_ID", String.valueOf(request.runId()));
            }
            String script = resolveScript("DEPENDENCY_ANALYSIS", request.taskScripts(), command);
            return List.of(new CompiledStep(base, resolveImage("DEPENDENCY_ANALYSIS", request.taskImages(), CDXGEN_IMAGE), script, env, false, false));
        }

        // ---- 分支：KUBECTL — 挂载 kubeconfig 凭证执行 kubectl ----
        if (job.kind() == PipelineJobKind.KUBECTL) {
            String script = resolveScript("KUBECTL", request.taskScripts(), command);
            String image = resolveImage("KUBECTL", request.taskImages(), DEPLOY_IMAGE);
            return List.of(new CompiledStep(base, image, script, env, false, false, true));
        }

        // ---- 通用分支：所有其他任务走模板替换 ----
        String image = switch (job.kind()) {
            case LINT_SEMGREP -> resolveImage("LINT_SEMGREP", request.taskImages(), SEMGREP_IMAGE);
            case LINT_SONAR -> resolveImage("LINT_SONAR", request.taskImages(), SONAR_IMAGE);
            case SCAN -> resolveImage("SCAN", request.taskImages(), SCAN_IMAGE);
            case UPLOAD -> resolveImage("UPLOAD", request.taskImages(), UPLOAD_IMAGE);
            case DEPLOY -> resolveImage("DEPLOY", request.taskImages(), DEPLOY_IMAGE);
            case NOTIFY -> resolveImage("NOTIFY", request.taskImages(), NOTIFY_IMAGE);
            default -> toolchainImageForJob(job);
        };
        String script = resolveScript(job.kind().name(), request.taskScripts(), command);
        boolean formatCredential = job.kind() == PipelineJobKind.FORMAT;
        return List.of(new CompiledStep(base, image, script, env, formatCredential, false));
    }

    /**
     * 解析任务的最终执行脚本。
     * 优先级：taskScripts 模板替换 &gt; 缺省通用模板。
     * 替换 {@code ${COMMAND}} 为用户命令。
     */
    private static String resolveScript(String kindValue, Map<String, String> taskScripts, String command) {
        String template = taskScripts != null ? taskScripts.get(kindValue) : null;
        if (template == null || template.isBlank()) {
            template = DEFAULT_TEMPLATE;
        }
        return template.replace("${COMMAND}", command != null ? command : "");
    }

    /**
     * 解析任务的最终镜像地址，优先级：toolImage &gt; defaultImage &gt; 硬编码默认值
     */
    private static String resolveImage(String kindValue, Map<String, String> taskImages, String fallback) {
        if (taskImages != null) {
            String effective = taskImages.get(kindValue);
            if (effective != null && !effective.isBlank()) {
                return effective;
            }
        }
        return fallback;
    }

    private static String toolchainImageForJob(PipelineJobSpec job) {
        String stack = job.stack();
        String runtime = job.runtimeVersion();
        if (stack == null || runtime == null) {
            return TOOLCHAIN.list().getFirst().image();
        }
        try {
            ToolchainResolved resolved = TOOLCHAIN.resolve(
                    PipelineStack.valueOf(stack), runtime, job.toolVersion());
            return resolved.image();
        } catch (Exception e) {
            return TOOLCHAIN.list().getFirst().image();
        }
    }

    private static List<String> uniqueStepNames(List<CompiledStep> steps) {
        return steps.stream().map(CompiledStep::name).toList();
    }
}