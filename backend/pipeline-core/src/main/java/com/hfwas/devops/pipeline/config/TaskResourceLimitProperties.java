package com.hfwas.devops.pipeline.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pipeline.task.resource-limits")
public class TaskResourceLimitProperties {

    /**
     * 最大 CPU（核心数等效），0 表示不限制。
     * 例如 8 = 8 核心，0.5 = 半核心
     */
    private double maxCpu = 8;

    /**
     * 最大内存（K8s 资源量格式），空字符串表示不限制。
     * 例如 "32Gi"、"16384Mi"、"16G"
     */
    private String maxMemory = "32Gi";

    public double getMaxCpu() {
        return maxCpu;
    }

    public void setMaxCpu(double maxCpu) {
        this.maxCpu = maxCpu;
    }

    public String getMaxMemory() {
        return maxMemory;
    }

    public void setMaxMemory(String maxMemory) {
        this.maxMemory = maxMemory;
    }
}