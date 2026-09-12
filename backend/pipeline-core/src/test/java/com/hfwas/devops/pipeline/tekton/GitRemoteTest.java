package com.hfwas.devops.pipeline.tekton;

import com.hfwas.devops.common.error.BizException;
import com.hfwas.devops.common.error.ResultCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitRemoteTest {

    @Test
    void parsesHttpsGitUrlWithoutVendorHost() {
        GitRemote remote = GitRemote.parse("https://gitlab.example.com/acme/demo.git");
        assertEquals("https", remote.scheme());
        assertEquals("gitlab.example.com", remote.host());
        assertEquals(-1, remote.port());
        assertEquals("gitlab.example.com", remote.hostAuthority());
        assertEquals("acme/demo.git", remote.path());
        assertEquals("https://gitlab.example.com/acme/demo.git", remote.urlWithoutAuth());
    }

    @Test
    void preservesNonDefaultPort() {
        GitRemote remote = GitRemote.parse("http://localhost:30880/root/hfwas-devops.git");
        assertEquals("http", remote.scheme());
        assertEquals("localhost", remote.host());
        assertEquals(30880, remote.port());
        assertEquals("localhost:30880", remote.hostAuthority());
        assertEquals("root/hfwas-devops.git", remote.path());
        assertEquals("http://localhost:30880/root/hfwas-devops.git", remote.urlWithoutAuth());
    }

    @Test
    void emptyUrlHasOwnMessage() {
        BizException ex = assertBadRequest(() -> GitRemote.parse("  "));
        assertEquals("仓库地址不能为空", ex.getMessage());
    }

    @Test
    void relativeUrlReportsMissingScheme() {
        BizException ex = assertBadRequest(() -> GitRemote.parse("not-a-url"));
        assertTrue(ex.getMessage().contains("http 或 https"));
        assertTrue(ex.getMessage().contains("not-a-url"));
    }

    @Test
    void illegalUriSurfacesParseFailure() {
        BizException ex = assertBadRequest(() -> GitRemote.parse("https://bad host/repo.git"));
        assertTrue(ex.getMessage().startsWith("仓库地址无法解析"));
        assertTrue(ex.getMessage().contains("https://bad host/repo.git"));
    }

    @Test
    void missingHostOrPathIsReported() {
        BizException ex = assertBadRequest(() -> GitRemote.parse("https://example.com"));
        assertTrue(ex.getMessage().contains("缺少主机或路径"));
        assertTrue(ex.getMessage().contains("https://example.com"));
    }

    private static BizException assertBadRequest(Runnable action) {
        BizException ex = assertThrows(BizException.class, action::run);
        assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        return ex;
    }
}
