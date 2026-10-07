package com.hfwas.devops.container.service.helm;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelmVersionsTest {

    @Test
    void ordersNumericAndPreRelease() {
        List<String> versions = new java.util.ArrayList<>(List.of("1.0.0", "1.10.0", "1.2.0", "1.10.0-alpha", "1.10.0-alpha.1"));
        versions.sort(HelmVersions.NEWER_FIRST);
        assertEquals(List.of("1.10.0", "1.10.0-alpha.1", "1.10.0-alpha", "1.2.0", "1.0.0"), versions);
    }

    @Test
    void rejectsLeadingZerosAndBuildMetadata() {
        assertTrue(HelmVersions.isSemVer("0.1.0"));
        assertTrue(HelmVersions.isSemVer("1.2.3-rc.1"));
        assertFalse(HelmVersions.isSemVer("1.10"));
        assertFalse(HelmVersions.isSemVer("01.2.3"));
        assertFalse(HelmVersions.isSemVer("1.2.3+build"));
    }
}
