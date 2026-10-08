package com.hfwas.devops.container.service.helm;

import com.hfwas.devops.container.dto.HelmInstallRequest;
import com.hfwas.devops.container.dto.HelmReleaseVO;
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
    private static final String STATUS = """
            {"name":"demo","namespace":"edge","version":1,"manifest":"apiVersion: v1\\nkind: ConfigMap\\nmetadata:\\n  name: demo\\n","info":{"status":"deployed","last_deployed":"2026-10-08T00:00:00Z","notes":"installed"},"chart":{"metadata":{"name":"sample","version":"0.1.0","appVersion":"1.0.0"}}}
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
        assertEquals(1, release.getRevision());
        assertEquals("replicaCount: 3\n", release.getValuesYaml());
        assertEquals(5L, release.getRepositoryId());
        assertEquals("oci://harbor.example/charts/sample:0.1.0", release.getChartRef());
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
            return fail("Error: cannot re-use a name that is still in use\npassword: " + PASSWORD
                    + "\nclient-key-data: " + MARKER);
        };

        HelmInstallRequest request = new HelmInstallRequest();
        request.setName("demo");
        request.setChartRef("oci://harbor.example/charts/sample:0.1.0");
        HelmReleaseConflictException ex = assertThrows(HelmReleaseConflictException.class,
                () -> service.install(4L, "edge", request));

        assertEquals(ContainerErrorCode.HELM_RELEASE_EXISTS.getCode(), ex.getCode());
        assertFalse(ex.getMessage().contains(PASSWORD));
        assertFalse(ex.getMessage().contains(MARKER));
        assertFalse(Files.exists(runner.commands.get(0).workDir()));
    }

    @Test
    void missingReleaseIsNotFound() {
        connectedCluster();
        runner.handler = command -> fail("Error: release: not found");

        HelmReleaseNotFoundException ex = assertThrows(HelmReleaseNotFoundException.class,
                () -> service.get(4L, "edge", "missing"));
        assertEquals(ContainerErrorCode.HELM_RELEASE_NOT_FOUND.getCode(), ex.getCode());
        assertFalse(Files.exists(runner.commands.get(0).workDir()));
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
        HelmChartArtifactEntity entity = new HelmChartArtifactEntity();
        entity.setId(9L);
        entity.setRepositoryId(5L);
        entity.setTenantId(0L);
        entity.setChartName("sample");
        entity.setVersion("0.1.0");
        entity.setChartRef("oci://harbor.example/charts/sample:0.1.0");
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
