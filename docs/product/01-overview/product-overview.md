# 产品总览

> 日期：2026-10-19
> 版本：v0.2
> 状态：已完成
> 数据来源：`README.md`、`frontend/src/shared/console/products.ts`、`frontend/src/router.tsx`、`backend/*/controller`、`docs/`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：产品定位、目标用户、子系统、核心链路、边界与技术栈 |
| v0.2 | 2026-10-19 | 编排文件迁入 `deploy/`：更新 `docker-compose.yml` 路径引用 |

---

## 1. 产品定位

HFWAS DevOps 是一套面向研发团队的**一体化 DevOps 平台**，把“需求与项目协作、代码流水线、容器与集群管理、接口质量保障、文档与文件处理”收敛到同一个控制台，减少研发在 Jira、Jenkins、Kubernetes Dashboard、Postman、在线文档工具之间的来回切换。

产品形态：单体 Spring Boot 后端 + React SPA 前端 + SQLite 本地数据 + Kubernetes/Tekton 执行面。默认零外部依赖即可本地启动，生产通过 Helm 部署到 Kubernetes。

## 2. 目标用户

| 角色 | 典型诉求 | 主要使用模块 |
|------|----------|--------------|
| 平台管理员 | 纳管租户、用户、集群、镜像仓库、通知渠道 | 用户中心、容器平台 |
| 项目/研发负责人 | 项目、需求、任务、缺陷、看板与工作流 | 项目管理 |
| 研发工程师 | 提交流水线、查看运行日志、Pod 终端、调试接口 | 流水线、容器平台、API 测试 |
| 测试工程师 | 接口定义与调试、集合批量运行、测试用例 | API 测试平台、项目管理 |
| 运维/SRE | 多集群管理、工作负载与 Helm 发布、监控告警 | 容器平台、流水线 |
| 售前/文档人员 | 生成样例文档、解析与转换文件、图片处理 | Docgen、文件解析、图片处理 |

## 3. 子系统

控制台产品注册表位于 `frontend/src/shared/console/products.ts`，共 10 项：8 项已上线、2 项占位；用户中心不在产品切换器中，但属于平台子系统。

| # | 子系统 | 控制台路径 | 分组 | 状态 | 一句话说明 |
|---|--------|-----------|------|------|-----------|
| 1 | 项目管理（PM） | `/pm/projects` | 研发协同 | 已上线 | 项目、事项、动态字段、工作流、看板 |
| 2 | 接口测试 | `/api-test` | 质量保障 | 已上线 | API 定义、调试、集合、环境 |
| 3 | 文件解析 | `/file-parser` | 效率工具 | 已上线 | 文档/PDF/图片解析与 OCR |
| 4 | 文档生成 | `/docgen` | 效率工具 | 已上线 | Word/Excel/PPT/图片/PDF/Markdown 生成 |
| 5 | 图片处理 | `/image` | 效率工具 | 已上线 | 图片预览、格式转换、元数据 |
| 6 | 流水线 | `/pipeline/pipelines` | 研发协同 | 已上线 | Tekton 流水线编排、运行、凭证、任务市场 |
| 7 | 容器管理 | `/container/clusters` | 基础设施 | 已上线 | 多集群 K8s、工作负载、Helm、镜像仓库、监控 |
| 8 | 工作流（Argo） | `/argo/workflows` | 基础设施 | 后端可用、前端未接入 | 基于 Argo Workflows 的通用工作流引擎 |
| — | 用户中心 | `/user/accounts` | 平台 | 已上线（不在产品切换器） | 用户、租户、日志、站内信、身份集成 |
| 9 | 制品仓库 | `/artifact/overview` | 基础设施 | 占位（comingSoon） | 镜像与二进制制品托管 |
| 10 | 安全审计 | `/audit/overview` | 安全治理 | 占位（comingSoon） | 操作审计与权限合规 |

补充：`/argo` 系列 Controller 已存在，但 `frontend/src/router.tsx` 尚未注册 `/argo/*` 路由，因此“工作流”产品当前只有后端能力。

## 4. 核心业务链路

### 4.1 研发协同链路

```text
租户/用户登录（Keycloak）
  → 选择项目（PM）
  → 创建需求/任务/缺陷，配置字段与状态工作流
  → 看板流转、评论、关联、活动审计
  → 流水线触发构建与部署
  → 容器平台查看 Pod/日志/监控
```

### 4.2 交付链路

```text
流水线定义（阶段/任务/参数/凭证）
  → 运行时选择参数
  → Tekton 编译并执行
  → 构建镜像 → 推送 Harbor
  → Helm 升级到目标集群
  → 运行产物与 SBOM 归档 → Dependency-Track 漏洞分析
```

### 4.3 质量链路

```text
接口定义（分组/版本/参数/响应/断言/脚本）
  → 环境变量
  → 单接口调试 / cURL 导入
  → 集合批量运行
  → 运行历史与断言结果
```

### 4.4 效率工具链路

```text
Docgen：选择格式/大小/数量 → 生成 → 下载或落盘
File Parser：上传文件 → 格式识别 → 文本/OCR/压缩结果
Image：上传/会话 → 元数据 → 格式转换 → 结果下载/历史
```

## 5. 产品边界与非目标

| 类型 | 内容 |
|------|------|
| 已覆盖 | 项目与事项协作、CI 流水线、K8s 多集群运维、接口测试、文档/文件/图片工具、用户与租户 |
| 明确占位 | 制品仓库、安全审计（仅控制台入口，标 comingSoon） |
| 非目标 | 通用 ITSM/工单、代码托管（对接外部 GitLab，不内置仓库）、通用 BI、独立低代码平台 |
| 独立产品 | 交付运维平台（Delivery Ops）为独立局点产品，设计与代码不进入本仓库 `backend/`、`frontend/`，见 `docs/superpowers/specs/2026-09-16-delivery-ops-platform-design.md` |

## 6. 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 21、Spring Boot 3.4、Spring Security（OAuth2 Resource Server）、MyBatis-Plus |
| 前端 | React 19、TypeScript、Vite 6、React Router 8、Tailwind CSS v4、shadcn/ui（Radix）、TanStack Query、Zustand、Zod |
| 数据库 | SQLite（本地与默认 Helm 部署）；DB 文件位于 `data/hfwas-devops.db` |
| 执行面 | Kubernetes（k3s / Docker Desktop）、Tekton、Helm、Harbor、Prometheus |
| 脚本 | Python 3（python-docx、openpyxl、python-pptx、matplotlib、markdown、fpdf） |
| 认证 | Keycloak（JWT），网关 Kong（DB-less） |
| 质量 | Dependency-Track（SBOM 漏洞分析）、Semgrep/Sonar（仓库含相关脚本与配置） |

> 说明：`docs/pm/`、`docs/api-test/`、`docs/container-platform/` 下部分历史设计文档仍写 Vue 3 + Naive UI + Pinia，属于**历史归档**；当前前端权威规范见 `docs/frontend/ai-collaboration-frontend.md`。

## 7. 部署形态

| 形态 | 适用场景 | 入口 |
|------|----------|------|
| docker-compose | 本地一键启动（backend/frontend/Kong/Keycloak） | `deploy/docker-compose.yml`、`scripts/` |
| Helm + Kubernetes | 生产/测试环境 | `deploy/charts/` |
| 宿主机进程 | 本地开发调试 | `scripts/start-backend.sh`、前端 `npm run dev` |

部署与运维细节见 [deployment-ops.md](../07-operations/deployment-ops.md) 与 [architecture.md](../07-operations/architecture.md)。

## 8. 相关文档

- 功能清单：[feature-catalog.md](../02-features/feature-catalog.md)
- 术语与领域模型：[glossary-domain-model.md](glossary-domain-model.md)
- 角色与权限：[roles-permissions.md](roles-permissions.md)
- 数据库总览：[data-dictionary.md](../03-database/data-dictionary.md)
- 接口总览：[api-overview.md](../04-api/api-overview.md)
- 测试规范：[test-spec.md](../05-testing/test-spec.md)
