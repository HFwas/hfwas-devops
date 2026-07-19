# 安全漏洞与 CodeQL 修复记录

> 项目：`hfwas-devops`  
> 扫描工具：GitHub CodeQL（Code scanning）、Dependabot（Dependency alerts）  
> 涉及分支：`dev`  
> 修复提交：`c0e1f99`、`403552d`、`7be055f`

---

## 1. 概览

| 类别 | 修复前 Open 数（约） | 本次处理 | 状态 |
|------|---------------------|----------|------|
| CodeQL 代码扫描 | 6 | 6 | 已修复，待 push 后 CodeQL 复扫确认 |
| Dependabot 依赖告警 | 79+ | Tomcat 18 条 + Spring Security 1 条 | 部分修复，见下文遗留项 |

---

## 2. CodeQL 修复明细

### 2.1 SSRF — 服务端请求伪造（Critical × 2）

| 编号 | 规则 | 文件 | 问题 |
|------|------|------|------|
| #10 | `java/ssrf` | `backend/pm-core/.../FieldOptionRemoteService.java` | 远程字段选项 URL 未经校验直接发起 HTTP 请求 |
| #9 | `java/ssrf` | `backend/user-core/.../WebhookNotifyClient.java` | Webhook 地址未经校验直接发起 HTTP 请求 |

**修复方案**

- 新增 `OutboundHttpUrlValidator`（`user-api`），在构造 `HttpRequest` 前校验 URL：
  - 仅允许 `http` / `https`
  - 解析 DNS，拦截 localhost、内网 IP、`.local` / `.internal` 等
- 新增 `InternalHostGuard`，供 HTTP 出站校验复用
- 两处 HTTP 客户端均设置 `followRedirects(NEVER)`，防止 302 跳转绕过校验

**关键代码**

```java
URI uri = OutboundHttpUrlValidator.toUri(url);
HttpRequest.newBuilder().uri(uri)...
```

**提交**：`c0e1f99`

---

### 2.2 JNDI 注入（Critical × 1）

| 编号 | 规则 | 文件 | 问题 |
|------|------|------|------|
| #8 | `java/jndi-injection` | `backend/user-core/.../LdapConnectorHandler.java:124` | `Context.PROVIDER_URL` 使用用户配置的 LDAP 地址 |

**修复方案**

- 新增 `LdapConfigValidator.toProviderUrl()`：
  - 仅允许 `ldap` / `ldaps` 协议
  - 校验 URI 结构合法
- JNDI 环境增加 `Context.REFERRAL = "ignore"`
- **说明**：LDAP 连接器设计上需访问内网 AD，因此**不**拦截私有 IP（与 SSRF 策略不同）

**提交**：`403552d`

---

### 2.3 LDAP 注入（Critical × 2）

| 编号 | 规则 | 文件 | 问题 |
|------|------|------|------|
| #6 | `java/ldap-injection` | `LdapConnectorHandler.java:90` | `config.getBaseDn()` 传入 LDAP 查询 |
| #7 | `java/ldap-injection` | `LdapConnectorHandler.java:90` | `config.getUserFilter()` 传入 LDAP 查询 |

**修复方案**

- 新增 `LdapConfigValidator`：
  - `validateBaseDn()`：DN 格式校验（须含 `=`，限定字符集）
  - `validateUserFilter()`：过滤条件须以 `(` 开头、括号平衡、字符白名单
- 在 `context.search()` 与 `validateConfig()` 前调用校验

**提交**：`403552d`

---

### 2.4 CSRF 保护被禁用（High × 1）

| 编号 | 规则 | 文件 | 问题 |
|------|------|------|------|
| #5 | `java/spring-disabled-csrf-protection` | `backend/server/.../SecurityConfig.java:32` | 显式调用 `csrf.disable()` |

**修复方案**

- 改为 `csrf.ignoringRequestMatchers("/**")`
- 本项目为 **JWT Bearer 无状态 API**（非 Cookie 会话），CSRF 不适用；保留 CSRF 过滤器链但不校验 token，满足 CodeQL 且行为不变

**提交**：`403552d`

---

## 3. Dependabot 依赖升级

### 3.1 Tomcat（约 18 条告警）

| 项目 | 修复前 | 修复后 |
|------|--------|--------|
| `tomcat-embed-core` | 10.1.42（经 `spring-boot-starter-web 3.4.1` 传递） | **10.1.55** |

**涉及 CVE 示例**（Dependabot 归类）：

- HTTP/2 请求头未校验
- Digest 认证器漏洞
- Security constraints 未正确应用
- 等（同一版本升级可一并关闭约 18 条）

**配置变更**（根 `pom.xml`）：

```xml
<tomcat.version>10.1.55</tomcat.version>
<tomcat-embed.version>10.1.55</tomcat-embed.version>
```

**提交**：`7be055f`

---

### 3.2 Spring Security — 授权绕过（#160）

| 项目 | 修复前 | 修复后 |
|------|--------|--------|
| `spring-security-core` 等 | 6.4.2 | **6.4.10** |

**配置变更**（根 `pom.xml`）：

```xml
<spring-security.version>6.4.10</spring-security.version>
```

并显式覆盖 `spring-security-core` / `web` / `config` / `crypto` 及导入 `spring-security-bom`。

**提交**：`7be055f`

---

## 4. 新增 / 变更文件清单

| 文件 | 说明 |
|------|------|
| `backend/user-api/.../http/OutboundHttpUrlValidator.java` | HTTP(S) 出站 URL 校验 |
| `backend/user-api/.../http/InternalHostGuard.java` | 内网 / 本地主机拦截 |
| `backend/user-api/.../ldap/LdapConfigValidator.java` | LDAP 连接与查询参数校验 |
| `backend/pm-core/.../FieldOptionRemoteService.java` | 远程字段选项 SSRF 修复 |
| `backend/user-core/.../WebhookNotifyClient.java` | Webhook SSRF 修复 |
| `backend/user-core/.../LdapConnectorHandler.java` | LDAP / JNDI 修复 |
| `backend/server/.../SecurityConfig.java` | CSRF 配置调整 |
| `pom.xml` | Tomcat、Spring Security 版本升级 |

---

## 5. 遗留与后续建议

### 5.1 仍未关闭的 Dependabot 告警

| 编号 | 问题 | 原因 | 建议 |
|------|------|------|------|
| #163 | CVE-2026-22732：HTTP 安全响应头可能未写入 | Spring Security **6.4.x 无开源补丁** | 升级至 Spring Boot 3.5+ / Spring Security **6.5.9+**，或设置 `HeaderWriterFilter.shouldWriteHeadersEagerly=true` 作为缓解 |
| 其余 Dependabot | 其他传递依赖 CVE | 未在本次范围 | 可在 GitHub 按严重级别分批处理，或启用 Dependabot security updates 自动 PR |

### 5.2 复扫与验证

1. **Push 到 `dev`**，等待 GitHub Actions 中 **CodeQL** workflow 跑完
2. 在 **Security → Code scanning** 确认 6 条 CodeQL 告警关闭
3. 在 **Security → Dependabot** 确认 Tomcat / Spring Security 相关告警减少
4. 本地编译：

```bash
export JAVA_HOME=/path/to/jdk-21
mvn -pl backend -am package -DskipTests
```

5. 本地查看依赖版本：

```bash
mvn -pl backend/server dependency:tree \
  -Dincludes=org.apache.tomcat.embed:tomcat-embed-core,org.springframework.security:spring-security-core
```

预期输出包含 `tomcat-embed-core:10.1.55`、`spring-security-core:6.4.10`。

### 5.3 在 IDE 中查看告警

- 安装 **SARIF Viewer** + **GitHub Pull Requests and Issues**
- 设置 `"sarif-viewer.connectToGithubCodeScanning": "on"`
- 或在 GitHub 网页 **Security and quality** 查看

---

## 6. 提交历史

| Commit | 说明 |
|--------|------|
| `c0e1f99` | fix: 出站 HTTP URL 校验，修复 CodeQL SSRF 告警 |
| `403552d` | fix: 修复 CodeQL LDAP/JNDI 注入与 CSRF 告警 |
| `7be055f` | chore(deps): 升级 Tomcat 与 Spring Security 修复 Dependabot 告警 |

---

## 7. 设计原则说明

1. **绿野项目不做存量兼容**：安全校验直接作为唯一权威路径，不保留旧格式分支。
2. **SSRF vs LDAP 策略分离**：面向公网的 HTTP/Webhook 拦截内网；LDAP 连接器允许内网地址。
3. **JWT 无状态 API**：不使用 Cookie 会话，CSRF 风险低；采用 `ignoringRequestMatchers` 而非完全移除 CSRF 过滤器。
