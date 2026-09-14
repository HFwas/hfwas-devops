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
import java.util.Set;

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

    private record SubStepDef(
            String nameSuffix,
            String kindKey,
            String defaultImage,
            boolean formatCredential,
            boolean usesKanikoCache,
            boolean usesKubeConfig
    ) {}

    private static final Map<PipelineJobKind, List<SubStepDef>> MULTI_STEP_KINDS = Map.of(
            PipelineJobKind.IMAGE, List.of(
                    new SubStepDef("", "IMAGE", BUILDAH_IMAGE, false, false, false),
                    new SubStepDef("-cosign", "IMAGE_COSIGN", COSIGN_IMAGE, false, false, false)
            )
    );

    @FunctionalInterface
    private interface StepHandler {
        List<CompiledStep> create(
                String base,
                CompileRequest request,
                GitRemote remote,
                Map<String, String> env,
                String command,
                Map<String, TaskResourceSpec> taskResources
        );
    }

    private static final Map<PipelineJobKind, StepHandler> STEP_HANDLERS = new LinkedHashMap<>();

    private static final Map<PipelineJobKind, String> GENERIC_IMAGES = new LinkedHashMap<>();

    private static final Set<PipelineJobKind> FORMAT_KINDS = Set.of(PipelineJobKind.FORMAT);

    static {
        STEP_HANDLERS.put(PipelineJobKind.CLONE, (base, req, remote, env, command, taskResources) -> {
            if (remote == null) {
                throw BizException.of(ResultCode.BAD_REQUEST, "仓库地址不能为空");
            }
            GitRemote r = remote;
            env.put("GIT_SCHEME", r.scheme());
            env.put("GIT_HOST", r.hostAuthority());
            env.put("GIT_PATH", r.path());
            env.put("GIT_REF", req.gitRef() == null || req.gitRef().isBlank() ? "main" : req.gitRef());
            if (r.hasEmbeddedCredentials()) {
                env.putIfAbsent("GIT_EMBEDDED_AUTH", r.userInfo());
            }
            if (req.gitHttpProxy() != null && !req.gitHttpProxy().isBlank()) {
                env.putIfAbsent("GIT_HTTP_PROXY", req.gitHttpProxy().trim());
            }
            return List.of(
                    buildStep(base, "CLONE", req, env, "", CLONE_IMAGE, taskResources, req.hasCredential(), false, false));
        });

        STEP_HANDLERS.put(PipelineJobKind.DEPENDENCY_ANALYSIS, (base, req, remote, env, command, taskResources) -> {
            if (req.apiEndpoint() != null && !req.apiEndpoint().isBlank()) {
                env.put("API_ENDPOINT", req.apiEndpoint());
                env.put("RUN_ID", String.valueOf(req.runId()));
            }
            return List.of(
                    buildStep(base, "DEPENDENCY_ANALYSIS", req, env, command, CDXGEN_IMAGE, taskResources, false, false, false));
        });

        STEP_HANDLERS.put(PipelineJobKind.KUBECTL, (base, req, remote, env, command, taskResources) ->
                List.of(buildStep(base, "KUBECTL", req, env, command, DEPLOY_IMAGE, taskResources, false, false, true)));

        GENERIC_IMAGES.put(PipelineJobKind.LINT_SEMGREP, SEMGREP_IMAGE);
        GENERIC_IMAGES.put(PipelineJobKind.LINT_SONAR, SONAR_IMAGE);
        GENERIC_IMAGES.put(PipelineJobKind.SCAN, SCAN_IMAGE);
        GENERIC_IMAGES.put(PipelineJobKind.UPLOAD, UPLOAD_IMAGE);
        GENERIC_IMAGES.put(PipelineJobKind.DEPLOY, DEPLOY_IMAGE);
        GENERIC_IMAGES.put(PipelineJobKind.NOTIFY, NOTIFY_IMAGE);
        GENERIC_IMAGES.put(PipelineJobKind.DEPENDENCY_TRACK, NOTIFY_IMAGE);
    }

    private TektonCompiler() {
    }

    public static CompiledTekton compile(CompileRequest request) {
        List<PipelineStageSpec> stages = request.graph().stages().stream()
                .sorted(Comparator.comparingInt(PipelineStageSpec::sortOrder))
                .toList();
        boolean serial = stages.stream().allMatch(stage -> stage.jobs() != null && stage.jobs().size() == 1);
        String name = DnsNames.objectName(request.runId());
        GitRemote remote = hasClone(stages) ? GitRemote.parse(request.repoUrl()) : null;
        Map<String, TaskResourceSpec> taskResources = request.taskResources();
        if (serial) {
            List<CompiledStep> steps = new ArrayList<>();
            for (PipelineStageSpec stage : stages) {
                steps.addAll(toSteps(stage.jobs().getFirst(), request, remote, uniqueStepNames(steps), taskResources));
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
                List<CompiledStep> steps = toSteps(job, request, remote, List.of(), taskResources);
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
            List<String> usedNames,
            Map<String, TaskResourceSpec> taskResources
    ) {
        if (job.kind() == PipelineJobKind.APPROVAL) {
            return List.of();
        }
        Map<String, String> env = new LinkedHashMap<>();
        env.put("GOTOOLCHAIN", "local");
        // 注入运行时参数（非 GIT_REF、非 CMD_ 前缀）到环境变量
        if (request.runtimeParams() != null) {
            for (Map.Entry<String, String> entry : request.runtimeParams().entrySet()) {
                String key = entry.getKey();
                if (!"GIT_REF".equals(key) && !key.startsWith("CMD_")) {
                    env.put(key, entry.getValue() != null ? entry.getValue() : "");
                }
            }
        }
        String base = DnsNames.uniqueName(DnsNames.stepName(job.name(), job.kind().name()), usedNames);
        String command = job.command() == null ? "" : job.command();

        // ---- Step 类型分发：MULTI_STEP_KINDS → STEP_HANDLERS → GENERIC_IMAGES 兜底 ----
        // 1) 多步骤任务（注册表驱动）
        List<SubStepDef> multiSteps = MULTI_STEP_KINDS.get(job.kind());
        if (multiSteps != null) {
            return multiSteps.stream()
                    .map(sub -> buildStep(
                            sub.nameSuffix().isEmpty() ? base : base + sub.nameSuffix(),
                            sub.kindKey(), request, env, command,
                            sub.defaultImage(), taskResources,
                            sub.formatCredential(), sub.usesKanikoCache(), sub.usesKubeConfig()
                    ))
                    .toList();
        }

        // 2) 特殊逻辑任务（StepHandler 注册表驱动）
        StepHandler handler = STEP_HANDLERS.get(job.kind());
        if (handler != null) {
            return handler.create(base, request, remote, env, command, taskResources);
        }

        // 3) 通用兜底 — 仅镜像选型不同，新增种类只需加 GENERIC_IMAGES 一行
        String defaultImage = GENERIC_IMAGES.get(job.kind());
        String image = defaultImage != null
                ? resolveImage(job.kind().name(), request.taskImages(), defaultImage)
                : toolchainImageForJob(job);
        boolean formatCredential = FORMAT_KINDS.contains(job.kind());
        return List.of(buildStep(base, job.kind().name(), request, env, command, image, taskResources, formatCredential, false, false));
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

    /**
     * 解析任务的资源配置，优先级：taskResources 中的显式配置 &gt; 空（集群默认）。
     */
    private static TaskResourceSpec resolveResources(String kindValue, Map<String, TaskResourceSpec> taskResources) {
        if (taskResources != null) {
            TaskResourceSpec spec = taskResources.get(kindValue);
            if (spec != null && !spec.isEmpty()) {
                return spec;
            }
        }
        return TaskResourceSpec.EMPTY;
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

    /**
     * 通用 Step 构建方法：解析脚本、镜像、资源，收敛 {@code new CompiledStep(...)} 的重复。
     */
    private static CompiledStep buildStep(
            String name,
            String kindKey,
            CompileRequest request,
            Map<String, String> env,
            String command,
            String defaultImage,
            Map<String, TaskResourceSpec> taskResources,
            boolean formatCredential,
            boolean usesKanikoCache,
            boolean usesKubeConfig
    ) {
        String script = resolveScript(kindKey, request.taskScripts(), command);
        String image = resolveImage(kindKey, request.taskImages(), defaultImage);
        TaskResourceSpec res = resolveResources(kindKey, taskResources);
        return new CompiledStep(name, image, script, env,
                formatCredential, usesKanikoCache, usesKubeConfig,
                res.cpuRequest(), res.cpuLimit(), res.memoryRequest(), res.memoryLimit());
    }

    private static List<String> uniqueStepNames(List<CompiledStep> steps) {
        return steps.stream().map(CompiledStep::name).toList();
    }
}