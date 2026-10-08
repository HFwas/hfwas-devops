package com.hfwas.devops.container.service.helm;

import com.hfwas.devops.container.dto.HelmReleaseVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelmOutputParserTest {

    @Test
    void parsesListHistoryStatusAndManifest() {
        List<HelmReleaseVO> listed = HelmOutputParser.releases("""
                [{"name":"demo","namespace":"default","revision":"2","updated":"2026-10-02 00:00:00.000000 +0000 UTC","status":"deployed","chart":"sample-0.2.0","app_version":"1.1.0"}]
                """);
        assertEquals(1, listed.size());
        assertEquals("sample", listed.get(0).getChartName());
        assertEquals("0.2.0", listed.get(0).getChartVersion());
        assertEquals(2, listed.get(0).getRevision());
        assertTrue(listed.get(0).getUpdatedAt().startsWith("2026-10-02T00:00:00"));

        HelmReleaseVO status = HelmOutputParser.status("""
                {"name":"demo","namespace":"edge","version":2,"manifest":"apiVersion: v1\\nkind: ConfigMap\\nmetadata:\\n  name: demo\\n  namespace: edge\\n","info":{"status":"deployed","last_deployed":"2026-10-08T00:00:00Z","notes":"hello"},"chart":{"metadata":{"name":"sample","version":"0.2.0","appVersion":"1.1.0"}}}
                """);
        assertEquals("sample", status.getChartName());
        assertEquals("hello", status.getNotes());
        assertEquals(1, status.getResources().size());
        assertEquals("ConfigMap", status.getResources().get(0).getKind());
        assertEquals("demo", status.getResources().get(0).getName());

        var history = HelmOutputParser.history("""
                [{"revision":1,"updated":"2026-10-01 00:00:00.000000 +0000 UTC","status":"superseded","chart":"my-sample-1.0.0","app_version":"1","description":"Install complete"}]
                """);
        assertEquals("my-sample", history.get(0).getChartName());
        assertEquals("1.0.0", history.get(0).getChartVersion());
    }

    @Test
    void stripsHelmValuesHeaderAndReadsDryRunManifest() {
        assertEquals("", HelmOutputParser.userValues("USER-SUPPLIED VALUES:\nnull\n"));
        assertEquals("replicaCount: 1\n", HelmOutputParser.userValues("# user-supplied values\nreplicaCount: 1\n"));

        String manifest = HelmOutputParser.dryRunManifest("""
                NAME: demo
                MANIFEST:
                ---
                apiVersion: v1
                kind: Service
                metadata:
                  name: demo
                NOTES:
                hello
                """);
        assertTrue(manifest.contains("kind: Service"));
        assertEquals(1, HelmOutputParser.resources(manifest).size());
    }
}
