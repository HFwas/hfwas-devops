package com.hfwas.devops.pipeline.tekton;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitHttpProxyTest {

    @Test
    void rewritesLoopbackAndDockerInternalToClusterReachableHost() {
        assertEquals("http://192.168.5.2:7890", GitHttpProxy.rewrite("127.0.0.1:7890", "192.168.5.2"));
        assertEquals("http://192.168.5.2:7890", GitHttpProxy.rewrite("http://localhost:7890", "192.168.5.2"));
        assertEquals("http://192.168.5.2:7890", GitHttpProxy.rewrite("http://host.docker.internal:7890", "192.168.5.2"));
        assertEquals("socks5://192.168.5.2:7891", GitHttpProxy.rewrite("socks5://127.0.0.1:7891", "192.168.5.2"));
    }

    @Test
    void leavesLanProxyUnchanged() {
        assertEquals("http://10.0.0.8:3128", GitHttpProxy.rewrite("http://10.0.0.8:3128", "192.168.5.2"));
    }

    @Test
    void blankStaysEmpty() {
        assertEquals("", GitHttpProxy.rewrite("  ", "192.168.5.2"));
        assertEquals("", GitHttpProxy.rewrite(null, "192.168.5.2"));
    }

    @Test
    void loopbackWithoutDockerHostKeepsNormalizedUrl() {
        assertEquals("http://127.0.0.1:7890", GitHttpProxy.rewrite("127.0.0.1:7890", ""));
        assertTrue(GitHttpProxy.needsRewrite("127.0.0.1"));
    }
}
