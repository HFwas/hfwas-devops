package com.hfwas.devops.container.service.helm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HelmFailureFormatterTest {

    @Test
    void summarizeKeepsTheErrorLineAndDropsPullNoise() {
        HelmProcessResult result = new HelmProcessResult(1, "", """
                Pulled: harbor.example/charts/sample:0.1.0
                Digest: sha256:abc
                Status: Downloaded newer image for harbor.example/charts/sample:0.1.0
                Error: INSTALLATION FAILED: cannot re-use a name that is still in use
                """);

        String summary = HelmFailureFormatter.summarize(result, "s3cret-oci");

        assertEquals("INSTALLATION FAILED: cannot re-use a name that is still in use", summary);
        assertFalse(summary.contains("\n"));
        assertFalse(summary.contains("Pulled"));
        assertFalse(summary.contains("sha256"));
    }

    @Test
    void summarizeRedactsSecretsOnTheKeptLine() {
        HelmProcessResult result = new HelmProcessResult(1, "",
                "Error: UPGRADE FAILED: password: s3cret-oci client-key-data: kube-marker");

        String summary = HelmFailureFormatter.summarize(result, "s3cret-oci");

        assertEquals("UPGRADE FAILED: password: ****** client-key-data: ******", summary);
        assertFalse(summary.contains("s3cret-oci"));
        assertFalse(summary.contains("kube-marker"));
    }
}
