package com.hfwas.devops.container.config.argo;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Argo Workflows 连接配置。
 * 通过 Fabric8 KubernetesClient 直连 K8s CRD（argoproj.io/v1alpha1），
 * 不走 Argo Server 的 HTTP API。
 */
@Data
@ConfigurationProperties(prefix = "argo")
public class ArgoWorkflowProperties {

    /** Argo Workflows CRD 所在命名空间（默认 argo） */
    private String namespace = "argo";

    /** 用于关联 WorkflowRun → WorkflowTemplate 的标签 key */
    private String templateLabelKey = "workflows.argoproj.io/workflow-template";
}