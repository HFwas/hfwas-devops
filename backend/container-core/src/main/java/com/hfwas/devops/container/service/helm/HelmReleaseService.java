package com.hfwas.devops.container.service.helm;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.container.dto.HelmDryRunVO;
import com.hfwas.devops.container.dto.HelmInstallRequest;
import com.hfwas.devops.container.dto.HelmManifestVO;
import com.hfwas.devops.container.dto.HelmReleaseHistoryVO;
import com.hfwas.devops.container.dto.HelmReleaseResourceVO;
import com.hfwas.devops.container.dto.HelmReleaseVO;
import com.hfwas.devops.container.dto.HelmRollbackRequest;
import com.hfwas.devops.container.dto.HelmUpgradeRequest;
import com.hfwas.devops.container.dto.HelmValuesVO;
import com.hfwas.devops.container.entity.ClusterEntity;
import com.hfwas.devops.container.entity.HelmChartArtifactEntity;
import com.hfwas.devops.container.entity.HelmChartRepositoryEntity;
import com.hfwas.devops.container.error.ContainerErrorCode;
import com.hfwas.devops.container.error.HelmReleaseConflictException;
import com.hfwas.devops.container.error.HelmReleaseNotFoundException;
import com.hfwas.devops.container.mapper.HelmChartArtifactMapper;
import com.hfwas.devops.container.mapper.HelmChartRepositoryMapper;
import com.hfwas.devops.container.service.SecurityHelper;
import com.hfwas.devops.container.service.cluster.ClusterService;
import com.hfwas.devops.container.service.cluster.KubeconfigCipher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Helm release operations for one connected cluster. The helm CLI runs in this process.
 * Kubeconfig is written to a private temp directory and removed when the call returns.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HelmReleaseService {

    private static final Pattern DNS_LABEL = Pattern.compile("[a-z0-9]([-a-z0-9]*[a-z0-9])?");
    private static final int MAX_VALUES_CHARS = 1024 * 1024;

    private final ClusterService clusterService;
    private final KubeconfigCipher kubeconfigCipher;
    private final HelmProcessRunner processRunner;
    private final HelmChartProperties chartProperties;
    private final HelmReleaseProperties releaseProperties;
    private final HelmChartArtifactMapper artifactMapper;
    private final HelmChartRepositoryMapper repositoryMapper;

    public List<HelmReleaseVO> list(Long clusterId, String namespace) {
        if (namespace != null && !namespace.isBlank()) {
            dnsLabel(namespace, "命名空间", 63);
        }
        ClusterEntity cluster = requireCluster(clusterId);
        try (HelmWorkspace workspace = HelmWorkspace.open(decrypt(cluster))) {
            HelmProcessResult result = run(workspace, HelmReleaseCommands.list(binary(), workspace.kubeconfigFile(), namespace),
                    null, queryTimeout(), HelmOp.LIST, null);
            List<HelmReleaseVO> releases = HelmOutputParser.releases(result.stdout());
            for (HelmReleaseVO release : releases) {
                release.setClusterId(Long.toString(clusterId));
                attachArtifact(release);
            }
            return releases;
        }
    }

    public HelmReleaseVO get(Long clusterId, String namespace, String name) {
        ClusterEntity cluster = requireCluster(clusterId);
        try (HelmWorkspace workspace = HelmWorkspace.open(decrypt(cluster))) {
            return describe(workspace, clusterId, namespace, name);
        }
    }

    public List<HelmReleaseHistoryVO> history(Long clusterId, String namespace, String name) {
        ClusterEntity cluster = requireCluster(clusterId);
        try (HelmWorkspace workspace = HelmWorkspace.open(decrypt(cluster))) {
            return history(workspace, namespace, name);
        }
    }

    public HelmValuesVO values(Long clusterId, String namespace, String name) {
        ClusterEntity cluster = requireCluster(clusterId);
        try (HelmWorkspace workspace = HelmWorkspace.open(decrypt(cluster))) {
            HelmValuesVO vo = new HelmValuesVO();
            vo.setValuesYaml(readValues(workspace, namespace, name, null));
            return vo;
        }
    }

    public HelmManifestVO manifest(Long clusterId, String namespace, String name) {
        ClusterEntity cluster = requireCluster(clusterId);
        try (HelmWorkspace workspace = HelmWorkspace.open(decrypt(cluster))) {
            HelmProcessResult result = run(workspace,
                    HelmReleaseCommands.manifest(binary(), workspace.kubeconfigFile(), requireNamespace(namespace), requireReleaseName(name)),
                    null, queryTimeout(), HelmOp.GET, null);
            HelmManifestVO vo = new HelmManifestVO();
            vo.setManifest(result.stdout() == null ? "" : result.stdout());
            return vo;
        }
    }

    public List<HelmReleaseResourceVO> resources(Long clusterId, String namespace, String name) {
        return HelmOutputParser.resources(manifest(clusterId, namespace, name).getManifest());
    }

    public HelmDryRunVO dryRunInstall(Long clusterId, String namespace, HelmInstallRequest request) {
        PreparedInstall prepared = prepareInstall(clusterId, namespace, request);
        try (HelmWorkspace workspace = prepared.workspace()) {
            String secret = password();
            login(workspace, prepared.chart(), secret);
            Path values = valuesFile(workspace, request.getValuesYaml());
            Duration timeout = operationTimeout(false);
            HelmProcessResult result = run(workspace, HelmReleaseCommands.install(
                    binary(), workspace.kubeconfigFile(), prepared.chart().ref(), prepared.releaseName(), prepared.namespace(),
                    values, Boolean.TRUE.equals(request.getCreateNamespace()), false, true,
                    prepared.chart().plainHttp(), timeoutLabel(timeout)), null, timeout, HelmOp.DRY_RUN, secret);
            return dryRun(result.stdout(), prepared.namespace());
        }
    }

    public HelmReleaseVO install(Long clusterId, String namespace, HelmInstallRequest request) {
        PreparedInstall prepared = prepareInstall(clusterId, namespace, request);
        try (HelmWorkspace workspace = prepared.workspace()) {
            String secret = password();
            login(workspace, prepared.chart(), secret);
            Path values = valuesFile(workspace, request.getValuesYaml());
            boolean wait = Boolean.TRUE.equals(request.getWait());
            Duration timeout = operationTimeout(wait);
            log.info("helm install cluster={} namespace={} release={} chart={}",
                    clusterId, prepared.namespace(), prepared.releaseName(), prepared.chart().ref().chartRef());
            run(workspace, HelmReleaseCommands.install(
                    binary(), workspace.kubeconfigFile(), prepared.chart().ref(), prepared.releaseName(), prepared.namespace(),
                    values, Boolean.TRUE.equals(request.getCreateNamespace()), wait, false,
                    prepared.chart().plainHttp(), timeoutLabel(timeout)), null, timeout, HelmOp.INSTALL, secret);
            return describe(workspace, clusterId, prepared.namespace(), prepared.releaseName());
        }
    }

    public HelmDryRunVO dryRunUpgrade(Long clusterId, String namespace, String name, HelmUpgradeRequest request) {
        PreparedUpgrade prepared = prepareUpgrade(clusterId, namespace, name, request);
        try (HelmWorkspace workspace = prepared.workspace()) {
            String secret = password();
            login(workspace, prepared.chart(), secret);
            Path values = valuesFile(workspace, request.getValuesYaml());
            Duration timeout = operationTimeout(false);
            HelmProcessResult result = run(workspace, HelmReleaseCommands.upgrade(
                    binary(), workspace.kubeconfigFile(), prepared.chart().ref(), prepared.releaseName(), prepared.namespace(),
                    values, false, false, true, prepared.chart().plainHttp(), timeoutLabel(timeout)),
                    null, timeout, HelmOp.DRY_RUN, secret);
            return dryRun(result.stdout(), prepared.namespace());
        }
    }

    public HelmReleaseVO upgrade(Long clusterId, String namespace, String name, HelmUpgradeRequest request) {
        PreparedUpgrade prepared = prepareUpgrade(clusterId, namespace, name, request);
        try (HelmWorkspace workspace = prepared.workspace()) {
            String secret = password();
            login(workspace, prepared.chart(), secret);
            Path values = valuesFile(workspace, request.getValuesYaml());
            boolean atomic = Boolean.TRUE.equals(request.getRollbackOnFailure());
            boolean wait = Boolean.TRUE.equals(request.getWait());
            Duration timeout = operationTimeout(atomic || wait);
            log.info("helm upgrade cluster={} namespace={} release={} chart={}",
                    clusterId, prepared.namespace(), prepared.releaseName(), prepared.chart().ref().chartRef());
            run(workspace, HelmReleaseCommands.upgrade(
                    binary(), workspace.kubeconfigFile(), prepared.chart().ref(), prepared.releaseName(), prepared.namespace(),
                    values, wait, atomic, false, prepared.chart().plainHttp(), timeoutLabel(timeout)),
                    null, timeout, HelmOp.UPGRADE, secret);
            return describe(workspace, clusterId, prepared.namespace(), prepared.releaseName());
        }
    }

    public HelmReleaseVO rollback(Long clusterId, String namespace, String name, HelmRollbackRequest request) {
        if (request == null || request.getRevision() == null || request.getRevision() < 1) {
            throw new BizException(ContainerErrorCode.HELM_RELEASE_INVALID, "revision 必须大于 0");
        }
        String ns = requireNamespace(namespace);
        String releaseName = requireReleaseName(name);
        ClusterEntity cluster = requireCluster(clusterId);
        try (HelmWorkspace workspace = HelmWorkspace.open(decrypt(cluster))) {
            log.info("helm rollback cluster={} namespace={} release={} revision={}",
                    clusterId, ns, releaseName, request.getRevision());
            run(workspace, HelmReleaseCommands.rollback(
                    binary(), workspace.kubeconfigFile(), ns, releaseName, request.getRevision(), false, timeoutLabel(operationTimeout(false))),
                    null, operationTimeout(false), HelmOp.ROLLBACK, null);
            return describe(workspace, clusterId, ns, releaseName);
        }
    }

    public void uninstall(Long clusterId, String namespace, String name) {
        String ns = requireNamespace(namespace);
        String releaseName = requireReleaseName(name);
        ClusterEntity cluster = requireCluster(clusterId);
        try (HelmWorkspace workspace = HelmWorkspace.open(decrypt(cluster))) {
            log.info("helm uninstall cluster={} namespace={} release={}", clusterId, ns, releaseName);
            run(workspace, HelmReleaseCommands.uninstall(binary(), workspace.kubeconfigFile(), ns, releaseName),
                    null, operationTimeout(false), HelmOp.UNINSTALL, null);
        }
    }

    private PreparedInstall prepareInstall(Long clusterId, String namespace, HelmInstallRequest request) {
        if (request == null) {
            throw new BizException(ContainerErrorCode.HELM_RELEASE_INVALID, "安装参数为空");
        }
        String ns = requireNamespace(namespace);
        String releaseName = requireReleaseName(request.getName());
        assertValuesObject(request.getValuesYaml());
        ResolvedChart chart = resolve(request.getChartRef(), request.getArtifactId(), null);
        ClusterEntity cluster = requireCluster(clusterId);
        return new PreparedInstall(ns, releaseName, chart, HelmWorkspace.open(decrypt(cluster)));
    }

    private PreparedUpgrade prepareUpgrade(Long clusterId, String namespace, String name, HelmUpgradeRequest request) {
        if (request == null) {
            throw new BizException(ContainerErrorCode.HELM_RELEASE_INVALID, "升级参数为空");
        }
        String ns = requireNamespace(namespace);
        String releaseName = requireReleaseName(name);
        assertValuesObject(request.getValuesYaml());
        ResolvedChart chart = resolve(request.getChartRef(), request.getArtifactId(), request.getVersion());
        ClusterEntity cluster = requireCluster(clusterId);
        return new PreparedUpgrade(ns, releaseName, chart, HelmWorkspace.open(decrypt(cluster)));
    }

    private HelmReleaseVO describe(HelmWorkspace workspace, Long clusterId, String namespace, String name) {
        String ns = requireNamespace(namespace);
        String releaseName = requireReleaseName(name);
        HelmProcessResult status = run(workspace,
                HelmReleaseCommands.status(binary(), workspace.kubeconfigFile(), ns, releaseName),
                null, queryTimeout(), HelmOp.GET, null);
        HelmReleaseVO release = HelmOutputParser.status(status.stdout());
        release.setClusterId(Long.toString(clusterId));
        if (release.getNamespace() == null || release.getNamespace().isBlank()) {
            release.setNamespace(ns);
        }
        if (release.getName() == null || release.getName().isBlank()) {
            release.setName(releaseName);
        }
        release.setValuesYaml(readValues(workspace, ns, releaseName, null));
        release.setResources(HelmOutputParser.resources(release.getManifest()));
        release.setHistory(history(workspace, ns, releaseName));
        attachArtifact(release);
        return release;
    }

    private List<HelmReleaseHistoryVO> history(HelmWorkspace workspace, String namespace, String name) {
        String ns = requireNamespace(namespace);
        String releaseName = requireReleaseName(name);
        HelmProcessResult result = run(workspace,
                HelmReleaseCommands.history(binary(), workspace.kubeconfigFile(), ns, releaseName),
                null, queryTimeout(), HelmOp.GET, null);
        List<HelmReleaseHistoryVO> items = HelmOutputParser.history(result.stdout());
        for (HelmReleaseHistoryVO item : items) {
            item.setValuesYaml(readValues(workspace, ns, releaseName, item.getRevision()));
        }
        return items;
    }

    private String readValues(HelmWorkspace workspace, String namespace, String name, Integer revision) {
        HelmProcessResult result = run(workspace,
                HelmReleaseCommands.values(binary(), workspace.kubeconfigFile(), namespace, name, revision),
                null, queryTimeout(), HelmOp.GET, null);
        return HelmOutputParser.userValues(result.stdout());
    }

    private void login(HelmWorkspace workspace, ResolvedChart chart, String password) {
        String username = chartProperties.getOci().getUsername() == null ? "" : chartProperties.getOci().getUsername().trim();
        if (username.isEmpty()) {
            return;
        }
        if (password.isBlank()) {
            throw new BizException(ContainerErrorCode.HELM_CHART_CREDENTIAL_INVALID, "OCI 用户名已配置但密码为空");
        }
        if (password.indexOf('\n') >= 0 || password.indexOf('\r') >= 0) {
            throw new BizException(ContainerErrorCode.HELM_CHART_CREDENTIAL_INVALID, "OCI 密码不能包含换行");
        }
        HelmProcessResult result = processRunner.run(HelmProcessCommand.captured(
                HelmReleaseCommands.login(binary(), chart.ref().host(), username, chart.plainHttp()),
                password.getBytes(StandardCharsets.UTF_8),
                workspace.environment(),
                workspace.root(),
                queryTimeout()));
        if (result.exitCode() != 0) {
            String detail = HelmFailureFormatter.summarize(result, password);
            String message = detail.isBlank() ? "Helm Chart 仓库登录失败" : "Helm Chart 仓库登录失败: " + detail;
            String blob = result.combined() == null ? "" : result.combined().toLowerCase(Locale.ROOT);
            if (blob.contains("unauthorized") || blob.contains("401") || blob.contains("forbidden") || blob.contains("403")) {
                throw new BizException(ContainerErrorCode.HELM_CHART_CREDENTIAL_INVALID, message);
            }
            throw new BizException(ContainerErrorCode.HELM_RELEASE_FAILED, message);
        }
    }

    private HelmProcessResult run(
            HelmWorkspace workspace,
            List<String> argv,
            byte[] stdin,
            Duration timeout,
            HelmOp op,
            String password) {
        HelmProcessResult result = processRunner.run(HelmProcessCommand.captured(
                argv, stdin, workspace.environment(), workspace.root(), timeout));
        if (result.exitCode() == 0) {
            return result;
        }
        throw failure(op, result, password);
    }

    private RuntimeException failure(HelmOp op, HelmProcessResult result, String password) {
        String summary = HelmFailureFormatter.summarize(result, password);
        String blob = result.combined() == null ? "" : result.combined().toLowerCase(Locale.ROOT);
        if (op == HelmOp.INSTALL && (blob.contains("cannot re-use a name") || blob.contains("still in use"))) {
            return new HelmReleaseConflictException(summary.isBlank() ? "Helm Release 已存在" : summary);
        }
        if ((op == HelmOp.GET || op == HelmOp.UPGRADE || op == HelmOp.ROLLBACK || op == HelmOp.UNINSTALL)
                && (blob.contains("release: not found")
                || blob.contains("no revision")
                || blob.contains("has no deployed releases"))) {
            return new HelmReleaseNotFoundException(summary.isBlank() ? null : summary);
        }
        String prefix = switch (op) {
            case INSTALL -> "安装失败";
            case UPGRADE -> "升级失败";
            case ROLLBACK -> "回滚失败";
            case UNINSTALL -> "卸载失败";
            case DRY_RUN -> "试运行失败";
            default -> "Helm 操作失败";
        };
        return new BizException(ContainerErrorCode.HELM_RELEASE_FAILED, summary.isBlank() ? prefix : prefix + ": " + summary);
    }

    private ResolvedChart resolve(String chartRef, Long artifactId, String version) {
        HelmChartArtifactEntity artifact = null;
        if (artifactId != null) {
            artifact = artifactMapper.selectById(artifactId);
            Long tenantId = tenantId();
            if (artifact == null || artifact.getTenantId() == null || !artifact.getTenantId().equals(tenantId)) {
                throw new BizException(ContainerErrorCode.HELM_CHART_NOT_FOUND, "Chart 制品不存在");
            }
            chartRef = artifact.getChartRef();
        }
        if (chartRef == null || chartRef.isBlank()) {
            throw new BizException(ContainerErrorCode.HELM_RELEASE_INVALID, "需要 chartRef 或 artifactId");
        }
        HelmChartRef ref = HelmChartRef.parse(chartRef);
        if (version != null && !version.isBlank() && !version.equals(ref.version())) {
            throw new BizException(ContainerErrorCode.HELM_RELEASE_INVALID, "version 与 chartRef 不一致");
        }
        if (artifact == null) {
            List<HelmChartArtifactEntity> rows = artifactMapper.selectList(new LambdaQueryWrapper<HelmChartArtifactEntity>()
                    .eq(HelmChartArtifactEntity::getTenantId, tenantId())
                    .eq(HelmChartArtifactEntity::getChartRef, ref.chartRef()));
            if (rows != null && rows.size() == 1) {
                artifact = rows.get(0);
            }
        }
        boolean plain = chartProperties.getOci().isInsecure();
        if (artifact != null) {
            HelmChartRepositoryEntity repository = repositoryMapper.selectById(artifact.getRepositoryId());
            if (repository != null && Boolean.TRUE.equals(repository.getInsecure())) {
                plain = true;
            }
        }
        return new ResolvedChart(ref, plain);
    }

    private void attachArtifact(HelmReleaseVO release) {
        if (release.getChartName() == null || release.getChartName().isBlank()
                || release.getChartVersion() == null || release.getChartVersion().isBlank()) {
            if (release.getChartRef() == null) {
                release.setChartRef("");
            }
            return;
        }
        List<HelmChartArtifactEntity> rows = artifactMapper.selectList(new LambdaQueryWrapper<HelmChartArtifactEntity>()
                .eq(HelmChartArtifactEntity::getTenantId, tenantId())
                .eq(HelmChartArtifactEntity::getChartName, release.getChartName())
                .eq(HelmChartArtifactEntity::getVersion, release.getChartVersion()));
        if (rows == null || rows.size() != 1) {
            if (release.getChartRef() == null) {
                release.setChartRef("");
            }
            return;
        }
        HelmChartArtifactEntity row = rows.get(0);
        release.setArtifactId(row.getId());
        release.setRepositoryId(row.getRepositoryId());
        release.setChartRef(row.getChartRef());
    }

    private HelmDryRunVO dryRun(String stdout, String namespace) {
        String manifest = HelmOutputParser.dryRunManifest(stdout);
        HelmDryRunVO vo = new HelmDryRunVO();
        vo.setManifest(manifest);
        List<HelmReleaseResourceVO> resources = HelmOutputParser.resources(manifest);
        for (HelmReleaseResourceVO resource : resources) {
            if (resource.getNamespace() == null || resource.getNamespace().isBlank()) {
                resource.setNamespace(namespace);
            }
        }
        vo.setResources(resources);
        return vo;
    }

    private Path valuesFile(HelmWorkspace workspace, String valuesYaml) {
        if (valuesYaml == null || valuesYaml.isBlank()) {
            return null;
        }
        return workspace.writeValues(valuesYaml);
    }

    private ClusterEntity requireCluster(Long clusterId) {
        if (clusterId == null) {
            throw new BizException(ContainerErrorCode.CLUSTER_NOT_FOUND);
        }
        ClusterEntity cluster = clusterService.getById(clusterId, SecurityHelper.currentTenantId());
        if (!"Connected".equals(cluster.getStatus())) {
            throw new BizException(ContainerErrorCode.CLUSTER_NOT_CONNECTED,
                    "集群状态为 " + cluster.getStatus() + "，无法操作");
        }
        return cluster;
    }

    private String decrypt(ClusterEntity cluster) {
        try {
            String plain = kubeconfigCipher.decrypt(cluster.getKubeconfig());
            if (plain == null || plain.isBlank()) {
                throw new BizException(ContainerErrorCode.CLUSTER_KUBECONFIG_INVALID, "集群 kubeconfig 为空");
            }
            return plain;
        } catch (BizException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new BizException(ContainerErrorCode.CLUSTER_KUBECONFIG_INVALID, "集群 kubeconfig 无法解密");
        }
    }

    private void assertValuesObject(String yaml) {
        if (yaml == null || yaml.isBlank()) {
            return;
        }
        if (yaml.length() > MAX_VALUES_CHARS) {
            throw new BizException(ContainerErrorCode.HELM_RELEASE_INVALID, "Values 超过大小限制");
        }
        LoaderOptions options = new LoaderOptions();
        options.setCodePointLimit(MAX_VALUES_CHARS);
        options.setMaxAliasesForCollections(20);
        options.setNestingDepthLimit(40);
        Object loaded;
        try {
            loaded = new Yaml(new SafeConstructor(options)).load(yaml);
        } catch (RuntimeException e) {
            throw new BizException(ContainerErrorCode.HELM_RELEASE_INVALID, "Values 不是合法的 YAML 对象");
        }
        if (loaded != null && !(loaded instanceof Map)) {
            throw new BizException(ContainerErrorCode.HELM_RELEASE_INVALID, "Values 必须是 YAML 对象");
        }
    }

    private static String requireNamespace(String namespace) {
        return dnsLabel(namespace, "命名空间", 63);
    }

    private static String requireReleaseName(String name) {
        return dnsLabel(name, "Release 名称", 53);
    }

    private static String dnsLabel(String value, String label, int max) {
        if (value == null || value.isBlank()) {
            throw new BizException(ContainerErrorCode.HELM_RELEASE_INVALID, "请填写" + label);
        }
        String trimmed = value.trim();
        if (trimmed.length() > max || !DNS_LABEL.matcher(trimmed).matches()) {
            throw new BizException(ContainerErrorCode.HELM_RELEASE_INVALID, label + " 只能使用小写字母、数字和连字符");
        }
        return trimmed;
    }

    private String binary() {
        String configured = chartProperties.getHelmBinary();
        return configured == null || configured.isBlank() ? "helm" : configured;
    }

    private String password() {
        return chartProperties.getOci().getPassword() == null ? "" : chartProperties.getOci().getPassword();
    }

    private Duration queryTimeout() {
        return Duration.ofSeconds(releaseProperties.queryTimeoutSeconds());
    }

    private Duration operationTimeout(boolean wait) {
        return Duration.ofSeconds(wait ? releaseProperties.waitTimeoutSeconds() : releaseProperties.timeoutSeconds());
    }

    private static String timeoutLabel(Duration timeout) {
        return timeout.toSeconds() + "s";
    }

    private static Long tenantId() {
        Long current = SecurityHelper.currentTenantId();
        return current == null ? HelmChartService.PLATFORM_TENANT_ID : current;
    }

    private enum HelmOp {
        LIST, GET, INSTALL, UPGRADE, ROLLBACK, UNINSTALL, DRY_RUN
    }

    private record ResolvedChart(HelmChartRef ref, boolean plainHttp) {
    }

    private record PreparedInstall(
            String namespace,
            String releaseName,
            ResolvedChart chart,
            HelmWorkspace workspace) {
    }

    private record PreparedUpgrade(
            String namespace,
            String releaseName,
            ResolvedChart chart,
            HelmWorkspace workspace) {
    }
}
