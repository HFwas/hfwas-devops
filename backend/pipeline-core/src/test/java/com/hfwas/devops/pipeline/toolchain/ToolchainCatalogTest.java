package com.hfwas.devops.pipeline.toolchain;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolchainCatalogTest {

    private final ToolchainCatalog catalog = new ToolchainCatalog();

    @Test
    void resolvesJava21Maven39() {
        ToolchainResolved resolved = catalog.resolve(PipelineStack.JAVA_MAVEN, "21", "3.9");
        assertEquals("maven:3.9.9-eclipse-temurin-21", resolved.image());
        assertEquals("mvn -B -DskipTests package", resolved.buildCommand());
        assertEquals("mvn -B test", resolved.testCommand());
    }

    @Test
    void resolvesNode22Pnpm() {
        ToolchainResolved resolved = catalog.resolve(PipelineStack.NODE, "22", "PNPM");
        assertEquals("node:22-bookworm", resolved.image());
        assertTrue(resolved.buildCommand().contains("pnpm"));
    }

    @Test
    void rejectsUnknownCombo() {
        BizException ex = assertThrows(BizException.class,
                () -> catalog.resolve(PipelineStack.JAVA_MAVEN, "21", "2.0"));
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("不支持的工具链组合"));
    }

    @Test
    void listsOnlyKnownCombos() {
        assertTrue(catalog.list().stream().anyMatch(item ->
                item.stack() == PipelineStack.GO && "1.23".equals(item.runtimeVersion())));
    }
}
