# 任务市场前端改造 Spec

> 日期：2026-10-11
> 版本：v1.1
> 状态：定稿
> 仓库：hfwas-devops（默认分支 `dev`）
> 相关文档：[任务市场设计](./pipeline-task-marketplace-design.md)、[运行时参数](./runtime-param-design.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v1.0 | 2026-10-11 | 定稿：卡片列表、参数可见性、显式发布版本、管理员页面编辑 |
| v1.1 | 2026-10-11 | 锁定版本号生成：省略 `version` 时后端按 semver 补丁位递增，首次为 `1.0.0` |

---

## 0. 已拍板决策

1. **版本策略**：显式「发布版本」，避免每次保存误发版。
2. **视觉参照**：通用插件市场卡片（密度与信息层级按常见插件市场落地，不绑定某产品截图）。

---

## 1. 背景与现状缺口

现有实现：

- `frontend/src/modules/pipeline/pages/TaskKindListPage.tsx`：`DataTable` 表格列表。
- `TaskKindDetailPage.tsx`：只读概览 + 参数表，仅支持启停。
- API 已有 `PUT /pipeline/task-kinds/{kind}`、`toggle`、参数类型 `input | select | api_select`，前端未接完整编辑。
- 设计文档曾规划卡片市场，但未落地；无参数可见性、无插件版本。

目标：插件数量少（最多几十个），先把任务市场前端补齐为可运营的卡片市场 + 可配置参数 + 版本管理。

---

## 2. 目标与非目标

### 目标

| 项 | 结论 |
|---|---|
| 列表形态 | 卡片网格：图标区 + 标题 + 分组/版本徽标 + 一句话描述 + 启用状态 |
| 规模 | 不做无限滚动分页；分组 Tab + 关键词搜索即可 |
| 环境变量 | 可配置：静态值 / 枚举 / 远程接口（沿用 `param_type`） |
| 参数可见性 | `visibility`: `user`（对用户开放） / `hidden`（仅管理员配置，流水线侧不展示） |
| 版本 | Task Kind 有版本；管理员显式发布；可查看历史并切换当前启用版本 |
| 管理编辑 | 管理员在详情页完整改元数据、参数；保存 ≠ 发布 |

### 非目标（本轮不做）

- 流水线编辑器大改（仅约定：绑定列表过滤掉 `hidden`）
- 自定义新增 Kind / 图标上传
- 版本 diff UI

---

## 3. 页面信息架构

### 3.1 列表 `/pipeline/task-kinds`

- 顶栏：标题「任务市场」+ 搜索 + 分组筛选（全部 / 各 `taskGroup`）
- 主体：响应式卡片网格（约 1～4 列）
- 卡片字段：图标（按 `kindValue`）、`label`、`taskGroup`、当前 `version`、短描述、启用状态
- 交互：点卡片进详情；管理员卡片上快捷启停；禁用卡片视觉降权

### 3.2 详情 `/pipeline/task-kinds/:kind`

Tab：`概览` | `参数` | `版本`

- **概览**：元数据展示；管理员「编辑」表单（名称、分组、描述、提示、镜像、资源、命令模板、排序）。保存写入当前草稿，不自动升版本。
- **参数**：列表展示全部参数；管理员增删改。字段：key、label、type、default、required、visibility、options / api*、placeholder；`api_select` 支持 preview。
- **版本**：版本列表（号、发布时间、备注、是否当前）；「基于当前配置发布版本」；切换当前启用版本需确认。

---

## 4. 数据模型增量

```ts
// TaskKindVO 增量
version: string                 // 当前启用版本，如 "1.2.0"；未发布时为空，前端显示 —
versions?: TaskKindVersion[]    // 详情可另拉 GET .../versions

interface TaskKindVersion {
  version: string
  note?: string
  createdAt: string
  isCurrent: boolean
}

// TaskKindParam 增量
visibility: 'user' | 'hidden'   // 默认 'user'
```

行为约定：

- `hidden`：流水线绑定与运行弹框不出现；运行时用任务市场默认值注入。
- `user`：流水线可「写死 / 设为变量」（与 [运行时参数](./runtime-param-design.md) 一致）。
- **保存**：更新当前工作副本（元数据 + params），不产生新版本号，也不修改 `version`。
- **发布版本**：将当前工作副本打成带备注的版本快照，并设为当前启用版本。
- **切换版本**：把某历史版本激活为当前；激活后工作副本（元数据 + params）与该版本快照一致。启停状态不随版本回滚。

版本号生成（已锁定，只此一种）：

- 请求体 `version` 可省略。
- 省略时，后端读取当前启用版本：空或非 semver 则生成 `1.0.0`，否则补丁位 +1（`1.2.0` → `1.2.1`）。
- 显式传入必须匹配 `^\d+\.\d+\.\d+$`，且同一 Kind 不得重复。
- 无历史版本时列表与详情显示 `—`，不把未发布的工作副本假装成 `1.0.0`。

---

## 5. API

复用：

- `GET /pipeline/task-kinds`（列表 VO 带当前 `version`）
- `GET /pipeline/task-kinds/{kind}`
- `PUT /pipeline/task-kinds/{kind}`（body 含 `taskGroup`，以及 `params[].visibility`；不接受、不修改 `version`）
- `PATCH /pipeline/task-kinds/{kind}/toggle`
- `POST /pipeline/job-params/preview-api`

新增：

| Method | Path | 说明 |
|--------|------|------|
| `GET` | `/pipeline/task-kinds/{kind}/versions` | 列出版本，新的在前 |
| `POST` | `/pipeline/task-kinds/{kind}/versions` | 显式发布：`{ note?: string, version?: string }`，返回新版本 |
| `PUT` | `/pipeline/task-kinds/{kind}/versions/{version}/activate` | 切换当前启用版本，返回更新后的 Task Kind |

持久化：

- `pipeline_task_kind.version`：当前启用版本，未发布为空字符串。
- `pipeline_task_kind_param.visibility`：`user` | `hidden`，默认 `user`。
- `pipeline_task_kind_version`：版本号、备注、工作副本 JSON 快照、发布时间。

`hidden` 的运行时过滤做在 `PipelineRunService`：默认参数接口不返回 `hidden`；合并运行参数时用任务市场默认值覆盖，即使用户在请求里带了同名键。React 流水线编辑器目前没有绑定列表，联调时在绑定 UI 里同样丢掉 `visibility === 'hidden'` 的参数。

---

## 6. 前端改动范围

| 文件 | 改动 |
|---|---|
| `TaskKindListPage.tsx` | 表格 → 卡片网格 + 分组筛选 |
| `components/TaskKindCard.tsx` | 单卡 |
| `TaskKindDetailPage.tsx` | 概览/参数可编辑；版本 Tab |
| `components/TaskKindParamEditor.tsx` | 类型、可见性、options/api、预览 |
| `components/TaskKindVersionsPanel.tsx` | 发布 / 列表 / 激活 |
| `types/pipeline.ts` | `version`、`visibility`、`TaskKindVersion` |
| `api/pipeline.ts` | 版本 API；toggle 改为 `PATCH` |

样式：沿用现有 console / shadcn 体系，卡片圆角边框、轻阴影、分组色或图标色块即可，接近通用插件市场密度。图标按 `kindValue` 映射 Lucide，不上传。

---

## 7. 权限

| 操作 | 权限 |
|------|------|
| 查看列表/详情 | 普通用户 |
| 编辑元数据/参数、发布/切换版本、启停 | 管理员 |

非管理员隐藏编辑、发布、切换与启停入口。前端用 `useAuthStore().isAdmin()`（`user.role === 'admin'`）。

---

## 8. 验收标准

1. 列表为卡片，搜索与分组可用。
2. 管理员可改元数据与参数并保存成功回读；保存不改变版本号。
3. 显式发布后列表/详情显示新版本号；可查看历史并切换当前版本。
4. 参数可配 `input` / `select` / `api_select`，远程可预览。
5. `hidden` 在任务市场可配；运行弹框与运行参数合并已过滤。流水线绑定列表待编辑器落地时过滤（本仓库 React 流水线页尚无该列表）。
6. 非管理员只读。

---

## 9. 实施顺序建议

1. 类型与 API 层（含 visibility / version 字段对接；后端缺字段时先加最小后端支持）。
2. 列表卡片化。
3. 详情编辑（概览 + 参数）。
4. 版本 Tab（发布 / 列表 / 激活）。
5. 与流水线绑定列表的 `hidden` 过滤（能顺手则做）。

---

## 10. 相关路径速查

- 列表/详情：`frontend/src/modules/pipeline/pages/TaskKindListPage.tsx`、`TaskKindDetailPage.tsx`
- 卡片与编辑：`frontend/src/modules/pipeline/components/TaskKindCard.tsx`、`TaskKindParamEditor.tsx`、`TaskKindVersionsPanel.tsx`
- API/类型：`frontend/src/modules/pipeline/api/pipeline.ts`、`types/pipeline.ts`
- 后端：`backend/pipeline-core/.../PipelineTaskKindController.java`、`PipelineTaskKindService.java`
- 表结构：`backend/server/src/main/resources/db/pipeline-schema.sql`、`deploy/charts/backend/files/db/15-task-kind-version-visibility.sql`
