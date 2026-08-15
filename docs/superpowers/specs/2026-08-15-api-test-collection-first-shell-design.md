# 接口管理集合优先工作台设计

> 日期：2026-08-15  
> 状态：已确认（待实现）  
> 范围：前端信息架构收束为「集合唯一入口」；对齐 Postman 集合树 + 请求上下分区调试；**本期不改后端 REST 契约形状**  
> 前置：`2026-08-14-api-test-shell-frontend-design.md`（已实现统一 Shell）

---

## 1. 背景与目标

现有 `ApiTestShell` 通过左侧模块竖轨切换「接口 / 集合 / 环境」等，主区为请求调试器。产品期望进一步对齐 Postman：

- 左侧**只有集合**：可新建集合，在集合（及文件夹）下新建接口
- 右侧为重心：接口文档 + 调试（上下分区）
- 右上角展示并管理环境

**目标：** 将壳层收束为集合优先工作台，去掉模块竖轨与独立接口树 UI，强化集合概览与环境轻量编辑。

**非目标（本期）：**

- 后端改为「请求直属集合、脱离 definition」的数据模型
- Auth / Scripts / Variables / Specs / Mocks 真实现
- 完整 Markdown 文档站
- 强制 E2E

---

## 2. 已确认决策

| 项 | 选择 |
|----|------|
| 数据入口 | **A**：集合即唯一 UI 入口；无独立「接口定义」树 |
| 请求主区布局 | **1**：上下分区（请求配置 + 响应）；Docs 为请求区 Tab |
| 点击集合 | **A**：打开集合概览 Tab（Overview / Runs 等） |
| 环境编辑 | **A**：右上角选择器 + 新建 + 抽屉编辑变量（不占左侧树） |
| 实现路径 | **方案 1**：收束现有 Shell；新建接口时隐式 create definition + collection item |

---

## 3. 整体布局与信息架构

```
┌──────────────────────────────────────────────────────────────┐
│ 控制台顶栏 ················ [环境选择器 ▾] [新建环境]        │
├────────────────┬─────────────────────────────────────────────┤
│ COLLECTIONS    │  Tab 栏：集合概览 | 请求1 | 请求2 …          │
│ [搜索] [+]     │─────────────────────────────────────────────│
│ ▾ 集合A        │  【请求 Tab】                               │
│   ▾ 文件夹     │  method · URL · Send · Save                 │
│   · POST xxx   │  Params | Headers | Body | Docs | Scripts… │
│ ▾ 集合B        │  ─────────── 可拖拽分割 ───────────         │
│                │  Response: Body | Headers | Tests…          │
│                │                                             │
│                │  【集合概览 Tab】                            │
│                │  Overview | Runs（其余子 Tab 可 Coming soon）│
└────────────────┴─────────────────────────────────────────────┘
```

### 变更要点

- **去掉**左侧模块竖轨（接口 / 集合 / 环境 / Docs / Specs / Mocks）及 `ResourcePanel` 按模块切换。
- 侧栏**固定**为集合树（强化现有 `CollectionPanel` / `CollectionTree`）。
- 环境**仅**右上角：选择、新建、抽屉编辑；不再使用壳内 `EnvironmentPanel` 作为主导航。
- 入口仍为 `/api-test`；旧路径 `definitions` / `environments` / `collections*` 继续 redirect 进壳（可带 query 打开对应 Tab/抽屉）。
- 侧栏宽度、请求/响应分割高度行为保持现有可拖拽 + session 记忆。

---

## 4. Tab 模型与主区行为

### 4.1 Tab 类型

| 类型 | 何时打开 | 内容 |
|------|----------|------|
| 集合概览 | 点击集合名 | Overview（名称、描述、元信息）、Runs（触发现有 Run 抽屉）；Authorization / Scripts / Variables 本期 Coming soon |
| 请求 | 点击集合内请求，或新建请求 | 现有 `RequestWorkspace` 上下分区；Send / Save；Docs Tab 绑定 definition 描述（有则编辑，无则占位文案） |
| scratch（可选） | 空态新建且未绑定集合 | 保存时必须选择目标集合（不再选择「接口分组」作为主路径） |

### 4.2 去重与数据加载

- 请求 Tab：以 `source: 'collection'` + `refId = collectionItemId` 去重；`definitionId` 用于加载/保存/调试（用户无感知）。
- 集合概览 Tab：以 `source: 'collectionOverview'`（或等价）+ `refId = collectionId` 去重。
- 打开请求：继续 `loadDefinitionIntoTab(definitionId)` 填 draft。

### 4.3 请求区 Tab（相对现状）

- 保留 Params / Headers / Body / Scripts / Tests（断言/提取）等已接真数据能力。
- **Docs**：优先展示/编辑 `description`（及现有可读文档字段）；不做独立文档产品。
- Auth / Visualize / Settings：仍可为 Coming soon。

---

## 5. 侧栏与新建流程

### 5.1 集合树操作

- 顶栏 `+`：新建集合（弹窗：名称、描述）→ 刷新列表 → 打开该集合概览 Tab。
- 集合行 `+`：在该集合下新建请求（默认名如 Untitled Request，方法 POST，空 URL）→ 见 §5.2。
- 集合 `…`：重命名、删除、Run、查看历史（复用现有能力与 Run 抽屉）。
- 文件夹：沿用现有 folder API；支持在文件夹下新建请求（传入 `folderId`，若 API 已支持）。
- 搜索：过滤集合/请求名称（沿用或补齐侧栏搜索）。

### 5.2 新建请求（隐式 definition）

用户只感知「在集合下新建接口」。实现步骤：

1. `POST` 创建 `api_definition`（项目内，最小字段：name / method / path）
2. `POST` 将该 definition 挂为 `collection_item`（可选 `folderId`）
3. 任一步失败则不留下半截关联（能回滚则回滚；否则提示错误并避免打开空 Tab）
4. 成功后刷新集合详情树，打开对应请求 Tab

**本期不改**后端 URL 与 DTO 形状；仅编排现有 API。

### 5.3 Scratch 保存

- 保存目标改为「选择集合」（及可选文件夹），创建/更新 definition 后写入 item；不再以「接口分组树」为唯一保存路径。

---

## 6. 环境（右上角）

- 选择器：`No environment` + 项目环境列表；写入 `environmentStore.selectedEnvironmentId`；Send 时带上。
- **新建环境**：下拉底部或旁侧入口 → 弹窗（名称）→ 创建后选中并打开编辑抽屉。
- **编辑变量**：侧滑抽屉（非新开页）——环境名、变量表（key / value / secret）、保存；替换当前「查看变量 → `/api-test/environments/:id` 新窗口」行为。
- `/api-test/environments*` redirect 进壳；可选 `?envEdit=<id>` 自动打开对应抽屉。

---

## 7. 空态与错误

**空态**

- 无集合：侧栏 CTA「创建第一个集合」。
- 有集合但无打开 Tab：主区引导「选择集合或新建接口」。
- 集合无请求：概览内提示「在集合下新建接口」。

**错误**

- 树/详情/Send/Save/建请求失败：`message.error`，不静默。
- 请求加载失败：Tab 保留 `loadError` 展示。

---

## 8. 组件与状态影响（前端）

| 区域 | 动作 |
|------|------|
| `ApiTestShell.vue` | 移除 ModuleRail；侧栏常驻集合面板；主区按 Tab 类型渲染请求工作区或集合概览 |
| `ModuleRail` / `ResourcePanel` 模块切换 | 删除或降级为不再使用 |
| `ApiTreePanel` | 壳内不再挂载 |
| `CollectionPanel` | 升级为唯一侧栏：扁平多集合树、点击集合开概览、+ 新建请求 |
| 新组件 | `CollectionOverviewTab`（或等价）；`EnvironmentEditDrawer` |
| `EnvironmentSelector` | 增加新建入口；「查看变量」改为开抽屉 |
| `workspace` store | 支持集合概览 Tab 类型；默认模块概念可移除或固定为 collections |
| 路由 / products | 保持 `/api-test`；redirect 行为微调（envEdit query） |

主题跟随控制台浅/深；不另起视觉体系。

---

## 9. 测试

- Vitest：壳层无 ModuleRail；点击集合打开概览 Tab；集合 `+` 新建请求时对 definition + item API 的调用顺序（mock）；环境抽屉打开与保存。
- 不强制 Playwright/E2E。

---

## 10. 成功标准

1. 进入接口测试后，左侧只有集合相关树与操作，无模块竖轨、无独立接口定义树。
2. 可新建集合；可在集合下新建接口并立即调试（Send/Save）。
3. 点击集合打开概览；点击请求打开上下分区调试，含 Docs Tab。
4. 右上角可选环境、新建环境、抽屉编辑变量；Send 使用所选环境。
5. 现有集合 Run / 历史抽屉仍可用。

---

## 11. 与前置设计的关系

本设计是对 `2026-08-14` Shell 设计的**信息架构收束**，不是推倒重来：保留多请求 Tab、RequestWorkspace、环境 store、集合 Run 抽屉；删除竖轨多模块范式，改为集合优先。
