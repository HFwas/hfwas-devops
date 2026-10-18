# Agent instructions

> 日期：2026-10-17
> 版本：v0.20

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-09-12 | 初版：优先本地 docs、绿野不兼容、k3s 进容器操作、外网走 127.0.0.1:7890、Vue 空值合并约束 |
| v0.2 | 2026-09-12 | 约定：新建或改文档须带版本号与变更记录 |
| v0.3 | 2026-09-12 | 变更记录固定放在文首（标题与元信息之后、正文之前），对齐 pod-exec-terminal |
| v0.4 | 2026-09-12 | 新增：禁止自动 commit 和 push |
| v0.5 | 2026-09-13 | docs 索引补充应用镜像构建与 Helm 升级 |
| v0.6 | 2026-09-13 | 本地部署按 OS 区分：macOS 用 k3s 容器；Windows 用 Docker Desktop `docker-desktop`；日志优先 `logs/`，Windows 集群日志走 `kubectl -n devops logs` |
| v0.7 | 2026-09-15 | 新增：数据库变更规范 |
| v0.8 | 2026-09-16 | docs 索引补充云游/交付研究目录 |
| v0.9 | 2026-09-16 | docs 索引补充交付运维平台技术设计 |
| v0.10 | 2026-09-16 | 交付运维为独立局点产品：设计在 docs，代码不进本仓库 backend/frontend |
| v0.11 | 2026-10-01 | 前端强制遵守 AI 协作规范：新建走 React + Tailwind v4 + shadcn/ui；既有 `frontend/` 走 Vue + shadcn-vue |
| v0.12 | 2026-10-01 | `frontend/` 统一为 React 轨，取消 Vue 例外 |
| v0.13 | 2026-10-16 | 新增「解决问题」约束：先复现 → 再定位 → 后修复 → 再验证，验证通过才算解决 |
| v0.14 | 2026-10-17 | 新增「新增功能」约束：先写功能设计、测试用例、代码变更三份文档再实施；实施后由独立 agent 比对文档差异并验证测试 |
| v0.15 | 2026-10-17 | 修正：每个功能三份文档统一放在 `docs/superpowers/features/YYYY-MM-DD-<feature>/` 独立目录下，不再分散到不同顶层目录 |
| v0.16 | 2026-10-17 | 修正：功能文档目录前缀日期格式改为 `YYYYMMDD` |
| v0.17 | 2026-10-17 | 新增「接口变更」约束：新增 / 修改接口必须同步维护 Markdown 与 Postman JSON 文档，覆盖正常与失败响应，随版本发布 |
| v0.18 | 2026-10-17 | docs 索引新增产品文档体系入口 `docs/product/README.md`（总览 / 功能 / 数据库 / 接口 / 测试 / 原型 / 运维） |
| v0.19 | 2026-10-18 | 新增「数据库文档」规范：四节结构、10 列数据字典、ER/枚举/JSON/迁移要求 |
| v0.20 | 2026-10-18 | 提交门禁：CI 与 PR 必须执行测试，禁止 `-Dmaven.test.skip=true` / `-DskipTests` 跳过主流程测试 |

---

HFWAS DevOps：从 0 到 1 的 DevOps 平台。后端 Spring Boot 3 / Java 21。前端 `frontend/` 为 React 19 + Tailwind v4 + shadcn/ui，必须遵守「前端」一节与 `docs/frontend/ai-collaboration-frontend.md`。Cursor 额外规则在 `.cursor/rules/`。

## 优先使用本地文档

回答产品、架构、运维、安装、设计类问题前，**先查 `docs/`**，再看代码或通用知识。

1. 按下面目录定位 1～3 篇相关文档并读完再回答。
2. 用 Grep / Glob 在 `docs/` 搜模块名、组件名、操作名。
3. 引用具体路径。本地文档与通用知识冲突时，**以 `docs/` 为准**。
4. 没有对应文档时，写明「本地 docs 未覆盖」，再根据代码或通用知识回答。

不要把整份 `docs/` 读进上下文。

| 主题 | 目录 |
|------|------|
| 产品总览 / 功能 / 数据库 / 接口 / 测试 / 原型 / 运维 | `docs/product/README.md` |
| 数据库文档规范 | `docs/product/03-database/database-documentation-standard.md` |
| Kong / 代理 / 本机安装 | `docs/devops/` |
| 应用镜像 / Helm 升级 | `docs/devops/app-helm-deploy.md` |
| 流水线 / Tekton / CI | `docs/pipeline/` |
| 容器 / Harbor / 监控 | `docs/container-platform/` |
| PM 设计与 API | `docs/pm/` |
| PM 工作流演进 | `docs/evolution/` |
| 后端架构 / API / 库表 | `docs/backend/` |
| 接口文档（Markdown + Postman） | 既有模块见「接口变更」一节；新模块 `docs/api/<module>/` |
| 前端 | `docs/frontend/`；强制规范 `docs/frontend/ai-collaboration-frontend.md` |
| 文件解析 / OCR | `docs/file-parser/` |
| 文档生成 | `docs/docgen/` |
| 图片处理 | `docs/image/` |
| API 测试平台 | `docs/api-test/` |
| 测试 / 进度 / 安全 | `docs/general/` |
| 设计稿与实现计划 | `docs/superpowers/` |
| 交付运维平台 | `docs/superpowers/specs/2026-09-16-delivery-ops-platform-design.md`（**独立产品**，禁止在本仓库 `backend/` `frontend/` 落地） |
| 云游 / 交付研究 | `docs/research/` |

## 解决问题：先复现 → 再定位 → 后修复 → 再验证

不要凭猜测改代码。任何问题（bug、报错、行为不符预期、性能异常）都按这个顺序走：

1. **先复现。** 在当前环境下按用户描述的操作路径实际跑一遍，拿到真实现场：日志、堆栈、HTTP 请求/响应、界面表现。**没复现前不要改代码**，也不要给「可能是……」的结论。
2. **再定位。** 基于复现出来的现象查根因：读日志、加断点或临时打印、对比正常/异常路径，直到能说清「哪一行、在什么条件下、为什么」，而不是只说一个猜测的位置。
3. **后修复。** 针对定位到的根因做最小改动，并说明该改动为什么能消除这个根因。
4. **再验证。** 用第 1 步同样的步骤重新跑一遍，确认现象消失；再跑相关测试，确认没有引入回归。**验证通过才能说问题已解决。**
5. **复现不了时**，如实说明「在当前环境未能复现」，列出已尝试的步骤、观察到的现象，以及需要补充的环境或数据，**不要**用推测性的改动冒充已解决的问题。

复现要基于用户实际所在的环境（macOS / Windows 集群差异见下面「本地部署」一节），不要拿另一套环境的现象直接下结论。

## 新增功能：文档先行，独立 agent 校验

适用于新增功能、新模块，以及跨 3 个以上文件的改动。单文件小修、错别字、纯配置调整可以跳过，但要在回复里说明为什么跳过。

**每个功能新建一个独立目录，三份文档全部放在该目录内，不要拆到不同顶层目录**：

```text
docs/superpowers/features/YYYYMMDD-<feature>/
├── design.md       # 功能设计文档
├── test-cases.md   # 测试用例文档（长期维护）
└── changes.md      # 代码变更文档
```

目录名前缀为 `YYYYMMDD`，其余部分用英文短横线（如 `20261017-dependabot-integration`）。历史 `docs/superpowers/specs/`、`docs/superpowers/plans/` 下的文档保持不动，新功能不再使用这些平铺目录。

顺序固定，前一步没完成不要进入下一步：

1. **先写功能设计文档**：`design.md`
   - 背景与目标、范围 / 非目标、接口与数据结构、关键流程、异常与边界、兼容性与迁移、验收标准。
2. **再写测试用例文档**：`test-cases.md`
   - 每条用例带编号，写清前置条件、步骤、输入、预期结果。
   - 必须覆盖正常路径、边界值、异常 / 失败路径；标明对应的自动化测试位置（`文件::测试名`），无法自动化的写手工验证步骤。
   - 这是长期维护的文档：需求变更时**先改测试用例文档，再改代码**。
3. **再写代码变更文档**：`changes.md`
   - 开工前先列骨架：计划新增 / 修改的文件，以及每处改动对应设计文档的哪一节。
   - 实施过程中补全实际改动，并记录所有**计划外偏差**及原因。
4. **然后才开始写代码**：按测试用例实现，同步补自动化测试；测试用例文档里标注的位置必须真实存在。
5. **实施完成后做两项校验**：
   - **用 Agent 工具新开一个独立 agent**（全新上下文，不继承当前实现过程），把三份文档 + 当前分支 diff 一起交给它。
   - 让它检查两件事：
     a. **文档一致性**：功能设计文档 ↔ 代码变更文档 ↔ 实际 diff 是否一致，列出漏做、多做、行为与设计不符的地方；
     b. **测试验证**：按测试用例文档逐条执行，确认全部通过。
   - 校验结论写回代码变更文档的「校验结果」一节：通过 / 失败、差异清单、失败用例与原因。
   - 有差异或有用例不通过 → 修复后重新校验；**未重新校验不得声称功能完成**。

三份文档同样遵守下面「文档」一节的版本号与变更记录要求。

## 接口变更：必须同步维护接口文档

新增或修改任何 HTTP 接口（路径、方法、鉴权、请求参数、header、请求体、返回结构、错误码、行为语义）时，**同一分支内**必须同步更新接口文档。代码改完、文档没改，视为未完成。

### 文档形态与位置

每个模块维护两份内容等价的文档，放在**同一目录、同一文件名**（仅扩展名不同）：

| 形态 | 文件名 | 用途 |
| ---- | ------ | ---- |
| Markdown | `<module>-api.md` | 人读，随版本发布 |
| Postman Collection | `<module>-api.postman_collection.json` | Postman 直接 Import，调试与回归 |

- 已有模块沿用现有位置与文件名，不要另起一套：`docs/backend/api_spec.md`、`docs/pipeline/pipeline-core-api.md`、`docs/pm/pm-api.md`。
- 新模块统一放在 `docs/api/<module>/` 下，文件名 `<module>-api.md` 与 `<module>-api.postman_collection.json`。
- Postman 文件必须是 v2.1 collection 格式，可按模块合并，按功能用 folder 分组。

### 每个接口必须覆盖

1. **请求定义**：方法、路径、鉴权方式、路径参数、查询参数、请求 header（如 `Authorization`、`Content-Type`、`X-Request-Id`）、请求体字段（类型、是否必填、约束、示例）。
2. **正常响应**：HTTP 状态码 + 完整响应体示例（`BaseResult<T>` 的 `code` / `msg` / `data` / `requestId`）。
3. **失败响应**：至少覆盖
   - 参数校验失败（HTTP 4xx，含具体字段错误信息）；
   - 业务失败（HTTP 200 且 `code != 0`，或实际约定的错误状态码）；
   - 鉴权 / 权限失败（401 / 403）。
   每种失败都要给出请求输入与完整响应体示例，并写明错误码含义（对齐 `docs/backend/error-code-design.md`）。
4. **变更记录**：Markdown 文档按下面「文档」一节维护版本号与变更记录，写清本次新增 / 修改了哪些字段、错误码或行为。

### Postman 要求

- 每个接口至少保存 **1 个正常响应 example** 和 **1 个失败响应 example**（Postman 的 saved examples），便于后续回归与发布演示。
- 修改接口时同步更新 collection 内的 url、params、headers、body 与 examples，不要只改代码留着旧 collection。
- 集合变量（`baseUrl`、token 等）用 `{{variable}}`，不要写死环境地址或密钥。

### 发布

接口文档（Markdown + Postman JSON）随版本一起交付，是发布产物的组成部分。发版前确认：本次版本涉及的接口在文档中均有正常与失败响应示例；缺少示例的接口视为文档不完整。

## 绿野项目：不做存量兼容

本地/开发库可随时重建，**不要**为已有数据库内容做向后兼容。

- 改表结构、列名、JSON 形状、API 字段时：**直接改** schema / entity / VO / 前端类型。
- **不要**写双写旧列、legacy 字段合成、`if (oldFormat)`、仅为迁旧数据的 runtime migration（除非用户明确要求）。
- 需要清库或重跑 schema / 启动脚本时，优先重建，不要打补丁兼容脏数据。
- 用户明确要求兼容某版本或线上迁移时，写一次性脚本，并在文档标明可删除期限。

## 本地部署

按操作系统走不同集群，**不要混用**（不要把 macOS 的 k3s 容器操作套到 Windows 上）。

### macOS：Docker 里的 k3s

本地集群跑在 Docker 里的 k3s。查集群、装 Helm、看 Pod、导入镜像时：

1. **先定位容器**，不要写死名字：
   ```bash
   docker ps | grep k3s
   ```
   本仓库 compose 一般是 `devops-k3s`，以 `docker ps` 实际输出为准。
2. **进入该容器再操作**（`docker exec` / `docker exec -it <容器> sh`）。
3. **禁止**使用宿主机的 `kubectl`、`helm`、`~/.kube/config`、`KUBECONFIG`，以及仓库里导出到宿主机的 kubeconfig（如 `data/pipeline/kubeconfig.yaml`）去打集群。
4. 容器内用 `kubectl` / `helm` / `ctr`。rancher/k3s 里 `/bin/kubectl` 是 k3s 的 symlink，**不要**写 `k3s kubectl`（会变成 `kubectl kubectl`）。

```bash
# 正确
docker exec <k3s容器> kubectl get ns
docker exec <k3s容器> kubectl -n harbor get pods
docker exec -i <k3s容器> ctr -n k8s.io images import -

# 错误：本机 kubectl / 本机 kubeconfig
kubectl get pods
kubectl --kubeconfig ~/.kube/config get pods
```

### Windows：Docker Desktop（`docker-desktop`）

本地集群是 Docker Desktop 自带的 Kubernetes，kube-context 为 **`docker-desktop`**。查集群、装 Helm、看 Pod 时用**宿主机** `kubectl` / `helm`，不要按 macOS 去 `docker exec` k3s 容器，也不要 `ctr import`（Desktop 与集群共用 Docker 镜像）。

```bash
kubectl config current-context   # 应为 docker-desktop
kubectl get ns
kubectl -n devops get pods
```

### 查看日志

1. **优先读仓库根目录 `logs/`**（docker-compose / 本机进程把文件日志写在这里），例如 `logs/backend/devops.log`、`logs/kong/`、`logs/frontend/`、`logs/keycloak/`。
2. 集群 Pod 日志（应用在 `devops` 命名空间）：
   - **macOS（k3s）**：本地 `logs/` 没有对应内容时，进 k3s 容器再 `kubectl -n devops logs <pod>`。
   - **Windows（docker-desktop）**：宿主机直接查：

```bash
kubectl -n devops get pods
kubectl -n devops logs <pod>
kubectl -n devops logs -f <pod>
kubectl -n devops logs deploy/devops-backend
```

## 外网与本地代理

访问外网（拉镜像、curl、npm、git、helm repo、安装脚本等）若超时、TLS 失败或被墙，**优先走本机代理** `127.0.0.1:7890`（Clash 等 HTTP 代理），不要先改镜像源或放弃。

```bash
export HTTP_PROXY=http://127.0.0.1:7890
export HTTPS_PROXY=http://127.0.0.1:7890
export ALL_PROXY=http://127.0.0.1:7890
export http_proxy=http://127.0.0.1:7890
export https_proxy=http://127.0.0.1:7890

curl -fsSL -x http://127.0.0.1:7890 https://example.com
```

Docker 容器访问宿主机代理用 `host.docker.internal:7890`，不要把 `127.0.0.1:7890` 写进容器环境（那是容器自己）。本机访问 Harbor / k3s NodePort 等内网地址时不要带代理。

## 文档

新建或实质性修改 `docs/`、`AGENTS.md`、`README.md` 等 Markdown 时，**必须**带版本号和变更记录。版式对齐 `docs/pipeline/pod-exec-terminal-design.md`：**变更记录放在文档最前边**（标题与元信息之后、正文之前），不要放到文末。

```markdown
# 文档标题

> 日期：YYYY-MM-DD
> 版本：vX.Y

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | YYYY-MM-DD | 初版：…… |
| v0.2 | YYYY-MM-DD | …… |

---

## 1. 正文从这里开始
```

- 新版本追加在变更记录表**底部**（旧上新下）。
- 新文档从 `v0.1` 起；实质性修改递增次版本（`v0.2`、`v0.3`…），重大重写递增主版本（`v1.0`）。
- 改文档时同步更新文首日期/版本，并追加一行变更说明（一句话，写清改了什么）。
- 旧文档没有版本时：在文首补上元信息与变更记录，当前内容记为 `v0.1`，本次改动记为 `v0.2`。
- 旧文档把修订记录放在文末时：改到文首，不要两边各留一份。
- 纯错别字、格式微调可不升版本，但不要删已有变更记录。
- 仅 `@` 引用其它文件的薄封装（如 `CLAUDE.md`）不必单独做版本表。

## Git 操作

禁止自动 commit 和 push。所有 commit 和 push 须在用户明确指令下执行。

### 提交前必须跑测试

- 后端 CI（`maven.yml`、`pr-backend.yml`）使用 `mvn -B -f backend/pom.xml clean verify`，**禁止**再用 `-Dmaven.test.skip=true` 或 `-DskipTests` 跳过主流程测试。
- 前端 PR 继续执行 `npm run build` + `npm test`。
- 本地提交前至少运行受影响模块的测试；全量后端测试用 `mvn -B -f backend/pom.xml test`。
- 测试失败必须先修复再提交；已知失败用例不得通过跳过测试来规避。
- 新增/修改测试导致既有失败时，按「解决问题」流程复现、定位、修复、验证。

## 前端

写任何前端代码前，先读 `docs/frontend/ai-collaboration-frontend.md` 并遵守本节。与通用知识冲突时，以该文档为准。

### 新建前端（默认，强制）

必须使用 **React 19 + TypeScript + Vite + Tailwind CSS v4 + shadcn/ui**。

- 用 shadcn CLI 初始化，组件源码放在 `src/components/ui`，用 `npx shadcn@latest add` 添加。禁止平行手写一套基础组件。
- 保留并先读 `components.json`。样式只用 Tailwind 工具类和 CSS 变量（`@theme` / OKLCH）。颜色用语义 token（如 `bg-background`），禁止在业务代码里写死十六进制颜色。
- 服务端状态用 TanStack Query，校验用 Zod，图标用 Lucide。
- 后端已是独立 API 时用 Vite SPA。用户未明确要求 SSR / RSC 时，禁止引入 Next.js。
- 需要对话、流式输出、工具调用界面时，用 Vercel AI SDK + AI Elements，或 assistant-ui。

### 本仓库 `frontend/`（强制）

与新建前端同一套栈。禁止在 `frontend/` 中引入 Vue、Pinia、Vue Router、naive-ui、shadcn-vue。

路由用 React Router。服务端状态用 TanStack Query。跨页面客户端状态用 Zustand。

### 共同禁止

- 禁止 Vue，以及 Element Plus、Ant Design、naive-ui、CSS-in-JS，或 Tailwind 以外的第二套原子化 CSS。
- 禁止把基础组件封进不可读的运行时黑盒。改视觉时改仓库内组件源码和 CSS 变量。
- 交付运维平台界面禁止落到本仓库 `frontend/`。

## 数据库变更

项目使用 SQLite，schema 初始化（`devops.schema.init`）分两种模式：

- **`embedded`**（默认）：`SqliteSchemaInitializer` / `UserSchemaMigration` 进程内执行建表
- **`external`**：Helm initContainer 用 `deploy/charts/backend/files/db/migrate.sh` 执行 SQL

涉及 DDL / DML 变更时：

1. **新增 SQL 文件，不修改已有文件。** 所有迁移脚本放在 `deploy/charts/backend/files/db/` 下，以 `.sql` 结尾。`migrate.sh` 按文件名顺序执行，并对已执行文件做 checksum 校验 —— 修改已有文件会导致 checksum 变化被检出（幂等 alter 除外），但应在新增文件中做增量变更。

2. **文件命名：`<两位递增数字>-<简短描述>.sql`。**
   - 继承当前最大编号（目前 `06-container-schema.sql`，下一个就是 `07-`）。
   - 示例：`07-add-xx-column.sql`、`07-create-yy-table.sql`。
   - 编码（同一级的不同变更）使用相同数字，下一级新数字。

3. **SQL 应幂等。** 使用 `CREATE TABLE IF NOT EXISTS`、`ALTER TABLE ADD COLUMN`（`migrate.sh` 会静默跳过重复列错误），确保重复执行安全。

4. **同步更新 Java 侧建表逻辑（`embedded` 模式）。**
   - 实体变更 → 更新 `SqliteSchemaInitializer` 中的 schema DDL 和 `schemaVersion`。
   - 确保两种模式建表结果一致。

5. **当前仅有 SQLite。** 引入新数据库（如 MySQL、PostgreSQL）时再建立对应子目录，命名规范后续补充。

## 数据库文档

所有数据库文档（`docs/product/03-database/` 及模块数据库设计文档）必须遵循 `docs/product/03-database/database-documentation-standard.md`：

1. 每个模块固定四节：**ER 图 → 表清单 → 数据字典 → 迁移与 DDL**。
2. 每张表先写表级元数据（用途、写入方、租户隔离、逻辑删除、量级、保留策略、敏感数据），再写 **10 列字段字典**：序号、字段名、中文名、主/外键、索引、类型、长度/精度、空否、默认值、说明。
3. 字段说明不得为空；枚举、JSON 结构、敏感字段必须单独成节。
4. ER 图优先使用 Mermaid `erDiagram`；可选 DBML。
5. 新增/修改表、字段、索引、枚举时，必须在同一变更内更新对应数据库文档与 `docs/product/03-database/data-dictionary.md`，否则视为未完成。
6. DDL 以 schema SQL 为准，文档列出文件、迁移编号与 embedded/external 差异，不复制大段建表语句。
