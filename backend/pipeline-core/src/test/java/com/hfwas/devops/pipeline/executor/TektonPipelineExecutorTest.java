package com.hfwas.devops.pipeline.executor;

import io.fabric8.kubernetes.api.model.ContainerStateRunning;
import io.fabric8.kubernetes.api.model.ContainerStateTerminated;
import io.fabric8.tekton.v1.StepState;
import io.fabric8.tekton.v1.StepStateBuilder;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TektonPipelineExecutorTest {

    @Test
    void stepDiagnosticsIncludesImagePullWaiting() {
        StepState waiting = new StepStateBuilder()
                .withName("step")
                .withNewWaiting("ImagePullBackOff", "Back-off pulling image \"alpine/git:2.45.2\"")
                .build();
        String text = TektonPipelineExecutor.stepDiagnostics(List.of(waiting));
        assertTrue(text.contains("ImagePullBackOff"));
        assertTrue(text.contains("alpine/git:2.45.2"));
    }

    @Test
    void mergeStepStatusKeepsWaitingJobsQueued() {
        StepState waiting = new StepStateBuilder()
                .withName("test")
                .withNewWaiting("PodInitializing", "waiting to start")
                .build();
        StepState running = new StepState();
        running.setName("build");
        running.setRunning(new ContainerStateRunning());
        StepState done = new StepState();
        done.setName("custom");
        ContainerStateTerminated terminated = new ContainerStateTerminated();
        terminated.setExitCode(0);
        done.setTerminated(terminated);

        assertEquals("QUEUED", TektonPipelineExecutor.mergeStepStatus(List.of(waiting)));
        assertEquals("RUNNING", TektonPipelineExecutor.mergeStepStatus(List.of(running)));
        assertEquals("SUCCEEDED", TektonPipelineExecutor.mergeStepStatus(List.of(done)));
        assertEquals("RUNNING", TektonPipelineExecutor.mergeStepStatus(List.of(done, running)));
        assertEquals("QUEUED", TektonPipelineExecutor.mergeStepStatus(List.of()));
    }

    @Test
    void jobTimesUseTektonStepTimestamps() {
        StepState done = terminatedStep("clone", "2026-09-13T02:30:00Z", "2026-09-13T02:30:22Z");
        assertEquals(LocalDateTime.of(2026, 9, 13, 2, 30, 0),
                TektonPipelineExecutor.resolveJobStartedAt("SUCCEEDED", List.of(done), null));
        assertEquals(LocalDateTime.of(2026, 9, 13, 2, 30, 22),
                TektonPipelineExecutor.resolveJobFinishedAt("SUCCEEDED", List.of(done), null));
    }

    @Test
    void jobFinishedAtDoesNotMoveOnLaterPoll() {
        StepState done = terminatedStep("clone", "2026-09-13T02:30:00.123456789Z", "2026-09-13T02:30:22.500Z");
        LocalDateTime previous = LocalDateTime.of(2026, 9, 13, 2, 30, 22, 500_000_000);
        assertEquals(previous, TektonPipelineExecutor.resolveJobFinishedAt("SUCCEEDED", List.of(done), previous));
        assertEquals(previous, TektonPipelineExecutor.resolveJobFinishedAt(
                "SUCCEEDED", List.of(done), LocalDateTime.of(2026, 9, 13, 2, 35, 0)));
    }

    @Test
    void runningJobUsesRunningStartedAtAndNoFinish() {
        StepState running = new StepState();
        running.setName("dep");
        ContainerStateRunning state = new ContainerStateRunning();
        state.setStartedAt("2026-09-13T02:30:22Z");
        running.setRunning(state);
        assertEquals(LocalDateTime.of(2026, 9, 13, 2, 30, 22),
                TektonPipelineExecutor.resolveJobStartedAt("RUNNING", List.of(running), null));
        assertNull(TektonPipelineExecutor.resolveJobFinishedAt("RUNNING", List.of(running), null));
    }

    @Test
    void queuedClearsJobTimes() {
        LocalDateTime previous = LocalDateTime.of(2026, 9, 13, 2, 30, 0);
        assertNull(TektonPipelineExecutor.resolveJobStartedAt("QUEUED", List.of(), previous));
        assertNull(TektonPipelineExecutor.resolveJobFinishedAt("QUEUED", List.of(), previous));
    }

    @Test
    void terminatedWithoutFinishedAtKeepsPrevious() {
        StepState done = terminatedStep("clone", "2026-09-13T02:30:00Z", null);
        LocalDateTime previous = LocalDateTime.of(2026, 9, 13, 2, 30, 5);
        assertEquals(previous, TektonPipelineExecutor.resolveJobFinishedAt("SUCCEEDED", List.of(done), previous));
    }

    private static StepState terminatedStep(String name, String startedAt, String finishedAt) {
        StepState done = new StepState();
        done.setName(name);
        ContainerStateTerminated terminated = new ContainerStateTerminated();
        terminated.setExitCode(0);
        terminated.setStartedAt(startedAt);
        terminated.setFinishedAt(finishedAt);
        done.setTerminated(terminated);
        return done;
    }
}
