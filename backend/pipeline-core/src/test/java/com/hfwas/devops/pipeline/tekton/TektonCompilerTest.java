package com.hfwas.devops.pipeline.tekton;

import com.hfwas.devops.pipeline.graph.PipelineGraphSpec;
import com.hfwas.devops.pipeline.graph.PipelineJobKind;
import com.hfwas.devops.pipeline.graph.PipelineJobSpec;
import com.hfwas.devops.pipeline.graph.PipelineStageSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TektonCompilerTest {

    private static final String IMAGE = "maven:3.9.9-eclipse-temurin-21";

    @Test
    void serialThreeJobsCompileToOneTaskWithThreeSteps() {
        PipelineGraphSpec graph = new PipelineGraphSpec(List.of(
                stage("clone", 0, List.of(job("clone", PipelineJobKind.CLONE, "", 0))),
                stage("build", 1, List.of(toolchainJob("build", PipelineJobKind.BUILD, "mvn -B -DskipTests package",
                        "JAVA_MAVEN", "21", "3.9", 0))),
                stage("test", 2, List.of(toolchainJob("test", PipelineJobKind.TEST, "mvn -B test",
                        "JAVA_MAVEN", "21", "3.9", 0)))
        ));
        CompiledTekton compiled = TektonCompiler.compile(request(99L, graph, true));

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
                "http://192.168.5.2:7890", Map.of(), null
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
        CompiledTekton compiled = TektonCompiler.compile(request(7L, graph, false));

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
                1L, 8L, "", "main", false, graph, "", Map.of()));
        assertEquals(1, compiled.tasks().getFirst().steps().size());
        assertTrue(compiled.tasks().getFirst().steps().getFirst().image() != null);
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
    void lintSonarUsesSonarImageAndFailsWhenTokenEmpty() {
        PipelineGraphSpec graph = new PipelineGraphSpec(List.of(
                stage("lint", 0, List.of(job("sn", PipelineJobKind.LINT_SONAR,
                        "export SONAR_HOST_URL=https://sonar.example.com\n"
                                + "export SONAR_TOKEN=\n"
                                + "export SONAR_PROJECT_KEY=app", 0)))
        ));
        List<CompiledStep> steps = TektonCompiler.compile(request(3L, graph, false)).tasks().getFirst().steps();
        assertEquals(1, steps.size());
        assertEquals(TektonCompiler.SONAR_IMAGE, steps.getFirst().image());
        assertTrue(steps.getFirst().script().contains("SONAR_HOST_URL:?SONAR_HOST_URL is required"));
        assertTrue(steps.getFirst().script().contains("SONAR_TOKEN:?SONAR_TOKEN is required"));
        assertTrue(steps.getFirst().script().contains("sonar-scanner"));
        assertFalse(steps.getFirst().script().contains("skip sonar"));
    }

    @Test
    void imageExpandsToBuildahAndCosign() {
        PipelineGraphSpec graph = new PipelineGraphSpec(List.of(
                stage("img", 0, List.of(job("img", PipelineJobKind.IMAGE,
                        "export DEST=registry.example.com/app:tag\n"
                                + "export IMAGE_PLATFORMS=linux/amd64,linux/arm64\n"
                                + "export DOCKERFILE=Dockerfile", 0)))
        ));
        CompiledTekton compiled = TektonCompiler.compile(request(4L, graph, false));
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
        CompiledStep step = TektonCompiler.compile(request(10L, graph, false))
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
        CompiledStep step = TektonCompiler.compile(request(11L, graph, true))
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
                12L, 8L, "https://github.com/acme/demo.git", "main", false, graph, "", Map.of(), null))
                .tasks().getFirst().steps().getFirst();
        assertTrue(step.image().contains("node"), "FORMAT with NODE/NPM should use a node image, got: " + step.image());
    }

    @Test
    void formatSkipsCommitWhenNoChanges() {
        PipelineGraphSpec graph = new PipelineGraphSpec(List.of(
                stage("fmt", 0, List.of(job("fmt", PipelineJobKind.FORMAT,
                        "black .", 0)))
        ));
        CompiledStep step = TektonCompiler.compile(request(13L, graph, false))
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
        CompiledStep step = TektonCompiler.compile(request(14L, graph, false))
                .tasks().getFirst().steps().getFirst();
        assertEquals(TektonCompiler.CDXGEN_IMAGE, step.image());
        assertTrue(step.script().contains("cdxgen -o target/sbom.json"));
    }

    @Test
    void dependencyAnalysisIncludesUploadScriptWhenApiEndpointSet() {
        PipelineGraphSpec graph = new PipelineGraphSpec(List.of(
                stage("dep", 0, List.of(job("dep", PipelineJobKind.DEPENDENCY_ANALYSIS,
                        "mvn org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom "
                                + "-Dcyclonedx.outputFormat=json -DoutputName=sbom -q", 0)))
        ));
        String apiEndpoint = "http://host.docker.internal:8089";
        CompiledStep step = TektonCompiler.compile(requestWithApi(15L, graph, false, apiEndpoint))
                .tasks().getFirst().steps().getFirst();
        assertTrue(step.script().contains("target/sbom.json"), "should check for sbom.json");
        assertTrue(step.script().contains(apiEndpoint), "should contain api endpoint");
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
        CompiledStep step = TektonCompiler.compile(request(16L, graph, false))
                .tasks().getFirst().steps().getFirst();
        assertFalse(step.script().contains("curl -fsS"), "should not contain curl when no api endpoint");
        assertFalse(step.script().contains("uploading SBOM"), "should not contain upload when no api endpoint");
        assertFalse(step.env().containsKey("API_ENDPOINT"));
    }

    private static String imageOf(PipelineJobKind kind, String command) {
        PipelineGraphSpec graph = new PipelineGraphSpec(List.of(
                stage("s", 0, List.of(job("j", kind, command, 0)))
        ));
        return TektonCompiler.compile(request(5L, graph, false)).tasks().getFirst().steps().getFirst().image();
    }

    private static CompileRequest request(long runId, PipelineGraphSpec graph, boolean credential) {
        return new CompileRequest(
                runId,
                8L,
                "https://github.com/acme/demo.git",
                "main",
                credential,
                graph,
                "",
                Map.of(),
                null
        );
    }

    private static CompileRequest requestWithApi(long runId, PipelineGraphSpec graph, boolean credential, String apiEndpoint) {
        return new CompileRequest(
                runId,
                8L,
                "https://github.com/acme/demo.git",
                "main",
                credential,
                graph,
                "",
                Map.of(),
                apiEndpoint
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
