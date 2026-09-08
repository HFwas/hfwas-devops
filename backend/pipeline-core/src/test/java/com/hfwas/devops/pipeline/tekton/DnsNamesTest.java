package com.hfwas.devops.pipeline.tekton;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DnsNamesTest {

    @Test
    void chineseJobNamesUseKindSoCloneDoesNotCollideWithBuild() {
        assertEquals("clone", DnsNames.stepName("代码克隆", "CLONE"));
        assertEquals("build", DnsNames.stepName("代码构建", "BUILD"));
        assertEquals("build-job", DnsNames.stepName("Build Job!", "BUILD"));
        List<String> assigned = DnsNames.assignJobStepNames(
                List.of("代码克隆", "代码构建", "执行Shell命令"),
                List.of("CLONE", "BUILD", "CUSTOM"));
        assertEquals(List.of("clone", "build", "shell"), assigned);
    }

    @Test
    void stepPrefixDoesNotStealLaterUniqueNames() {
        List<String> assigned = List.of("step", "step-2", "step-3");
        assertTrue(DnsNames.stepBelongsTo("step", "step", assigned));
        assertFalse(DnsNames.stepBelongsTo("step-2", "step", assigned));
        assertTrue(DnsNames.stepBelongsTo("step-2", "step-2", assigned));
        assertTrue(DnsNames.stepBelongsTo("image-crane", "image", List.of("image", "build")));
        assertTrue(DnsNames.stepBelongsTo("image-2-cosign", "image-2", List.of("image", "image-2")));
        assertFalse(DnsNames.stepBelongsTo("image-2-cosign", "image", List.of("image", "image-2")));
    }

    @Test
    void parsesCommitMarkerFromCloneLog() {
        assertEquals("48b618f9408d74b9eb5f260be9db9d087350203a", DnsNames.parseCommitSha("""
                Cloning into 'src'...
                HFWAS_GIT_REF=main
                HFWAS_COMMIT=48b618f9408d74b9eb5f260be9db9d087350203a
                """.stripIndent()));
        assertEquals(null, DnsNames.parseCommitSha("no marker"));
    }
}
