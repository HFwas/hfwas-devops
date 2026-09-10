package com.hfwas.devops.container.error;

import com.hfwas.devops.common.error.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ContainerErrorCode implements ErrorCode {

    CLUSTER_NOT_FOUND(30001, "集群不存在"),
    CLUSTER_NAME_DUPLICATE(30002, "当前租户下集群名称已存在"),
    CLUSTER_KUBECONFIG_INVALID(30003, "Kubeconfig 无效"),
    CLUSTER_CONNECTION_FAILED(30004, "集群连接测试失败"),
    CLUSTER_FORBIDDEN(30005, "无权访问该集群"),
    CLUSTER_NOT_CONNECTED(30006, "集群未连接"),

    RESOURCE_NAMESPACE_REQUIRED(30101, "namespace 不能为空"),
    RESOURCE_NOT_FOUND(30102, "K8s 资源不存在"),
    RESOURCE_OPERATION_FAILED(30103, "资源操作失败"),

    REGISTRY_NOT_FOUND(30201, "镜像仓库不存在"),
    REGISTRY_FORBIDDEN(30202, "无权访问该镜像仓库"),
    REGISTRY_CONNECTION_FAILED(30203, "镜像仓库连接失败"),
    REGISTRY_CREDENTIAL_INVALID(30204, "镜像仓库凭据无效"),
    REGISTRY_ARTIFACT_DELETE_FAILED(30205, "删除制品失败"),
    REGISTRY_DEPLOY_FAILED(30206, "镜像部署失败"),
    REGISTRY_NAMESPACE_REQUIRED(30207, "deploy namespace 不能为空"),
    ;

    private final int code;
    private final String message;
}