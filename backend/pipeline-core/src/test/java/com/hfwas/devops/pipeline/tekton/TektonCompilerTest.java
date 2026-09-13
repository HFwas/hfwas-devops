package com.hfwas.devops.pipeline.tekton;

import com.hfwas.devops.pipeline.graph.PipelineGraphSpec;
import com.hfwas.devops.pipeline.graph.PipelineJobKind;
import com.hfwas.devops.pipeline.graph.PipelineJobSpec;
import com.hfwas.devops.pipeline.graph.PipelineStageSpec;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TektonCompilerTest {

    private static final String IMAGE = "maven:3.9.9-eclipse-temurin-21";

    /**
     * 构建测试用的 taskScripts 映射，包含 CLONE / FORMAT / LINT_SONAR / IMAGE / IMAGE_COSIGN / DEPENDENCY_ANALYSIS 等模板。
     * 这些模板内容对应原硬编码脚本，现在由测试显式传入以验证模板替换机制。
     */
    private static Map<String, String> fullTaskScripts() {
        Map<String, String> s = new HashMap<>();

        s.put("CLONE", """
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
                elif [ -n "${GIT_EMBEDDED_AUTH:-}" ]; then
                  AUTH="${GIT_EMBEDDED_AUTH}@"
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
                """.stripIndent());

        s.put("FORMAT", """
                set -eu
                cd "$(workspaces.source.path)/src"
                eval "$(cat <<'HFWAS_USER'
                ${COMMAND}
                HFWAS_USER
                )"
                if [ -z "$(git status --porcelain)" ]; then
                  echo "no changes after formatter, skip commit"
                  exit 0
                fi
                git add .
                git config user.name "HFwas Pipeline"
                git config user.email "pipeline@hfwas.com"
                git commit -m "style: auto format code [skip ci]"
                git pull --rebase
                git push
                """.stripIndent());

        s.put("LINT_SONAR", """
                set -eu
                mkdir -p "$(workspaces.source.path)/src"
                cd "$(workspaces.source.path)/src"
                eval "$(cat <<'HFWAS_USER'
                ${COMMAND}
                HFWAS_USER
                )"
                : "${SONAR_HOST_URL:?SONAR_HOST_URL is required}"
                : "${SONAR_TOKEN:?SONAR_TOKEN is required}"
                : "${SONAR_PROJECT_KEY:=app}"
                sonar-scanner -Dsonar.host.url="$SONAR_HOST_URL" -Dsonar.token="$SONAR_TOKEN" -Dsonar.projectKey="$SONAR_PROJECT_KEY" -Dsonar.sources=.
                """.stripIndent());

        s.put("IMAGE", """
                set -eu
                mkdir -p "$(workspaces.source.path)/src"
                cd "$(workspaces.source.path)/src"
                eval "$(cat <<'HFWAS_USER'
                ${COMMAND}
                HFWAS_USER
                )"
                : "${DEST:?DEST is required}"
                : "${IMAGE_PLATFORMS:=linux/amd64,linux/arm64}"
                : "${DOCKERFILE:=Dockerfile}"
                n=0
                for p in $(echo "$IMAGE_PLATFORMS" | tr ',' ' '); do
                  p=$(echo "$p" | tr -d ' ')
                  [ -n "$p" ] || continue
                  n=$((n + 1))
                done
                if [ "$n" -eq 1 ]; then
                  buildah build --file "$DOCKERFILE" --platform "$IMAGE_PLATFORMS" -t "$DEST" .
                  buildah push "$DEST"
                else
                  buildah manifest create "$DEST"
                  for p in $(echo "$IMAGE_PLATFORMS" | tr ',' ' '); do
                    p=$(echo "$p" | tr -d ' ')
                    [ -n "$p" ] || continue
                    buildah build \\\\
                      --manifest "$DEST" \\\\
                      --platform "$p" \\\\
                      --file "$DOCKERFILE" \\\\
                      .
                  done
                  buildah manifest push --all "$DEST" "docker://$DEST"
                fi
                """.stripIndent());

        s.put("IMAGE_COSIGN", """
                set -eu
                : "${DEST:?DEST is required}"
                if [ -z "${COSIGN_PRIVATE_KEY:-}" ]; then
                  echo "skip cosign: COSIGN_PRIVATE_KEY empty"
                  exit 0
                fi
                printf '%s' "$COSIGN_PRIVATE_KEY" > /tmp/cosign.key
                cosign sign --key /tmp/cosign.key --yes "$DEST"
                """.stripIndent());

        s.put("DEPENDENCY_ANALYSIS", """
                set -eu
                mkdir -p "$(workspaces.source.path)/src"
                cd "$(workspaces.source.path)/src"
                ${COMMAND}

                if [ -n "${API_ENDPOINT:-}" ] && [ -n "${RUN_ID:-}" ]; then
                  SBOM_FILE=""
                  for f in target/sbom.json target/bom.json; do
                    if [ -f "$f" ]; then SBOM_FILE="$f"; break; fi
                  done
                  if [ -z "$SBOM_FILE" ]; then
                    SBOM_FILE=$(find . \\( -path '*/target/sbom.json' -o -path '*/target/bom.json' \\) 2>/dev/null | head -n 1 || true)
                  fi
                  if [ -n "$SBOM_FILE" ]; then
                    if ! command -v curl >/dev/null 2>&1; then
                      apt-get update -qq && DEBIAN_FRONTEND=noninteractive apt-get install -y -qq curl ca-certificates >/dev/null 2>&1 || true
                    fi
                    size=$(stat -f%z "$SBOM_FILE" 2>/dev/null || stat -c%s "$SBOM_FILE" 2>/dev/null || echo 0)
                    echo "uploading SBOM (${size} bytes) from ${SBOM_FILE} to ${API_ENDPOINT}"
                    curl -fsS -X POST "${API_ENDPOINT}/pipeline/runs/${RUN_ID}/artifacts" \\
                      -F "type=sbom" \\
                      -F "file=@${SBOM_FILE};filename=sbom.json" \\
                      --connect-timeout 10 \\
                      --max-time 60 && echo " SBOM uploaded" || echo " SBOM upload failed (non-fatal)"
                  else
                    echo "target/sbom.json not found, skip upload"
                  fi
                else
                  echo "API_ENDPOINT not configured, skip SBOM upload"
                fi
                """.stripIndent());

        return Map.copyOf(s);
    }

    @Test
    void serialThreeJobsCompileToOneTaskWithThreeSteps() {
        PipelineGraphSpec graph = new PipelineGraphSpec(List.of(
                stage("clone", 0, List.of(job("clone", PipelineJobKind.CLONE, "", 0))),
                stage("build", 1, List.of(toolchainJob("build", PipelineJobKind.BUILD, "mvn -B -DskipTests package",
                        "JAVA_MAVEN", "21", "3.9", 0))),
                stage("test", 2, List.of(toolchainJob("test", PipelineJobKind.TEST, "mvn -B test",
                        "JAVA_MAVEN", "21", "3.9", 0)))
        ));
        CompiledTekton compiled = TektonCompiler.compile(request(99L, graph, true, fullTaskScripts(), null));

        assertEquals(TektonMode.TASK, compiled.mode());
        assertEquals("hfwas-99", compiled.name());
        assertFalse(compiled.needsPvc());
        assertEquals(1, compiled.tasks().size());
        List<CompiledStep> steps = compiled.tasks().getFirst().steps();
        assertEquals(3, steps.size());
        assertEquals("alpine/git:2.45.2", steps.get(0).image());
        assertTrue(steps.get(0).usesGitSecret());
        assertFalse(steps.get(0).script().contains("ghp_secret"));
        assertTrue(steps.get(0).script().contains("GIT_USERNAME"));
        assertTrue(steps.get(0).script().contains("${GIT_SCHEME}://"));
        assertTrue(steps.get(0).script().contains("http.version HTTP/1.1"));
        assertTrue(steps.get(0).script().contains("HFWAS_COMMIT"));
        assertEquals("https", steps.get(0).env().get("GIT_SCHEME"));
        assertEquals(IMAGE, steps.get(1).image());
        assertTrue(steps.get(1).script().contains("mvn -B -DskipTests package"));
        assertTrue(steps.get(1).script().contains("mkdir -p"));
        assertEquals(IMAGE, steps.get(2).image());
        assertTrue(steps.get(2).env().containsKey("GOTOOLCHAIN"));
        assertEquals("local", steps.get(2).env().get("GOTOOLCHAIN"));
    }

    @Test
    void cloneInjectsGitHttpProxy() {
        PipelineGraphSpec graph = new PipelineGraphSpec(List.of(
                stage("clone", 0, List.of(job("clone", PipelineJobKind.CLONE, "", 0)))
        ));
        CompiledStep step = TektonCompiler.compile(new CompileRequest(
                6L, 8L, "https://github.com/acme/demo.git", "main", false, graph,
                "http://192.168.5.2:7890", Map.of(), fullTaskScripts(), Map.of(), null, null
        )).tasks().getFirst().steps().getFirst();
        assertEquals("http://192.168.5.2:7890", step.env().get("GIT_HTTP_PROXY"));
    }

    @Test
    void parallelColumnCompilesToPipelineWithSharedWorkspace() {
        PipelineGraphSpec graph = new PipelineGraphSpec(List.of(
                stage("clone", 0, List.of(job("clone", PipelineJobKind.CLONE, "", 0))),
                stage("verify", 1, List.of(
                        job("unit", PipelineJobKind.TEST, "mvn -B test", 0),
                        job("lint", PipelineJobKind.CUSTOM, "mvn -B -DskipTests verify", 1)
                ))
        ));
        CompiledTekton compiled = TektonCompiler.compile(request(7L, graph, false, fullTaskScripts(), null));

        assertEquals(TektonMode.PIPELINE, compiled.mode());
        assertTrue(compiled.needsPvc());
        assertEquals(3, compiled.tasks().size());
        assertEquals(3, compiled.pipelineTasks().size());
        CompiledPipelineTask clone = compiled.pipelineTasks().getFirst();
        assertEquals("clone", clone.name());
        assertTrue(clone.runAfter().isEmpty());
        List<String> followers = compiled.pipelineTasks().stream()
                .filter(item -> !item.runAfter().isEmpty())
                .map(CompiledPipelineTask::name)
                .toList();
        assertEquals(2, followers.size());
        compiled.pipelineTasks().stream()
                .filter(item -> !item.runAfter().isEmpty())
                .forEach(item -> assertEquals(List.of("clone"), item.runAfter()));
    }

    @Test
    void objectNameIsDnsLabel() {
        assertEquals("hfwas-1234567890123456789", DnsNames.objectName(1234567890123456789L));
        assertTrue(DnsNames.stepName("Build Job!").matches("[a-z0-9]([-a-z0-9]*[a-z0-9])?"));
        assertEquals("clone", DnsNames.stepName("代码克隆", "CLONE"));
    }

    @Test
    void masksSecretInLogs() {
        String masked = LogMasker.mask("fatal: https://x-access-token:ghp_secret@github.com/acme/demo.git", "ghp_secret");
        assertFalse(masked.contains("ghp_secret"));
        assertTrue(masked.contains("****"));
    }

    @Test
    void shellOnlySkipsGitRemote() {
        PipelineGraphSpec graph = new PipelineGraphSpec(List.of(
                stage("run", 0, List.of(job("echo", PipelineJobKind.CUSTOM, "echo ok", 0)))
        ));
        CompiledTekton compiled = TektonCompiler.compile(new CompileRequest(
                1L, 8L, "", "main", false, graph, "", Map.of(), Map.of(), Map.of(), null, null));
        assertEquals(1, compiled.tasks().getFirst().steps().size());
        assertNotNull(compiled.tasks().getFirst().steps().getFirst().image());
        assertTrue(compiled.tasks().getFirst().steps().getFirst().script().contains("mkdir -p"));
    }

    @Test
    void scanUsesTrivyImage() {
        PipelineGraphSpec graph = new PipelineGraphSpec(List.of(
                stage("scan", 0, List.of(job("s", PipelineJobKind.SCAN, "trivy fs .", 0)))
        ));
        CompiledStep step = TektonCompiler.compile(request(2L, graph, false)).tasks().getFirst().steps().getFirst();
        assertEquals(TektonCompiler.SCAN_IMAGE, step.image());
    }

    @Test
    void lintSemgrepUsesSemgrepImageAndUserCli() {
        PipelineGraphSpec graph = new PipelineGraphSpec(List.of(
                stage("lint", 0, List.of(job("sg", PipelineJobKind.LINT_SEMGREP,
                        "semgrep scan --error --config=auto .", 0)))
        ));
        List<CompiledStep> steps = TektonCompiler.compile(request(3L, graph, false)).tasks().getFirst().steps();
        assertEquals(1, steps.size());
        assertEquals(TektonCompiler.SEMGREP_IMAGE, steps.getFirst().image());
        assertTrue(steps.getFirst().script().contains("semgrep scan --error --config=auto ."));
        assertFalse(steps.getFirst().script().contains("LINT_SKIP_SEMGREP"));
        assertFalse(steps.getFirst().script().contains("sonar-scanner"));
    }

    @Test
    void lintSonarUsesSonarImageAndContainsSonarEnvChecks() {
        PipelineGraphSpec graph = new PipelineGraphSpec(List.of(
                stage("lint", 0, List.of(job("sn", PipelineJobKind.LINT_SONAR,
                        "export SONAR_HOST_URL=https://sonar.example.com\n"
                                + "export SONAR_TOKEN=\n"
                                + "export SONAR_PROJECT_KEY=app", 0)))
        ));
        List<CompiledStep> steps = TektonCompiler.compile(request(3L, graph, false, fullTaskScripts(), null))
                .tasks().getFirst().steps();
        assertEquals(1, steps.size());
        assertEquals(TektonCompiler.SONAR_IMAGE, steps.getFirst().image());
        assertTrue(steps.getFirst().script().contains("SONAR_HOST_URL:?SONAR_HOST_URL is required"));
        assertTrue(steps.getFirst().script().contains("SONAR_TOKEN:?SONAR_TOKEN is required"));
        assertTrue(steps.getFirst().script().contains("sonar-scanner"));
    }

    @Test
    void imageExpandsToBuildahAndCosign() {
        PipelineGraphSpec graph = new PipelineGraphSpec(List.of(
                stage("img", 0, List.of(job("img", PipelineJobKind.IMAGE,
                        "export DEST=registry.example.com/app:tag\n"
                                + "export IMAGE_PLATFORMS=linux/amd64,linux/arm64\n"
                                + "export DOCKERFILE=Dockerfile", 0)))
        ));
        CompiledTekton compiled = TektonCompiler.compile(request(4L, graph, false, fullTaskScripts(), null));
        List<CompiledStep> steps = compiled.tasks().getFirst().steps();
        assertEquals(2, steps.size());
        assertEquals(TektonCompiler.BUILDAH_IMAGE, steps.get(0).image());
        assertTrue(steps.get(0).script().contains("buildah manifest create"));
        assertTrue(steps.get(0).script().contains("linux/amd64,linux/arm64"));
        assertEquals(TektonCompiler.COSIGN_IMAGE, steps.get(1).image());
        assertTrue(steps.get(1).script().contains("skip cosign"));
        assertFalse(compiled.anyKanikoCache());
    }

    @Test
    void uploadDeployNotifyUseFixedImages() {
        assertEquals(TektonCompiler.UPLOAD_IMAGE, imageOf(PipelineJobKind.UPLOAD, "rclone copy ./ :s3:b"));
        assertEquals(TektonCompiler.DEPLOY_IMAGE, imageOf(PipelineJobKind.DEPLOY, "kubectl apply -f k8s/"));
        assertEquals(TektonCompiler.NOTIFY_IMAGE, imageOf(PipelineJobKind.NOTIFY, "curl -fsS https://example.com"));
        assertNotNull(imageOf(PipelineJobKind.PUBLISH, "mvn -B deploy"));
    }

    @Test
    void formatIncludesCommitPushAndSkipCi() {
        PipelineGraphSpec graph = new PipelineGraphSpec(List.of(
                stage("fmt", 0, List.of(job("fmt", PipelineJobKind.FORMAT,
                        "npx prettier --write .", 0)))
        ));
        CompiledStep step = TektonCompiler.compile(request(10L, graph, false, fullTaskScripts(), null))
                .tasks().getFirst().steps().getFirst();
        assertTrue(step.script().contains("git add ."));
        assertTrue(step.script().contains("commit -m"), "should contain commit command");
        assertTrue(step.script().contains("git push"));
        assertTrue(step.script().contains("[skip ci]"));
        assertTrue(step.script().contains("npx prettier --write ."));
        assertTrue(step.script().contains("git status --porcelain"));
    }

    @Test
    void formatUsesGitSecret() {
        PipelineGraphSpec graph = new PipelineGraphSpec(List.of(
                stage("fmt", 0, List.of(job("fmt", PipelineJobKind.FORMAT,
                        "npx prettier --write .", 0)))
        ));
        CompiledStep step = TektonCompiler.compile(request(11L, graph, true, fullTaskScripts(), null))
                .tasks().getFirst().steps().getFirst();
        assertTrue(step.usesGitSecret());
    }

    @Test
    void formatUsesToolchainImage() {
        PipelineGraphSpec graph = new PipelineGraphSpec(List.of(
                stage("fmt", 0, List.of(toolchainJob("fmt", PipelineJobKind.FORMAT,
                        "npx prettier --write .", "NODE", "22", "NPM", 0)))
        ));
        CompiledStep step = TektonCompiler.compile(new CompileRequest(
                12L, 8L, "https://github.com/acme/demo.git", "main", false, graph, "",
                Map.of(), fullTaskScripts(), Map.of(), null, null))
                .tasks().getFirst().steps().getFirst();
        assertTrue(step.image().contains("node"), "FORMAT with NODE/NPM should use a node image, got: " + step.image());
    }

    @Test
    void formatSkipsCommitWhenNoChanges() {
        PipelineGraphSpec graph = new PipelineGraphSpec(List.of(
                stage("fmt", 0, List.of(job("fmt", PipelineJobKind.FORMAT,
                        "black .", 0)))
        ));
        CompiledStep step = TektonCompiler.compile(request(13L, graph, false, fullTaskScripts(), null))
                .tasks().getFirst().steps().getFirst();
        assertTrue(step.script().contains("no changes after formatter"));
        assertTrue(step.script().contains("black ."));
        assertTrue(step.script().contains("git pull --rebase"));
    }

    @Test
    void dependencyAnalysisUsesCdxgenImage() {
        PipelineGraphSpec graph = new PipelineGraphSpec(List.of(
                stage("dep", 0, List.of(job("dep", PipelineJobKind.DEPENDENCY_ANALYSIS,
                        "cdxgen -o target/sbom.json -t cyclonedx:json", 0)))
        ));
        CompiledStep step = TektonCompiler.compile(request(14L, graph, false, fullTaskScripts(), null))
                .tasks().getFirst().steps().getFirst();
        assertEquals(TektonCompiler.CDXGEN_IMAGE, step.image());
        assertTrue(step.script().contains("cdxgen -o target/sbom.json"));
    }

    @Test
    void dependencyAnalysisIncludesUploadScriptWhenApiEndpointSet() {
        PipelineGraphSpec graph = new PipelineGraphSpec(List.of(
                stage("dep", 0, List.of(job("dep", PipelineJobKind.DEPENDENCY_ANALYSIS,
                        "mvn org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom "
                                + "-Dcyclonedx.outputFormat=json -DoutputName=sbom", 0)))
        ));
        String apiEndpoint = "http://host.docker.internal:8089";
        CompiledStep step = TektonCompiler.compile(requestWithApi(15L, graph, false, apiEndpoint, fullTaskScripts()))
                .tasks().getFirst().steps().getFirst();
        assertTrue(step.script().contains("target/sbom.json"), "should check for sbom.json");
        assertTrue(step.script().contains("API_ENDPOINT"), "should reference API_ENDPOINT env var");
        assertTrue(step.script().contains("uploading SBOM"), "should have upload log");
        assertTrue(step.script().contains("curl -fsS -X POST"), "should contain curl upload");
        assertTrue(step.script().contains("RUN_ID"), "should inject RUN_ID env");
        assertEquals(apiEndpoint, step.env().get("API_ENDPOINT"));
        assertEquals("15", step.env().get("RUN_ID"));
    }

    @Test
    void dependencyAnalysisSkipsUploadWhenNoApiEndpoint() {
        PipelineGraphSpec graph = new PipelineGraphSpec(List.of(
                stage("dep", 0, List.of(job("dep", PipelineJobKind.DEPENDENCY_ANALYSIS,
                        "cdxgen -o target/sbom.json -t cyclonedx:json", 0)))
        ));
        CompiledStep step = TektonCompiler.compile(request(16L, graph, false, fullTaskScripts(), null))
                .tasks().getFirst().steps().getFirst();
        // 模板包含上传脚本，但运行时被 if [ -n "${API_ENDPOINT:-}" ] 守卫
        assertTrue(step.script().contains("if [ -n \"${API_ENDPOINT:-}\" ]"),
                "should have runtime guard for API_ENDPOINT");
        assertFalse(step.env().containsKey("API_ENDPOINT"), "should not set API_ENDPOINT env when not configured");
    }

    /** 验证 DEFAULT_TEMPLATE 在不传 taskScripts 时正常使用 */
    @Test
    void templateSubstitutionReplacesCommand() {
        PipelineGraphSpec graph = new PipelineGraphSpec(List.of(
                stage("run", 0, List.of(job("echo", PipelineJobKind.BUILD, "echo hello world", 0)))
        ));
        CompiledStep step = TektonCompiler.compile(new CompileRequest(
                20L, 8L, "", "main", false, graph, "", Map.of(), null, Map.of(), null, null))
                .tasks().getFirst().steps().getFirst();
        assertTrue(step.script().contains("echo hello world"), "${COMMAND} should be replaced");
        assertTrue(step.script().contains("mkdir -p"), "DEFAULT_TEMPLATE should include mkdir");
    }

    /** 验证 ${COMMAND} 保留字面量，不会被 bash 当作变量解析 */
    @Test
    void commandPlaceholderNotBashVariable() {
        String script = TektonCompiler.compile(new CompileRequest(
                21L, 8L, "", "main", false,
                new PipelineGraphSpec(List.of(stage("run", 0, List.of(job("echo", PipelineJobKind.BUILD, "echo ok", 0))))),
                "", Map.of(), Map.of(), Map.of(), null, null))
                .tasks().getFirst().steps().getFirst().script();
        // ${COMMAND} 已经在编译期被替换为 "echo ok"，脚本里不应该再有 ${COMMAND}
        assertFalse(script.contains("${COMMAND}"), "${COMMAND} should be replaced at compile time");
    }

    private static String imageOf(PipelineJobKind kind, String command) {
        PipelineGraphSpec graph = new PipelineGraphSpec(List.of(
                stage("s", 0, List.of(job("j", kind, command, 0)))
        ));
        return TektonCompiler.compile(request(5L, graph, false)).tasks().getFirst().steps().getFirst().image();
    }

    private static CompileRequest request(long runId, PipelineGraphSpec graph, boolean credential) {
        return request(runId, graph, credential, Map.of(), null);
    }

    private static CompileRequest request(long runId, PipelineGraphSpec graph, boolean credential,
                                          Map<String, String> taskScripts, String apiEndpoint) {
        return new CompileRequest(
                runId, 8L, "https://github.com/acme/demo.git", "main", credential, graph,
                "", Map.of(), taskScripts, Map.of(), apiEndpoint, null
        );
    }

    private static CompileRequest requestWithApi(long runId, PipelineGraphSpec graph, boolean credential,
                                                  String apiEndpoint, Map<String, String> taskScripts) {
        return new CompileRequest(
                runId, 8L, "https://github.com/acme/demo.git", "main", credential, graph,
                "", Map.of(), taskScripts, Map.of(), apiEndpoint, null
        );
    }

    private static PipelineStageSpec stage(String name, int order, List<PipelineJobSpec> jobs) {
        return new PipelineStageSpec(null, name, order, jobs);
    }

    private static PipelineJobSpec job(String name, PipelineJobKind kind, String command, int order) {
        return new PipelineJobSpec(null, name, kind, command, null, null, null, order);
    }

    private static PipelineJobSpec toolchainJob(String name, PipelineJobKind kind, String command,
                                                 String stack, String runtime, String tool, int order) {
        return new PipelineJobSpec(null, name, kind, command, stack, runtime, tool, order);
    }
}