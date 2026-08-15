# 接口测试前端统一工作台（ApiTestShell）设计

> 日期：2026-08-14  
> 状态：已确认并实现中/已实现  
> 范围：前端信息架构与页面重设计；对齐 Postman / Apifox 交互；**本期不改后端 API 契约**

---

## 1. 背景与目标

现有 `api-test` 前端已具备接口定义工作台雏形（侧栏树 + 请求编辑 + 响应），但环境、集合仍为顶栏独立页，缺少多请求 Tab、统一壳层与 Postman 级请求/响应分区。

**目标：** 以统一工作台 `ApiTestShell` 承载接口 / 集合 / 环境（及文档类占位），主区始终为请求调试器，交互与布局对齐 Postman/Apifox。

**非目标（本期）：** Auth / Docs / Specs / Mocks / Visualize 的后端实现；整页集合详情路由；压测 / Flows；强制 E2E。

---

## 2. 已确认决策

| 项 | 选择 |
|----|------|
| 信息架构 | 统一工作台：左侧模块轨切换，主区请求调试器 |
| 侧栏模块 | 接口、集合、环境可用；文档 / Specs / Mocks 占位 |
| 多请求 Tab | 第一期支持，可并行编辑/调试 |
| 请求区 Tab | 全量布局：Params / Auth / Headers / Body / Scripts / Tests / Docs / Settings |
| 未齐能力 | 能接真数据的接真数据；Auth / Docs / Visualize / Settings 等占位 |
| 主题 | 跟随控制台浅/深主题 |
| 实现路径 | 新建 `ApiTestShell`，取消顶栏二级 Tab，旧路由 redirect |

模块轨位置：**左侧竖轨**（非底部图标栏）。

---

## 3. 整体布局与导航

```
┌─────────────────────────────────────────────────────────────┐
│ 产品顶栏（现有控制台） · 环境快速选择器                      │
├────┬──────────┬─────────────────────────────────────────────┤
│竖轨│ 资源面板  │ 请求 Tab 栏（多标签）                        │
│API │ (树/列表) │ method · URL · Send · Save                  │
│COL │          │ Request tabs: Params|Auth*|Headers|Body|…  │
│ENV │          ├─────────────────────────────────────────────┤
│DOC*│          │ Response: Body|Headers|Console|Tests|…      │
│SPC*│          │                                             │
│MCK*│          │                                             │
└────┴──────────┴─────────────────────────────────────────────┘
* = Coming soon 占位
```

### 导航约定

- 入口：`/api-test`（默认激活接口模块）；产品切换器仍指向该路径
- 从 `CONSOLE_TABS` **移除**「API 接口 / 环境变量 / 集合管理」三项（壳内竖轨替代）
- 旧路径全部 redirect 进壳：
  - `/api-test/definitions`、`/api-test/definitions/:id`
  - `/api-test/environments`
  - `/api-test/collections`、`/api-test/collections/:id`、`…/runs`
- 切竖轨只换左侧 `ResourcePanel`，**不关闭**已打开的请求 Tab
- 侧栏宽度、请求/响应上下高度可拖拽；响应高度可按 session 记忆

---

## 4. 多标签与请求 / 响应工作区

### Tab 模型

```ts
type TabSource = 'definition' | 'collection' | 'scratch'

interface RequestTab {
  id: string
  source: TabSource
  refId?: number          // definitionId 或 collectionItemId
  title: string
  method: string
  dirty: boolean
  draft: RequestDraft     // url/method/headers/params/body/scripts/assertions/extracts…
  result?: ApiDebugResultVO | null
}
```

### 交互

| 动作 | 行为 |
|------|------|
| 单击树节点 | 若已有同 `source+refId` Tab → 激活；否则新建并加载详情 |
| 「+」 | 打开空白 `scratch` Tab |
| 关闭脏 Tab | 确认后再关 |
| Send | `draft` → 现有 debug API；结果写入**当前 Tab**（Tab 间隔离） |
| Save | `definition` / `scratch`（另存为）→ 写接口定义及脚本/断言/提取；`collection` → 写回关联的接口定义（Item 仅作入口，本期不做 Item 级请求覆盖） |
| Auth / Docs / Settings / Visualize | 占位 UI，不阻断 Send/Save |

### 请求区 Tab（真数据 vs 占位）

| Tab | 本期 |
|-----|------|
| Params / Headers / Body | 真数据 |
| Scripts（前置/后置） | 真数据（独立 Scripts Tab） |
| Tests（断言） | 真数据（独立 Tests Tab） |
| Extracts | 真数据；编辑挂在 Tests Tab 内分区；Response 侧 Extracts 仅展示本次执行结果 |
| Auth / Docs / Settings | 占位 |
| Response: Body / Headers / Console / Assertions / Extracts | 真数据 |
| Response: Visualize | 占位 |

---

## 5. 侧栏模块行为

### 接口（APIs）

- 分组树 + 接口节点（复用 group / definition）
- 右键：新建/编辑/删除分组、新建接口
- 单击接口 → 打开/激活请求 Tab（`source=definition`）

### 集合（Collections）

- 集合列表 → 进入后展示文件夹 + Item 树
- 单击 Item → 打开请求 Tab（`source=collection`）
- Run / Run 历史：壳内抽屉或临时「Run Result」Tab，**不跳独立整页**
- 废弃独立集合详情页作为主路径

### 环境（Environments）

- 侧栏：环境列表 + 当前环境变量表编辑
- 顶栏环境选择器与侧栏当前环境同步
- 切换环境立即作用于后续 Send
- 本期不把环境强制做成主区 Tab

### 占位模块

- Docs / Specs / Mocks：图标 + Coming soon 文案

---

## 6. 组件结构

```
frontend/src/modules/api-test/
├── shell/
│   ├── views/ApiTestShell.vue
│   ├── components/
│   │   ├── ModuleRail.vue
│   │   ├── ResourcePanel.vue
│   │   ├── ApiTreePanel.vue
│   │   ├── CollectionPanel.vue
│   │   ├── EnvironmentPanel.vue
│   │   ├── PlaceholderPanel.vue
│   │   ├── RequestTabBar.vue
│   │   └── RequestWorkspace.vue
│   ├── stores/workspace.ts
│   └── router/…（并入 apiTestRoutes）
├── define/ …          # 保留 api/types/stores；UI 迁入或薄封装
├── collection/ …
├── environment/ …
├── debug/ …           # Request/Response/Script/Assertion/Extract 组件复用
└── shared/ …
```

优先**复用**现有 `KeyValueEditor`、`ScriptEditor`、`AssertionEditor`、`ExtractEditor`、`ResponseViewer` 等，避免平行实现两套编辑器。

---

## 7. 状态与数据流

- **新建** `workspaceStore`：`activeModule`、`tabs[]`、`activeTabId`、布局尺寸
- **复用** `definition` / `group` / `collection` / `environment` / `debug` stores
- Send：当前 Tab `draft` → `debugStore.execute`（传入 `environmentId`）→ `result` 写回该 Tab  
  - 注意：现有 `debugStore.currentResult` 为全局单例时，需改为「按 Tab 存结果」或 execute 后立即拷贝到 Tab，避免串台
- Save：definition update 或 collection item update；脚本/断言/提取按现有子资源 API
- 路由：Tab 状态以内存为主；可选 `sessionStorage` 恢复最近 active；深链可用 query（如 `?def=id`）打开对应 Tab

### 错误处理

- Send/Save 失败：全局 message + 当前 Tab 内错误条
- 加载详情失败：Tab 内重试，不卸载 Shell
- 关脏 Tab：确认框

### 测试（本期）

- `workspaceStore` 单元：开/关/切 Tab、脏标记、同资源复用
- Shell 挂载与模块切换 smoke（若项目已有前端测框架则补；否则手工清单）
- 手工主路径：开接口 → Send → Save；集合 Item 打开；环境切换后变量生效

---

## 8. 主题

- 使用现有控制台主题机制（`useConsoleTheme` 等），Shell 内样式走 CSS 变量 / Naive 主题
- 不为 api-test 单独锁死深色；布局与信息架构对齐 Postman，视觉跟随产品浅/深

---

## 9. 迁移与清理

1. 落地 `ApiTestShell` 与 `workspaceStore`
2. 迁入/包装侧栏三面板与请求工作区
3. 路由改为壳入口 + 旧路径 redirect
4. 从 `CONSOLE_TABS` 移除三个二级 Tab（产品切换仍进 `/api-test`）
5. 标记废弃：`ApiDefinitionList` / 独立 `CollectionDetail` 整页主路径（可暂留文件至无引用后删）
6. 绿野原则：不做旧布局长期兼容分支

---

## 10. 成功标准

- 用户可在同一壳内完成：浏览接口树、多 Tab 调试、保存、切换环境、打开集合 Item、触发集合 Run（壳内看结果）
- 布局可辨识为 Postman/Apifox 同类工具（竖轨 + 树 + 多 Tab + 请求/响应）
- Auth/Docs/Visualize 等占位清晰，不出现半残报错
- 浅色/深色下均可正常使用

---

## 11. 后续（明确不在本期）

- Auth 配置持久化与注入
- Docs / OpenAPI Specs / Mocks
- Response Visualize
- Tab 状态服务端同步 / 跨设备恢复
- 集合详情高级编排 UI
