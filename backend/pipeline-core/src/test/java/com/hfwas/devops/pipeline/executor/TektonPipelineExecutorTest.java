package com.hfwas.devops.pipeline.executor;

import io.fabric8.kubernetes.api.model.ContainerStateRunning;
import io.fabric8.kubernetes.api.model.ContainerStateTerminated;
import io.fabric8.tekton.v1.StepState;
import io.fabric8.tekton.v1.StepStateBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
