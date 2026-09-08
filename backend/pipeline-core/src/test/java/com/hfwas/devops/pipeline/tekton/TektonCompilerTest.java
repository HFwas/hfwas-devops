package com.hfwas.devops.pipeline.tekton;

import com.hfwas.devops.pipeline.graph.PipelineGraphSpec;
import com.hfwas.devops.pipeline.graph.PipelineJobKind;
import com.hfwas.devops.pipeline.graph.PipelineJobSpec;
import com.hfwas.devops.pipeline.graph.PipelineStageSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TektonCompilerTest {

    private static final String IMAGE = "maven:3.9.9-eclipse-temurin-21";

    @Test
    void serialThreeJobsCompileToOneTaskWithThreeSteps() {
        PipelineGraphSpec graph = new PipelineGraphSpec(List.of(
                stage("clone", 0, List.of(job("clone", PipelineJobKind.CLONE, "", 0))),
                stage("build", 1, List.of(job("build", PipelineJobKind.BUILD, "mvn -B -DskipTests package", 0))),
                stage("test", 2, List.of(job("test", PipelineJobKind.TEST, "mvn -B test", 0)))
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
                6L, 8L, "https://github.com/acme/demo.git", "main", IMAGE, false, graph,
                "http://192.168.5.2:7890"
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
                1L, 8L, "", "main", IMAGE, false, graph, ""));
        assertEquals(1, compiled.tasks().getFirst().steps().size());
        assertEquals(IMAGE, compiled.tasks().getFirst().steps().getFirst().image());
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
    void lintExpandsToSemgrepAndSonar() {
        PipelineGraphSpec graph = new PipelineGraphSpec(List.of(
                stage("lint", 0, List.of(job("lint", PipelineJobKind.LINT, PipelineJobKind.LINT.defaultCommand(), 0)))
        ));
        List<CompiledStep> steps = TektonCompiler.compile(request(3L, graph, false)).tasks().getFirst().steps();
        assertEquals(2, steps.size());
        assertEquals(TektonCompiler.SEMGREP_IMAGE, steps.get(0).image());
        assertEquals(TektonCompiler.SONAR_IMAGE, steps.get(1).image());
        assertTrue(steps.get(1).script().contains("skip sonar"));
    }

    @Test
    void imageExpandsToKanikoCraneCosignWithCache() {
        PipelineGraphSpec graph = new PipelineGraphSpec(List.of(
                stage("img", 0, List.of(job("img", PipelineJobKind.IMAGE, PipelineJobKind.IMAGE.defaultCommand(), 0)))
        ));
        CompiledTekton compiled = TektonCompiler.compile(request(4L, graph, false));
        List<CompiledStep> steps = compiled.tasks().getFirst().steps();
        assertEquals(3, steps.size());
        assertEquals(TektonCompiler.KANIKO_IMAGE, steps.get(0).image());
        assertTrue(steps.get(0).usesKanikoCache());
        assertTrue(steps.get(0).script().contains("--cache-dir=/cache"));
        assertEquals(TektonCompiler.CRANE_IMAGE, steps.get(1).image());
        assertEquals(TektonCompiler.COSIGN_IMAGE, steps.get(2).image());
        assertTrue(steps.get(2).script().contains("skip cosign"));
        assertTrue(compiled.anyKanikoCache());
    }

    @Test
    void uploadDeployNotifyUseFixedImages() {
        assertEquals(TektonCompiler.UPLOAD_IMAGE, imageOf(PipelineJobKind.UPLOAD, "rclone copy ./ :s3:b"));
        assertEquals(TektonCompiler.DEPLOY_IMAGE, imageOf(PipelineJobKind.DEPLOY, "kubectl apply -f k8s/"));
        assertEquals(TektonCompiler.NOTIFY_IMAGE, imageOf(PipelineJobKind.NOTIFY, "curl -fsS https://example.com"));
        assertEquals(IMAGE, imageOf(PipelineJobKind.PUBLISH, "mvn -B deploy"));
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
                IMAGE,
                credential,
                graph,
                ""
        );
    }

    private static PipelineStageSpec stage(String name, int order, List<PipelineJobSpec> jobs) {
        return new PipelineStageSpec(null, name, order, jobs);
    }

    private static PipelineJobSpec job(String name, PipelineJobKind kind, String command, int order) {
        return new PipelineJobSpec(null, name, kind, command, order);
    }
}
