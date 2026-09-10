package com.hfwas.devops.container.service.prometheus;

import lombok.experimental.UtilityClass;

/**
 * PromQL query string builder.
 * All methods return fully-formed PromQL expressions.
 */
@UtilityClass
public class PrometheusQueryBuilder {

    // ── Node metrics ──

    public static String nodeCpuUsage(String nodeName) {
        return String.format(
            "100 - avg by(instance) (rate(node_cpu_seconds_total{mode=\"idle\", instance=~\".*%s.*\"}[5m])) * 100",
            nodeName);
    }

    public static String nodeMemoryUsage(String nodeName) {
        return String.format(
            "(1 - node_memory_MemAvailable_bytes{instance=~\".*%s.*\"} / node_memory_MemTotal_bytes{instance=~\".*%s.*\"}) * 100",
            nodeName, nodeName);
    }

    public static String nodeNetworkReceive(String nodeName) {
        return String.format(
            "rate(node_network_receive_bytes_total{instance=~\".*%s.*\", device!=\"lo\"}[5m])",
            nodeName);
    }

    public static String nodeNetworkTransmit(String nodeName) {
        return String.format(
            "rate(node_network_transmit_bytes_total{instance=~\".*%s.*\", device!=\"lo\"}[5m])",
            nodeName);
    }

    public static String nodeTcpConnections(String nodeName) {
        return String.format(
            "node_netstat_Tcp_CurrEstab{instance=~\".*%s.*\"}",
            nodeName);
    }

    public static String nodeDiskRead(String nodeName) {
        return String.format(
            "rate(node_disk_read_bytes_total{instance=~\".*%s.*\"}[5m])",
            nodeName);
    }

    public static String nodeDiskWrite(String nodeName) {
        return String.format(
            "rate(node_disk_written_bytes_total{instance=~\".*%s.*\"}[5m])",
            nodeName);
    }

    // ── Pod metrics ──

    public static String podCpuUsage(String namespace, String podName) {
        return String.format(
            "sum(rate(container_cpu_usage_seconds_total{namespace=\"%s\", pod=\"%s\", container!=\"\", container!=\"POD\"}[5m])) by (pod, container) * 1000",
            namespace, podName);
    }

    public static String podMemoryUsage(String namespace, String podName) {
        return String.format(
            "sum(container_memory_working_set_bytes{namespace=\"%s\", pod=\"%s\", container!=\"\", container!=\"POD\"}) by (pod, container)",
            namespace, podName);
    }

    public static String podNetworkReceive(String namespace, String podName) {
        return String.format(
            "sum(rate(container_network_receive_bytes_total{namespace=\"%s\", pod=\"%s\"}[5m])) by (pod)",
            namespace, podName);
    }

    public static String podNetworkTransmit(String namespace, String podName) {
        return String.format(
            "sum(rate(container_network_transmit_bytes_total{namespace=\"%s\", pod=\"%s\"}[5m])) by (pod)",
            namespace, podName);
    }

    // ── JVM metrics (OTel Java Agent) ──

    public static String jvmCheck(String namespace, String podName) {
        return String.format(
            "count(jvm_memory_used_bytes{kubernetes_namespace=\"%s\", kubernetes_pod_name=\"%s\"})",
            namespace, podName);
    }

    public static String jvmHeapUsed(String namespace, String podName) {
        return String.format(
            "jvm_memory_used_bytes{area=\"heap\", kubernetes_namespace=\"%s\", kubernetes_pod_name=\"%s\"}",
            namespace, podName);
    }

    public static String jvmHeapMax(String namespace, String podName) {
        return String.format(
            "jvm_memory_limit_bytes{area=\"heap\", kubernetes_namespace=\"%s\", kubernetes_pod_name=\"%s\"}",
            namespace, podName);
    }

    public static String jvmHeapCommitted(String namespace, String podName) {
        return String.format(
            "jvm_memory_committed_bytes{area=\"heap\", kubernetes_namespace=\"%s\", kubernetes_pod_name=\"%s\"}",
            namespace, podName);
    }

    public static String jvmNonHeapUsed(String namespace, String podName) {
        return String.format(
            "jvm_memory_used_bytes{area=\"nonheap\", kubernetes_namespace=\"%s\", kubernetes_pod_name=\"%s\"}",
            namespace, podName);
    }

    public static String jvmGcCount(String namespace, String podName) {
        return String.format(
            "rate(jvm_gc_collections_count_total{kubernetes_namespace=\"%s\", kubernetes_pod_name=\"%s\"}[5m])",
            namespace, podName);
    }

    public static String jvmGcElapsed(String namespace, String podName) {
        return String.format(
            "rate(jvm_gc_collections_elapsed_total{kubernetes_namespace=\"%s\", kubernetes_pod_name=\"%s\"}[5m])",
            namespace, podName);
    }

    public static String jvmThreadCount(String namespace, String podName) {
        return String.format(
            "jvm_threads_count{kubernetes_namespace=\"%s\", kubernetes_pod_name=\"%s\"}",
            namespace, podName);
    }

    public static String jvmMemoryPools(String namespace, String podName) {
        return String.format(
            "jvm_memory_pool_used_bytes{kubernetes_namespace=\"%s\", kubernetes_pod_name=\"%s\"}",
            namespace, podName);
    }

    // ── Cluster overview ──

    public static String clusterCpuUsage() {
        return "100 - avg by(instance) (rate(node_cpu_seconds_total{mode=\"idle\"}[5m])) * 100";
    }

    public static String clusterMemoryUsage() {
        return "(1 - node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes) * 100";
    }

    public static String clusterDiskRead() {
        return "sum(rate(node_disk_read_bytes_total[5m]))";
    }

    public static String clusterDiskWrite() {
        return "sum(rate(node_disk_written_bytes_total[5m]))";
    }

    public static String clusterNodeCount() {
        return "count(kube_node_info)";
    }

    public static String clusterNodeReadyCount() {
        return "count(kube_node_status_condition{condition=\"Ready\", status=\"true\"})";
    }

    public static String clusterPodCount() {
        return "count(kube_pod_info)";
    }

    public static String clusterPodRunningCount() {
        return "count(kube_pod_status_phase{phase=\"Running\"})";
    }
}