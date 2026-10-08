package com.hfwas.devops.container.service.helm;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DefaultHelmProcessRunnerTest {

    @Test
    void dropsInheritedSecretsBeforeCommandEnvironmentIsApplied() {
        Map<String, String> env = new HashMap<>();
        env.put("PATH", "/usr/bin");
        env.put("HELM_CHART_OCI_PASSWORD", "s3cret");
        env.put("KUBECONFIG", "/tmp/old");
        env.put("HTTP_PROXY", "http://127.0.0.1:7890");

        DefaultHelmProcessRunner.scrubInheritedSecrets(env);
        env.put("KUBECONFIG", "/tmp/private/kubeconfig");

        assertEquals("/usr/bin", env.get("PATH"));
        assertEquals("http://127.0.0.1:7890", env.get("HTTP_PROXY"));
        assertEquals("/tmp/private/kubeconfig", env.get("KUBECONFIG"));
        assertFalse(env.containsKey("HELM_CHART_OCI_PASSWORD"));
    }
}
