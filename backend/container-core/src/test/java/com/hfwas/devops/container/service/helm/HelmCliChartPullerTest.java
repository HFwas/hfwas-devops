package com.hfwas.devops.container.service.helm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelmCliChartPullerTest {

    @TempDir
    Path tempDir;

    @Test
    void pullLogsInThenDownloadsWithoutPuttingPasswordOnArgv() throws Exception {
        RecordingRunner runner = new RecordingRunner();
        runner.handler = command -> {
            String joined = String.join(" ", command.argv());
            assertFalse(joined.contains("s3cret"));
            if (joined.contains("registry login")) {
                assertEquals("s3cret", new String(command.stdin(), StandardCharsets.UTF_8));
                return new HelmProcessResult(0, "", "");
            }
            assertTrue(joined.contains("pull"));
            assertTrue(joined.contains("--version"));
            assertTrue(joined.contains("0.1.0"));
            assertFalse(joined.contains("oci://harbor.example/charts/sample:0.1.0"));
            try {
                Files.write(command.workDir().resolve("sample-0.1.0.tgz"), new byte[]{1, 2, 3});
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return new HelmProcessResult(0, "", "");
        };
        HelmChartProperties properties = new HelmChartProperties();
        properties.setHelmBinary("helm");
        HelmCliChartPuller puller = new HelmCliChartPuller(runner, properties);

        Path archive = puller.pull(new OciPullRequest(
                "oci://harbor.example/charts/sample:0.1.0",
                false,
                "robot",
                "s3cret",
                tempDir,
                Duration.ofSeconds(5)));

        assertEquals("sample-0.1.0.tgz", archive.getFileName().toString());
        assertFalse(new OciPullRequest("oci://harbor.example/charts/sample:0.1.0", false, "robot", "s3cret", tempDir, Duration.ofSeconds(5))
                .toString().contains("s3cret"));
    }

    private static final class RecordingRunner implements HelmProcessRunner {
        private final List<HelmProcessCommand> commands = new ArrayList<>();
        private java.util.function.Function<HelmProcessCommand, HelmProcessResult> handler;

        @Override
        public HelmProcessResult run(HelmProcessCommand command) {
            commands.add(command);
            return handler.apply(command);
        }
    }
}
