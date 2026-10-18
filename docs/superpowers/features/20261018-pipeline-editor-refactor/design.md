# 流水线编辑器重构与全局弹框样式 —— 功能设计

> 日期：2026-10-18
> 版本：v0.5
> 状态：待原型冻结
> 所属模块：pipeline（前端 + 后端 + 数据库）
> 关联：[`docs/pipeline/pipeline-ux-audit.md`](../../../pipeline/pipeline-ux-audit.md)、[`kite-frontend-style-guide.md`](../../../frontend/kite-frontend-style-guide.md) v0.8

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-18 | 初版：编辑器头部与克隆配置重构、阶段自动命名、移除缩放、全局弹框 Kite 化；仓库配置真正移入克隆任务参数并配套迁移 |
| v0.2 | 2026-10-18 | 新增：编辑页任务名称与类型全部只读（含本次新加任务）；新建页仍可编辑 |
| v0.3 | 2026-10-18 | 新增：代码克隆任务隐藏“高级设置”（技术栈/运行时/工具版本） |
| v0.4 | 2026-10-18 | 修正：任务名称/类型在新建页与编辑页均只读，不再区分 |
| v0.5 | 2026-10-18 | 按独立校验修正：17 迁移改为只补种子（零 UPDATE），旧数据回填移到启动组件 `CloneConfigBackfill`；统一 Commit 8 位；修正阶段命名示例与原型版本引用 |

---

## 1. 背景与目标

本轮来自 4 条明确诉求：

1. **新建/编辑流水线页面重构**：名称移到标题旁；仓库地址、分支、凭证从页头移除，改为代码克隆任务的配置；首次克隆完成后，把实际仓库、分支、commit 回显到编辑页顶部与流水线详情。
2. **阶段名称自动填充**：新增阶段自动取名，用户不必先补名称；仍允许手动修改。
3. **弹框样式统一**：参考 [kitehq.dev](https://kitehq.dev/) 的卡片式浮层，修正 Dialog 与 AlertDialog 之间圆角、底色、遮罩、分隔线不一致的问题。
4. **移除 DAG 缩放**：删除 75/100/125/150 缩放控件与相关 transform。
5. **任务身份只读**：新建页与编辑页一致，所有任务的名称与类型禁止修改；类型只在“添加任务”时从任务市场选定，名称按类型自动生成。

目标：

- 流水线定义里**仓库、分支、凭证成为代码克隆任务的参数**，不再是流水线级输入。
- 保存时由克隆任务参数派生流水线级 `repo_url / git_ref / credential_id`（保留为派生列，供列表、详情与执行器读取），执行链路不变。
- 编辑页在最近一次运行产生 commit 后显示只读“最近克隆”摘要；流水线详情增加 Commit 指标。
- 全站 Dialog / AlertDialog 使用同一套 Kite 浮层样式。
- 画布固定 100%，保留横向与纵向滚动。

## 2. 范围与非目标

### 2.1 范围

| 层 | 内容 |
|----|------|
| 前端 | 编辑器头部、克隆任务配置、阶段命名、移除缩放、最近克隆回显、详情 Commit 指标、Dialog/AlertDialog 样式 |
| 后端 | `PipelineSaveDTO` 移除仓库字段；保存时从 CLONE 参数派生；`DependencyScanService` 适配；CLONE 参数种子 |
| 数据库 | 新增迁移 `17-clone-job-config.sql`：只补参数种子（零 UPDATE）；旧绑定回填由启动组件 `CloneConfigBackfill` 在所有 schema 模式执行 |
| 文档/原型 | 功能三份文档；流水线原型 v0.8；Kite 弹框样式说明 |

### 2.2 非目标

- 不改 Tekton 编译与执行器：派生列仍供其读取。
- 不做运行时仓库/分支覆盖的新交互（现有 `GIT_REF` 运行时参数保留）。
- 不改运行详情排版，仅沿用现有 commit 展示。
- 不重构任务市场参数编辑器。
- 不引入新的 UI 组件库或主题。

## 3. 关键决策（已与用户确认）

| 决策 | 结论 |
|------|------|
| 仓库/分支/凭证归属 | **真正移入克隆任务参数**；流水线表字段保留为保存时派生的缓存 |
| 回显位置 | 编辑页顶部 + 流水线详情 |
| 阶段命名 | 自动预填，**仍可编辑** |
| 弹框范围 | **全站** Dialog / AlertDialog 统一 Kite 风格 |

## 4. 数据模型与接口

### 4.1 克隆任务参数

`pipeline_task_kind_param` 增加两条 CLONE 参数（`GIT_REF` 已存在）：

| param_key | label | type | 默认 | 必填 | 说明 |
|-----------|-------|------|------|:----:|------|
| `REPO_URL` | 仓库地址 | input | 空 | 是 | http/https Git 地址 |
| `GIT_REF` | 代码分支 | input | main | 是 | 已有参数 |
| `CREDENTIAL_ID` | 凭证 | credential | 空 | 否 | 凭证 ID；前端用 AsyncSelect |

- `REPO_URL` / `CREDENTIAL_ID` 新增种子行；不改已有 `GIT_REF` 行。
- 前端在克隆任务检查器里把这三个参数渲染为独立“仓库配置”区块；其余参数仍走通用参数绑定。
- 克隆任务的 `GIT_USERNAME / GIT_PASSWORD / GIT_EMBEDDED_AUTH / GIT_HTTP_PROXY` 保留，与凭证二选一。

### 4.2 保存接口

`PipelineSaveDTO` 移除 `repoUrl / gitRef / credentialId`，只保留 `id / name / stages`。

保存时：

1. 在 `stages` 中找唯一 `CLONE` 任务（图校验已保证至多一个）。
2. 从该任务的 `paramBindings` 读取固定值：
   - `REPO_URL`：必填，`GitRemote.parse` 校验；
   - `GIT_REF`：缺省 `main`；
   - `CREDENTIAL_ID`：空 → `null`；非数字 → `400`。
3. 写入 `pipeline.repo_url / git_ref / credential_id`（派生缓存）。
4. 无 CLONE 任务时：`repo_url=""`、`git_ref="main"`、`credential_id=null`。

`PipelineVO` 仍输出这三个字段，列表、详情、运行创建、Tekton 编译无需改动。

### 4.3 现有调用方

- `DependencyScanService` 创建临时流水线：改为把 `REPO_URL / GIT_REF` 写入克隆任务绑定，不再调用 DTO setter。
- `PipelineRunService`：继续读取派生列与运行时 `GIT_REF`，行为不变。
- 其它按 id 读取 `PipelineEntity` 的服务（依赖扫描、组件统计）不变。

### 4.4 数据库迁移

新增 `deploy/charts/backend/files/db/17-clone-job-config.sql`：

1. `INSERT OR IGNORE` 两条 CLONE 参数种子。
2. **不含 UPDATE**：`migrate.py` 只允许 `16-upgrade-task-template-seeds.sql` 使用 UPDATE，迁移脚本保持零 UPDATE 基线。

旧数据回填由应用启动组件 `CloneConfigBackfill`（`@Order(2)`）在所有 schema 模式下执行：

- 若 `param_bindings` 缺少 `REPO_URL`，用 `pipeline.repo_url / git_ref / credential_id` 写入
  `{"REPO_URL":{"mode":"fixed","value":...},"GIT_REF":{...},"CREDENTIAL_ID":{...}}`；
- 使用 SQLite JSON1 的 `json_set / json_object`，语句幂等（已有 `REPO_URL` 不重复写）。

embedded 模式：

- `backend/server/src/main/resources/db/pipeline-schema.sql` 同步两条参数种子（供全新库使用）；
- `CloneConfigBackfill` 在 schema 初始化后统一执行回填，与 external 模式一致。

## 5. 前端设计

### 5.1 编辑器头部

```text
┌──────────────────────────────────────────────────────────────────┐
│ 新建流水线  [ 后端发布            ]   最近克隆 owner/repo · main · 9f2c1ab │
│                                             取消  保存  保存并运行 │
└──────────────────────────────────────────────────────────────────┘
```

- 标题与名称输入同一行：`新建流水线` / `编辑流水线` 后紧跟名称输入框（`aria-label="名称"`）。
- 移除原“基本信息”整行（名称、仓库、分支、凭证）。
- “最近克隆”只在最近一次运行有 `commitSha` 时显示：
  - 仓库取克隆任务 `REPO_URL`（短名）；
  - 分支取运行 `gitRef`（缺省派生列）；
  - commit 取运行 `commitSha` 的短 SHA（沿用现有 `formatCommit`，8 位）；
  - 样式 `font-mono text-xs text-muted-foreground`，整段可复制。

### 5.2 克隆任务配置

选中 `CLONE` 任务时，检查器顶部渲染“仓库配置”：

| 字段 | 控件 | 说明 |
|------|------|------|
| 仓库地址 | Input，`aria-label="仓库地址"` | 必填；校验失败 `aria-invalid` |
| 分支 | Input，`aria-label="代码分支"` | 默认 `main` |
| 凭证 | AsyncSelect，`aria-label="凭证"` | 可清空；与 GIT_USERNAME 等参数二选一 |

- 通用“参数绑定”列表隐藏 `REPO_URL / GIT_REF / CREDENTIAL_ID`，避免重复编辑。
- 其它 CLONE 参数（Git 用户名/密码/Token/代理）保持原样。
- **CLONE 任务不显示“高级设置”**（技术栈 / 运行时 / 工具版本）：克隆步骤使用固定 `CLONE_IMAGE`，这三个字段只影响构建、测试、镜像等任务。
- 非 CLONE 任务不显示仓库配置，仍保留高级设置。
- 已保存克隆任务里历史遗留的 stack/runtime/toolVersion 字段不读取、不展示；保存时保持原值或为空，不影响执行。

### 5.3 阶段命名

- `appendEmptyStage` 预填 `阶段 N`（N = 当前阶段数 + 1）。
- 向空阶段加入第一个任务时，若阶段名仍是自动名（`阶段 N` 或历史 `未命名`），按任务类型自动改名（沿用现有 `defaultStageName`，如“拉取”“构建”）。
- 名称输入框保留，可手动改；不再出现必须用户先补名称的空阶段。

### 5.4 任务名称与类型只读（新建与编辑一致）

- 新建页与编辑页的检查器：
  - “任务名称”和“任务类型”渲染为只读（disabled 控件），并显示提示“任务名称与类型由任务市场决定，不在此修改”。
  - 任务类型只能通过“添加任务”面板从任务市场选择；任务名称沿用任务类型 label 自动生成。
  - 本次新加、尚未保存的任务同样只读。
- 这是前端交互约束；后端 API 仍可接受名称/类型更新，避免在缺少版本模型时误伤历史数据修订。

### 5.5 移除缩放

- 删除 `zoom` 状态、`ScalableTrack`、画布右上角缩放下拉。
- 画布固定 100%，保留 `overflow-auto` 滚动与拖拽排序。
- 不再生成 `transform: scale()`，长画布滚动行为更可预测。

### 5.6 最近克隆回显（详情页）

流水线详情指标卡从 5 张扩为 6 张：仓库、分支、**Commit**、阶段、最近运行、凭证。

- Commit 取运行历史第一条（最近一次）的 `commitSha` 短 SHA（沿用 `formatCommit`，8 位）；无运行时显示 `—`。
- 仓库/分支继续读派生列；保存后与克隆任务配置一致。

### 5.7 全局弹框 Kite 化

统一 `components/ui/dialog.tsx` 与 `alert-dialog.tsx`：

| 属性 | Dialog | AlertDialog（统一后） |
|------|--------|------------------------|
| 圆角 | `rounded-xl` | `rounded-xl`（原 `rounded-lg`） |
| 底色 | `bg-card` | `bg-card`（原 `bg-background`） |
| 边框/阴影 | `border shadow-lg` | `border shadow-lg` |
| 遮罩 | `bg-black/40` | `bg-black/40`（原 `/50`） |
| 标题区 | 底部 `border-b pb-3` | 底部 `border-b pb-3` |
| 底部操作区 | 顶部 `border-t pt-3` | 顶部 `border-t pt-3` |

- 只改共享组件，业务页不写私有弹框皮肤。
- 运行参数、删除确认、放弃修改、凭证新建等现有弹框自动获得统一样式。

## 6. 异常与边界

| 场景 | 期望 |
|------|------|
| 克隆任务缺少仓库地址 | 保存前前端提示“仓库地址不能为空”；后端同样 400 |
| 仓库地址非 http/https | 前后端一致拒绝 |
| `CREDENTIAL_ID` 非数字 | 后端 400 |
| 无克隆任务的流水线 | 允许保存；派生列为空/默认 |
| 旧数据未迁移 | 迁移回填；前端只读新参数，不读取顶部字段 |
| 迁移重复执行 | `INSERT OR IGNORE` + “缺 REPO_URL 才回填”，幂等 |
| 最近运行没有 commit | 编辑页不显示“最近克隆”；详情 Commit 显示 `—` |
| 用户新建阶段后不填名称 | 预填“阶段 N”，保存通过 |
| 移除克隆任务 | 该流水线不再有仓库配置；详情仓库/分支显示 `—` |
| 弹框在暗色模式 | 继续使用语义 token，不写死颜色 |

## 7. 兼容性与迁移

- 数据库：新增 `17-*` 迁移；不改已有 SQL 文件。
- 接口：`PipelineSaveDTO` 移除三个入参属于破坏性变更；本仓库前端同步修改，按项目“不做存量兼容”约定不保留旧入参。
- `PipelineVO` 与执行器读取的派生列保持不变，运行链路无感。
- 本地/开发库可重建；迁移仍提供回填以保留现有定义。
- 无 feature flag。

## 8. 原型与验收

### 8.1 原型

更新模块原型 `docs/pipeline/prototype` 到 v0.8：

- 编辑器：标题旁名称、无基本信息行、克隆检查器仓库配置、无缩放控件、最近克隆摘要、编辑页任务名称/类型只读。
- 详情：增加 Commit 指标卡。
- 弹框：统一圆角/底色/遮罩/分隔线，生成新画板。
- 评审冻结后再写页面代码。

### 8.2 验收标准

| AC | 标准 | 对应用例 |
|----|------|----------|
| AC-01 | 新建/编辑页名称在标题后，页头无仓库/分支/凭证 | TC-ED-01～03 |
| AC-02 | 克隆任务检查器可编辑仓库/分支/凭证，不显示高级设置；其它任务不显示仓库配置、保留高级设置 | TC-ED-04～06、TC-ED-20 |
| AC-03 | 保存后流水线派生列与克隆参数一致；缺仓库/非法 URL 被拒绝 | TC-ED-07～10、TC-BE-01～05 |
| AC-04 | 最近克隆完成后编辑页顶部与详情 Commit 卡回显 | TC-ED-11～12 |
| AC-05 | 新增阶段自动命名“阶段 N”，加首个任务后按类型改名，仍可手动编辑 | TC-ED-13～15 |
| AC-06 | 画布无缩放控件，固定 100% 可滚动 | TC-ED-16 |
| AC-07 | Dialog / AlertDialog 圆角、底色、遮罩、分隔线一致 | TC-UI-01～02 |
| AC-08 | 迁移 17 幂等，旧克隆任务获得 REPO_URL/GIT_REF/CREDENTIAL_ID | TC-DB-01～03 |
| AC-09 | 新建页与编辑页任务名称/类型均只读；类型通过添加任务选定，名称自动生成 | TC-ED-17～19 |
| AC-10 | 后端测试、前端测试、`tsc`、构建全部通过 | TC-REG-01～04 |

## 9. 风险

| 风险 | 影响 | 缓解 |
|------|------|------|
| 移除 DTO 字段影响依赖扫描等内部调用方 | 创建流水线失败 | 全仓搜索 `PipelineSaveDTO` 调用方并同步；单测覆盖 |
| 旧库未回填导致克隆参数缺失 | 执行失败/配置丢失 | 启动回填 + 保存时校验 |
| JSON1 在目标 SQLite 不可用 | 迁移失败 | 已用 Python sqlite3 验证 `json_set/json_object` 可用；迁移脚本保持单语句 |
| 共享弹框样式影响全站 | 视觉回归 | 只改圆角/底色/遮罩/分隔线，不动尺寸与交互；人工抽查 2 个模块 |
| 克隆凭证与 Git 用户名/密码参数并存 | 语义混淆 | 文案注明“凭证与下方 Git 用户名/密码二选一” |
