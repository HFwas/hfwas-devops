# 接口测试壳层 Postman 对齐设计

> 日期：2026-08-15  
> 状态：已确认（待实现）  
> 范围：前端壳层对齐 Postman 三块——侧栏 History、请求面包屑、集合树精修；**不改后端 REST 契约**  
> 前置：`2026-08-15-api-test-collection-first-shell-design.md`、`2026-08-15-api-test-shell-compact-ui-design.md`

---

## 1. 背景与目标

集合优先壳层已可用，但与 Postman 仍有明显差距：

- 发送后的调试记录没有左侧 **History** 入口（用户习惯在侧栏找）
- 请求主区缺少 `集合 > 文件夹 > 请求名` 面包屑
- 集合树方法色/选中态还可再贴近 Postman 扫读体验

**目标：** 在不改后端契约的前提下，完成 A+B+C 对齐，使「发送 → 左侧 History 可见 → 点击重开」闭环成立。

**非目标：**

- History 高级筛选/分组、多选删除产品化
- 面包屑点击跳转侧栏（本期纯展示）
- 树拖拽排序、完整右键菜单重做
- Postman 独立深色侧栏主题
- 后端数据模型变更

---

## 2. 已确认决策

| 项 | 选择 |
|----|------|
| 范围 | **A+B+C**：侧栏 History + 面包屑 + 树精修 |
| History 点击 | **1**：始终打开**新**请求 Tab（scratch），填入历史请求 + 当时响应 |
| 实现路径 | **方案 1**：前端壳层精修，复用 `debug-histories` API |
| History 主入口 | **侧栏**；响应区「历史」Tab **移除**，避免双入口 |
| 面包屑跳转 | 本期只展示；末级可编辑名称 |
| 后端 | 不改 REST 形状 |

---

## 3. 整体布局

```
┌──────────────────────────────────────────────────────────────┐
│ 控制台顶栏 ················ [环境 ▾] [编辑]                   │
├────────────────┬─────────────────────────────────────────────┤
│ Collections│History │  Tab 栏 …                              │
│ (mode toggle)  │─────────────────────────────────────────────│
│                │  【请求 Tab】                               │
│ [搜索/工具]    │  集合 > 文件夹 > [可编辑名称]                 │
│                │  method · URL · Save · Send                 │
│ 树 或 History  │  Params | Headers | Body | …                │
│ 列表           │  ─────────── 分割 ───────────               │
│                │  响应 | Visualize                           │
└────────────────┴─────────────────────────────────────────────┘
```

---

## 4. §A — 侧栏 Collections | History

### 4.1 切换

- `CollectionsSidebar` 顶部增加同宽分段：`Collections` / `History`（`data-testid="sidebar-mode-collections"` / `sidebar-mode-history`）
- 模式状态可放在 sidebar 本地 `ref`，或 `workspace` 轻量字段；刷新后默认 Collections 即可

### 4.2 Collections 模式

- 保持现有：搜索、导入 cURL、新建集合、集合树、Run/集合历史抽屉入口

### 4.3 History 模式

- 数据：`debugHistoryApi.page({ projectId: 1, pageNo: 1, pageSize: 50 })`（可前端 keyword 过滤 name/url）
- 行展示：方法色 + 名称（或截断 URL）+ 状态码 + 时间；`data-testid="history-row-{id}"`
- 空态：「发送请求后会出现在这里」
- 工具条：刷新按钮（可选）

### 4.4 点击 History 行

1. `debugHistoryApi.detail(id)`
2. `workspace.openScratchTab()`（每次新开，**不去重**）
3. `patchDraft`：method、url、headers、queryParams、body、contentType
4. `setTabMeta`：title = history.name，method
5. `setTabResult`：由 detail 映射为 `ApiDebugResultVO`（与现有响应区组件兼容）
6. 停留在 History 模式亦可；用户可手动切回 Collections

### 4.5 发送后刷新

- `RequestWorkspace.handleSend` 成功后：若侧栏处于 History 模式则刷新 page 列表
- 可通过 `provide/inject`、轻量 event、或 pinia `debugStore` 增加 `historyVersion` 计数触发 sidebar watch
- 推荐：`debugStore` 增加 `bumpHistoryEpoch()`，sidebar History 模式 watch epoch 后 reload

### 4.6 响应区 History Tab

- **删除** `RequestWorkspace` 响应区「历史」Tab（及仅服务于它的 UI）；侧栏为唯一请求 History 入口
- 集合 Run History 仍走现有抽屉，命名保持「运行历史」，避免混淆

---

## 5. §B — 请求面包屑

### 5.1 展示规则

| Tab 源 | 面包屑 |
|--------|--------|
| `collection` | `集合名 > 文件夹路径… > [可编辑请求名]` |
| `scratch`（含 History 打开） | `Scratch` 或 `History` > `[可编辑标题]` |
| `definition` | `Definition` > `[可编辑标题]`（深链遗留） |
| `collectionOverview` | 不显示本面包屑 |

### 5.2 数据

- Tab 增加可选 `folderId?: number | null`
- 从集合打开请求时写入 `folderId`（`CollectionItemVO.folderId`）
- 路径解析：用侧栏已缓存的 `CollectionDetailVO`（或 `loadDetail`）按 `folders` 的 `parentId` / 嵌套 `children` 回溯文件夹名链
- 集合名：detail.name 或 page 列表中的集合 VO

### 5.3 UI

- 合并现有「大号名称行」为面包屑末级 `n-input`（`data-testid="request-name"` 保留）
- 中间段纯文本 + `>` 分隔；不可点击（本期）
- Save 仍更新 definition.name + collection item.name（既有逻辑）

---

## 6. §C — 集合树精修

- method 标签：`toUpperCase()`；固定短宽；沿用 `--api-method-*`
- 行高对齐 `--api-row-height`
- 选中态：把当前请求 Tab 的 `refId`（collection item id）传入 `CollectionTree.selectedId` 并高亮
- 文件夹前缀：展开 ▾ / 收起 ▸（与侧栏集合行一致即可）
- 不做拖拽、多选、菜单重做

---

## 7. 组件与文件（预期）

| 区域 | 主要改动 |
|------|----------|
| `CollectionsSidebar.vue` | mode 切换；History 列表；选中 item 传 selectedId；打开时带 folderId |
| 新组件（可选）`HistorySidebarList.vue` | History 列表渲染，便于单测 |
| `RequestWorkspace.vue` | 面包屑；移除响应区 History Tab；send 后 bump epoch |
| `workspace` types/store | `folderId`；必要时 `sidebarMode` / 仅用 debugStore epoch |
| `debug` store | `historyEpoch` + `bumpHistoryEpoch`；`clearHistory` 保留 |
| `CollectionTree.vue` | 选中高亮、method upper、行高 |
| 测试 | sidebar mode/history click；breadcrumb；send→epoch；tree selected |

---

## 8. 测试与验收

1. 侧栏可切换 History；初始空态文案正确  
2. 已保存请求 Send 后，切到 History 可见新行（或停留 History 时自动出现）  
3. 点击 History 行 → 新 Tab，URL/方法/响应与详情一致  
4. 集合内请求显示正确面包屑；改名 Save 后树与标题更新  
5. 当前打开请求在树中高亮；method 颜色正确  
6. 响应区无「历史」Tab；集合「运行历史」抽屉仍可用  

---

## 9. 风险与注意

- 未保存的 scratch Send：历史 `definitionId` 可能为 null，仍出现在 **项目级** page 列表（与 by-definition 不同）——符合侧栏 History 预期  
- `detailCache` 未加载时面包屑可能只有集合名 + 请求名；打开请求前应已 `ensureDetail`  
- History 每次新开 Tab，注意脏 Tab 过多——本期不自动关旧 Tab  
