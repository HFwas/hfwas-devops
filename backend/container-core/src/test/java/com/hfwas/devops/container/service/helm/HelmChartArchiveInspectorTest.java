package com.hfwas.devops.container.service.helm;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.container.error.ContainerErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelmChartArchiveInspectorTest {

    private static final HelmChartArchiveLimits LIMITS = new HelmChartArchiveLimits(50, 1024 * 1024, 64 * 1024);

    private final HelmChartArchiveInspector inspector = new HelmChartArchiveInspector();

    @TempDir
    Path tempDir;

    @Test
    void fixtureChartParsesNameAndVersion() throws Exception {
        Path fixture = tempDir.resolve("sample-0.1.0.tgz");
        try (var in = getClass().getResourceAsStream("/helm/sample-0.1.0.tgz")) {
            assertTrue(in != null, "fixture chart is missing");
            Files.copy(in, fixture);
        }
        HelmChartPackageMeta meta = inspector.inspect(fixture, LIMITS);
        assertEquals("sample", meta.name());
        assertEquals("0.1.0", meta.version());
        assertEquals("v2", meta.apiVersion());
        assertEquals("1.0.0", meta.appVersion());
        assertEquals("Tiny fixture chart for upload tests", meta.description());
    }

    @Test
    void readsKeywordsReadmeAndValues() throws Exception {
        String chart = """
                apiVersion: v2
                name: sample
                description: Tiny fixture chart
                type: application
                version: 0.2.0
                appVersion: "1.10"
                keywords:
                  - web
                  - demo
                """;
        byte[] bytes = ChartArchiveFixtures.tgz(
                ChartArchiveFixtures.file("sample/Chart.yaml", chart),
                ChartArchiveFixtures.file("sample/values.yaml", "replicaCount: 2\n"),
                ChartArchiveFixtures.file("sample/README.md", "# Hello\n"));
        Path archive = ChartArchiveFixtures.write(tempDir, "meta.tgz", bytes);

        HelmChartPackageMeta meta = inspector.inspect(archive, LIMITS);

        assertEquals(java.util.List.of("web", "demo"), meta.keywords());
        assertEquals("# Hello\n", meta.readme());
        assertEquals("replicaCount: 2\n", meta.valuesYaml());
    }

    @Test
    void keepsUnquotedAppVersionDigits() throws Exception {
        Path archive = ChartArchiveFixtures.write(tempDir, "sample-1.10.0.tgz",
                ChartArchiveFixtures.validChart("sample", "1.10.0"));
        HelmChartPackageMeta meta = inspector.inspect(archive, LIMITS);
        assertEquals("1.10.0", meta.version());
        assertEquals("1.10", meta.appVersion());
    }

    @Test
    void allowsNestedDependencyChartYaml() throws Exception {
        byte[] bytes = ChartArchiveFixtures.tgz(
                ChartArchiveFixtures.file("sample/Chart.yaml", ChartArchiveFixtures.chartYaml("sample", "0.2.0")),
                ChartArchiveFixtures.file("sample/charts/dep/Chart.yaml", ChartArchiveFixtures.chartYaml("dep", "0.1.0")),
                ChartArchiveFixtures.file("sample/values.yaml", "x: 1\n"));
        Path archive = ChartArchiveFixtures.write(tempDir, "nested.tgz", bytes);
        HelmChartPackageMeta meta = inspector.inspect(archive, LIMITS);
        assertEquals("sample", meta.name());
        assertEquals("0.2.0", meta.version());
    }

    @Test
    void rejectsZipSlip() throws Exception {
        byte[] bytes = ChartArchiveFixtures.tgz(
                ChartArchiveFixtures.file("sample/Chart.yaml", ChartArchiveFixtures.chartYaml("sample", "0.1.0")),
                ChartArchiveFixtures.file("sample/../../etc/passwd", "root\n"));
        Path archive = ChartArchiveFixtures.write(tempDir, "slip.tgz", bytes);
        BizException ex = assertThrows(BizException.class, () -> inspector.inspect(archive, LIMITS));
        assertEquals(ContainerErrorCode.HELM_CHART_INVALID.getCode(), ex.getCode());
    }

    @Test
    void rejectsAbsolutePath() throws Exception {
        byte[] bytes = ChartArchiveFixtures.tgz(
                ChartArchiveFixtures.file("/tmp/Chart.yaml", ChartArchiveFixtures.chartYaml("sample", "0.1.0")));
        Path archive = ChartArchiveFixtures.write(tempDir, "abs.tgz", bytes);
        BizException ex = assertThrows(BizException.class, () -> inspector.inspect(archive, LIMITS));
        assertEquals(ContainerErrorCode.HELM_CHART_INVALID.getCode(), ex.getCode());
    }

    @Test
    void rejectsSymlink() throws Exception {
        byte[] bytes = ChartArchiveFixtures.tgz(
                ChartArchiveFixtures.file("sample/Chart.yaml", ChartArchiveFixtures.chartYaml("sample", "0.1.0")),
                ChartArchiveFixtures.symlink("sample/link", "../outside"));
        Path archive = ChartArchiveFixtures.write(tempDir, "link.tgz", bytes);
        BizException ex = assertThrows(BizException.class, () -> inspector.inspect(archive, LIMITS));
        assertEquals(ContainerErrorCode.HELM_CHART_INVALID.getCode(), ex.getCode());
    }

    @Test
    void rejectsMissingChartYaml() throws Exception {
        byte[] bytes = ChartArchiveFixtures.tgz(ChartArchiveFixtures.file("sample/values.yaml", "a: 1\n"));
        Path archive = ChartArchiveFixtures.write(tempDir, "missing.tgz", bytes);
        BizException ex = assertThrows(BizException.class, () -> inspector.inspect(archive, LIMITS));
        assertEquals(ContainerErrorCode.HELM_CHART_INVALID.getCode(), ex.getCode());
    }

    @Test
    void rejectsSecondTopLevelChart() throws Exception {
        byte[] bytes = ChartArchiveFixtures.tgz(
                ChartArchiveFixtures.file("sample/Chart.yaml", ChartArchiveFixtures.chartYaml("sample", "0.1.0")),
                ChartArchiveFixtures.file("other/Chart.yaml", ChartArchiveFixtures.chartYaml("other", "0.1.0")));
        Path archive = ChartArchiveFixtures.write(tempDir, "two.tgz", bytes);
        BizException ex = assertThrows(BizException.class, () -> inspector.inspect(archive, LIMITS));
        assertEquals(ContainerErrorCode.HELM_CHART_INVALID.getCode(), ex.getCode());
    }

    @Test
    void rejectsDirectoryNameMismatch() throws Exception {
        byte[] bytes = ChartArchiveFixtures.tgz(
                ChartArchiveFixtures.file("other/Chart.yaml", ChartArchiveFixtures.chartYaml("sample", "0.1.0")));
        Path archive = ChartArchiveFixtures.write(tempDir, "mismatch.tgz", bytes);
        BizException ex = assertThrows(BizException.class, () -> inspector.inspect(archive, LIMITS));
        assertEquals(ContainerErrorCode.HELM_CHART_INVALID.getCode(), ex.getCode());
    }

    @Test
    void rejectsOversizedUncompressedPayload() throws Exception {
        byte[] bytes = ChartArchiveFixtures.tgz(
                ChartArchiveFixtures.file("sample/Chart.yaml", ChartArchiveFixtures.chartYaml("sample", "0.1.0")),
                ChartArchiveFixtures.file("sample/big.txt", new byte[200]));
        Path archive = ChartArchiveFixtures.write(tempDir, "big.tgz", bytes);
        HelmChartArchiveLimits tight = new HelmChartArchiveLimits(20, 150, 64 * 1024);
        BizException ex = assertThrows(BizException.class, () -> inspector.inspect(archive, tight));
        assertEquals(ContainerErrorCode.HELM_CHART_TOO_LARGE.getCode(), ex.getCode());
    }

    @Test
    void rejectsTooManyEntries() throws Exception {
        byte[] bytes = ChartArchiveFixtures.tgz(
                ChartArchiveFixtures.file("sample/Chart.yaml", ChartArchiveFixtures.chartYaml("sample", "0.1.0")),
                ChartArchiveFixtures.file("sample/a.txt", "a"),
                ChartArchiveFixtures.file("sample/b.txt", "b"));
        Path archive = ChartArchiveFixtures.write(tempDir, "many.tgz", bytes);
        HelmChartArchiveLimits tight = new HelmChartArchiveLimits(2, 1024 * 1024, 64 * 1024);
        BizException ex = assertThrows(BizException.class, () -> inspector.inspect(archive, tight));
        assertEquals(ContainerErrorCode.HELM_CHART_TOO_LARGE.getCode(), ex.getCode());
    }

    @Test
    void rejectsBuildMetadataAndUppercaseName() throws Exception {
        Path plus = ChartArchiveFixtures.write(tempDir, "plus.tgz", ChartArchiveFixtures.tgz(
                ChartArchiveFixtures.file("sample/Chart.yaml",
                        ChartArchiveFixtures.chartYaml("sample", "1.0.0+build.1"))));
        BizException plusEx = assertThrows(BizException.class, () -> inspector.inspect(plus, LIMITS));
        assertEquals(ContainerErrorCode.HELM_CHART_INVALID.getCode(), plusEx.getCode());

        Path upper = ChartArchiveFixtures.write(tempDir, "upper.tgz", ChartArchiveFixtures.tgz(
                ChartArchiveFixtures.file("Sample/Chart.yaml",
                        ChartArchiveFixtures.chartYaml("Sample", "1.0.0"))));
        BizException upperEx = assertThrows(BizException.class, () -> inspector.inspect(upper, LIMITS));
        assertEquals(ContainerErrorCode.HELM_CHART_INVALID.getCode(), upperEx.getCode());
    }
}
