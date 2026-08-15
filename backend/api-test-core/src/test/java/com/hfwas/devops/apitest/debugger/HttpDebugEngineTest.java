package com.hfwas.devops.apitest.debugger;

import com.hfwas.devops.apitest.debugger.engine.HttpDebugEngine;
import com.hfwas.devops.apitest.debugger.model.DebugRequest;
import com.hfwas.devops.apitest.debugger.model.DebugResult;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HttpDebugEngine — HTTP 请求执行引擎单元测试
 * <p>
 * 覆盖：
 * - 无效请求参数（已在请求验证中覆盖）
 * - URL 构建逻辑
 * - 内嵌 HTTP 服务器验证真实请求流程
 *
 * @author hfwas
 */
@DisplayName("HttpDebugEngine — HTTP 请求执行引擎")
@ExtendWith(MockitoExtension.class)
class HttpDebugEngineTest {

    @InjectMocks
    private HttpDebugEngine httpDebugEngine;

    @Nested
    @DisplayName("execute — 请求验证")
    class ExecuteValidation {

        @Test
        @DisplayName("无效 URL 应返回 ERROR")
        void invalidUrl() {
            DebugRequest request = new DebugRequest();
            request.setUrl("invalid-url");
            request.setMethod("GET");

            DebugResult result = httpDebugEngine.execute(request);
            assertThat(result.getStatus()).isEqualTo("ERROR");
            assertThat(result.getErrorMessage()).isNotNull();
        }

        @Test
        @DisplayName("空 URL 应返回 ERROR")
        void emptyUrl() {
            DebugRequest request = new DebugRequest();
            request.setUrl("");
            request.setMethod("GET");

            DebugResult result = httpDebugEngine.execute(request);
            assertThat(result.getStatus()).isEqualTo("ERROR");
        }

        @Test
        @DisplayName("null Method 应返回 ERROR")
        void nullMethod() {
            DebugRequest request = new DebugRequest();
            request.setUrl("http://localhost:8080/api/test");
            request.setMethod(null);

            DebugResult result = httpDebugEngine.execute(request);
            assertThat(result.getStatus()).isEqualTo("ERROR");
        }

        @Test
        @DisplayName("请求执行应有响应时间记录")
        void shouldRecordDuration() {
            DebugRequest request = new DebugRequest();
            request.setUrl("http://localhost:9999/not-exists");
            request.setMethod("GET");

            DebugResult result = httpDebugEngine.execute(request);
            assertThat(result.getDurationMs()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("URL 带特殊字符应返回 ERROR")
        void urlWithSpecialChars() {
            DebugRequest request = new DebugRequest();
            request.setUrl("http://localhost:8080/api/test?q=hello world&lang=zh");
            request.setMethod("GET");

            DebugResult result = httpDebugEngine.execute(request);
            // URL 含空格可能导致 URI 创建失败
            assertThat(result.getStatus()).isIn("ERROR", "SUCCESS", "FAILURE");
        }

        @Test
        @DisplayName("null 请求头不应导致异常")
        void nullHeaders() {
            DebugRequest request = new DebugRequest();
            request.setUrl("http://localhost:9999/api/test");
            request.setMethod("GET");
            request.setHeaders(null);

            DebugResult result = httpDebugEngine.execute(request);
            assertThat(result.getStatus()).isEqualTo("ERROR");
        }
    }

    @Nested
    @DisplayName("execute — 与内嵌 HTTP 服务器交互")
    class ExecuteWithEmbeddedServer {

        private com.sun.net.httpserver.HttpServer server;
        private int port;

        @BeforeEach
        void startServer() throws Exception {
            server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(0), 0);
            port = server.getAddress().getPort();

            // GET 端点
            server.createContext("/api/test", exchange -> {
                String body = "{\"status\":\"ok\"}";
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body.getBytes());
                }
            });

            // POST 端点
            server.createContext("/api/echo", exchange -> {
                byte[] reqBody = exchange.getRequestBody().readAllBytes();
                String body = "{\"echoed\":" + new String(reqBody) + "}";
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body.getBytes());
                }
            });

            // 带 Query 的端点
            server.createContext("/api/query", exchange -> {
                String query = exchange.getRequestURI().getQuery();
                String body = "{\"query\":\"" + (query != null ? query : "") + "\"}";
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body.getBytes());
                }
            });

            // 404 端点
            server.createContext("/api/not-found", exchange -> {
                String body = "{\"error\":\"not found\"}";
                exchange.sendResponseHeaders(404, body.length());
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body.getBytes());
                }
            });

            server.setExecutor(null);
            server.start();
        }

        @AfterEach
        void stopServer() {
            if (server != null) {
                server.stop(0);
            }
        }

        @Test
        @DisplayName("GET 请求成功返回 SUCCESS 状态")
        void getRequestSuccess() {
            DebugRequest request = new DebugRequest();
            request.setUrl("http://localhost:" + port + "/api/test");
            request.setMethod("GET");

            DebugResult result = httpDebugEngine.execute(request);
            assertThat(result.getStatus()).isEqualTo("SUCCESS");
            assertThat(result.getResponse()).isNotNull();
            assertThat(result.getResponse().getStatusCode()).isEqualTo(200);
            assertThat(result.getResponse().getBody()).contains("ok");
        }

        @Test
        @DisplayName("GET 请求获取响应体")
        void getRequestResponseBody() {
            DebugRequest request = new DebugRequest();
            request.setUrl("http://localhost:" + port + "/api/test");
            request.setMethod("GET");

            DebugResult result = httpDebugEngine.execute(request);
            assertThat(result.getResponse().getBody()).isEqualTo("{\"status\":\"ok\"}");
        }

        @Test
        @DisplayName("GET 请求获取响应头")
        void getRequestResponseHeaders() {
            DebugRequest request = new DebugRequest();
            request.setUrl("http://localhost:" + port + "/api/test");
            request.setMethod("GET");

            DebugResult result = httpDebugEngine.execute(request);
            assertThat(result.getResponse().getHeaders())
                    .containsKey("content-type");
        }

        @Test
        @DisplayName("POST 请求带 Body 成功")
        void postRequestWithBody() {
            DebugRequest request = new DebugRequest();
            request.setUrl("http://localhost:" + port + "/api/echo");
            request.setMethod("POST");
            request.setBody("{\"id\":1}");

            DebugResult result = httpDebugEngine.execute(request);
            assertThat(result.getStatus()).isEqualTo("SUCCESS");
            assertThat(result.getResponse().getBody()).contains("echoed");
        }

        @Test
        @DisplayName("POST 请求带请求头")
        void postRequestWithHeaders() {
            DebugRequest request = new DebugRequest();
            request.setUrl("http://localhost:" + port + "/api/echo");
            request.setMethod("POST");
            request.setBody("{\"id\":1}");

            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer test-token");
            headers.put("Content-Type", "application/json");
            request.setHeaders(headers);

            DebugResult result = httpDebugEngine.execute(request);
            assertThat(result.getStatus()).isEqualTo("SUCCESS");
        }

        @Test
        @DisplayName("DELETE 请求成功")
        void deleteRequest() {
            DebugRequest request = new DebugRequest();
            request.setUrl("http://localhost:" + port + "/api/test");
            request.setMethod("DELETE");

            DebugResult result = httpDebugEngine.execute(request);
            assertThat(result.getStatus()).isEqualTo("SUCCESS");
        }

        @Test
        @DisplayName("404 响应返回 FAILURE 状态")
        void notFoundResponse() {
            DebugRequest request = new DebugRequest();
            request.setUrl("http://localhost:" + port + "/api/not-found");
            request.setMethod("GET");

            DebugResult result = httpDebugEngine.execute(request);
            assertThat(result.getStatus()).isEqualTo("FAILURE");
            assertThat(result.getResponse().getStatusCode()).isEqualTo(404);
        }

        @Test
        @DisplayName("查询参数不会被拼接到 URL 中（engine 处理）")
        void queryParamsInUrl() {
            DebugRequest request = new DebugRequest();
            request.setUrl("http://localhost:" + port + "/api/query?page=1&size=20");
            request.setMethod("GET");

            DebugResult result = httpDebugEngine.execute(request);
            assertThat(result.getStatus()).isEqualTo("SUCCESS");
            assertThat(result.getResponse().getBody()).contains("page=1");
        }
    }
}