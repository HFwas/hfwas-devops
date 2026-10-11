package com.hfwas.devops.pipeline.param;

import com.hfwas.devops.common.error.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TaskKindVersionsTest {

    @Test
    void omittedVersionStartsAtOneAndBumpsPatch() {
        assertEquals("1.0.0", TaskKindVersions.resolve(null, null));
        assertEquals("1.0.0", TaskKindVersions.resolve("  ", "  "));
        assertEquals("1.0.0", TaskKindVersions.resolve("draft", null));
        assertEquals("1.2.1", TaskKindVersions.resolve("1.2.0", null));
        assertEquals("2.0.0", TaskKindVersions.resolve("1.2.0", " 2.0.0 "));
    }

    @Test
    void explicitVersionMustBeSemver() {
        assertThrows(BizException.class, () -> TaskKindVersions.resolve("1.0.0", "1.2"));
        assertThrows(BizException.class, () -> TaskKindVersions.resolve("1.0.0", "v1.2.0"));
    }
}
