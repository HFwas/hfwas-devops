package com.hfwas.devops.container.service.helm;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.container.error.ContainerErrorCode;
import com.hfwas.devops.container.error.HelmChartConflictException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelmCliOciPusherTest {

    @TempDir
    Path tempDir;

    @Test
    void pushesWhenRemoteTagIsMissingAndKeepsPasswordOffArgv() throws Exception {
        Path archive = Files.write(tempDir.resolve("sample-0.1.0.tgz"), new byte[]{1, 2, 3});
        RecordingRunner runner = new RecordingRunner();
        runner.results.add(new HelmProcessResult(0, "", ""));
        runner.results.add(new HelmProcessResult(1, "", "Error: chart not found"));
        runner.results.add(new HelmProcessResult(0, "Pushed: harbor.example/charts/sample:0.1.0\nDigest: sha256:abc\n", ""));
        HelmCliOciPusher pusher = new HelmCliOciPusher(runner, properties(false));

        OciPushResult result = pusher.push(request(archive, false));

        assertEquals("oci://harbor.example/charts/sample:0.1.0", result.chartRef());
        assertEquals("sha256:abc", result.digest());
        assertEquals(3, runner.commands.size());
        List<String> login = runner.commands.get(0).argv();
        assertTrue(login.contains("--password-stdin"));
        assertFalse(login.contains("s3cret"));
        assertEquals("s3cret", new String(runner.commands.get(0).stdin(), StandardCharsets.UTF_8));
        assertTrue(runner.commands.get(2).argv().contains("oci://harbor.example/charts"));
        assertFalse(runner.commands.get(2).argv().contains("--plain-http"));
        assertEquals("sha256:abc", HelmCliOciPusher.parseDigest(runner.results.get(2).stdout()));
    }

    @Test
    void doesNotPushWhenTagAlreadyExists() throws Exception {
        Path archive = Files.write(tempDir.resolve("sample-0.1.0.tgz"), new byte[]{1});
        RecordingRunner runner = new RecordingRunner();
        runner.results.add(new HelmProcessResult(0, "", ""));
        runner.results.add(new HelmProcessResult(0, "apiVersion: v2\n", ""));
        HelmCliOciPusher pusher = new HelmCliOciPusher(runner, properties(true));

        assertThrows(HelmChartConflictException.class, () -> pusher.push(request(archive, true)));
        assertEquals(2, runner.commands.size());
        assertTrue(runner.commands.get(0).argv().contains("--insecure"));
        assertTrue(runner.commands.get(1).argv().contains("--plain-http"));
    }

    @Test
    void authFailureIsNotTreatedAsMissingChart() throws Exception {
        Path archive = Files.write(tempDir.resolve("sample-0.1.0.tgz"), new byte[]{1});
        RecordingRunner runner = new RecordingRunner();
        runner.results.add(new HelmProcessResult(0, "", ""));
        runner.results.add(new HelmProcessResult(1, "", "Error: unauthorized: authentication required"));
        HelmCliOciPusher pusher = new HelmCliOciPusher(runner, properties(false));

        BizException ex = assertThrows(BizException.class, () -> pusher.push(request(archive, false)));
        assertEquals(ContainerErrorCode.HELM_CHART_PUSH_FAILED.getCode(), ex.getCode());
        assertFalse(ex.getMessage().contains("s3cret"));
        assertEquals(2, runner.commands.size());
    }

    @Test
    void redactsPasswordFromHelmOutput() {
        assertEquals("login failed: ******", HelmCliOciPusher.redact("login failed: s3cret", "s3cret"));
    }

    private static OciPushRequest request(Path archive, boolean plainHttp) {
        return new OciPushRequest(
                archive,
                new OciChartReference("harbor.example", "charts", plainHttp),
                "robot$charts",
                "s3cret",
                "sample",
                "0.1.0");
    }

    private static HelmChartProperties properties(boolean insecure) {
        HelmChartProperties properties = new HelmChartProperties();
        properties.setHelmBinary("helm");
        properties.setPushTimeoutSeconds(15);
        properties.getOci().setInsecure(insecure);
        properties.getOci().setUsername("robot$charts");
        properties.getOci().setPassword("s3cret");
        return properties;
    }

    private static final class RecordingRunner implements HelmProcessRunner {
        private final List<HelmProcessCommand> commands = new ArrayList<>();
        private final List<HelmProcessResult> results = new ArrayList<>();

        @Override
        public HelmProcessResult run(HelmProcessCommand command) {
            commands.add(command);
            return results.get(commands.size() - 1);
        }
    }
}
