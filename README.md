# HFWAS DevOps

可扩展的 DevOps 平台，当前包含 **项目管理（PM）**、**用户/租户中心**、**文档生成（Docgen）** 与 **文件解析（File Parser）** 四大子系统。后端为单体 Spring Boot 服务，前端为 Vue 3 SPA，本地开发使用 SQLite，零外部依赖即可启动。

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

### 用户中心

| 能力 | 说明 |
|------|------|
| 认证 | JWT 登录、登出、会话管理 |
| 多租户 | 租户切换、`X-Tenant-Id` 上下文 |
| 用户管理 | 账号 CRUD（平台 admin） |
| 站内信 | 收件箱、管理员群发 |
| 审计 | 登录日志、操作日志 |
| 集成 | LDAP 等身份连接器（admin 配置） |

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

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 21、Spring Boot 3.4、Spring Security、MyBatis-Plus |
| 前端 | Vue 3、TypeScript、Vite 6、Naive UI、Pinia、Vue Flow |
| 脚本 | Python 3（python-docx、openpyxl、python-pptx、matplotlib、fpdf） |
| 数据库 | SQLite（`./data/hfwas-devops.db`，启动时自动迁移） |
| 构建 | Maven 3.8+、npm |

---


## 项目结构

```
hfwas-devops/
├── backend/
│   ├── user-api/          # 用户模块公共 API / 注解 / 错误码
│   ├── user-core/         # 用户领域逻辑、认证、租户、站内信
│   ├── pm-core/           # PM 内核：事项、字段、查询引擎、工作流
│   ├── file-parser/       # 文件解析：图片 OCR、文件压缩、MIME 格式检测
│   ├── server/            # Spring Boot 启动入口 + REST Controllers
│   ├── scripts/           # Python 脚本（文档生成引擎 generate_doc.py）
│   ├── Dockerfile         # 后端容器镜像构建
│   └── .dockerignore      # 后端 Docker 构建忽略规则
├── frontend/
│   ├── docker/
│   │   └── nginx.conf     # 生产环境 Nginx 配置
│   ├── Dockerfile         # 前端容器镜像构建
│   └── .dockerignore      # 前端 Docker 构建忽略规则
├── charts/
│   ├── backend/            # Helm Chart（后端 Spring Boot 部署）
│   │   ├── Chart.yaml
│   │   ├── values.yaml
│   │   └── templates/
│   │       ├── _helpers.tpl
│   │       ├── configmap.yaml          # 应用环境变量
│   │       ├── deployment.yaml         # 部署（含反亲和 + 安全上下文）
│   │       ├── hpa.yaml                # 自动伸缩
│   │       ├── logback-configmap.yaml  # 可挂载的日志配置
│   │       ├── pvc.yaml                # 数据、文件、日志持久卷
│   │       ├── secret.yaml             # JWT 密钥
│   │       └── service.yaml
│   └── frontend/           # Helm Chart（前端 Vue 3 + Nginx 部署）
│       ├── Chart.yaml
│       ├── values.yaml
│       └── templates/
│           ├── _helpers.tpl
│           ├── configmap.yaml          # Nginx 配置（含 API 反向代理）
│           ├── deployment.yaml         # 部署（含反亲和 + 安全上下文）
│           ├── hpa.yaml
│           ├── ingress.yaml            # 入口配置
│           └── service.yaml
├── docker-compose.yml     # 本地 Docker Compose 编排
├── scripts/               # 本地开发启动脚本
└── docs/                  # 设计文档与 API 说明
```

**分层原则：** `pm-core` / `user-core` 承载领域逻辑，`server` 仅做 HTTP 适配；前端按模块划分路由与 API Client。

---

## 环境要求

| 工具 | 版本 |
|------|------|
| JDK | 21 |
| Maven | 3.8+ |
| Node.js | 18+（推荐 20+） |
| npm | 9+ |
| Python | 3.8+（文档生成需要） |

---

## 快速开始

### 一键启动（推荐）

```bash
# 首次或依赖变更时
./scripts/start-dev.sh --build --install

# 日常开发
./scripts/start-dev.sh
```

脚本会后台启动后端、前台启动前端；`Ctrl+C` 退出时会自动停止后端。

| 服务 | 地址 |
|------|------|
| 前端 | http://localhost:5173 |
| 后端 API | http://localhost:8089 |
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

### 默认账号

首次启动且数据库为空时，会自动创建：

| 用户名 | 密码 | 角色 |
|--------|------|------|
| `admin` | `admin123` | 平台管理员 |

登录页：http://localhost:5173/user/login

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
| `/api/docgen/*` | 文档生成 |

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

**文档生成模块**

| Controller | Base Path | 说明 |
|------------|-----------|------|
| DocgenController | `/api/docgen` | 文档生成：单文件下载、批量生成到目录 |

完整接口清单见 [docs/pm-api.md](docs/pm-api.md) §13。

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
| `file-parser.compression.enabled` | `true` | 是否启用文件压缩 |
| `file-parser.compression.quality` | `0.8` | 压缩质量（0-1） |
| `file-parser.compression.max-width` | `1920` | 压缩最大宽度（px） |
| `file-parser.compression.max-height` | `1920` | 压缩最大高度（px） |
| `file-parser.compression.min-file-size` | `10240` | 最小压缩文件大小（字节） |

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

### Python 文档生成脚本

文档生成功能依赖 Python 3 及以下库：

```bash
pip install python-docx openpyxl python-pptx matplotlib fpdf2 markdown
```

脚本位于 `backend/scripts/generate_doc.py`，支持 Word、Excel、PPT、图片、Markdown、PDF 六种格式，以及文件大小梯度控制。

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
docker build -t hfwas/devops-backend:latest -f backend/Dockerfile .

# 前端镜像（从项目根目录构建）
docker build -t hfwas/devops-frontend:latest -f frontend/Dockerfile .
```

### Docker Compose 启动

```bash
# 从项目根目录启动
docker compose up -d

# 查看日志
docker compose logs -f
```

### Helm 部署（Kubernetes）

```bash
# 安装后端
helm install devops-backend ./charts/backend \
  --set config.jwtSecret="your-secret-here" \
  --set replicaCount=2

# 安装前端（需先部署后端）
helm install devops-frontend ./charts/frontend \
  --set config.backendUrl="http://devops-backend:8089" \
  --set ingress.hosts[0].host="devops.example.com" \
  --set replicaCount=2

# 升级
helm upgrade devops-backend ./charts/backend
helm upgrade devops-frontend ./charts/frontend

# 卸载
helm uninstall devops-backend
helm uninstall devops-frontend
```

---

## 前端路由

| 路径 | 说明 |
|------|------|
| `/user/login` | 登录 |
| `/pm/projects` | 项目列表 |
| `/pm/projects/:id/items/:type` | 事项列表 |
| `/pm/projects/:id/board/:type` | 看板 |
| `/pm/projects/:id/settings/*` | 项目设置（模块、字段、类型、工作流） |
| `/user/*` | 用户中心（admin） |
| `/messages` | 站内信收件箱 |
| `/docgen` | 文档生成 |

---

## 文档索引

| 文档 | 说明 |
|------|------|
| [docs/pm-design.md](docs/pm-design.md) | PM 架构与领域设计 |
| [docs/pm-api.md](docs/pm-api.md) | PM REST API 完整说明 |
| [docs/error-code-design.md](docs/error-code-design.md) | 全局错误码规范 |
| [docs/pm-evolution-roadmap.md](docs/pm-evolution-roadmap.md) | PM 分步演进路线图 |
| [docs/pm-jira-comparison.md](docs/pm-jira-comparison.md) | 与 Jira 能力对比 |
| [docs/evolution/](docs/evolution/) | 各演进步骤详细设计 |

---

## CI

GitHub Actions（`.github/workflows/maven.yml`）在 `master` / `dev` 分支 push 时执行后端 Maven 构建（Java 21）。

---

## 开发说明

- 本项目为 **绿野（从 0 到 1）** 模式：改 schema / API 时直接改单一真相，不做存量兼容；本地库可随时删除重建。
- 后端模块边界：`pm-core` 不依赖 HTTP；新增 PM 能力优先在内核实现，Controller 只做 DTO 转换。
- 前端 PM 模块位于 `frontend/src/modules/pm/`，共享请求封装在 `frontend/src/shared/api/`。

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

查看日志：`.run/backend.log`

**前端 API 404**

确认后端已就绪（`curl http://localhost:8089/health/check` 返回 `UP`），且前端通过 `/api` 前缀访问。
