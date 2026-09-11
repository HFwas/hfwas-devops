package com.hfwas.devops.container.service.prometheus;

import lombok.experimental.UtilityClass;

/**
 * PromQL query string builder.
 * All methods return fully-formed PromQL expressions.
 */
@UtilityClass
public class PrometheusQueryBuilder {

    /**
     * node-exporter instance is "{ip}:9100", not the Kubernetes node name.
     * Join via node_uname_info.nodename (matches kubectl node names).
     * {@code .*} / blank means all nodes (cluster overview).
     */
    private static String nodeUname(String nodeName) {
        if (nodeName == null || nodeName.isBlank() || ".*".equals(nodeName)) {
            return "node_uname_info";
        }
        return "node_uname_info{nodename=\"" + escapeLabelValue(nodeName) + "\"}";
    }

    private static String onNode(String expr, String nodeName) {
        return "(" + expr + ") * on(instance) group_left(nodename) " + nodeUname(nodeName);
    }

    /** Escape a PromQL double-quoted label value. */
    static String escapeLabelValue(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    // ── Node metrics ──

    public static String nodeCpuUsage(String nodeName) {
        return onNode(
            "(1 - avg by (instance) (rate(node_cpu_seconds_total{mode=\"idle\"}[5m]))) * 100",
            nodeName);
    }

    public static String nodeMemoryUsage(String nodeName) {
        return onNode(
            "(1 - node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes) * 100",
            nodeName);
    }

    public static String nodeNetworkReceive(String nodeName) {
        return onNode(
            "rate(node_network_receive_bytes_total{device!=\"lo\"}[5m])",
            nodeName);
    }

    public static String nodeNetworkTransmit(String nodeName) {
        return onNode(
            "rate(node_network_transmit_bytes_total{device!=\"lo\"}[5m])",
            nodeName);
    }

    public static String nodeTcpConnections(String nodeName) {
        return onNode("node_netstat_Tcp_CurrEstab", nodeName);
    }

    public static String nodeDiskRead(String nodeName) {
        return onNode("rate(node_disk_read_bytes_total[5m])", nodeName);
    }

    public static String nodeDiskWrite(String nodeName) {
        return onNode("rate(node_disk_written_bytes_total[5m])", nodeName);
    }

    public static String nodeLoad1(String nodeName) {
        return onNode("node_load1", nodeName);
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

    // ── JVM metrics (OTel Java Agent 2.x semantic conventions) ──

    private static String jvmNsPod(String namespace, String podName) {
        return "kubernetes_namespace=\"" + escapeLabelValue(namespace)
                + "\", kubernetes_pod_name=\"" + escapeLabelValue(podName) + "\"";
    }

    public static String jvmCheck(String namespace, String podName) {
        return "count(jvm_memory_used_bytes{" + jvmNsPod(namespace, podName) + "})";
    }

    public static String jvmHeapUsed(String namespace, String podName) {
        return "sum(jvm_memory_used_bytes{jvm_memory_type=\"heap\", " + jvmNsPod(namespace, podName) + "})";
    }

    public static String jvmHeapMax(String namespace, String podName) {
        return "sum(jvm_memory_limit_bytes{jvm_memory_type=\"heap\", " + jvmNsPod(namespace, podName) + "})";
    }

    public static String jvmHeapCommitted(String namespace, String podName) {
        return "sum(jvm_memory_committed_bytes{jvm_memory_type=\"heap\", " + jvmNsPod(namespace, podName) + "})";
    }

    public static String jvmNonHeapUsed(String namespace, String podName) {
        return "sum(jvm_memory_used_bytes{jvm_memory_type=\"non_heap\", " + jvmNsPod(namespace, podName) + "})";
    }

    public static String jvmGcCount(String namespace, String podName) {
        return "sum by (jvm_gc_name) (rate(jvm_gc_duration_seconds_count{" + jvmNsPod(namespace, podName) + "}[5m]))";
    }

    public static String jvmGcElapsed(String namespace, String podName) {
        return "sum by (jvm_gc_name) (rate(jvm_gc_duration_seconds_sum{" + jvmNsPod(namespace, podName) + "}[5m]))";
    }

    public static String jvmThreadCount(String namespace, String podName) {
        return "sum(jvm_thread_count{" + jvmNsPod(namespace, podName) + "})";
    }

    public static String jvmThreadByState(String namespace, String podName) {
        return "sum by (jvm_thread_daemon, jvm_thread_state) (jvm_thread_count{" + jvmNsPod(namespace, podName) + "})";
    }

    public static String jvmMemoryPools(String namespace, String podName) {
        return "sum by (jvm_memory_pool_name) (jvm_memory_used_bytes{" + jvmNsPod(namespace, podName) + "})";
    }

    public static String jvmClassCount(String namespace, String podName) {
        return "jvm_class_count{" + jvmNsPod(namespace, podName) + "}";
    }

    public static String jvmClassLoaded(String namespace, String podName) {
        return "jvm_class_loaded_total{" + jvmNsPod(namespace, podName) + "}";
    }

    public static String jvmClassUnloaded(String namespace, String podName) {
        return "jvm_class_unloaded_total{" + jvmNsPod(namespace, podName) + "}";
    }

    public static String jvmCpuUtilization(String namespace, String podName) {
        return "jvm_cpu_recent_utilization_ratio{" + jvmNsPod(namespace, podName) + "} * 100";
    }

    public static String jvmCpuTime(String namespace, String podName) {
        return "rate(jvm_cpu_time_seconds_total{" + jvmNsPod(namespace, podName) + "}[5m])";
    }

    public static String jvmCpuCount(String namespace, String podName) {
        return "jvm_cpu_count{" + jvmNsPod(namespace, podName) + "}";
    }

    public static String jvmMemoryAfterGc(String namespace, String podName) {
        return "sum by (jvm_memory_pool_name) (jvm_memory_used_after_last_gc_bytes{jvm_memory_type=\"heap\", "
                + jvmNsPod(namespace, podName) + "})";
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