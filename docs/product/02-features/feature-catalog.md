# 功能清单

> 日期：2026-10-21
> 版本：v0.3
> 状态：骨架（能力清单已完成，逐功能验收标准待细化）
> 数据来源：`README.md`、`frontend/src/router.tsx`、`frontend/src/shared/console/products.ts`、`backend/*/controller`、各模块设计文档

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：8 个子系统功能项、状态、页面与主要 API |
| v0.2 | 2026-10-17 | 修正流水线“运行日志与终端”“运行产物”状态为部分可用（后端可用、前端未接入） |
| v0.3 | 2026-10-21 | 移除项目监控功能项与 `/pm/monitor` 入口；项目内导航改由左侧菜单承载 |

---

## 1. 状态定义

| 状态 | 含义 |
|------|------|
| 可用 | 前后端均已实现，有页面入口或稳定 API |
| 部分可用 | 后端可用但前端未接入，或仅覆盖核心路径 |
| 占位 | 控制台有入口或菜单，功能未实现 |
| 规划 | 仅在文档/路线图中出现 |

## 2. 项目管理（PM）

页面入口：`/pm/projects`、`/pm/projects/:projectId/items/:typeCode`、`.../board/:typeCode`、`.../settings/workflow/:typeCode`

| 功能项 | 说明 | 状态 | 主要 API |
|--------|------|------|----------|
| 项目 CRUD | 项目列表、创建、编辑、删除、访问上下文 | 可用 | `/pm/projects/*` |
| 项目内导航 | 进入项目后左侧展示全部项目、启用类型、看板与工作流 | 可用 | `/pm/projects/issue-types/list` |
| 事项类型 | 类型定义、项目启用类型 | 可用 | `/pm/meta/types`、`/pm/projects/issue-types/*` |
| 统一事项 | 需求/任务/缺陷等统一 CRUD、分页、流转 | 可用 | `/pm/work-items/*` |
| 动态字段 | 字段定义、选项、类型绑定、布局 | 可用 | `/pm/fields/definitions/*`、`/pm/fields/layout/*` |
| 状态工作流 | 状态矩阵、流转、校验器、后置动作 | 可用 | `/pm/status/workflow/*` |
| 看板 | 按类型分列、拖拽流转 | 可用 | `/pm/work-items/{id}/transition` |
| 事项关联 | 关联/阻塞/重复等关系 | 可用 | `/pm/work-items/links/*` |
| 评论与活动 | 评论、评论计数、变更活动 | 可用 | `/pm/work-items/{id}/comments`、`/activities` |
| 模块树 | 树形模块维护 | 可用 | `/pm/project-modules/*` |
| 保存视图 | 自定义查询与列配置 | 部分可用 | `/pm/views/*` |
| 导入导出 | Excel 模板、预览、导入、导出 | 可用 | `/pm/work-items/io/*` |
| 类型方案 | 方案导入导出、预览 | 可用 | `/pm/issue-type-schemes/*` |
| 工作台 | 快捷入口、最近访问、我的事项 | 可用 | 前端聚合 |

## 3. 用户 / 租户中心

页面入口：`/user/accounts`、`/user/settings`

| 功能项 | 说明 | 状态 | 主要 API |
|--------|------|------|----------|
| 登录与身份 | Keycloak JWT 登录、当前用户、我的租户 | 可用 | `/user/auth/me`、`/my-tenants` |
| 租户切换 | 切换当前租户上下文 | 可用 | `/user/auth/switch-tenant` |
| 用户管理 | 用户分页、创建/编辑、删除、选项 | 可用 | `/user/users/*` |
| 租户管理 | 租户分页、选项、创建/编辑、删除 | 可用 | `/user/tenants/*` |
| 租户成员 | 成员分页、可选用户、添加、改角色、移除 | 可用 | `/user/tenants/{tenantId}/members/*` |
| 登录日志 | 分页查询 | 可用 | `/user/login-logs/page` |
| 操作日志 | 分页查询 | 可用 | `/user/oper-logs/page` |
| 站内信 | 未读数、最近、分页、标记已读、删除、管理员发送 | 可用 | `/user/messages/*` |
| 通知渠道 | 渠道查询、保存、测试 | 可用 | `/user/message-notify/*` |
| 身份连接器 | LDAP 等类型、连接测试、同步 | 可用 | `/user/integrations/*` |
| Keycloak 事件回调 | 登录事件写入 | 可用 | `/internal/keycloak/events` |

## 4. 流水线 / CI

页面入口：`/pipeline/pipelines`、`/new`、`/:pipelineId/edit`、`/:pipelineId`、`/runs/:runId`、`/credentials`、`/task-kinds`

| 功能项 | 说明 | 状态 | 主要 API |
|--------|------|------|----------|
| 流水线 CRUD | 列表、创建、编辑、删除、详情 | 可用 | `/pipeline/pipelines/*` |
| 阶段与任务 | 多阶段、阶段内任务、排序、命令 | 可用 | 随流水线保存 |
| 运行 | 触发运行、默认参数、运行列表、详情、取消、审批 | 可用 | `/pipeline/pipelines/{id}/runs/*` |
| 任务市场 | 任务类型列表/详情、启停、版本、校验 | 可用 | `/pipeline/task-kinds/*` |
| 运行时参数 | 参数定义、API 选项预览、值来源 | 可用 | `/pipeline/job-params/*` |
| 凭证管理 | Git/镜像仓库/kubeconfig 凭证 | 可用 | `/pipeline/credentials/*` |
| 工具链 | 工具链模板查询 | 可用 | `/pipeline/toolchains` |
| 运行日志与终端 | 任务日志、Pod Web 终端 | 部分可用（后端可用，前端仅日志） | WebSocket `/ws/exec/**` |
| 运行产物 | 产物下载、SBOM 查询 | 部分可用（后端可用，前端无入口） | `/pipeline/runs/{runId}/artifacts/*` |
| 依赖分析 | 提交扫描、批量提交、任务查询 | 部分可用 | `/dependency-scan/*` |
| 依赖组件 | 组件分页、聚合查询 | 部分可用 | `/dependency/components/*` |
| Argo 工作流 | 工作流/模板 CRUD、提交、日志 | 后端可用、前端未接入 | `/argo/*` |

## 5. 容器平台

页面入口：`/container/clusters`、`/registries`、`/images`、`/helm/{releases,charts,upload}`

| 功能项 | 说明 | 状态 | 主要 API |
|--------|------|------|----------|
| 集群管理 | 纳管、分页、详情、编辑、删除、连接测试、统计、组件 | 可用 | `/container/clusters/*` |
| 命名空间与事件 | 列表、事件查询 | 可用 | `/container/clusters/{id}/namespaces`、`/events` |
| 节点 | 列表、详情 | 可用 | `/container/clusters/{id}/nodes/*` |
| Pod | 列表、详情、YAML、日志、删除 | 可用 | `/container/clusters/{id}/pods/*` |
| Pod 终端与文件 | Web Shell、上传、下载 | 可用 | `/ws/container/shell/**`、`/pods/{name}/upload|download` |
| Deployment / StatefulSet | 列表、详情、Pod、环境变量、卷、YAML、扩缩容、重启、删除 | 可用 | `/container/clusters/{id}/deployments/*`、`/statefulsets/*` |
| Service / ConfigMap / Secret / PVC / StorageClass | 列表与详情、YAML 编辑、删除 | 可用 | 对应 Controller |
| 监控 | 集群概览、节点/Pod CPU/内存/网络/磁盘、JVM 指标 | 可用 | `/container/clusters/{id}/monitor/*` |
| 镜像仓库 | 仓库 CRUD、连接测试、project/repo/artifact 浏览、删除、漏洞扫描 | 可用 | `/container/registries/*` |
| 镜像搜索 | 跨仓库搜索镜像 | 可用 | `/container/images/search` |
| Helm 仓库与 Chart | 仓库列表、chart 上传/列表/版本/values | 可用 | `/container/helm/*` |
| Helm Release | 列表、详情、dry-run、安装、升级、回滚、历史、values、manifest、资源、卸载 | 可用 | `/container/clusters/{id}/helm/releases/*` |
| 资源占位页 | DaemonSet/Job/CronJob/Ingress/HPA/CRD 等 16 类 | 占位 | 前端 `ResourceStubPage` |

## 6. API 测试平台

页面入口：`/api-test`、`/api-test/definitions`、`/collections`、`/environments`

| 功能项 | 说明 | 状态 | 主要 API |
|--------|------|------|----------|
| 接口分组 | 树、创建、编辑、删除 | 可用 | `/apitest/groups/*` |
| 接口定义 | 分页、详情、创建、编辑、删除、发布/废弃/回退草稿 | 可用 | `/apitest/definitions/*` |
| 参数与响应 | Path/Query/Header/Body 参数、响应定义 | 可用 | 随定义保存 |
| 脚本与断言 | 前置/后置脚本、断言、变量提取 | 可用 | 随定义保存 |
| 单接口调试 | 执行请求、查看响应/耗时/断言日志 | 可用 | `/apitest/debug/execute` |
| cURL 导入 | 解析 cURL 为请求 | 可用 | `/apitest/curl/parse` |
| 调试历史 | 分页、详情、按接口查询、删除、批量删除 | 可用 | `/apitest/debug-histories/*` |
| 环境管理 | 分页、列表、详情、创建、编辑、删除 | 可用 | `/apitest/environments/*` |
| 集合管理 | 集合 CRUD、文件夹树、集合项、排序、批量添加 | 可用 | `/apitest/collections/*` |
| 集合运行 | 触发运行、运行列表、运行详情、删除 | 可用 | `/apitest/collections/{id}/run` |

## 7. 文档生成（Docgen）

页面入口：`/docgen`

| 功能项 | 说明 | 状态 | 主要 API |
|--------|------|------|----------|
| 单文件生成 | Word/Excel/PPT/图片/Markdown/PDF | 可用 | `/api/docgen/generate` |
| 批量生成 | 多格式 × 大小 × 数量组合 | 可用 | `/api/docgen/batch-generate` |
| 生成到目录 | 结果保存到服务器目录 | 可用 | `/api/docgen/generate-to-dir` |

## 8. 文件解析（File Parser）

页面入口：`/file-parser`

| 功能项 | 说明 | 状态 | 主要 API |
|--------|------|------|----------|
| 文件上传解析 | DOCX/PPTX/XLSX/PDF/图片/TXT | 可用 | `/api/file-parser/upload` |
| OCR | 图片文字提取 | 可用 | 同上（按文件类型） |
| 压缩 | 质量、最大尺寸、最小压缩比 | 可用 | 同上 |
| 健康检查 | 服务状态 | 可用 | `/api/file-parser/health` |

## 9. 图片处理

页面入口：`/image`

| 功能项 | 说明 | 状态 | 主要 API |
|--------|------|------|----------|
| 创建会话 | 上传图片并建立处理会话 | 可用 | `/api/image/sessions` |
| 预览与元数据 | 预览、尺寸/格式等元数据 | 可用 | `/api/image/sessions/{id}/preview`、`/metadata` |
| 格式转换 | PNG/JPEG/WebP/BMP/GIF/SVG/TIFF 互转 | 可用 | `/api/image/sessions/{id}/convert` |
| 批量转换 | 多文件批量转换 | 可用 | `/api/image/batch-convert` |
| 转换任务查询 | 异步任务状态 | 可用 | `/api/image/sessions/{id}/jobs/{jobId}` |
| 结果下载与历史 | 结果获取、历史列表、删除会话 | 可用 | `/api/image/sessions/{id}/result`、`/history` |

## 10. 跨模块能力

| 能力 | 说明 | 状态 |
|------|------|------|
| 统一产品切换器 | 控制台顶部切换 8 个产品 | 可用 |
| 工作台 | 快捷入口、最近访问、我的事项 | 可用 |
| 租户上下文 | `X-Tenant-Id` 全链路传递 | 可用 |
| 统一响应与错误码 | `BaseResult` + `ResultCode` | 可用 |
| 请求追踪 | `X-Request-Id` + MDC + 响应 `requestId` | 可用 |
