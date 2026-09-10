package com.hfwas.devops.container.service.prometheus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.container.dto.MonitorOverviewVO;
import com.hfwas.devops.container.dto.MonitorPointVO;
import com.hfwas.devops.container.dto.MonitorSeriesVO;
import com.hfwas.devops.container.entity.ClusterEntity;
import com.hfwas.devops.container.error.ContainerErrorCode;
import com.hfwas.devops.container.service.cluster.ClusterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MonitorService {

    private final ClusterService clusterService;
    private final PrometheusClient prometheusClient;
    private final ObjectMapper objectMapper;

    /**
     * Get prometheusUrl from cluster labels, or throw.
     */
    public String getPrometheusUrl(Long clusterId, Long tenantId) {
        ClusterEntity entity = clusterService.getById(clusterId, tenantId);
        return extractPrometheusUrl(entity.getLabels());
    }

    /**
     * Get prometheusUrl from cluster (internal, no tenant check).
     */
    public String getPrometheusUrlInternal(Long clusterId) {
        ClusterEntity entity = clusterService.getByIdInternal(clusterId);
        return extractPrometheusUrl(entity.getLabels());
    }

    private String extractPrometheusUrl(String labelsJson) {
        if (labelsJson == null || labelsJson.isBlank()) {
            throw new BizException(ContainerErrorCode.CLUSTER_NOT_CONNECTED, "该集群未配置 Prometheus 地址（labels 为空）");
        }
        try {
            JsonNode labels = objectMapper.readTree(labelsJson);
            JsonNode urlNode = labels.get("prometheusUrl");
            if (urlNode == null || urlNode.asText().isBlank()) {
                throw new BizException(ContainerErrorCode.CLUSTER_NOT_CONNECTED, "该集群未配置 Prometheus 地址（labels 中无 prometheusUrl）");
            }
            return urlNode.asText();
        } catch (JsonProcessingException e) {
            throw new BizException(ContainerErrorCode.CLUSTER_NOT_CONNECTED, "该集群 labels 解析失败: " + e.getMessage());
        }
    }

    // ── Node monitor queries ──

    public List<MonitorSeriesVO> queryNodeCpu(Long clusterId, String nodeName, Long tenantId) {
        return queryNodeCpu(clusterId, nodeName, tenantId, "1h", "15s");
    }

    public List<MonitorSeriesVO> queryNodeCpu(Long clusterId, String nodeName, Long tenantId, String range, String step) {
        String url = getPrometheusUrl(clusterId, tenantId);
        return queryRangeSeries(url, PrometheusQueryBuilder.nodeCpuUsage(nodeName), range, step);
    }

    public List<MonitorSeriesVO> queryNodeMemory(Long clusterId, String nodeName, Long tenantId) {
        return queryNodeMemory(clusterId, nodeName, tenantId, "1h", "15s");
    }

    public List<MonitorSeriesVO> queryNodeMemory(Long clusterId, String nodeName, Long tenantId, String range, String step) {
        String url = getPrometheusUrl(clusterId, tenantId);
        return queryRangeSeries(url, PrometheusQueryBuilder.nodeMemoryUsage(nodeName), range, step);
    }

    public List<MonitorSeriesVO> queryNodeNetwork(Long clusterId, String nodeName, Long tenantId) {
        return queryNodeNetwork(clusterId, nodeName, tenantId, "1h", "15s");
    }

    public List<MonitorSeriesVO> queryNodeNetwork(Long clusterId, String nodeName, Long tenantId, String range, String step) {
        String url = getPrometheusUrl(clusterId, tenantId);
        List<MonitorSeriesVO> rx = queryRangeSeries(url, PrometheusQueryBuilder.nodeNetworkReceive(nodeName), range, step);
        List<MonitorSeriesVO> tx = queryRangeSeries(url, PrometheusQueryBuilder.nodeNetworkTransmit(nodeName), range, step);
        // Add direction label
        rx.forEach(s -> s.getLabels().put("direction", "receive"));
        tx.forEach(s -> s.getLabels().put("direction", "transmit"));
        List<MonitorSeriesVO> result = new ArrayList<>();
        result.addAll(rx);
        result.addAll(tx);
        return result;
    }

    public List<MonitorSeriesVO> queryNodeConnections(Long clusterId, String nodeName, Long tenantId) {
        return queryNodeConnections(clusterId, nodeName, tenantId, "1h", "15s");
    }

    public List<MonitorSeriesVO> queryNodeConnections(Long clusterId, String nodeName, Long tenantId, String range, String step) {
        String url = getPrometheusUrl(clusterId, tenantId);
        return queryRangeSeries(url, PrometheusQueryBuilder.nodeTcpConnections(nodeName), range, step);
    }

    public List<MonitorSeriesVO> queryNodeDisk(Long clusterId, String nodeName, Long tenantId) {
        return queryNodeDisk(clusterId, nodeName, tenantId, "1h", "15s");
    }

    public List<MonitorSeriesVO> queryNodeDisk(Long clusterId, String nodeName, Long tenantId, String range, String step) {
        String url = getPrometheusUrl(clusterId, tenantId);
        List<MonitorSeriesVO> read = queryRangeSeries(url, PrometheusQueryBuilder.nodeDiskRead(nodeName), range, step);
        List<MonitorSeriesVO> write = queryRangeSeries(url, PrometheusQueryBuilder.nodeDiskWrite(nodeName), range, step);
        read.forEach(s -> s.getLabels().put("direction", "read"));
        write.forEach(s -> s.getLabels().put("direction", "write"));
        List<MonitorSeriesVO> result = new ArrayList<>();
        result.addAll(read);
        result.addAll(write);
        return result;
    }

    public List<MonitorSeriesVO> queryNodeLoad1(Long clusterId, String nodeName, Long tenantId) {
        return queryNodeLoad1(clusterId, nodeName, tenantId, "1h", "15s");
    }

    public List<MonitorSeriesVO> queryNodeLoad1(Long clusterId, String nodeName, Long tenantId, String range, String step) {
        String url = getPrometheusUrl(clusterId, tenantId);
        return queryRangeSeries(url, PrometheusQueryBuilder.nodeLoad1(nodeName), range, step);
    }

    // ── Pod monitor queries ──

    public List<MonitorSeriesVO> queryPodCpu(Long clusterId, String namespace, String podName, Long tenantId) {
        return queryPodCpu(clusterId, namespace, podName, tenantId, "1h", "15s");
    }

    public List<MonitorSeriesVO> queryPodCpu(Long clusterId, String namespace, String podName, Long tenantId, String range, String step) {
        String url = getPrometheusUrl(clusterId, tenantId);
        return queryRangeSeries(url, PrometheusQueryBuilder.podCpuUsage(namespace, podName), range, step);
    }

    public List<MonitorSeriesVO> queryPodMemory(Long clusterId, String namespace, String podName, Long tenantId) {
        return queryPodMemory(clusterId, namespace, podName, tenantId, "1h", "15s");
    }

    public List<MonitorSeriesVO> queryPodMemory(Long clusterId, String namespace, String podName, Long tenantId, String range, String step) {
        String url = getPrometheusUrl(clusterId, tenantId);
        return queryRangeSeries(url, PrometheusQueryBuilder.podMemoryUsage(namespace, podName), range, step);
    }

    public List<MonitorSeriesVO> queryPodNetwork(Long clusterId, String namespace, String podName, Long tenantId) {
        return queryPodNetwork(clusterId, namespace, podName, tenantId, "1h", "15s");
    }

    public List<MonitorSeriesVO> queryPodNetwork(Long clusterId, String namespace, String podName, Long tenantId, String range, String step) {
        String url = getPrometheusUrl(clusterId, tenantId);
        List<MonitorSeriesVO> rx = queryRangeSeries(url, PrometheusQueryBuilder.podNetworkReceive(namespace, podName), range, step);
        List<MonitorSeriesVO> tx = queryRangeSeries(url, PrometheusQueryBuilder.podNetworkTransmit(namespace, podName), range, step);
        rx.forEach(s -> s.getLabels().put("direction", "receive"));
        tx.forEach(s -> s.getLabels().put("direction", "transmit"));
        List<MonitorSeriesVO> result = new ArrayList<>();
        result.addAll(rx);
        result.addAll(tx);
        return result;
    }

    // ── JVM monitor queries ──

    public boolean checkJvmMetrics(Long clusterId, String namespace, String podName, Long tenantId) {
        try {
            String url = getPrometheusUrl(clusterId, tenantId);
            Optional<JsonNode> result = prometheusClient.query(url, PrometheusQueryBuilder.jvmCheck(namespace, podName));
            if (result.isEmpty()) return false;
            JsonNode results = result.get().path("result");
            if (!results.isArray() || results.isEmpty()) return false;
            // If count > 0, JVM metrics exist
            return results.get(0).path("value").size() >= 2
                    && !"0".equals(results.get(0).path("value").get(1).asText());
        } catch (Exception e) {
            log.warn("JVM check query failed: {}", e.getMessage());
            return false;
        }
    }

    public List<MonitorSeriesVO> queryJvmHeap(Long clusterId, String namespace, String podName, Long tenantId) {
        return queryJvmHeap(clusterId, namespace, podName, tenantId, "1h", "15s");
    }

    public List<MonitorSeriesVO> queryJvmHeap(Long clusterId, String namespace, String podName, Long tenantId, String range, String step) {
        String url = getPrometheusUrl(clusterId, tenantId);
        List<MonitorSeriesVO> used = queryRangeSeries(url, PrometheusQueryBuilder.jvmHeapUsed(namespace, podName), range, step);
        List<MonitorSeriesVO> max = queryRangeSeries(url, PrometheusQueryBuilder.jvmHeapMax(namespace, podName), range, step);
        List<MonitorSeriesVO> committed = queryRangeSeries(url, PrometheusQueryBuilder.jvmHeapCommitted(namespace, podName), range, step);
        used.forEach(s -> s.getLabels().put("metric", "used"));
        max.forEach(s -> s.getLabels().put("metric", "max"));
        committed.forEach(s -> s.getLabels().put("metric", "committed"));
        List<MonitorSeriesVO> result = new ArrayList<>();
        result.addAll(used);
        result.addAll(max);
        result.addAll(committed);
        return result;
    }

    public List<MonitorSeriesVO> queryJvmNonHeap(Long clusterId, String namespace, String podName, Long tenantId) {
        return queryJvmNonHeap(clusterId, namespace, podName, tenantId, "1h", "15s");
    }

    public List<MonitorSeriesVO> queryJvmNonHeap(Long clusterId, String namespace, String podName, Long tenantId, String range, String step) {
        String url = getPrometheusUrl(clusterId, tenantId);
        return queryRangeSeries(url, PrometheusQueryBuilder.jvmNonHeapUsed(namespace, podName), range, step);
    }

    public List<MonitorSeriesVO> queryJvmGc(Long clusterId, String namespace, String podName, Long tenantId) {
        return queryJvmGc(clusterId, namespace, podName, tenantId, "1h", "15s");
    }

    public List<MonitorSeriesVO> queryJvmGc(Long clusterId, String namespace, String podName, Long tenantId, String range, String step) {
        String url = getPrometheusUrl(clusterId, tenantId);
        List<MonitorSeriesVO> count = queryRangeSeries(url, PrometheusQueryBuilder.jvmGcCount(namespace, podName), range, step);
        List<MonitorSeriesVO> elapsed = queryRangeSeries(url, PrometheusQueryBuilder.jvmGcElapsed(namespace, podName), range, step);
        count.forEach(s -> s.getLabels().put("metric", "count"));
        elapsed.forEach(s -> s.getLabels().put("metric", "elapsed"));
        List<MonitorSeriesVO> result = new ArrayList<>();
        result.addAll(count);
        result.addAll(elapsed);
        return result;
    }

    public List<MonitorSeriesVO> queryJvmThread(Long clusterId, String namespace, String podName, Long tenantId) {
        return queryJvmThread(clusterId, namespace, podName, tenantId, "1h", "15s");
    }

    public List<MonitorSeriesVO> queryJvmThread(Long clusterId, String namespace, String podName, Long tenantId, String range, String step) {
        String url = getPrometheusUrl(clusterId, tenantId);
        return queryRangeSeries(url, PrometheusQueryBuilder.jvmThreadCount(namespace, podName), range, step);
    }

    public List<MonitorSeriesVO> queryJvmMemoryPools(Long clusterId, String namespace, String podName, Long tenantId) {
        return queryJvmMemoryPools(clusterId, namespace, podName, tenantId, "1h", "15s");
    }

    public List<MonitorSeriesVO> queryJvmMemoryPools(Long clusterId, String namespace, String podName, Long tenantId, String range, String step) {
        String url = getPrometheusUrl(clusterId, tenantId);
        return queryRangeSeries(url, PrometheusQueryBuilder.jvmMemoryPools(namespace, podName), range, step);
    }

    // ── Cluster overview ──

    public MonitorOverviewVO queryOverview(Long clusterId, Long tenantId) {
        String url = getPrometheusUrl(clusterId, tenantId);

        double cpuUsage = querySingleInstant(url, PrometheusQueryBuilder.clusterCpuUsage());
        double memUsage = querySingleInstant(url, PrometheusQueryBuilder.clusterMemoryUsage());
        double diskRead = querySingleInstant(url, PrometheusQueryBuilder.clusterDiskRead());
        double diskWrite = querySingleInstant(url, PrometheusQueryBuilder.clusterDiskWrite());
        int nodeTotal = (int) querySingleInstant(url, PrometheusQueryBuilder.clusterNodeCount());
        int nodeReady = (int) querySingleInstant(url, PrometheusQueryBuilder.clusterNodeReadyCount());
        int podTotal = (int) querySingleInstant(url, PrometheusQueryBuilder.clusterPodCount());
        int podRunning = (int) querySingleInstant(url, PrometheusQueryBuilder.clusterPodRunningCount());

        return new MonitorOverviewVO(cpuUsage, memUsage, nodeTotal, nodeReady, podTotal, podRunning,
                (long) diskRead, (long) diskWrite);
    }

    // ── Internal helpers ──

    /** Parse a range string like "1h", "6h", "24h", "7d" into seconds. */
    private static long rangeToSeconds(String range) {
        if (range == null || range.isBlank()) return 3600;
        String trimmed = range.trim();
        char unit = trimmed.charAt(trimmed.length() - 1);
        long value = Long.parseLong(trimmed.substring(0, trimmed.length() - 1));
        return switch (unit) {
            case 'd' -> value * 86400;
            case 'h' -> value * 3600;
            case 'm' -> value * 60;
            default -> 3600;
        };
    }

    /**
     * Query a range query and parse into MonitorSeriesVO list.
     */
    private List<MonitorSeriesVO> queryRangeSeries(String url, String promql) {
        return queryRangeSeries(url, promql, "1h", "15s");
    }

    private List<MonitorSeriesVO> queryRangeSeries(String url, String promql, String range, String step) {
        long end = Instant.now().getEpochSecond();
        long start = end - rangeToSeconds(range);
        if (step == null || step.isBlank()) step = "15s";
        return queryRangeSeries(url, promql, start, end, step);
    }

    private List<MonitorSeriesVO> queryRangeSeries(String url, String promql, long start, long end, String step) {
        Optional<JsonNode> opt = prometheusClient.queryRange(url, promql, start, end, step);
        if (opt.isEmpty()) return List.of();
        return parseSeriesResults(opt.get());
    }

    /**
     * Query an instant query and return the first numeric value, or 0.
     */
    private double querySingleInstant(String url, String promql) {
        Optional<JsonNode> opt = prometheusClient.query(url, promql);
        if (opt.isEmpty()) return 0;
        JsonNode results = opt.get().path("result");
        if (!results.isArray() || results.isEmpty()) return 0;
        JsonNode value = results.get(0).path("value");
        if (value.size() >= 2) {
            try {
                return Double.parseDouble(value.get(1).asText());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * Parse Prometheus response JSON "result" array into MonitorSeriesVO list.
     */
    private List<MonitorSeriesVO> parseSeriesResults(JsonNode data) {
        List<MonitorSeriesVO> seriesList = new ArrayList<>();
        JsonNode results = data.path("result");
        if (!results.isArray()) return seriesList;

        for (JsonNode result : results) {
            // Parse labels
            Map<String, String> labels = new HashMap<>();
            JsonNode metric = result.path("metric");
            if (metric.isObject()) {
                metric.fieldNames().forEachRemaining(key ->
                    labels.put(key, metric.get(key).asText()));
            }

            // Parse values
            List<MonitorPointVO> points = new ArrayList<>();
            JsonNode values = result.path("values");
            if (values.isArray()) {
                for (JsonNode v : values) {
                    if (v.isArray() && v.size() >= 2) {
                        long ts = v.get(0).asLong();
                        double val = v.get(1).asDouble();
                        points.add(new MonitorPointVO(ts, val));
                    }
                }
            }

            seriesList.add(new MonitorSeriesVO(labels, points));
        }
        return seriesList;
    }
}