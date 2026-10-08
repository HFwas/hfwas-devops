package com.hfwas.devops.container.service.helm;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelmReleaseCommandsTest {

    private final Path kubeconfig = Path.of("/tmp/helm-release/kubeconfig");
    private final HelmChartRef chart = HelmChartRef.parse("oci://harbor.example/charts/sample:0.1.0");

    @Test
    void installSplitsVersionAndKeepsSecretsOutOfArgv() {
        List<String> argv = HelmReleaseCommands.install(
                "helm", kubeconfig, chart, "demo", "edge", Path.of("/tmp/values.yaml"),
                true, true, false, true, "180s");

        assertEquals("oci://harbor.example/charts/sample", argv.get(3));
        assertTrue(argv.contains("--version"));
        assertTrue(argv.contains("0.1.0"));
        assertTrue(argv.contains("--create-namespace"));
        assertTrue(argv.contains("--wait"));
        assertTrue(argv.contains("--plain-http"));
        assertTrue(argv.contains(kubeconfig.toString()));
        assertFalse(String.join(" ", argv).contains("s3cret"));
    }

    @Test
    void upgradeDryRunAndAtomicDoNotShareFlags() {
        List<String> dryRun = HelmReleaseCommands.upgrade(
                "helm", kubeconfig, chart, "demo", "edge", null,
                true, true, true, false, "600s");
        assertTrue(dryRun.contains("--dry-run=client"));
        assertFalse(dryRun.contains("--atomic"));
        assertFalse(dryRun.contains("--wait"));

        List<String> atomic = HelmReleaseCommands.upgrade(
                "helm", kubeconfig, chart, "demo", "edge", null,
                true, true, false, false, "600s");
        assertTrue(atomic.contains("--atomic"));
        assertFalse(atomic.contains("--wait"));
        assertTrue(atomic.contains("600s"));
    }

    @Test
    void rollbackAndListShape() {
        List<String> rollback = HelmReleaseCommands.rollback("helm", kubeconfig, "edge", "demo", 2, false, "180s");
        assertEquals(List.of("helm", "rollback", "demo", "2", "--namespace", "edge", "--kubeconfig", kubeconfig.toString()), rollback);

        List<String> list = HelmReleaseCommands.list("helm", kubeconfig, null);
        assertTrue(list.contains("--all-namespaces"));
        assertTrue(list.contains("--all"));
    }
}
