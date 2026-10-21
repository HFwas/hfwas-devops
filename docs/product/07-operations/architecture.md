# 系统架构

> 日期：2026-10-21
> 版本：v0.3
> 状态：已完成
> 数据来源：`pom.xml`、`backend/*/pom.xml`、`frontend/src/`、`deploy/docker-compose.yml`、`deploy/charts/`、`docs/backend/module_arch.md`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：模块划分、部署拓扑、请求与数据流 |
| v0.3 | 2026-10-21 | 数据库由 SQLite 切换为 MySQL 8 + Flyway，更新拓扑与数据流 |
| v0.2 | 2026-10-19 | 编排文件迁入 `deploy/`：更新 `docker-compose.yml` 路径引用 |

---

## 1. 逻辑架构

```text
┌──────────────────────────────────────────────────────────────┐
│                     浏览器（React SPA）                       │
│  React 19 · React Router 8 · TanStack Query · Zustand         │
│  Tailwind v4 · shadcn/ui · Keycloak JS                        │
└───────────────┬──────────────────────────────────────────────┘
                │ HTTP / WebSocket
        ┌───────▼────────┐
        │  Kong（网关）   │  可选；本地直连也可
        └───────┬────────┘
                │
┌───────────────▼──────────────────────────────────────────────┐
│                  Spring Boot 单体（server）                   │
│  ┌──────────┬──────────┬──────────┬──────────┬─────────────┐  │
│  │ user-core│ pm-core  │pipeline- │container-│ api-test-   │  │
│  │          │          │ core     │ core     │ core        │  │
│  ├──────────┼──────────┼──────────┼──────────┼─────────────┤  │
│  │ image-   │file-     │ docgen   │ user-api │ server      │  │
│  │ core     │ parser   │ (server) │ (SPI)    │ (启动/聚合)  │  │
│  └──────────┴──────────┴──────────┴──────────┴─────────────┘  │
└───────┬───────────────┬───────────────┬───────────────┬───────┘
        │               │               │               │
   ┌────▼────┐     ┌────▼────┐     ┌────▼─────┐    ┌────▼─────┐
   │ MySQL 8 │     │ Keycloak│     │ Kubernetes│    │ Harbor / │
   │ 单文件   │     │  JWT    │     │ + Tekton  │    │ Prometheus│
   └─────────┘     └─────────┘     └───────────┘    └──────────┘
```

## 2. 后端模块

| Maven 模块 | 职责 | 关键包 |
|------------|------|--------|
| `backend/user-api` | 跨模块 SPI/契约：`BaseResult`、`ErrorCode`、租户头、操作日志注解 | `com.hfwas.devops.common.*`、`user.spi` |
| `backend/user-core` | 用户、租户、认证、租户上下文、通知、操作日志 | `com.hfwas.devops.user.*` |
| `backend/pm-core` | 项目、事项、字段、工作流、类型方案 | `com.hfwas.devops.pm.*`（实体/服务） |
| `backend/api-test-core` | 接口定义、调试、环境、调试历史、集合 | `com.hfwas.devops.apitest.*` |
| `backend/pipeline-core` | 流水线定义、凭证、任务类型、Tekton 编译运行、产物、依赖扫描 | `com.hfwas.devops.pipeline.*` |
| `backend/container-core` | 多集群 K8s、Helm、Registry、监控、WebSocket | `com.hfwas.devops.container.*` |
| `backend/image-core` | 图片会话、元数据、格式转换 | `com.hfwas.devops.image.*` |
| `backend/file-parser` | DOCX/PPTX/XLSX/PDF/图片/TXT 解析、OCR、压缩 | `com.hfwas.devops.fileparser.*` |
| `backend/server` | Spring Boot 启动、聚合 Controller（PM/user/docgen/health）、安全配置 | `com.hfwas.devops.*` |

> PM 与 user 的 Controller 放在 `backend/server`，领域服务在各自 core 模块；这是历史演进结果，新增模块建议 Controller 与领域逻辑同模块。

## 3. 前端结构

```text
frontend/src/
├── main.tsx              # 入口
├── router.tsx            # 全部路由
├── shared/
│   ├── api/              # axios 实例与拦截器
│   ├── console/          # 产品注册表、侧栏导航
│   ├── keycloak/         # 登录与 token
│   ├── errors/           # ApiError 与错误提示
│   └── types/            # 共享类型
├── components/
│   ├── ui/               # shadcn/ui 组件源码
│   └── console/          # 控制台壳、产品切换器
├── modules/
│   ├── pm/               # 页面、API、类型、工具
│   ├── pipeline/         # 含 graph/ 流水线图与测试
│   ├── container/
│   ├── api-test/
│   ├── user/
│   ├── docgen/
│   ├── file-parser/
│   └── image/
└── stores/               # Zustand
```

## 4. 请求链路

```text
浏览器
  → 前端 axios（注入 Authorization、X-Tenant-Id、X-Request-Id）
  → Vite 代理（/api 前缀去掉）或 Kong
  → Spring Security（JWT 校验）
  → RequestIdFilter（MDC + 响应头）
  → TenantContextFilter（解析租户）
  → Controller → Service → Mapper
  → MySQL / 外部服务
  → BaseResult（注入 requestId）
```

## 5. 数据流

| 数据 | 存储位置 | 说明 |
|------|----------|------|
| 业务数据 | MySQL 8 `hfwas_devops` | 用户、PM、流水线、容器连接信息、API 测试、图片历史 |
| 上传/生成文件 | `data/uploads`、`files/`、Pod 文件系统 | 通过卷挂载持久化 |
| 流水线日志 | `pipeline_run_job.log_text` + 日志文件 | `logs/` |
| K8s 资源 | 集群 API（实时） | 不落库 |
| Helm Release | 集群内 Helm | 不落库 |
| 监控指标 | Prometheus（实时） | 不落库 |
| 认证 | Keycloak | 后端只读 JWT |

## 6. 部署拓扑

### 6.1 docker-compose（本地/单机）

```text
frontend(80) ── backend(8089) ── MySQL 8(volume/RDS)
      │              │
      └── Kong(8000/8443/8001)
                     │
                 Keycloak(8081)
                     │
          k3s(6443/30880/30222) + Tekton
```

辅助一次性容器：`gateway-init`、`backend-build`、`frontend-build`、`keycloak-spi`、`tekton-install`。

### 6.2 Kubernetes / Helm

| Chart | 用途 |
|-------|------|
| `deploy/charts/backend` | 后端 + schema initContainer |
| `deploy/charts/frontend` | 前端静态资源 |
| `deploy/charts/keycloak` | 认证 |
| `deploy/charts/kong` | 网关（DB-less） |
| `deploy/charts/harbor` | 镜像仓库 |
| `deploy/charts/gitlab` | 代码托管 |
| `deploy/charts/dependency-track` | SBOM 漏洞分析 |
| `deploy/charts/argo-*` | Argo Workflows / Events / CD |
| `deploy/charts/delivery-platform`、`cn-app-operator` | 交付运维相关（独立产品/Operator） |

## 7. 扩展点

| 扩展点 | 位置 |
|--------|------|
| 新增控制台产品 | `frontend/src/shared/console/products.ts` + `navigation.ts` + `router.tsx` |
| 新增后端模块 | 新建 Maven 子模块，在 `backend/pom.xml` 注册 |
| 新增任务类型 | 任务市场数据（`pipeline_task_kind`） |
| 新增字段类型 | PM 字段类型 SPI |
| 新增通知渠道 | `sys_notify_channel` + `NotifyChannelService` |
| 新增身份连接器 | `sys_identity_connector` + `IdentityConnectorService` |

## 8. 已知架构风险

1. PM/user Controller 与领域模块分离，依赖关系不够清晰。
2. 单体 + MySQL，写入并发由数据库与索引支撑；迁移由 Flyway 管理。
3. 流水线/容器与 K8s 强耦合，本地未启动集群时相关功能降级。
4. 部分模块（api-test）缺少 `tenant_id`，隔离依赖 `project_id`。
