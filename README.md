# HFWAS DevOps

> 日期：2026-10-01
> 版本：v0.5

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-09-12 | 现有 README 内容 |
| v0.2 | 2026-09-13 | Helm 部署指向 `deploy/charts/deploy-app` 脚本 |
| v0.3 | 2026-09-13 | 本机 GitLab 代码同步用 `scripts/sync-gitlab` |
| v0.4 | 2026-09-15 | 补齐容器平台、流水线/CI、API 测试平台、图片处理四大新增子系统；更新项目结构与 API 索引 |
| v0.5 | 2026-10-01 | 各服务 Dockerfile 集中到 `deploy/docker/<服务名>/` |

---

可扩展的 DevOps 平台，当前包含 **项目管理（PM）**、**用户/租户中心**、**流水线/CI**、**容器平台**、**API 测试平台**、**文档生成（Docgen）**、**文件解析（File Parser）** 与 **图片处理** 八大子系统。后端为单体 Spring Boot 服务，前端为 Vue 3 SPA，本地开发使用 SQLite，零外部依赖即可启动。

---

## 功能概览

### 项目管理（PM）

| 能力 | 说明 |
|------|------|
| 项目与模块 | 多租户项目、树形模块划分 |
| 统一事项（Work Item） | 需求、任务、缺陷等共用模型，Jira 式 `itemKey`（如 `DEMO-1`） |
| 动态字段 | 字段定义、类型绑定、布局配置；支持多种字段类型 SPI |
| 组合查询 | 可视化 QueryBuilder，多条件 AND/OR |
| 状态工作流 | 状态矩阵、流转校验、Post-function、Condition、Vue Flow 可视化设计器 |
| 看板 | 按类型分列展示，拖拽流转 |
| 事项关联与活动 | 链接、评论、变更活动日志 |
| 导入导出 | Excel 模板下载、预览与批量导入 |
| 方案管理 | 事项类型方案、项目级配置 Import/Export |
| 保存视图 | 自定义筛选视图（后端 API + 前端逐步完善） |
| 工作台 | 个人工作台：快捷入口、最近访问、我的事项 |

### 用户中心

| 能力 | 说明 |
|------|------|
| 认证 | JWT 登录、登出、会话管理 |
| 多租户 | 租户切换、`X-Tenant-Id` 上下文 |
| 用户管理 | 账号 CRUD（平台 admin） |
| 站内信 | 收件箱、管理员群发 |
| 审计 | 登录日志、操作日志 |
| 集成 | LDAP 等身份连接器（admin 配置） |

### 流水线 / CI（Pipeline / Tekton）

| 能力 | 说明 |
|------|------|
| 流水线编排 | 可视化 DAG 编排（Vue Flow），多阶段并行/串行任务 |
| 任务市场 | 预置任务类型：GIT_CLONE、MAVEN_BUILD、DEPENDENCY_ANALYSIS、DEPENDENCY_TRACK、KUBECTL、IMAGE_BUILD、DOCKER_PUSH、HELM_UPGRADE 等 |
| 任务脚本模板化 | 所有 Tekton 任务脚本由数据库管理，支持 CodeMirror 编辑器在线编辑与校验 |
| 运行时参数 | 支持 input / select / api_select 三种参数来源，运行弹框选择 |
| 凭证管理 | Git 凭证、镜像仓库凭证、kubeconfig 凭证 |
| 资源分配 | 任务级 CPU/Memory 资源配置 |
| 依赖分析 | 多语言 Monorepo 支持自动检测，SBOM 生成与回传 |
| Dependency-Track 集成 | SBOM 上传 → 漏洞分析 → 结果入库 |
| 制品管理 | 流水线运行产物归档与下载 |
| Pod 终端 | 运行中 Pod 的 Web 终端（kubectl exec） |
| 工具链 | 任务工具链模板管理 |

### 容器平台

| 能力 | 说明 |
|------|------|
| 集群管理 | 多集群注册与切换（kubeconfig 导入） |
| 节点管理 | 节点列表、详情、监控 |
| Pod 管理 | 列表、详情、日志流式查看、Web Shell 终端、文件浏览与上传下载 |
| 工作负载 | Deployment / StatefulSet 详情与 YAML 编辑 |
| 网络与存储 | Service、ConfigMap、Secret、PVC、StorageClass 管理 |
| 监控 | 集群/节点/Pod 监控指标（CPU、内存、网络、磁盘）；JVM 监控 |
| 注册中心 | 镜像仓库管理（Harbor），镜像/仓库浏览，漏洞扫描结果查看 |
| 事件 | Kubernetes 事件流实时展示 |

### API 测试平台

| 能力 | 说明 |
|------|------|
| API 调试 | 类 Postman 工作台：请求构建、响应查看、断言、脚本、认证编辑 |
| API 定义管理 | 接口分组、定义 CRUD、Swagger/OpenAPI 兼容 |
| 集合管理 | 集合树形组织、批量运行、运行历史 |
| 环境管理 | 多环境变量管理，变量插值替换 |
| cURL 导入 | 粘贴 cURL 命令自动解析为 API 请求 |
| 请求历史 | 调试历史记录与回放 |
| Shell 工作台 | 集成式工作区：API 树、集合面板、环境面板、历史记录 |

### 文档生成（Docgen）

| 能力 | 说明 |
|------|------|
| 格式支持 | Word（.docx）、Excel（.xlsx）、PPT（.pptx）、图片（.png）、Markdown（.md）、PDF（.pdf） |
| 批量生成 | 多格式 × 多文件大小 × 文件数，自动生成所有组合 |
| 文件大小梯度 | 100KB、500KB、1MB、2MB、5MB、10MB、15MB、20MB，支持多选 |
| 输出方式 | 单个文件直接下载 / 批量文件保存到服务器目录 |
| 文件命名 | `{格式标签}_{大小标签}_{基础名}_{序号}.{ext}`，如 `Word_100KB_文档_1.docx` |
| 生成引擎 | Python 脚本（`python-docx`、`openpyxl`、`python-pptx`、`matplotlib`、`markdown`、`fpdf`） |

### 文件解析（File Parser）

| 能力 | 说明 |
|------|------|
| 图片 OCR | 支持图片文字提取（Tesseract） |
| 文件压缩 | 配置压缩质量、最大尺寸、最小压缩比 |
| 格式检测 | 基于 MIME Type 的格式识别，支持 WPS Office 及国产信创格式 |

### 图片处理

| 能力 | 说明 |
|------|------|
| 格式转换 | 图片格式互转（PNG/JPEG/WebP/BMP/GIF/SVG/TIFF） |
| 批量转换 | 多文件批量转换，支持质量与尺寸配置 |
| 历史记录 | 转换操作历史查看与回退 |

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 21、Spring Boot 3.4、Spring Security、MyBatis-Plus |
| 前端 | Vue 3、TypeScript、Vite 6、Naive UI、Pinia、Vue Flow |
| 脚本 | Python 3（python-docx、openpyxl、python-pptx、matplotlib、fpdf） |
| 数据库 | SQLite（Compose / Helm 由 `schema-migrate` 容器执行 SQL；宿主机 `start-backend.sh` 仍进程内建表） |
| 构建 | Maven 3.8+、npm |
| CI/CD | Tekton（自定义 CRD + Tekton Compiler + Task 模板化） |
| 容器 | Docker、Kubernetes（k3s / Docker Desktop）、Helm |
| 质量 | Dependency-Track（SBOM 漏洞分析） |

---

## 项目结构

```
hfwas-devops/
├── backend/
│   ├── user-api/                    # 用户模块公共 API / 注解 / 错误码
│   ├── user-core/                   # 用户领域逻辑、认证、租户、站内信
│   ├── pm-core/                     # PM 内核：事项、字段、查询引擎、工作流
│   ├── pipeline-core/               # 流水线内核：编排、Tekton 编译、凭证、任务、依赖分析
│   ├── container-core/              # 容器平台：K8s 集群管理、Pod/Deployment/Service 等资源
│   ├── api-test-core/               # API 测试平台：调试、定义、集合、环境管理
│   ├── image-core/                  # 图片处理：格式转换、批量处理
│   ├── file-parser/                 # 文件解析：图片 OCR、文件压缩、MIME 格式检测
│   ├── server/                      # Spring Boot 启动入口 + REST Controllers
│   ├── scripts/                     # Python 脚本（文档生成引擎 generate_doc.py）
│   └── .dockerignore                # 后端 Docker 构建忽略规则
├── frontend/
│   ├── src/modules/
│   │   ├── user/                    # 用户 / 租户 / 认证
│   │   ├── pm/                      # 项目管理
│   │   ├── pipeline/                # 流水线 / CI
│   │   ├── container/               # 容器平台
│   │   ├── api-test/                # API 测试平台
│   │   ├── image/                   # 图片处理
│   │   ├── docgen/                  # 文档生成
│   │   └── file-parser/             # 文件解析
│   ├── docker/
│   │   └── nginx.conf               # 生产环境 Nginx 配置
│   └── .dockerignore                # 前端 Docker 构建忽略规则
├── deploy/docker/                   # 各服务 Dockerfile，构建上下文仍是对应源码目录
│   ├── backend/
│   ├── frontend/
│   ├── cn-app-operator/
│   ├── delivery-platform-backend/
│   ├── delivery-platform-frontend/
│   └── keycloak-http-listener/
├── deploy/charts/
│   ├── backend/                     # Helm Chart（后端 Spring Boot 部署）
│   │   ├── Chart.yaml
│   │   ├── values.yaml
│   │   └── templates/               # ConfigMap、Deployment、HPA、Service、Secret、PVC、Logback
│   ├── frontend/                    # Helm Chart（前端 Vue 3 + Nginx 部署）
│   │   ├── Chart.yaml
│   │   ├── values.yaml
│   │   └── templates/               # ConfigMap、Deployment、HPA、Ingress、Service
│   ├── keycloak/                    # Helm Chart（Keycloak 身份认证 + Postgres）
│   ├── kong/                        # Helm Chart（Kong API 网关，DB-less 模式）
│   ├── gitlab/                      # Helm Chart（本机 GitLab 实例）
│   ├── harbor/                      # Helm Chart（Harbor 镜像仓库）
│   └── dependency-track/            # Helm Chart（Dependency-Track SBOM 分析）
├── docker-compose.yml               # 本地 Docker Compose 编排（backend / frontend / Kong / Keycloak）
├── scripts/                         # 本地开发启动脚本
├── keycloak/                        # Keycloak SPI 自定义扩展
│   └── http-event-listener/         # Keycloak 事件回写后端监听器
└── docs/                            # 设计文档与 API 说明
```

**分层原则：** `pm-core` / `user-core` / `pipeline-core` / `container-core` / `api-test-core` / `image-core` 承载领域逻辑，`server` 仅做 HTTP 适配；前端按模块划分路由与 API Client。

---

## 环境要求

| 工具 | 版本 |
|------|------|
| JDK | 21 |
| Maven | 3.8+ |
| Node.js | 18+（推荐 20+） |
| npm | 9+ |
| Python | 3.8+（文档生成、OCR v6；启动脚本会自动建虚拟环境） |

---

## 快速开始

### 一键启动（推荐）

```bash
# 首次或依赖变更时
./scripts/start-dev.sh --build --install

# 日常开发
./scripts/start-dev.sh
```

脚本会通过 Docker Compose 启动后端、前端、Kong 与 Keycloak。构建产物在 `artifacts/`，日志在 `logs/`。`Ctrl+C` 退出时会停止全部容器。Docker Desktop 需已启动。宿主机热更新请用 `scripts/start-backend.sh` / `scripts/start-frontend.sh`。

| 服务 | 地址 |
|------|------|
| 统一入口（Kong） | http://localhost:8000 |
| 前端（nginx） | http://localhost:80 |
| 后端 API | http://localhost:8089 |
| Keycloak 管理台 | http://localhost:8081/auth/admin |
| 健康检查 | http://localhost:8089/health/check |

停止服务：

```bash
./scripts/stop-dev.sh
```

### 分别启动

```bash
# 后端（可选 --build 先编译）
./scripts/start-backend.sh --build

# 前端（可选 --install）
./scripts/start-frontend.sh
```

### 流水线执行集群（可选）

```bash
# 启动流水线执行环境（Tekton + 本地集群）
./scripts/start-pipeline-cluster.sh
```

### 默认账号

首次启动且数据库为空时，会自动创建：

| 用户名 | 密码 | 角色 |
|--------|------|------|
| `admin` | `admin123` | 平台管理员 |

登录页：http://localhost:8000/user/login

> 生产环境务必修改 JWT Secret 与默认密码。本地开发库可随时删除 `./data/hfwas-devops.db` 重建。

---

## 服务与接口

### 端口与代理

| 环境 | 后端 | 前端 |
|------|------|------|
| 本地默认 | `8089` | `5173` |

前端请求统一加前缀 `/api`，Vite 代理会去掉前缀后转发到后端（见 `frontend/vite.config.ts`）。

可通过环境变量覆盖端口：

```bash
BACKEND_PORT=8089 FRONTEND_PORT=5173 ./scripts/start-dev.sh
```

### 认证

除 `/health/check`、`/user/auth/login` 外，接口需携带：

| Header | 说明 |
|--------|------|
| `Authorization` | `Bearer {JWT}` |
| `X-Tenant-Id` | 当前租户 ID（推荐；前端切换租户后持久化） |

### 响应格式

业务接口统一返回 `BaseResult<T>`：

```json
{
  "code": 0,
  "msg": null,
  "data": {},
  "requestId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

- `code === 0` 表示成功；非零为业务错误码（见 [docs/error-code-design.md](docs/error-code-design.md)）
- `requestId` 请求追踪 ID，由 `RequestIdResponseAdvice` 自动注入每个响应，出错时用于检索完整链路日志
- Long 类型 ID 序列化为字符串，避免 JavaScript 精度丢失

### API 模块前缀

| 前缀 | 模块 |
|------|------|
| `/health/*` | 健康检查 |
| `/user/*` | 用户、租户、认证、站内信、审计 |
| `/pm/*` | 项目管理 |
| `/pipeline/*` | 流水线编排、凭证、任务市场、工具链 |
| `/dependency/*` | 依赖分析、组件管理 |
| `/container/*` | 容器平台（集群、Pod、Deployment、Service 等） |
| `/apitest/*` | API 测试（调试、定义、集合、环境） |
| `/api/docgen/*` | 文档生成 |
| `/api/image/*` | 图片处理 |

### 主要 REST 入口

**用户模块**

| Controller | Base Path | 说明 |
|------------|-----------|------|
| UserAuthController | `/user/auth` | 登录、登出、当前用户、切换租户 |
| UserManageController | `/user/users` | 用户管理（admin） |
| TenantManageController | `/user/tenants` | 租户管理（admin） |
| TenantMemberController | `/user/tenants/{tenantId}/members` | 租户成员 |
| UserMessageController | `/user/messages` | 站内信 |
| UserSessionController | `/user/sessions` | 会话管理（admin） |
| LoginLogController | `/user/login-logs` | 登录日志（admin） |
| OperLogController | `/user/oper-logs` | 操作日志（admin） |
| IdentityConnectorController | `/user/integrations` | 身份集成（admin） |
| NotifyChannelController | `/user/message-notify` | 通知渠道（admin） |

**PM 模块**

| Controller | Base Path | 说明 |
|------------|-----------|------|
| PmProjectController | `/pm/projects` | 项目 CRUD |
| PmProjectModuleController | `/pm/project-modules` | 项目模块 |
| PmWorkItemController | `/pm/work-items` | 事项 CRUD、流转、关联 |
| PmWorkItemCommentController | `/pm/work-items` | 评论 |
| PmWorkItemActivityController | `/pm/work-items` | 活动日志 |
| PmWorkItemImportExportController | `/pm/work-items/io` | Excel 导入导出 |
| PmFieldDefinitionController | `/pm/fields/definitions` | 字段定义 |
| PmFieldLayoutController | `/pm/fields/layout` | 字段布局 |
| PmStatusWorkflowController | `/pm/status/workflow` | 状态工作流 |
| PmIssueTypeSchemeController | `/pm/issue-type-schemes` | 类型方案 Import/Export |
| PmProjectIssueTypeController | `/pm/projects/issue-types` | 项目启用类型 |
| PmSavedViewController | `/pm/views` | 保存视图 |
| PmMetaController | `/pm` | 元数据、看板、类型目录 |

**流水线 / CI 模块**

| Controller | Base Path | 说明 |
|------------|-----------|------|
| PipelineController | `/pipeline/pipelines` | 流水线 CRUD、运行、日志 |
| PipelineCredentialController | `/pipeline/credentials` | 凭证管理（Git / 镜像 / kubeconfig） |
| PipelineTaskKindController | `/pipeline/task-kinds` | 任务市场 / 任务类型 |
| PipelineToolchainController | `/pipeline/toolchains` | 工具链模板 |
| PipelineJobParamController | `/pipeline/job-params` | 任务运行时参数 |
| PipelineRunArtifactController | `/pipeline/runs/{runId}/artifacts` | 运行产物 |
| PodExecController | `/pipeline/pipelines/{id}/runs/{runId}/jobs/{jobId}` | Pod 终端 WebSocket |
| DependencyScanController | `/dependency-scan` | 依赖扫描触发 |
| DependencyComponentController | `/dependency/components` | 组件管理 |

**容器平台**

| Controller | Base Path | 说明 |
|------------|-----------|------|
| ClusterController | `/container/clusters` | 集群注册与管理 |
| NamespaceController | `/container/clusters/{clusterId}/namespaces` | 命名空间管理 |
| NodeController | `/container/clusters/{clusterId}` | 节点列表与详情 |
| PodController | `/container/clusters/{clusterId}` | Pod 列表、详情、日志、YAML |
| PodFileController | `/container/clusters/{clusterId}/namespaces/{ns}/pods/{name}` | Pod 文件浏览与传输 |
| DeploymentController | `/container/clusters/{clusterId}` | Deployment 管理 |
| StatefulSetController | `/container/clusters/{clusterId}` | StatefulSet 管理 |
| ServiceController | `/container/clusters/{clusterId}` | Service 管理 |
| ConfigMapController | `/container/clusters/{clusterId}` | ConfigMap 管理 |
| SecretController | `/container/clusters/{clusterId}` | Secret 管理 |
| PvcController | `/container/clusters/{clusterId}` | 持久卷声明管理 |
| StorageClassController | `/container/clusters/{clusterId}` | 存储类管理 |
| EventController | `/container/clusters/{clusterId}/namespaces/{ns}/events` | 事件流 |
| MonitorController | `/container/clusters/{clusterId}/monitor` | 监控指标 |
| RegistryController | `/container/registries` | 镜像仓库注册 |
| ImageController | `/container/registries/{registryId}` | 镜像/仓库浏览 |
| ImageSearchController | `/container/images` | 镜像搜索 |

**API 测试平台**

| Controller | Base Path | 说明 |
|------------|-----------|------|
| ApiDebugController | `/apitest/debug` | API 调试（发送请求） |
| ApiDefinitionController | `/apitest/definitions` | API 定义管理 |
| ApiGroupController | `/apitest/groups` | API 分组 |
| CollectionController | `/apitest/collections` | 集合管理 |
| CollectionFolderController | `/apitest/collections/{id}/folders` | 集合文件夹 |
| CollectionItemController | `/apitest/collections/{id}/items` | 集合项 |
| CurlImportController | `/apitest/curl` | cURL 导入解析 |
| EnvironmentController | `/apitest/environments` | 环境变量管理 |
| DebugHistoryController | `/apitest/debug-histories` | 调试历史 |

**文档生成模块**

| Controller | Base Path | 说明 |
|------------|-----------|------|
| DocgenController | `/api/docgen` | 文档生成：单文件下载、批量生成到目录 |

**图片处理**

| Controller | Base Path | 说明 |
|------------|-----------|------|
| ImageProcessorController | `/api/image` | 图片转换、批量处理、格式信息 |

完整接口清单见各模块设计文档。

---

## 配置

主配置文件：`backend/server/src/main/resources/application.yml`

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `server.port` | `8089` | HTTP 端口 |
| `spring.datasource.url` | `jdbc:sqlite:./data/hfwas-devops.db` | SQLite 路径（相对 server 工作目录） |
| `user.jwt.secret` | 内置 dev 值 | **生产必须修改** |
| `user.jwt.expire-seconds` | `86400` | Token 有效期（秒） |
| `docgen.script-path` | `../scripts/generate_doc.py` | 文档生成 Python 脚本路径 |
| `docgen.python-path` | `python3` | Python 解释器路径 |
| `docgen.output-dir` | `../../files` | 文档生成默认输出目录（相对 server 工作目录） |

开发 profile（`application-dev.yml`）会禁用 Redis 自动配置；本地无需 Redis。

数据库 Schema 在启动时由 Migration 自动执行：

- `backend/user-core/src/main/resources/db/user-schema.sql`
- `backend/server/src/main/resources/db/pm-schema.sql`

---

## 构建

### 后端

```bash
cd backend
mvn install -pl server -am -DskipTests
```

产物：`backend/server/target/server-1.0-SNAPSHOT.jar`（版本以 pom 为准）。

运行：

```bash
cd backend/server
java -jar target/server-1.0-SNAPSHOT.jar
```

### Python 文档生成与 OCR

`./scripts/start-backend.sh` / `start-dev.sh` 会自动准备 `backend/scripts/.venv` 虚拟环境，并安装：

- `backend/scripts/requirements.txt`（文档生成）
- `backend/scripts/requirements-ocr.txt`（PP-OCRv6 worker）
- `backend/scripts/prepare_ocr_models.py`（把 PP-OCRv6 ONNX 保存到 `backend/file-parser/src/main/resources/ocr/models/`）

也可手动安装：

```bash
python3 -m venv backend/scripts/.venv
backend/scripts/.venv/bin/pip install -r backend/scripts/requirements.txt
backend/scripts/.venv/bin/pip install -r backend/scripts/requirements-ocr.txt
backend/scripts/.venv/bin/python backend/scripts/prepare_ocr_models.py
```

文档生成脚本：`backend/scripts/generate_doc.py`。
OCR worker：`backend/file-parser/src/main/resources/ocr/ocr_worker.py`（Java 启动后常驻，不需要单独起进程）。

跳过 Python 准备：`./scripts/start-backend.sh --skip-python`。

### 前端

```bash
cd frontend
npm install
npm run build
```

产物：`frontend/dist/`

### Docker 构建

```bash
# 后端镜像（从项目根目录构建）
docker build -t hfwas/devops-backend:latest -f deploy/docker/backend/Dockerfile .

# 前端镜像（上下文是 frontend/）
docker build -t hfwas/devops-frontend:latest -f deploy/docker/frontend/Dockerfile ./frontend
```

### Docker Compose 启动

```bash
# 全部服务（backend / frontend / Kong / Keycloak）
docker compose up -d --build
./scripts/start-dev.sh --build

# 只起应用容器
docker compose up -d backend frontend
./scripts/start-dev.sh --no-kong

# 查看日志
docker compose logs -f
tail -f logs/backend/devops.log logs/frontend/access.log logs/kong/error.log logs/keycloak/keycloak.log
```

产物：`artifacts/backend/server.jar`、`artifacts/frontend/`。

### Helm 部署（Kubernetes）

本地改完 backend / frontend 后打镜像并升级，用 `deploy/charts/deploy-app.sh`（Windows：`deploy-app.ps1`），说明见 [`docs/devops/app-helm-deploy.md`](docs/devops/app-helm-deploy.md)。

本机 GitLab 要把当前分支已提交代码推上去时，用 `scripts/sync-gitlab.ps1`（macOS / Git Bash：`scripts/sync-gitlab.sh`），说明见 [`deploy/charts/gitlab/README.md`](deploy/charts/gitlab/README.md)。

```bash
./deploy/charts/deploy-app.sh backend
./deploy/charts/deploy-app.sh frontend
```

Chart 在 `deploy/charts/`。Keycloak / Kong / Harbor / Dependency-Track 镜像清单见 [`deploy/charts/images.txt`](deploy/charts/images.txt)。

```bash
# SPI 镜像（Keycloak 事件回写后端）
docker build -t hfwas/keycloak-http-listener:latest -f deploy/docker/keycloak-http-listener/Dockerfile keycloak/http-event-listener

# Keycloak（默认带 Postgres；k3s 用 values-k3s.yaml，Service 名为 keycloak）
helm install keycloak ./deploy/charts/keycloak -f ./deploy/charts/keycloak/values-k3s.yaml

# Kong DB-less（上游默认 devops-backend / devops-frontend / keycloak）
helm install kong ./deploy/charts/kong -f ./deploy/charts/kong/values-k3s.yaml

# GitLab 实例
helm install gitlab ./deploy/charts/gitlab -f ./deploy/charts/gitlab/values-k3s.yaml

# Harbor 镜像仓库
helm install harbor ./deploy/charts/harbor -f ./deploy/charts/harbor/values-k3s.yaml

# Dependency-Track SBOM 分析
helm install dependency-track ./deploy/charts/dependency-track \
  -f ./deploy/charts/dependency-track/values-k3s.yaml

# 安装后端
helm install devops-backend ./deploy/charts/backend \
  --set config.jwtSecret="your-secret-here" \
  --set replicaCount=2

# 安装前端（需先部署后端）
helm install devops-frontend ./deploy/charts/frontend \
  --set config.backendUrl="http://devops-backend:8089" \
  --set ingress.hosts[0].host="devops.example.com" \
  --set replicaCount=2

# 升级
helm upgrade keycloak ./deploy/charts/keycloak
helm upgrade kong ./deploy/charts/kong
helm upgrade devops-backend ./deploy/charts/backend
helm upgrade devops-frontend ./deploy/charts/frontend
helm upgrade gitlab ./deploy/charts/gitlab
helm upgrade harbor ./deploy/charts/harbor
helm upgrade dependency-track ./deploy/charts/dependency-track

# 卸载
helm uninstall kong
helm uninstall keycloak
helm uninstall devops-backend
helm uninstall devops-frontend
helm uninstall gitlab
helm uninstall harbor
helm uninstall dependency-track
```

---

## 前端路由

| 路径 | 说明 |
|------|------|
| `/workbench` | 个人工作台 |
| `/user/login` | 登录 |
| `/pm/projects` | 项目列表 |
| `/pm/projects/:id/items/:type` | 事项列表 |
| `/pm/projects/:id/board/:type` | 看板 |
| `/pm/projects/:id/settings/*` | 项目设置（模块、字段、类型、工作流） |
| `/pm/monitor` | PM 监控仪表盘 |
| `/user/*` | 用户中心（admin） |
| `/messages` | 站内信收件箱 |
| `/pipeline/pipelines` | 流水线列表与新建 |
| `/pipeline/pipelines/:id/edit` | 流水线编排（DAG 可视化编辑） |
| `/pipeline/pipelines/:id/runs/:runId` | 运行详情 |
| `/pipeline/credentials` | 凭证管理 |
| `/pipeline/task-kinds` | 任务市场 |
| `/pipeline/dependency/components` | 依赖组件列表 |
| `/pipeline/dependency/scan` | 依赖扫描记录 |
| `/container/clusters` | 集群列表 |
| `/container/clusters/:id` | 集群详情（Pod / Deployment / Service 等） |
| `/container/clusters/:id/pods/:namespace/:name` | Pod 详情、日志、终端、文件 |
| `/container/monitor/*` | 监控仪表盘 |
| `/container/registries` | 镜像仓库列表 |
| `/container/registries/:id` | 仓库详情（项目 / 仓库 / 镜像） |
| `/container/images` | 镜像搜索 |
| `/apitest/workspace` | API 测试工作台 |
| `/apitest/definitions` | API 定义管理 |
| `/apitest/collections` | 集合管理 |
| `/apitest/environments` | 环境管理 |
| `/docgen` | 文档生成 |
| `/file-parser` | 文件解析 |
| `/image` | 图片处理 |

---

## 文档索引

| 文档 | 说明 |
|------|------|
| **项目管理** | |
| [docs/pm-design.md](docs/pm-design.md) | PM 架构与领域设计 |
| [docs/pm-api.md](docs/pm-api.md) | PM REST API 完整说明 |
| [docs/pm-evolution-roadmap.md](docs/pm-evolution-roadmap.md) | PM 分步演进路线图 |
| [docs/pm-jira-comparison.md](docs/pm-jira-comparison.md) | 与 Jira 能力对比 |
| [docs/evolution/](docs/evolution/) | 各演进步骤详细设计 |
| **流水线/CI** | |
| [docs/pipeline/pipeline-core-api.md](docs/pipeline/pipeline-core-api.md) | 流水线 API 说明 |
| [docs/pipeline/cicd-tech-selection.md](docs/pipeline/cicd-tech-selection.md) | CI/CD 技术选型 |
| [docs/pipeline/tekton-intro.md](docs/pipeline/tekton-intro.md) | Tekton 介绍 |
| [docs/pipeline/pod-exec-terminal-design.md](docs/pipeline/pod-exec-terminal-design.md) | Pod 终端设计 |
| [docs/pipeline/task-script-template-design.md](docs/pipeline/task-script-template-design.md) | 任务脚本模板化设计 |
| [docs/pipeline/runtime-param-design.md](docs/pipeline/runtime-param-design.md) | 运行时参数设计 |
| [docs/pipeline/task-resource-config-design.md](docs/pipeline/task-resource-config-design.md) | 任务资源配置设计 |
| [docs/pipeline/kubectl-task-design.md](docs/pipeline/kubectl-task-design.md) | KUBECTL 任务类型设计 |
| [docs/pipeline/pipeline-task-marketplace-design.md](docs/pipeline/pipeline-task-marketplace-design.md) | 任务市场设计 |
| [docs/pipeline/dependency-analysis-design.md](docs/pipeline/dependency-analysis-design.md) | 依赖分析设计 |
| [docs/pipeline/dependency-track-design.md](docs/pipeline/dependency-track-design.md) | Dependency-Track 集成设计 |
| [docs/pipeline/monorepo-dependency-analysis-design.md](docs/pipeline/monorepo-dependency-analysis-design.md) | Monorepo 依赖分析设计 |
| [docs/pipeline/pipeline-toolchain-image-strategy.md](docs/pipeline/pipeline-toolchain-image-strategy.md) | 工具链镜像策略 |
| **容器平台** | |
| [docs/container-platform/](docs/container-platform/) | 容器平台文档 |
| **部署运维** | |
| [docs/devops/](docs/devops/) | 本地与生产部署 |
| [docs/devops/app-helm-deploy.md](docs/devops/app-helm-deploy.md) | 应用镜像构建与 Helm 升级 |
| **后端架构** | |
| [docs/backend/](docs/backend/) | 后端架构与 API |
| [docs/error-code-design.md](docs/error-code-design.md) | 全局错误码规范 |
| **前端** | |
| [docs/frontend/](docs/frontend/) | 前端文档 |
| **其他** | |
| [docs/docgen/](docs/docgen/) | 文档生成设计 |
| [docs/file-parser/](docs/file-parser/) | 文件解析设计 |
| [docs/image/](docs/image/) | 图片处理设计 |
| [docs/api-test/](docs/api-test/) | API 测试平台设计 |
| [docs/general/](docs/general/) | 测试、进度、安全 |
| [docs/superpowers/](docs/superpowers/) | 设计稿与实现计划 |

---

## CI

GitHub Actions（`.github/workflows/maven.yml`）在 `master` / `dev` 分支 push 时执行后端 Maven 构建（Java 21）。

---

## 开发说明

- 本项目为 **绿野（从 0 到 1）** 模式：改 schema / API 时直接改单一真相，不做存量兼容；本地库可随时删除重建。
- 后端模块边界：`pm-core` / `pipeline-core` / `container-core` / `api-test-core` 不依赖 HTTP；新增能力优先在内核实现，Controller 只做 DTO 转换。
- 前端按模块划分，共享请求封装在 `frontend/src/shared/api/`。

---

## 常见问题

**端口被占用**

```bash
./scripts/start-dev.sh --force
# 或
./scripts/stop-dev.sh
```

**重置数据库**

```bash
./scripts/stop-dev.sh
rm -f backend/server/data/hfwas-devops.db
./scripts/start-backend.sh --build
```

**后端启动超时**

查看日志：`logs/backend.log`

**前端 API 404**

确认后端已就绪（`curl http://localhost:8089/health/check` 返回 `UP`），且前端通过 `/api` 前缀访问。