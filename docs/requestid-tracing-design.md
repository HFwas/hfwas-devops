# 全局 RequestId 链路追踪 — 技术设计方案

> 版本：v1.0  
> 日期：2026-08-19  
> 状态：待评审

---

## 1. 背景与目标

### 1.1 现状

当前系统在收到 HTTP 请求并出错时，日志分散在各模块中，缺乏统一的请求标识。当出现以下场景时难以定位问题：

- 用户报告某个操作失败，无法快速检索该请求的所有日志
- 批量操作中某一步出错，上下文已丢失
- 接口超时，无法关联超时前后的日志

### 1.2 目标

- 每个请求分配全局唯一的 `requestId`，贯穿整个请求生命周期
- 所有日志输出自动携带 `requestId`，支持 `grep requestId` 检索完整链路
- 异常场景（业务异常、系统异常、超时、Filter 前置拒绝）下不丢失 `requestId`
- 支持异步调用（`@Async`、线程池）传递 `requestId`
- 支持跨服务调用（Feign / RestTemplate）传递 `requestId`
- 响应头返回 `requestId`，方便前端和调用方定位

---

## 2. 总体架构

```
┌──────────────────────────────────────────────────────────────────┐
│                         HTTP Request                             │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ① RequestIdFilter (最先执行)                                    │
│     ├─ 读取 X-Request-Id 请求头（客户端传入）或生成 UUID         │
│     ├─ 设置 MDC.put("requestId", id)                             │
│     ├─ 设置响应头 X-Request-Id                                    │
│     └─ 请求结束时 MDC.clear()                                    │
│                                                                  │
│  ② JwtAuthFilter (Spring Security)                               │
│     └─ 日志自动携带 requestId                                    │
│                                                                  │
│  ③ TenantContextFilter                                           │
│     └─ 日志自动携带 requestId                                    │
│                                                                  │
│  ④ Controller / Service / DAO                                   │
│     └─ 所有日志自动携带 requestId                                │
│                                                                  │
│  ⑤ ExceptionAdvice (全局异常处理)                                │
│     └─ 错误响应中返回 requestId                                  │
│                                                                  │
│  ⑥ Async / 线程池                                                │
│     └─ MDC 上下文传递到子线程                                    │
│                                                                  │
│  ⑦ Feign / RestTemplate (跨服务)                                 │
│     └─ 请求头 X-Request-Id 透传                                  │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

---

## 3. 详细设计

### 3.1 RequestId 生成规则

| 策略 | 说明 |
|------|------|
| 客户端传入 | 优先读取 `X-Request-Id` 请求头（允许调用方传入自己的 traceId） |
| 服务端生成 | 使用 `UUID.randomUUID().toString()`，保留原生 `-` 分隔符 |
| 格式示例 | `a1b2c3d4-e5f6-7890-1234-5678abcdef01` |

### 3.2 RequestId 上下文持有者

创建一个 `RequestIdHolder` 工具类，封装 MDC 操作和响应头设置：

```java
public class RequestIdHolder {
    private static final String REQUEST_ID_KEY = "requestId";
    private static final String HEADER_NAME = "X-Request-Id";

    /** 生成或获取 requestId，写入 MDC */
    public static String setIfAbsent(String requestId) {
        String existing = MDC.get(REQUEST_ID_KEY);
        if (existing != null) return existing;
        String id = (requestId != null && !requestId.isEmpty()) ? requestId : generateId();
        MDC.put(REQUEST_ID_KEY, id);
        return id;
    }

    /** 获取当前 requestId */
    public static String get() {
        return MDC.get(REQUEST_ID_KEY);
    }

    /** 清理 */
    public static void clear() {
        MDC.remove(REQUEST_ID_KEY);
    }

    /** 写入响应头 */
    public static void setResponseHeader(HttpServletResponse response) {
        String id = get();
        if (id != null) {
            response.setHeader(HEADER_NAME, id);
        }
    }

    private static String generateId() {
        return UUID.randomUUID().toString();
    }
}
```

### 3.3 RequestIdFilter

Spring `OncePerRequestFilter`，放在过滤器链最前面：

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        try {
            // 1. 读取请求头或生成 UUID
            String headerId = request.getHeader(REQUEST_ID_HEADER);
            String requestId = RequestIdHolder.setIfAbsent(headerId);

            // 2. 设置响应头
            response.setHeader(REQUEST_ID_HEADER, requestId);

            // 3. 记录请求入口日志（含 requestId，自动从 MDC 获取）
            log.info("=== Request start: {} {} [{}] ===",
                    request.getMethod(), getRequestUri(request), requestId);

            filterChain.doFilter(request, response);

            // 4. 记录请求结束日志
            log.info("=== Request end: {} {} [{}] ===",
                    request.getMethod(), getRequestUri(request), requestId);

        } finally {
            // 5. 清理 MDC（防止线程池复用导致上下文污染）
            RequestIdHolder.clear();
        }
    }

    private String getRequestUri(HttpServletRequest request) {
        String qs = request.getQueryString();
        return qs != null ? request.getRequestURI() + "?" + qs : request.getRequestURI();
    }
}
```

#### 过滤器注册

在 `SecurityConfig` 中，将 `RequestIdFilter` 放在 `JwtAuthFilter` 之前：

```java
.addFilterBefore(requestIdFilter, JwtAuthFilter.class)
```

或者通过 `@Order` + 自动注册，Spring Boot 会自动应用 `OncePerRequestFilter` 到所有请求。

### 3.4 Logback 配置变更

日志 pattern 增加 `[%X{requestId}]` 占位符：

```xml
<encoder>
    <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} [%X{requestId}] - %msg%n</pattern>
</encoder>
```

所有 appender（CONSOLE、FILE、ERROR_FILE）统一修改。

### 3.5 全局异常处理增强

在 `ExceptionAdvice` 中将 `requestId` 注入错误响应：

```java
@ExceptionHandler(Exception.class)
@ResponseStatus(HttpStatus.OK)
public BaseResult<Void> handleException(Exception e) {
    log.error("Unhandled exception [requestId={}]", RequestIdHolder.get(), e);
    return BaseResult.failed(ResultCode.INTERNAL_ERROR, "requestId: " + RequestIdHolder.get());
}
```

所有异常处理方法的 `log.warn` / `log.error` 不再需要手动传 `requestId`，因为 MDC 会自动携带。但错误响应体中应包含 `requestId` 供调用方检索日志。

### 3.6 BaseResult 增加 requestId 字段

```java
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BaseResult<T> {
    private Integer code;
    private String msg;
    private T data;
    private String requestId;  // 新增

    public static <T> BaseResult<T> failed(ErrorCode errorCode) {
        return restResult(errorCode.getCode(), errorCode.getMessage(), null);
    }

    private static <T> BaseResult<T> restResult(int code, String msg, T data) {
        BaseResult<T> apiResult = new BaseResult<>();
        apiResult.setCode(code);
        apiResult.setData(data);
        apiResult.setMsg(msg);
        apiResult.setRequestId(RequestIdHolder.get());  // 自动注入
        return apiResult;
    }
}
```

### 3.7 ApiErrorWriter 增强

Filter 层（如 JwtAuthFilter、TenantContextFilter）的拒绝响应也要包含 `requestId`：

```java
public void write(HttpServletResponse response, HttpStatus httpStatus, int code, String message) throws IOException {
    response.setStatus(httpStatus.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());

    // 注入 requestId
    BaseResult<Object> result = BaseResult.failed(code, message);
    // 若 response 尚未提交，设置响应头
    String requestId = RequestIdHolder.get();
    if (requestId != null) {
        response.setHeader("X-Request-Id", requestId);
    }

    objectMapper.writeValue(response.getWriter(), result);
}
```

### 3.8 异步线程池传递 MDC

Spring `@Async` 默认不传递 MDC。需要自定义 `TaskDecorator`：

```java
@Component("mdcAwareTaskExecutor")
public class MdcAwareTaskExecutor implements Executor {

    private final ThreadPoolTaskExecutor delegate;

    public MdcAwareTaskExecutor() {
        this.delegate = new ThreadPoolTaskExecutor();
        this.delegate.setCorePoolSize(4);
        this.delegate.setMaxPoolSize(8);
        this.delegate.setQueueCapacity(100);
        this.delegate.setThreadNamePrefix("mdc-async-");
        this.delegate.setTaskDecorator(runnable -> {
            Map<String, String> contextMap = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                try {
                    if (contextMap != null) {
                        MDC.setContextMap(contextMap);
                    }
                    runnable.run();
                } finally {
                    if (previous != null) {
                        MDC.setContextMap(previous);
                    } else {
                        MDC.clear();
                    }
                }
            };
        });
        this.delegate.initialize();
    }

    @Override
    public void execute(Runnable task) {
        delegate.execute(task);
    }
}
```

在 `@Async` 使用：

```java
@Async("mdcAwareTaskExecutor")
public CompletableFuture<Void> someAsyncMethod() {
    // MDC 中的 requestId 可用
    log.info("Async task running with requestId: {}", RequestIdHolder.get());
    // ...
}
```

### 3.9 接口超时场景

#### 3.9.1 Spring MVC 异步超时

对于 `DeferredResult` 或 `Callable` 的超时，Spring 会在超时时触发 `onTimeout` 回调，此时仍在原请求线程中，`requestId` 可用：

```java
@GetMapping("/async")
public DeferredResult<String> asyncWithTimeout() {
    DeferredResult<String> result = new DeferredResult<>(5000L);
    result.onTimeout(() -> {
        log.warn("Async request timed out [requestId={}]", RequestIdHolder.get());
        result.setErrorResult("timeout");
    });
    return result;
}
```

#### 3.9.2 Tomcat 连接超时

Tomcat 线程池耗尽或连接超时由容器层处理，进入自定义的 `ErrorPage` 或 `ErrorController`：

```java
@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public ResponseEntity<BaseResult<Void>> handleError(HttpServletRequest request) {
        // 此时 MDC 中的 requestId 可能已丢失，尝试从请求属性恢复
        String requestId = (String) request.getAttribute("requestId");
        if (requestId == null) {
            requestId = RequestIdHolder.get();
        }
        if (requestId == null) {
            requestId = "unknown";
        }
        log.warn("Container-level error handled [requestId={}]", requestId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(BaseResult.failed(ResultCode.INTERNAL_ERROR, "requestId: " + requestId));
    }
}
```

#### 3.9.3 请求属性兜底

在 `RequestIdFilter` 中将 `requestId` 额外存入 `request.setAttribute("requestId", id)`，确保在 Filter 链之外（如 Tomcat 错误处理）也能通过 `request.getAttribute("requestId")` 获取。

### 3.10 跨服务传递（Feign / RestTemplate）

#### Feign 拦截器

```java
@Component
public class RequestIdFeignInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String requestId = RequestIdHolder.get();
        if (requestId != null) {
            template.header("X-Request-Id", requestId);
        }
    }
}
```

#### RestTemplate 拦截器

```java
@Component
public class RequestIdRestTemplateInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                        ClientHttpRequestExecution execution) throws IOException {
        String requestId = RequestIdHolder.get();
        if (requestId != null) {
            request.getHeaders().add("X-Request-Id", requestId);
        }
        return execution.execute(request, body);
    }
}
```

### 3.11 健康检查/白名单路径处理

`/health/check`、`/user/auth/login` 等白名单路径同样需要 `requestId`，`RequestIdFilter` 不应跳过。但可以跳过不需要记录日志的静态资源路径。

```java
@Override
protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    // 静态资源跳过，但 API 路径全部记录
    return path.startsWith("/static/") || path.startsWith("/webjars/");
}
```

---

## 4. 异常场景覆盖矩阵

| 场景 | requestId 是否可用 | 说明 |
|------|-------------------|------|
| 正常请求 | ✅ | MDC 全程携带，日志自动输出 |
| 业务异常 (BizException) | ✅ | ExceptionAdvice 中 MDC 可用 |
| 参数校验失败 | ✅ | 在 Controller 层之前已设置 requestId |
| JWT 认证失败 | ✅ | RequestIdFilter 在 JwtAuthFilter 之前执行 |
| 权限不足 (403) | ✅ | RequestIdFilter 先执行，MDC 可用 |
| Tenant 校验失败 | ✅ | 同上 |
| 未捕获的系统异常 | ✅ | ExceptionAdvice 中 MDC 仍存在 |
| Filter 层直接拒绝写回 | ✅ | RequestIdFilter 在最前执行，MDC 已设置 |
| 接口超时（异步） | ✅ | 超时回调在请求线程中执行 |
| Tomcat 连接超时 | ✅ | 通过 `request.getAttribute("requestId")` 兜底 |
| 异步线程 (@Async) | ✅ | 自定义 TaskDecorator 传递 MDC |
| 跨服务 Feign 调用 | ✅ | 拦截器自动传递 X-Request-Id |
| WebSocket | ⚠️ | 建立连接时生成 requestId，消息处理持续使用 |
| 定时任务 (@Scheduled) | ⚠️ | 每次执行生成独立 traceId |

---

## 5. 修改文件清单

### 5.1 新增文件

| 文件 | 路径 | 说明 |
|------|------|------|
| `RequestIdHolder.java` | `server/src/main/java/com/hfwas/devops/common/core/requestid/` | MDC 操作封装 + requestId 生成 |
| `RequestIdFilter.java` | `server/src/main/java/com/hfwas/devops/common/core/requestid/` | 全局请求过滤器 |
| `MdcTaskDecorator.java` | `server/src/main/java/com/hfwas/devops/common/core/requestid/` | 异步线程 MDC 传递 |
| `RequestIdFeignInterceptor.java` | `server/src/main/java/com/hfwas/devops/common/core/requestid/` | Feign 拦截器（可选，当前无 Feign 可暂缓） |
| `RequestIdRestTemplateInterceptor.java` | `server/src/main/java/com/hfwas/devops/common/core/requestid/` | RestTemplate 拦截器（可选） |

### 5.2 修改文件

| 文件 | 修改内容 |
|------|---------|
| `logback-spring.xml` | 所有 pattern 增加 `[%X{requestId}]` |
| `BaseResult.java` | 新增 `requestId` 字段，自动注入 |
| `ExceptionAdvice.java` | 错误响应包含 `requestId` |
| `ApiErrorWriter.java` | 响应头设置 `X-Request-Id` |
| `SecurityConfig.java` | 注册 `RequestIdFilter` 到过滤器链 |

---

## 6. 日志效果示例

修改后日志输出格式：

```
2026-08-19 14:30:01.123 [http-nio-8089-exec-1]  INFO  c.h.d.common.core.requestid.RequestIdFilter [a1b2c3d4-e5f6-7890-1234-5678abcdef01] - === Request start: POST /api/file-parser/upload [a1b2c3d4-e5f6-7890-1234-5678abcdef01] ===
2026-08-19 14:30:01.234 [http-nio-8089-exec-1] DEBUG c.h.d.fileparser.service.FileParserService [a1b2c3d4-e5f6-7890-1234-5678abcdef01] - Detected MIME type: text/plain for file: test.txt
2026-08-19 14:30:01.345 [http-nio-8089-exec-1]  INFO c.h.d.fileparser.parser.PlainTextParser [a1b2c3d4-e5f6-7890-1234-5678abcdef01] - Plain text parsed test.txt in 5ms, text length=1024, charset=UTF-8
2026-08-19 14:30:01.456 [http-nio-8089-exec-1]  INFO c.h.d.common.core.requestid.RequestIdFilter [a1b2c3d4-e5f6-7890-1234-5678abcdef01] - === Request end: POST /api/file-parser/upload [a1b2c3d4-e5f6-7890-1234-5678abcdef01] ===
```

异常日志示例：

```
2026-08-19 14:30:02.789 [http-nio-8089-exec-1] ERROR c.h.d.common.core.exception.ExceptionAdvice [a1b2c3d4-e5f6-7890-1234-5678abcdef01] - Unhandled exception [requestId=a1b2c3d4-e5f6-7890-1234-5678abcdef01]
java.lang.NullPointerException: ...
```

检索命令：

```bash
grep "a1b2c3d4-e5f6-7890-1234-5678abcdef01" ./logs/devops.log
```

---

## 7. 实施步骤

| 步骤 | 内容 | 预估工作量 |
|------|------|-----------|
| 1 | 创建 `RequestIdHolder` 工具类 | 0.5h |
| 2 | 创建 `RequestIdFilter` + 注册到 SecurityConfig | 0.5h |
| 3 | 修改 `logback-spring.xml` pattern | 0.2h |
| 4 | 修改 `BaseResult` 增加 `requestId` 字段 | 0.3h |
| 5 | 修改 `ExceptionAdvice` 注入 `requestId` | 0.3h |
| 6 | 修改 `ApiErrorWriter` 设置响应头 | 0.3h |
| 7 | 创建 `MdcTaskDecorator` + 异步线程池配置 | 0.5h |
| 8 | 自定义 ErrorController 兜底 Tomcat 层错误 | 0.5h |
| 9 | 测试：正常请求、异常、超时、异步 | 1h |
| 合计 | | **4.1h** |

---

## 8. 可选增强

| 功能 | 说明 | 优先级 |
|------|------|--------|
| 前端 Axios 拦截器 | 生成 `X-Request-Id` 并透传，用户操作失败时可复制 ID 反馈 | 中 |
| Sleuth / Zipkin 集成 | 如需更完整的分布式追踪，可引入 Spring Cloud Sleuth + Zipkin | 低 |
| 日志采集 | 对接 ELK 或 Loki，通过 requestId 快速聚合检索 | 低 |
| 定时任务 traceId | `@Scheduled` 方法每次执行生成独立 traceId，避免与 Web 请求混淆 | 中 |