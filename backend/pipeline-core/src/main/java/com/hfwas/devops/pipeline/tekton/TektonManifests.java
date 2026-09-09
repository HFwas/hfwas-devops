package com.hfwas.devops.pipeline.tekton;

import io.fabric8.kubernetes.api.model.EmptyDirVolumeSource;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.api.model.SecretKeySelectorBuilder;
import io.fabric8.tekton.v1.Pipeline;
import io.fabric8.tekton.v1.PipelineBuilder;
import io.fabric8.tekton.v1.PipelineRun;
import io.fabric8.tekton.v1.PipelineRunBuilder;
import io.fabric8.tekton.v1.PipelineTask;
import io.fabric8.tekton.v1.PipelineTaskBuilder;
import io.fabric8.tekton.v1.PipelineWorkspaceDeclarationBuilder;
import io.fabric8.tekton.v1.Step;
import io.fabric8.tekton.v1.StepBuilder;
import io.fabric8.tekton.v1.Task;
import io.fabric8.tekton.v1.TaskBuilder;
import io.fabric8.tekton.v1.TaskRefBuilder;
import io.fabric8.tekton.v1.TaskRun;
import io.fabric8.tekton.v1.TaskRunBuilder;
import io.fabric8.tekton.v1.WorkspaceBindingBuilder;
import io.fabric8.tekton.v1.WorkspaceDeclarationBuilder;
import io.fabric8.tekton.v1.WorkspacePipelineTaskBindingBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class TektonManifests {

    private TektonManifests() {
    }

    public static Secret gitSecret(String namespace, String name, String username, String password) {
        return new SecretBuilder()
                .withNewMetadata().withNamespace(namespace).withName(name).endMetadata()
                .withType("Opaque")
                .addToStringData("username", username)
                .addToStringData("password", password)
                .build();
    }

    public static PersistentVolumeClaim workspaceClaim(String namespace, String name) {
        return claim(namespace, name, "2Gi");
    }

    private static PersistentVolumeClaim claim(String namespace, String name, String size) {
        return new PersistentVolumeClaimBuilder()
                .withNewMetadata().withNamespace(namespace).withName(name).endMetadata()
                .withNewSpec()
                .withAccessModes("ReadWriteOnce")
                .withNewResources()
                .addToRequests("storage", new Quantity(size))
                .endResources()
                .endSpec()
                .build();
    }

    public static Task task(String namespace, CompiledTask compiled, String gitSecretName) {
        List<Step> steps = new ArrayList<>();
        for (CompiledStep step : compiled.steps()) {
            StepBuilder builder = new StepBuilder()
                    .withName(step.name())
                    .withImage(step.image())
                    .withScript(step.script());
            List<EnvVar> env = new ArrayList<>();
            for (Map.Entry<String, String> entry : step.env().entrySet()) {
                env.add(new EnvVarBuilder().withName(entry.getKey()).withValue(entry.getValue()).build());
            }
            if (step.usesGitSecret() && gitSecretName != null) {
                env.add(secretEnv("GIT_USERNAME", gitSecretName, "username"));
                env.add(secretEnv("GIT_PASSWORD", gitSecretName, "password"));
            }
            builder.withEnv(env);
            steps.add(builder.build());
        }
        List<io.fabric8.tekton.v1.WorkspaceDeclaration> workspaces = new ArrayList<>();
        workspaces.add(new WorkspaceDeclarationBuilder().withName(TektonCompiler.WORKSPACE).build());
        return new TaskBuilder()
                .withNewMetadata().withNamespace(namespace).withName(compiled.name()).endMetadata()
                .withNewSpec()
                .withSteps(steps)
                .withWorkspaces(workspaces)
                .endSpec()
                .build();
    }

    public static TaskRun taskRun(String namespace, String name, String taskName) {
        List<io.fabric8.tekton.v1.WorkspaceBinding> bindings = new ArrayList<>();
        bindings.add(new WorkspaceBindingBuilder()
                .withName(TektonCompiler.WORKSPACE)
                .withEmptyDir(new EmptyDirVolumeSource())
                .build());
        return new TaskRunBuilder()
                .withNewMetadata().withNamespace(namespace).withName(name).endMetadata()
                .withNewSpec()
                .withTaskRef(new TaskRefBuilder().withName(taskName).build())
                .withWorkspaces(bindings)
                .endSpec()
                .build();
    }

    public static Pipeline pipeline(String namespace, CompiledTekton compiled) {
        List<PipelineTask> tasks = new ArrayList<>();
        for (CompiledPipelineTask item : compiled.pipelineTasks()) {
            CompiledTask task = compiled.tasks().stream()
                    .filter(row -> row.name().equals(item.taskRef()))
                    .findFirst()
                    .orElse(null);
            List<io.fabric8.tekton.v1.WorkspacePipelineTaskBinding> ws = new ArrayList<>();
            ws.add(new WorkspacePipelineTaskBindingBuilder()
                    .withName(TektonCompiler.WORKSPACE)
                    .withWorkspace(TektonCompiler.WORKSPACE)
                    .build());
            PipelineTaskBuilder builder = new PipelineTaskBuilder()
                    .withName(item.name())
                    .withTaskRef(new TaskRefBuilder().withName(item.taskRef()).build())
                    .withWorkspaces(ws);
            if (!item.runAfter().isEmpty()) {
                builder.withRunAfter(item.runAfter());
            }
            tasks.add(builder.build());
        }
        List<io.fabric8.tekton.v1.PipelineWorkspaceDeclaration> pws = new ArrayList<>();
        pws.add(new PipelineWorkspaceDeclarationBuilder().withName(TektonCompiler.WORKSPACE).build());
        return new PipelineBuilder()
                .withNewMetadata().withNamespace(namespace).withName(compiled.name()).endMetadata()
                .withNewSpec()
                .withTasks(tasks)
                .withWorkspaces(pws)
                .endSpec()
                .build();
    }

    public static PipelineRun pipelineRun(
            String namespace,
            String name,
            String pipelineName,
            String claimName
    ) {
        List<io.fabric8.tekton.v1.WorkspaceBinding> bindings = new ArrayList<>();
        bindings.add(new WorkspaceBindingBuilder()
                .withName(TektonCompiler.WORKSPACE)
                .withPersistentVolumeClaim(new PersistentVolumeClaimVolumeSourceBuilder()
                        .withClaimName(claimName)
                        .build())
                .build());
        return new PipelineRunBuilder()
                .withNewMetadata().withNamespace(namespace).withName(name).endMetadata()
                .withNewSpec()
                .withNewPipelineRef().withName(pipelineName).endPipelineRef()
                .withWorkspaces(bindings)
                .endSpec()
                .build();
    }

    private static EnvVar secretEnv(String envName, String secret, String key) {
        return new EnvVarBuilder()
                .withName(envName)
                .withValueFrom(new EnvVarSourceBuilder()
                        .withSecretKeyRef(new SecretKeySelectorBuilder()
                                .withName(secret)
                                .withKey(key)
                                .build())
                        .build())
                .build();
    }
}
