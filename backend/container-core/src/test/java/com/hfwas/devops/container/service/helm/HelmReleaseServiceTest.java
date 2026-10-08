package com.hfwas.devops.container.service.helm;

import com.hfwas.devops.container.dto.HelmInstallRequest;
import com.hfwas.devops.container.dto.HelmReleaseVO;
import com.hfwas.devops.container.dto.HelmRollbackRequest;
import com.hfwas.devops.container.dto.HelmUpgradeRequest;
import com.hfwas.devops.container.entity.ClusterEntity;
import com.hfwas.devops.container.entity.HelmChartArtifactEntity;
import com.hfwas.devops.container.entity.HelmChartRepositoryEntity;
import com.hfwas.devops.container.error.ContainerErrorCode;
import com.hfwas.devops.container.error.HelmReleaseConflictException;
import com.hfwas.devops.container.error.HelmReleaseNotFoundException;
import com.hfwas.devops.container.mapper.HelmChartArtifactMapper;
import com.hfwas.devops.container.mapper.HelmChartRepositoryMapper;
import com.hfwas.devops.container.service.cluster.ClusterService;
import com.hfwas.devops.container.service.cluster.KubeconfigCipher;
import com.hfwas.devops.common.error.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HelmReleaseServiceTest {

    private static final String MARKER = "kube-marker-do-not-leak";
    private static final String PASSWORD = "s3cret-oci";
    /** Mirrors helm 3.18 {@code helm status -o json}: no chart object. */
    private static final String STATUS = """
            {"name":"demo","namespace":"edge","version":1,"manifest":"apiVersion: v1\\nkind: ConfigMap\\nmetadata:\\n  name: demo\\n","info":{"first_deployed":"2026-10-08T00:00:00Z","last_deployed":"2026-10-08T00:00:00Z","deleted":"","description":"Install complete","status":"deployed","notes":"installed"},"config":{},"hooks":null}
            """;
    private static final String HISTORY = """
            [{"revision":1,"updated":"2026-10-08 00:00:00.000000 +0000 UTC","status":"deployed","chart":"sample-0.1.0","app_version":"1.0.0","description":"Install complete"}]
            """;

    @Mock
    private ClusterService clusterService;
    @Mock
    private HelmChartArtifactMapper artifactMapper;
    @Mock
    private HelmChartRepositoryMapper repositoryMapper;

    private RecordingRunner runner;
    private HelmReleaseService service;
    private KubeconfigCipher cipher;

    @BeforeEach
    void setUp() {
        runner = new RecordingRunner();
        cipher = new KubeconfigCipher("test-key");
        HelmChartProperties charts = new HelmChartProperties();
        charts.setHelmBinary("helm");
        charts.getOci().setUsername("robot");
        charts.getOci().setPassword(PASSWORD);
        service = new HelmReleaseService(
                clusterService,
                cipher,
                runner,
                charts,
                new HelmReleaseProperties(),
                artifactMapper,
                repositoryMapper);
    }

    @Test
    void installLogsInPullsChartAndDeletesKubeconfig() throws Exception {
        connectedCluster();
        when(artifactMapper.selectList(any())).thenReturn(List.of(artifact()));
        when(repositoryMapper.selectById(5L)).thenReturn(repository(false));
        runner.handler = command -> {
            String joined = String.join(" ", command.argv());
            assertFalse(joined.contains(PASSWORD));
            assertFalse(joined.contains(MARKER));
            if (joined.contains("registry login")) {
                assertTrue(joined.contains("--password-stdin"));
                assertEquals(PASSWORD, new String(command.stdin()));
                return ok("");
            }
            if (joined.contains(" install ")) {
                assertTrue(Files.exists(command.workDir().resolve("kubeconfig")));
                assertTrue(read(command.workDir().resolve("kubeconfig")).contains(MARKER));
                assertTrue(joined.contains("--version"));
                assertTrue(joined.contains("0.1.0"));
                assertTrue(joined.contains("--create-namespace"));
                return ok("");
            }
            if (joined.contains(" status ")) {
                return ok(STATUS);
            }
            if (joined.contains(" history ")) {
                return ok(HISTORY);
            }
            if (joined.contains(" get values ")) {
                return ok("replicaCount: 3\n");
            }
            return fail("unexpected " + joined);
        };

        HelmInstallRequest request = new HelmInstallRequest();
        request.setName("demo");
        request.setChartRef("oci://harbor.example/charts/sample:0.1.0");
        request.setValuesYaml("replicaCount: 3\n");
        request.setCreateNamespace(true);

        HelmReleaseVO release = service.install(4L, "edge", request);

        assertEquals("demo", release.getName());
        assertEquals("sample", release.getChartName());
        assertEquals("0.1.0", release.getChartVersion());
        assertEquals("1.0.0", release.getAppVersion());
        assertEquals(1, release.getRevision());
        assertEquals("replicaCount: 3\n", release.getValuesYaml());
        assertEquals(5L, release.getRepositoryId());
        assertEquals(9L, release.getArtifactId());
        assertEquals("oci://harbor.example/charts/sample:0.1.0", release.getChartRef());
        assertEquals("edge", release.getResources().get(0).getNamespace());
        assertFalse(Files.exists(runner.commands.get(0).workDir()));
        assertTrue(runner.commands.stream().anyMatch(command -> String.join(" ", command.argv()).contains("registry login")));
    }

    @Test
    void duplicateInstallIsConflictAndRedactsSecrets() {
        connectedCluster();
        when(artifactMapper.selectList(any())).thenReturn(List.of());
        runner.handler = command -> {
            String joined = String.join(" ", command.argv());
            if (joined.contains("registry login")) {
                return ok("");
            }
            return fail("""
                    Pulled: harbor.example/charts/sample:0.1.0
                    Digest: sha256:abc
                    Error: INSTALLATION FAILED: cannot re-use a name that is still in use
                    password: %s
                    client-key-data: %s
                    """.formatted(PASSWORD, MARKER));
        };

        HelmInstallRequest request = new HelmInstallRequest();
        request.setName("demo");
        request.setChartRef("oci://harbor.example/charts/sample:0.1.0");
        HelmReleaseConflictException ex = assertThrows(HelmReleaseConflictException.class,
                () -> service.install(4L, "edge", request));

        assertEquals(ContainerErrorCode.HELM_RELEASE_EXISTS.getCode(), ex.getCode());
        assertEquals("Helm Release 已存在", ex.getMessage());
        assertFalse(ex.getMessage().contains(PASSWORD));
        assertFalse(ex.getMessage().contains(MARKER));
        assertFalse(ex.getMessage().contains("Pulled"));
        assertFalse(ex.getMessage().contains("Digest"));
        assertFalse(Files.exists(runner.commands.get(0).workDir()));
    }

    @Test
    void missingReleaseIsNotFound() {
        connectedCluster();
        runner.handler = command -> fail("Error: release: not found");

        HelmReleaseNotFoundException ex = assertThrows(HelmReleaseNotFoundException.class,
                () -> service.get(4L, "edge", "missing"));
        assertEquals(ContainerErrorCode.HELM_RELEASE_NOT_FOUND.getCode(), ex.getCode());
        assertEquals("Helm Release 不存在", ex.getMessage());
        assertFalse(Files.exists(runner.commands.get(0).workDir()));
    }

    @Test
    void secondUninstallIsNotFoundWithStableMessage() {
        connectedCluster();
        runner.handler = command -> fail("Error: uninstall: Release not loaded: demo");

        HelmReleaseNotFoundException ex = assertThrows(HelmReleaseNotFoundException.class,
                () -> service.uninstall(4L, "edge", "demo"));
        assertEquals(ContainerErrorCode.HELM_RELEASE_NOT_FOUND.getCode(), ex.getCode());
        assertEquals("Helm Release 不存在", ex.getMessage());
        assertFalse(ex.getMessage().contains("Release not loaded"));
    }

    @Test
    void detailFillsChartFromCurrentHistoryRevisionAndLinksArtifact() {
        connectedCluster();
        when(artifactMapper.selectList(any())).thenReturn(List.of(artifact("sample", "0.2.0",
                "oci://harbor.example/charts/sample:0.2.0")));
        runner.handler = command -> {
            String joined = String.join(" ", command.argv());
            if (joined.contains(" status ")) {
                return ok("""
                        {"name":"demo","namespace":"edge","version":2,"manifest":"apiVersion: apps/v1\\nkind: Deployment\\nmetadata:\\n  name: demo\\n","info":{"status":"deployed","last_deployed":"2026-10-08T01:00:00Z"},"config":{},"hooks":null}
                        """);
            }
            if (joined.contains(" history ")) {
                return ok("""
                        [{"revision":1,"updated":"2026-10-08 00:00:00.000000 +0000 UTC","status":"superseded","chart":"oldchart-0.1.0","app_version":"0.1.0","description":"Install complete"},{"revision":2,"updated":"2026-10-08 01:00:00.000000 +0000 UTC","status":"deployed","chart":"sample-0.2.0","app_version":"2.0.0","description":"Upgrade complete"}]
                        """);
            }
            if (joined.contains(" get values ")) {
                return ok("replicaCount: 2\n");
            }
            return fail("unexpected " + joined);
        };

        HelmReleaseVO release = service.get(4L, "edge", "demo");

        assertEquals("sample", release.getChartName());
        assertEquals("0.2.0", release.getChartVersion());
        assertEquals("2.0.0", release.getAppVersion());
        assertEquals(9L, release.getArtifactId());
        assertEquals(5L, release.getRepositoryId());
        assertEquals("oci://harbor.example/charts/sample:0.2.0", release.getChartRef());
        assertEquals("edge", release.getResources().get(0).getNamespace());
    }

    @Test
    void rollbackResponseUsesChartFromNewRevision() {
        connectedCluster();
        when(artifactMapper.selectList(any())).thenReturn(List.of(artifact()));
        runner.handler = command -> {
            String joined = String.join(" ", command.argv());
            if (joined.contains(" rollback ")) {
                return ok("");
            }
            if (joined.contains(" status ")) {
                return ok(STATUS.replace("\"version\":1", "\"version\":3"));
            }
            if (joined.contains(" history ")) {
                return ok(HISTORY.replace("\"revision\":1", "\"revision\":3"));
            }
            if (joined.contains(" get values ")) {
                return ok("replicaCount: 1\n");
            }
            return fail("unexpected " + joined);
        };

        HelmRollbackRequest request = new HelmRollbackRequest();
        request.setRevision(1);
        HelmReleaseVO release = service.rollback(4L, "edge", "demo", request);

        assertEquals(3, release.getRevision());
        assertEquals("sample", release.getChartName());
        assertEquals("0.1.0", release.getChartVersion());
        assertEquals("oci://harbor.example/charts/sample:0.1.0", release.getChartRef());
        assertEquals("edge", release.getResources().get(0).getNamespace());
    }

    @Test
    void resourcesInheritReleaseNamespace() {
        connectedCluster();
        runner.handler = command -> {
            String joined = String.join(" ", command.argv());
            if (joined.contains(" get manifest ")) {
                return ok("""
                        apiVersion: v1
                        kind: ConfigMap
                        metadata:
                          name: demo
                        ---
                        apiVersion: v1
                        kind: Service
                        metadata:
                          name: demo
                          namespace: other
                        """);
            }
            return fail("unexpected " + joined);
        };

        var resources = service.resources(4L, "edge", "demo");
        assertEquals("edge", resources.get(0).getNamespace());
        assertEquals("ConfigMap", resources.get(0).getKind());
        assertEquals("other", resources.get(1).getNamespace());
    }

    @Test
    void otherHelmFailureIsOneSanitizedLine() {
        connectedCluster();
        when(artifactMapper.selectList(any())).thenReturn(List.of(artifact()));
        runner.handler = command -> {
            String joined = String.join(" ", command.argv());
            if (joined.contains("registry login")) {
                return ok("");
            }
            return fail("""
                    Pulled: harbor.example/charts/sample:0.1.0
                    Digest: sha256:abc
                    Status: Downloaded newer image for harbor.example/charts/sample:0.1.0
                    Error: UPGRADE FAILED: password: %s refused
                    client-key-data: %s
                    """.formatted(PASSWORD, MARKER));
        };

        HelmUpgradeRequest request = new HelmUpgradeRequest();
        request.setChartRef("oci://harbor.example/charts/sample:0.1.0");
        BizException ex = assertThrows(BizException.class, () -> service.upgrade(4L, "edge", "demo", request));

        assertEquals(ContainerErrorCode.HELM_RELEASE_FAILED.getCode(), ex.getCode());
        assertEquals("升级失败: UPGRADE FAILED: password: ****** refused", ex.getMessage());
        assertFalse(ex.getMessage().contains("\n"));
        assertFalse(ex.getMessage().contains(PASSWORD));
        assertFalse(ex.getMessage().contains(MARKER));
        assertFalse(ex.getMessage().contains("Pulled"));
        assertFalse(ex.getMessage().contains("Digest"));
        assertFalse(ex.getMessage().contains("Downloaded"));
    }

    @Test
    void dryRunReturnsRenderedManifestWithoutInstalling() {
        connectedCluster();
        runner.handler = command -> {
            String joined = String.join(" ", command.argv());
            if (joined.contains("registry login")) {
                return ok("");
            }
            assertTrue(joined.contains("--dry-run=client"));
            assertFalse(joined.contains(" upgrade "));
            return ok("""
                    {"name":"demo","manifest":"apiVersion: v1\\nkind: Service\\nmetadata:\\n  name: demo\\n"}
                    """);
        };

        HelmInstallRequest request = new HelmInstallRequest();
        request.setName("demo");
        request.setArtifactId(9L);
        when(artifactMapper.selectById(9L)).thenReturn(artifact());
        var preview = service.dryRunInstall(4L, "edge", request);

        assertTrue(preview.getManifest().contains("kind: Service"));
        assertEquals("Service", preview.getResources().get(0).getKind());
        assertEquals("edge", preview.getResources().get(0).getNamespace());
        assertTrue(runner.commands.stream().noneMatch(command -> String.join(" ", command.argv()).contains(" status ")));
    }

    @Test
    void upgradeUsesAtomicWhenRollbackOnFailure() {
        connectedCluster();
        when(artifactMapper.selectList(any())).thenReturn(List.of(artifact()));
        when(repositoryMapper.selectById(5L)).thenReturn(repository(false));
        runner.handler = command -> {
            String joined = String.join(" ", command.argv());
            if (joined.contains("registry login")) {
                return ok("");
            }
            if (joined.contains(" upgrade ")) {
                assertTrue(joined.contains("--atomic"));
                assertFalse(joined.contains("--wait"));
                return ok("");
            }
            if (joined.contains(" status ")) {
                return ok(STATUS);
            }
            if (joined.contains(" history ")) {
                return ok(HISTORY);
            }
            if (joined.contains(" get values ")) {
                return ok("replicaCount: 1\n");
            }
            return fail("unexpected " + joined);
        };

        HelmUpgradeRequest request = new HelmUpgradeRequest();
        request.setChartRef("oci://harbor.example/charts/sample:0.1.0");
        request.setVersion("0.1.0");
        request.setValuesYaml("replicaCount: 1\n");
        request.setRollbackOnFailure(true);
        request.setWait(true);

        HelmReleaseVO release = service.upgrade(4L, "edge", "demo", request);
        assertEquals(1, release.getRevision());
    }

    @Test
    void disconnectedClusterDoesNotRunHelm() {
        ClusterEntity cluster = new ClusterEntity();
        cluster.setId(4L);
        cluster.setStatus("Disconnected");
        cluster.setKubeconfig(cipher.encrypt("apiVersion: v1\n"));
        when(clusterService.getById(eq(4L), isNull())).thenReturn(cluster);

        BizException ex = assertThrows(BizException.class, () -> service.list(4L, null));
        assertEquals(ContainerErrorCode.CLUSTER_NOT_CONNECTED.getCode(), ex.getCode());
        assertTrue(runner.commands.isEmpty());
    }

    private void connectedCluster() {
        ClusterEntity cluster = new ClusterEntity();
        cluster.setId(4L);
        cluster.setTenantId(0L);
        cluster.setStatus("Connected");
        cluster.setKubeconfig(cipher.encrypt("apiVersion: v1\nkind: Config\n# " + MARKER + "\n"));
        when(clusterService.getById(eq(4L), isNull())).thenReturn(cluster);
    }

    private static HelmChartArtifactEntity artifact() {
        return artifact("sample", "0.1.0", "oci://harbor.example/charts/sample:0.1.0");
    }

    private static HelmChartArtifactEntity artifact(String chartName, String version, String chartRef) {
        HelmChartArtifactEntity entity = new HelmChartArtifactEntity();
        entity.setId(9L);
        entity.setRepositoryId(5L);
        entity.setTenantId(0L);
        entity.setChartName(chartName);
        entity.setVersion(version);
        entity.setChartRef(chartRef);
        return entity;
    }

    private static HelmChartRepositoryEntity repository(boolean insecure) {
        HelmChartRepositoryEntity entity = new HelmChartRepositoryEntity();
        entity.setId(5L);
        entity.setTenantId(0L);
        entity.setName("default");
        entity.setInsecure(insecure);
        return entity;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static HelmProcessResult ok(String stdout) {
        return new HelmProcessResult(0, stdout, "");
    }

    private static HelmProcessResult fail(String stderr) {
        return new HelmProcessResult(1, "", stderr);
    }

    private static final class RecordingRunner implements HelmProcessRunner {
        private final List<HelmProcessCommand> commands = new ArrayList<>();
        private java.util.function.Function<HelmProcessCommand, HelmProcessResult> handler = command -> ok("");

        @Override
        public HelmProcessResult run(HelmProcessCommand command) {
            commands.add(command);
            return handler.apply(command);
        }
    }
}
