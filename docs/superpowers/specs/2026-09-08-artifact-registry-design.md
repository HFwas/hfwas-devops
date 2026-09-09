# 制品仓库产品第一期设计

> 日期：2026-09-08  
> 状态：已拍板，待实施  
> 流水线对照：[2026-09-07-pipeline-design.md](./2026-09-07-pipeline-design.md)  
> 任务类型：[2026-09-07-pipeline-job-kind-design.md](./2026-09-07-pipeline-job-kind-design.md)  
> CI 分层：[cicd-tech-selection.md](../../cicd-tech-selection.md)（流水线不应吞掉制品层）

绿野项目：直接按本文建表与 API，不做存量兼容。流水线任务目录里「不新增 Registry 凭证表」在制品仓上线后作废：凭证归本产品，流水线只引用项目 id。

---

## 1. 目标

控制台产品「制品仓库」管理租户制品项目：浏览 **Harbor** 中的镜像、浏览 **Nexus** 中的语言包，并把项目绑定到流水线。

- `IMAGE` 推 Harbor  
- `BUILD` / `TEST` 从 Nexus **group** 拉依赖  
- `PUBLISH` 推 Nexus **hosted**

开发用 compose profile `artifact`；生产外接已有 Harbor / Nexus。默认 `docker compose up` **不**启动引擎。

### 1.1 选型摘要（已拍板）

制品不是一个桶，拆两层：

| 层 | 职责 | 引擎 | 不选 |
|----|------|------|------|
| OCI | 镜像、Helm、Cosign 配件、推送扫描 | **Harbor 2.13.x**（CNCF Graduated） | 自研 Registry、distribution 当产品、云 ACR 当内核、Nexus Docker 当默认推送目标 |
| 语言包 | Maven / npm / PyPI / Go 的 proxy + hosted + group | **Nexus Repository OSS 3.82.x** | Artifactory（许可证）、GitHub Packages（绑 GitHub）、只用 MinIO 存 jar |

控制面自研；Harbor Portal / Nexus UI 不作为租户主界面。HTTP 客户端用 Spring `RestClient`，不引入官方 SDK。

### 1.2 已拍板

| 项 | 决定 |
|----|------|
| 控制面 | 新模块 `artifact-api` + `artifact-core`，包名 `com.hfwas.devops.artifact`，经 `server` 入 classpath |
| 权威数据 | 制品项目与 robot 密钥在 SQLite。Harbor project / Nexus 仓库可删可重建，控制面按元数据再同步 |
| 引擎地址 | 仅全局 `application.yml`。第一期无租户级 URL 表 |
| 本地 | compose profile `artifact`，与 `pipeline` 独立 |
| 生产 | 外接 Harbor + Nexus（URL + admin 环境变量） |
| 多租户 | `X-Tenant-Id` + `CurrentUserAccessor` |
| Harbor 隔离 | 每制品项目一个 Harbor project：`t{tenantId}-{projectKey}` + 专用 robot |
| Nexus 隔离 | 实例级一套 group/hosted；坐标约定隔离（§3.2）。第一期不按租户拆 blob store |
| 流水线 | `pipeline.artifact_project_id` 可空；空则行为与现在相同 |
| 平台自身构建 | 第一期仍走 Maven Central / npm；不改本仓库 `pom.xml` / `package-lock.json` 的 registry |
| 人机身份 | 人走 Keycloak；机器走 Harbor robot + Nexus user token。第一期引擎不接 OIDC |

### 1.3 非目标（第一期）

- JFrog Artifactory、Nexus IQ、云 ACR/SWR/TCR、GitHub Packages、ChartMuseum
- 自研 OCI / Maven / npm 协议
- 租户登录 Harbor Portal 或 Nexus UI（含 iframe）
- 晋级（dev→prod）、跨机房复制、强制 content trust、Tekton Chains、SBOM 上传
- 每租户独立 Nexus 实例
- Gradle / Poetry / uv 专用仓库类型（有需求仍走现有 Maven/PyPI hosted）
- 把仓库根目录 `artifacts/` 升级成租户库
- 任务级 JSON 表单改写 `IMAGE` 命令（只注入 Secret、settings、以及占位 `DEST` 改写）

---

## 2. 架构

```
控制台 /artifact/*
  → artifact-core 落库 artifact_project
  → HarborClient 保证 project + robot
  → NexusClient 保证实例级 group（进程内只确保一次）
  → 列表/搜索代理引擎 REST，blob 元数据不进 SQLite

流水线「运行」
  → 若 pipeline.artifact_project_id 非空
  → ArtifactBindingPort.issue(tenantId, projectId) → ArtifactBind
  → CompileRequest 带上 bind
  → Tekton 注入 Kaniko docker config 与 settings.xml / .npmrc / PIP_INDEX_URL / GOPROXY
```

| 层 | 做法 |
|----|------|
| 产品 | 去掉 `comingSoon`，path `/artifact/projects` |
| 前端 | `frontend/src/modules/artifact/`，`ArtifactShell` 竖轨（对齐 `PipelineShell`）。**不**往 `CONSOLE_TABS` 加项 |
| 后端 | `artifact-api`（给流水线看的端口）+ `artifact-core`（实现） |
| 无引擎 | `UnavailableArtifactEngine`：页面可进，写操作返回可读错误（对齐无集群时的流水线） |
| 密钥 | AES，键 `artifact.credential-key`；未配则回退 `pipeline.credential-key` |

依赖方向（禁止反向）：

```
server → artifact-core → artifact-api
server → pipeline-core → artifact-api
artifact-core 不依赖 pipeline-core
pipeline-core 不依赖 artifact-core
```

`pipeline-core` 对 `ArtifactBindingPort` 使用 `@Autowired(required = false)`。Bean 不存在或 `issue` 返回空：不注入仓库。

---

## 3. 引擎与仓库拓扑

### 3.1 Harbor（每个制品项目）

名称钉死，绿野可删可重建。

| 对象 | 规则 |
|------|------|
| Project | `t{tenantId}-{projectKey}`，例如 `t12-demo` |
| Robot | `robot$t{tenantId}-{projectKey}-pipeline`，权限：该 project 的 push + pull + list |
| 推送扫描 | `metadata.auto_scan=true` |
| prevent-pull | **关**（第一期不挡漏洞镜像） |

探活：`GET {harbor}/api/v2.0/health`  
建项目：`POST /api/v2.0/projects`  
建机器人：`POST /api/v2.0/robots`（密钥只在创建响应出现一次，立即 AES 入库）  
列仓库：`GET /api/v2.0/projects/{projectName}/repositories`  
列制品：`GET /api/v2.0/projects/{projectName}/repositories/{repoName}/artifacts`

开发 HTTP：`artifact.harbor.insecure=true`。Kaniko 同步加 `--insecure --skip-tls-verify`。生产 HTTPS 则 `insecure=false`，不加这两个 flag。

推送目标：`{harborHost}/{harborProject}/{pipelineName}:{runId}`  
`pipelineName` 与 `runId` 做 DNS 标签化（小写、非 `[a-z0-9-]` 改 `-`，截断 63）。

### 3.2 Nexus（实例级，启动时确保一次）

探活：`GET {nexus}/service/rest/v1/status`  
确保仓库：`GET /service/rest/v1/repositories`，缺则 `POST` 创建。已存在则跳过，不改配置。  
搜索：`GET /service/rest/v1/search?repository={repo}&name={q}`

| 名称 | 格式 | 类型 | 上游 / 成员 |
|------|------|------|-------------|
| `maven-central` | maven2 | proxy | `https://repo1.maven.org/maven2/` |
| `maven-hosted` | maven2 | hosted (release) | — |
| `maven-snapshots` | maven2 | hosted (snapshot) | — |
| `maven-public` | maven2 | group | central + hosted + snapshots |
| `npm-proxy` | npm | proxy | `https://registry.npmjs.org/` |
| `npm-hosted` | npm | hosted | — |
| `npm-group` | npm | group | proxy + hosted |
| `pypi-proxy` | pypi | proxy | `https://pypi.org/` |
| `pypi-hosted` | pypi | hosted | — |
| `pypi-group` | pypi | group | proxy + hosted |
| `go-proxy` | go | proxy | `https://proxy.golang.org/` |

仓库名禁止改。第一期 **不** 按制品项目新建 Nexus repo。

路径约定（控制面文案 + 默认 `PUBLISH` 命令遵守；引擎侧不做 ACL 切割）：

| 生态 | 约定 |
|------|------|
| Maven | `groupId` 以 `com.hfwas.t{tenantId}.{projectKey}` 为前缀 |
| npm | scope `@{projectKey}` |
| PyPI | 包名 `{projectKey}-` 前缀 |
| Go | 模块路径含 `t{tenantId}/{projectKey}` |

### 3.3 配置

`application.yml`：

```yaml
artifact:
  credential-key: ${ARTIFACT_CREDENTIAL_KEY:}
  harbor:
    base-url: ${HARBOR_BASE_URL:}
    admin-user: ${HARBOR_ADMIN_USER:admin}
    admin-password: ${HARBOR_ADMIN_PASSWORD:}
    insecure: ${HARBOR_INSECURE:true}
  nexus:
    base-url: ${NEXUS_BASE_URL:}
    admin-user: ${NEXUS_ADMIN_USER:admin}
    admin-password: ${NEXUS_ADMIN_PASSWORD:}
```

`base-url` 为空或探活失败 → 引擎 DOWN。管理员凭证只给 `HarborClient` / `NexusClient`，不进租户 API、不进前端。

---

## 4. 数据模型

### 4.1 `artifact_project`

```sql
CREATE TABLE IF NOT EXISTS artifact_project (
    id                 INTEGER      NOT NULL PRIMARY KEY,
    tenant_id          INTEGER      NOT NULL,
    name               TEXT         NOT NULL,
    project_key        TEXT         NOT NULL,
    harbor_project     TEXT,
    harbor_robot_name  TEXT,
    robot_secret_enc   TEXT,
    nexus_maven_group  TEXT,
    nexus_npm_group    TEXT,
    nexus_pypi_group   TEXT,
    nexus_go_proxy     TEXT,
    nexus_maven_hosted TEXT,
    nexus_npm_hosted   TEXT,
    deleted            INTEGER      NOT NULL DEFAULT 0,
    create_by          INTEGER,
    update_by          INTEGER,
    create_time        TEXT         NOT NULL DEFAULT (datetime('now')),
    update_time        TEXT         NOT NULL DEFAULT (datetime('now'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_artifact_project_key
    ON artifact_project (tenant_id, project_key) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_artifact_project_tenant
    ON artifact_project (tenant_id, deleted);
```

| 列 | 规则 |
|----|------|
| `name` | 非空，最长 64 |
| `project_key` | `^[a-z][a-z0-9-]{1,30}$`，租户内未删除行唯一 |
| `harbor_*` / `nexus_*` | 创建成功后回填；失败则整笔事务回滚，不留半创建行 |
| `robot_secret_enc` | AES；VO 永不出现 |

镜像 tag、digest、扫描状态、Maven 坐标：**不落库**，每次查引擎。

### 4.2 `pipeline` 加列

在 `pipeline-schema.sql` 追加（绿野直接加，SQLite 用 `ALTER` + `continueOnError` 与现有 `triggered_by_name` 同一套路）：

```sql
ALTER TABLE pipeline ADD COLUMN artifact_project_id INTEGER;
```

删除制品项目：软删；把引用它的 `pipeline.artifact_project_id` **置空**，不级联删流水线。

`PipelineEntity` / `PipelineSaveDTO` / `PipelineVO` / 前端 `PipelineSummary` / `PipelineSavePayload` 增加 `artifactProjectId: Long | null`。

### 4.3 不建的表

- 无 `artifact_engine_setting`（第一期全局 yml）
- 无 Registry / Nexus 凭证表（robot 在 `artifact_project`）
- 无镜像/包本地缓存表

---

## 5. API

前缀 `/artifact`（axios `baseURL=/api` → `/api/artifact/...`）。均需登录。按 tenant 隔离。统一 `BaseResult`。参数错误一律 `BizException.of(ResultCode.BAD_REQUEST, 具体中文原因)`，不新增 ResultCode。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/artifact/status` | `{ harbor: "UP"\|"DOWN", nexus: "UP"\|"DOWN", harborUrl, nexusUrl, message }`。URL 可给前端只读展示；**无** admin 密码 |
| GET | `/artifact/projects` | 当前租户未删除列表 |
| POST | `/artifact/projects` | body `{ name, projectKey }` → 同步引擎 → 返回 id |
| GET | `/artifact/projects/{id}` | 详情。字段含 harborProject、robotName、各 Nexus URL；**无** secret |
| DELETE | `/artifact/projects/{id}` | 软删；best-effort 删 Harbor project；Nexus 实例级仓库不动；流水线绑定置空 |
| GET | `/artifact/projects/{id}/images?page=1&size=20&q=` | Harbor artifacts。元素：`repository, tag, digest, size, scanStatus, signed, pushTime` |
| GET | `/artifact/projects/{id}/packages?format=maven\|npm\|pypi\|go&q=` | `format` 必填。元素：`name, version, repository, format, lastModified` |

非法 `projectKey` → `制品项目标识只能是小写字母开头、数字与短横线，最长 31 位`。  
引擎 DOWN 时 POST 创建 → `制品引擎不可用：{harbor|nexus} DOWN`。  
`GET` 列表/详情在 DOWN 时仍返回 SQLite 元数据；images/packages 返回空列表且 `message` 说明原因（HTTP 仍 200，避免页面崩）。

**禁止** HTTP 暴露 robot 密码。流水线只通过同进程 `ArtifactBindingPort.issue` 取密文解密后的 `ArtifactBind`。

`artifact-api` 中的端口与记录（名称与字段钉死）：

```java
public record ArtifactBind(
        String harborRegistry,
        String harborProject,
        String robotName,
        String robotSecret,
        boolean insecure,
        String nexusUser,
        String nexusPassword,
        String mavenGroupUrl,
        String npmGroupUrl,
        String pypiGroupUrl,
        String goProxyUrl,
        String mavenHostedUrl,
        String npmHostedUrl
) {}

public interface ArtifactBindingPort {
    Optional<ArtifactBind> issue(long tenantId, long artifactProjectId);
}
```

`harborRegistry` 为 `host[:port]`，无 scheme。  
`issue`：项目不属于该租户或已删除 → 空 Optional（流水线按未绑定处理，不 500）。

---

## 6. 前端

### 6.1 路由与壳

- 产品 path：`/artifact/projects`，去掉 `comingSoon`
- `/artifact/overview` → `/artifact/projects`
- 模块：`frontend/src/modules/artifact/`
- `ArtifactShell`：左侧 `n-menu` 宽 168px，对齐 `PipelineShell`

| 菜单 | 路由 | 页 |
|------|------|----|
| 项目 | `/artifact/projects` | 列表 |
| 状态 | `/artifact/status` | Harbor / Nexus 探活与 URL（只读） |

项目内页（仍在壳里，竖轨高亮「项目」）：

| 路由 | 页 |
|------|----|
| `/artifact/projects/new` | 新建（名称 + projectKey） |
| `/artifact/projects/:id` | 详情：页内 Tab「镜像」「包」 |

`router/index.ts` 挂 `artifactRoutes`。`CONSOLE_TABS` 保持空数组。

### 6.2 列表

页头「制品项目」+「新建」。表格：名称、`projectKey`、Harbor 项目名、更新时间、操作（打开、删除）。引擎 DOWN 时顶部 `n-alert` 引用 `/artifact/status` 的 `message`，仍允许浏览已有行。

### 6.3 详情

- 镜像 Tab：仓库、tag、digest 前 12 位、扫描状态、是否已签名、推送时间。`q` 过滤。空列表合法。
- 包 Tab：`format` 下拉（默认 maven）+ 关键字；列坐标/包名、版本、Nexus 仓库名、时间。

### 6.4 流水线编辑器

在名称/仓库/工具链同一行附近增加「制品项目」下拉（可空，选项来自 `GET /artifact/projects`）。未选文案：`使用任务命令里的仓库`。

已绑定且 `IMAGE` 命令仍等于平台占位时，预填：

```
export DEST={harborRegistry}/{harborProject}/{pipelineName}:latest
export IMAGE_PLATFORMS=linux/amd64
export DOCKERFILE=Dockerfile
```

占位原文（与任务目录一致）为 `export DEST=registry.example.com/app:tag`。仅当用户未改过 `DEST` 这一行时替换。

---

## 7. 流水线注入

`CompileRequest` 增加最后一参 `ArtifactBind artifactBind`（可 null）。所有现有测试调用补 `null`。

`PipelineRunService` 在编译前：

1. `artifactProjectId == null` → `artifactBind = null`
2. 否则 `artifactBindingPort.issue(tenantId, artifactProjectId).orElse(null)`  
   端口 bean 缺失视为 null，**不**因此失败运行
3. 绑了项目但引擎 DOWN 且本图含 `IMAGE` / `BUILD` / `TEST` / `PUBLISH` → 运行 `FAILED`，`error_message=制品引擎不可用`（与无集群同一风格）

### 7.1 `IMAGE`

- 把 robot 写成 Kaniko 可读的 docker config Secret（与 git Secret 相同生命周期：Run 结束可删）。
- `insecure=true` 时 Kaniko 固定追加 `--insecure --skip-tls-verify`。
- 用户命令 `eval` 之后、Kaniko 之前：若 `DEST` 等于 `registry.example.com/app:tag`，则改成 `{harborRegistry}/{harborProject}/{dns(pipelineName)}:{runId}`。用户已改 `DEST` 则尊重用户。

不把 robot 密码写进用户命令。日志对 `robotSecret` 打码（并入现有 `LogMasker`）。

### 7.2 `BUILD` / `TEST` / `PUBLISH`

在栈镜像 step 开头、用户命令之前写文件 / 环境变量（`artifactBind != null` 时）。绑定后 Maven mirror 覆盖中央库，这是预期。

两套机器身份互不混用：

| 用途 | 身份 | 存放 |
|------|------|------|
| Harbor 推拉镜像 | 每项目 robot | `artifact_project.robot_secret_enc` |
| Nexus 拉/发包 | 全局 `artifact.nexus.admin-user` / `admin-password` | 仅 yml，不入库 |

第一期构建直接用 Nexus admin，不创建 `ci-t{tenantId}`。二期再拆租户用户。日志对 robot 密码与 Nexus 密码都打码。

**Maven** — `/root/.m2/settings.xml`：

```xml
<settings>
  <mirrors>
    <mirror>
      <id>hfwas</id>
      <url>${mavenGroupUrl}</url>
      <mirrorOf>*</mirrorOf>
    </mirror>
  </mirrors>
  <servers>
    <server>
      <id>hfwas-hosted</id>
      <username>${nexusUser}</username>
      <password>${nexusPassword}</password>
    </server>
  </servers>
</settings>
```

`PUBLISH` 且命令仍为空时，默认：

```
mvn -B deploy -DaltDeploymentRepository=hfwas-hosted::default::${mavenHostedUrl}
```

**npm** — 写入 `{workspace}/src/.npmrc`（clone 之后的源码目录；无 clone 则 step 已 `mkdir -p src`）：

```
registry=${npmGroupUrl}
//${nexusHost}/repository/npm-group/:_auth=${base64(nexusUser:nexusPassword)}
always-auth=true
```

`PUBLISH` 默认（命令为空时）：`npm publish --registry ${npmHostedUrl}`。

**Python**：`export PIP_INDEX_URL={pypiGroupUrl}`（若 URL 需认证，写成 `http://user:pass@host/repository/pypi-group/simple`）。

**Go**：`export GOPROXY={goProxyUrl},direct` 与 `export GOSUMDB=off`（内网第一期关闭 sumdb；生产有公网可再开）。

### 7.3 `SCAN`

不变。镜像 CVE 以 Harbor 推送扫描为准，在制品详情展示，不把 Harbor 扫描结果写进 `pipeline_run_job.log_text`。

---

## 8. Compose

`docker-compose.yml` 增加 `profiles: ["artifact"]`。不进入 `scripts/start-dev.sh`。

| 服务 | 镜像 | 端口 | 数据 |
|------|------|------|------|
| `nexus` | `sonatype/nexus3:3.82.0` | `8082:8081` | `./data/nexus`（已在 `.gitignore` 的 `data/`） |
| Harbor 套件 | 官方 2.13.x compose 片段，落在 `deploy/harbor/` | 宿主机 `8088:80` | `./data/harbor` |

`scripts/start-artifact.sh`：

```bash
docker compose --profile artifact up -d
```

打印：

- Harbor UI（排障用，不写进产品）：`http://localhost:8088`
- Nexus UI（排障用）：`http://localhost:8082`
- 控制台：`http://localhost:8000/artifact/projects`

`.env.example` 增加 `HARBOR_ADMIN_PASSWORD`、`NEXUS_ADMIN_PASSWORD`、`HARBOR_BASE_URL=http://localhost:8088`、`NEXUS_BASE_URL=http://localhost:8082`。

k3s 与 Harbor 都在 Docker 里时，Kaniko 的 `DEST` host 必须是 **集群能解析** 的地址，不能用控制面在宿主机上访问的 `localhost`。

开发默认（本仓库 macOS Docker Desktop）：

- 控制面探活：`HARBOR_BASE_URL=http://localhost:8088`、`NEXUS_BASE_URL=http://localhost:8082`
- 注入给 Kaniko / Maven 的 registry host：`host.docker.internal:8088` / `host.docker.internal:8082`
- k3s compose 已有或补上 `extra_hosts: ["host.docker.internal:host-gateway"]`

`ArtifactBind.harborRegistry` 存 **注入给构建的 host**（`host.docker.internal:8088`），不要存 `localhost`。生产外接实例时 yml 只配一套公网/内网 URL，探活与注入相同。

Harbor 与 Tekton 不要求同一 profile 同时启动；要跑通 `IMAGE` 时两个 profile 都开。

---

## 9. 安全

- `robot_secret_enc` AES；VO / HTTP 无 `secret` / `secretEnc` / `password`
- 查改删校验 `tenant_id`
- `LogMasker` 增加 Harbor robot 密码、Nexus admin 密码
- 不把 Harbor / Nexus admin 密码发给前端
- 开发 HTTP 仅绑定本机端口；生产必须 HTTPS 且 `insecure=false`
- 不把 docker.sock 挂进构建 Pod（沿用 Kaniko）

---

## 10. 验收

1. 产品目录可进入制品仓库；`CONSOLE_TABS` 仍无制品项；竖轨有「项目」「状态」。
2. 无引擎：`GET /artifact/status` 为 DOWN；可打开列表；创建项目返回「制品引擎不可用」。
3. `docker compose --profile artifact up -d` 后：创建 `projectKey=demo` 成功；Harbor 出现 `t{tenantId}-demo`；详情无密码字段。
4. 镜像 Tab、包 Tab 空列表可展示；Nexus `maven-public` 等仓库已存在。
5. 流水线绑定该项目：`IMAGE` 占位 `DEST` 被改写；Kaniko 能推到 Harbor（两 profile 都开时）；控制台镜像 Tab 能看到 tag。
6. 绑定后 Java `BUILD` 第二次运行，Nexus 代理对同一坐标不再回源（以 Nexus 任务日志或 blob 目录为准）。
7. 删除制品项目后，流水线 `artifactProjectId` 为空，定义仍在。
8. 默认 `docker compose up` 不启动 Harbor/Nexus。
9. 不引入 Vue Flow；不嵌引擎 UI。

---

## 11. 文件清单（实施时）

**新建**

- `backend/artifact-api/`（`ArtifactBind`、`ArtifactBindingPort`、pom）
- `backend/artifact-core/`（AutoConfiguration、controller、service、engine、entity、mapper、dto、`UnavailableArtifactEngine`）
- `backend/server/src/main/resources/db/artifact-schema.sql`
- `frontend/src/modules/artifact/`（router、shell、api、types、列表/详情/状态页）
- `deploy/harbor/`（官方 2.13 compose 片段与 README）
- `scripts/start-artifact.sh`

**修改**

- `backend/pom.xml` 增加 `artifact-api`、`artifact-core`
- `backend/server/pom.xml` 依赖 `artifact-core`
- `backend/pipeline-core/pom.xml` 依赖 `artifact-api`
- `SqliteSchemaInitializer` 增加 `artifact-schema.sql`
- `pipeline-schema.sql`、`PipelineEntity`、`PipelineSaveDTO`、`PipelineVO`
- `CompileRequest`、`PipelineRunService`、`TektonCompiler`、`LogMasker` 及对应测试
- `application.yml` / `application-dev.yml`
- `frontend/src/shared/console/products.ts`（path、去掉 comingSoon）
- `frontend/src/router/index.ts`
- 流水线编辑器下拉与类型
- `docker-compose.yml`、`.env.example`、`README.md` 增加 profile 说明

**不改**

- 本仓库业务 `pom.xml` 的 Maven Central、前端 npm registry
- `CONSOLE_TABS`
- `artifacts/` 目录用途
