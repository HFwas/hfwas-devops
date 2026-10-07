package com.hfwas.devops.container.service.helm;

import com.hfwas.devops.common.error.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OciChartReferenceTest {

    @Test
    void parsesOciUrl() {
        OciChartReference ref = OciChartReference.parse("oci://Harbor.Example:30002/Library/", false);
        assertEquals("harbor.example:30002", ref.host());
        assertEquals("library", ref.project());
        assertFalse(ref.plainHttp());
        assertEquals("oci://harbor.example:30002/library", ref.registryBase());
        assertEquals("oci://harbor.example:30002/library/sample:0.1.0", ref.chartRef("sample", "0.1.0"));
    }

    @Test
    void httpUrlForcesPlainHttp() {
        OciChartReference ref = OciChartReference.parse("http://127.0.0.1:30002/charts", false);
        assertTrue(ref.plainHttp());
        assertEquals("127.0.0.1:30002", ref.host());
    }

    @Test
    void insecureFlagForcesPlainHttpOnHttpsUrl() {
        OciChartReference ref = OciChartReference.parse("https://harbor.example/charts", true);
        assertTrue(ref.plainHttp());
    }

    @Test
    void rejectsUserInfoAndMissingProject() {
        assertThrows(BizException.class, () -> OciChartReference.parse("oci://user:pass@harbor.example/charts", false));
        assertThrows(BizException.class, () -> OciChartReference.parse("oci://harbor.example", false));
        assertThrows(BizException.class, () -> OciChartReference.parse(" ", false));
    }
}
