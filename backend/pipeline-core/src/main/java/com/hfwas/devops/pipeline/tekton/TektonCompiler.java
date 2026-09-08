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
    public static final String SCAN_IMAGE = "aquasec/trivy:0.66.0";
    public static final String UPLOAD_IMAGE = "rclone/rclone:1.68.2";
    public static final String DEPLOY_IMAGE = "bitnami/kubectl:1.31.4";
    public static final String NOTIFY_IMAGE = "curlimages/curl:8.11.1";
    public static final String KANIKO_IMAGE = "gcr.io/kaniko-project/executor:v1.23.2-debug";
    public static final String CRANE_IMAGE = "gcr.io/go-containerregistry/crane:v0.20.3";
    public static final String COSIGN_IMAGE = "ghcr.io/sigstore/cosign:v2.4.3";
    public static final String SEMGREP_IMAGE = "semgrep/semgrep:1.97.0";
    public static final String SONAR_IMAGE = "sonarsource/sonar-scanner-cli:11.2";
    public static final String WORKSPACE = "source";
    public static final String CACHE_WORKSPACE = "cache";
    public static final String SOURCE_DIR = "src";

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
        if (job.kind() == PipelineJobKind.CLONE) {
            if (remote == null) {
                throw BizException.of(ResultCode.BAD_REQUEST, "仓库地址不能为空");
            }
            env.put("GIT_SCHEME", remote.scheme());
            env.put("GIT_HOST", remote.host());
            env.put("GIT_PATH", remote.path());
            env.put("GIT_REF", request.gitRef() == null || request.gitRef().isBlank() ? "main" : request.gitRef());
            if (request.gitHttpProxy() != null && !request.gitHttpProxy().isBlank()) {
                env.put("GIT_HTTP_PROXY", request.gitHttpProxy().trim());
            }
            String script = """
                    set -eu
                    cd "$(workspaces.source.path)"
                    git config --global http.version HTTP/1.1
                    git config --global http.postBuffer 524288000
                    if [ -n "${GIT_HTTP_PROXY:-}" ]; then
                      git config --global http.proxy "${GIT_HTTP_PROXY}"
                      git config --global https.proxy "${GIT_HTTP_PROXY}"
                    fi
                    AUTH=""
                    if [ -n "${GIT_USERNAME:-}" ]; then
                      AUTH="${GIT_USERNAME}:${GIT_PASSWORD}@"
                    fi
                    URL="${GIT_SCHEME}://${AUTH}${GIT_HOST}/${GIT_PATH}"
                    attempt=1
                    until git clone --depth 1 --branch "${GIT_REF}" "$URL" src; do
                      attempt=$((attempt + 1))
                      if [ "$attempt" -gt 3 ]; then
                        echo "git clone failed after 3 attempts"
                        exit 1
                      fi
                      echo "git clone retry ${attempt}/3 ..."
                      rm -rf src
                      sleep $((attempt * 2))
                    done
                    echo "HFWAS_GIT_REF=${GIT_REF}"
                    echo "HFWAS_COMMIT=$(git -C src rev-parse HEAD)"
                    """.stripIndent();
            return List.of(new CompiledStep(base, CLONE_IMAGE, script, env, request.hasCredential(), false));
        }
        String command = job.command() == null ? "" : job.command();
        if (job.kind() == PipelineJobKind.IMAGE) {
            return List.of(
                    new CompiledStep(base, KANIKO_IMAGE, imageKanikoScript(command), env, false, true),
                    new CompiledStep(base + "-crane", CRANE_IMAGE, imageCraneScript(command), env, false, false),
                    new CompiledStep(base + "-cosign", COSIGN_IMAGE, imageCosignScript(command), env, false, false)
            );
        }
        if (job.kind() == PipelineJobKind.LINT_SONAR) {
            return List.of(new CompiledStep(base, SONAR_IMAGE, lintSonarScript(command), env, false, false));
        }
        String image = switch (job.kind()) {
            case LINT_SEMGREP -> SEMGREP_IMAGE;
            case SCAN -> SCAN_IMAGE;
            case UPLOAD -> UPLOAD_IMAGE;
            case DEPLOY -> DEPLOY_IMAGE;
            case NOTIFY -> NOTIFY_IMAGE;
            default -> request.stackImage();
        };
        String script = commandScript(command);
        return List.of(new CompiledStep(base, image, script, env, false, false));
    }

    private static String commandScript(String command) {
        return """
                set -eu
                mkdir -p "$(workspaces.source.path)/src"
                cd "$(workspaces.source.path)/src"
                %s
                """.formatted(command).stripIndent();
    }

    private static String evalPrefix(String command) {
        return """
                set -eu
                mkdir -p "$(workspaces.source.path)/src"
                cd "$(workspaces.source.path)/src"
                eval "$(cat <<'HFWAS_USER'
                %s
                HFWAS_USER
                )"
                """.formatted(command).stripIndent();
    }

    private static String imageKanikoScript(String command) {
        return evalPrefix(command) + """
                
                : "${DEST:?DEST is required}"
                : "${IMAGE_PLATFORMS:=linux/amd64}"
                : "${DOCKERFILE:=Dockerfile}"
                n=0
                for p in $(echo "$IMAGE_PLATFORMS" | tr ',' ' '); do
                  n=$((n + 1))
                done
                for p in $(echo "$IMAGE_PLATFORMS" | tr ',' ' '); do
                  p=$(echo "$p" | tr -d ' ')
                  [ -n "$p" ] || continue
                  arch="${p##*/}"
                  if [ "$n" -eq 1 ]; then
                    dest="$DEST"
                  else
                    dest="${DEST}-${arch}"
                  fi
                  /kaniko/executor --context=dir://. --dockerfile="$DOCKERFILE" --custom-platform="$p" --destination="$dest" --cache=true --cache-dir=/cache
                done
                """.stripIndent();
    }

    private static String imageCraneScript(String command) {
        return evalPrefix(command) + """
                
                : "${DEST:?DEST is required}"
                : "${IMAGE_PLATFORMS:=linux/amd64}"
                n=0
                for p in $(echo "$IMAGE_PLATFORMS" | tr ',' ' '); do
                  n=$((n + 1))
                done
                if [ "$n" -le 1 ]; then
                  echo skip crane: single platform
                  exit 0
                fi
                args=""
                for p in $(echo "$IMAGE_PLATFORMS" | tr ',' ' '); do
                  p=$(echo "$p" | tr -d ' ')
                  [ -n "$p" ] || continue
                  arch="${p##*/}"
                  args="$args ${DEST}-${arch}"
                done
                crane index append -t "$DEST" $args
                """.stripIndent();
    }

    private static String imageCosignScript(String command) {
        return evalPrefix(command) + """
                
                : "${DEST:?DEST is required}"
                if [ -z "${COSIGN_PRIVATE_KEY:-}" ]; then
                  echo skip cosign: COSIGN_PRIVATE_KEY empty
                  exit 0
                fi
                printf '%s' "$COSIGN_PRIVATE_KEY" > /tmp/cosign.key
                cosign sign --key /tmp/cosign.key --yes "$DEST"
                """.stripIndent();
    }

    private static String lintSonarScript(String command) {
        return evalPrefix(command) + """
                
                : "${SONAR_HOST_URL:?SONAR_HOST_URL is required}"
                : "${SONAR_TOKEN:?SONAR_TOKEN is required}"
                : "${SONAR_PROJECT_KEY:=app}"
                sonar-scanner -Dsonar.host.url="$SONAR_HOST_URL" -Dsonar.token="$SONAR_TOKEN" -Dsonar.projectKey="$SONAR_PROJECT_KEY" -Dsonar.sources=.
                """.stripIndent();
    }

    private static List<String> uniqueStepNames(List<CompiledStep> steps) {
        return steps.stream().map(CompiledStep::name).toList();
    }
}
