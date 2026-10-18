# 流水线管理高保真可点击原型

> 日期：2026-10-18
> 版本：v0.11

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：按 prototype-spec.json 生成 HTML 可点击原型、17 张 2x 高保真画板和 Figma 连线说明 |
| v0.2 | 2026-10-18 | 页面调教第 1 批：侧栏“配置”分组、顶栏搜索改为流水线、列表整行可点与 `⋯` 操作菜单、运行详情来源感知返回、新增凭证页与校验失败态；画板增至 20 张 |
| v0.3 | 2026-10-18 | 编辑器重构：名称移到标题旁、移除基本信息行、克隆检查器内配置仓库/分支/凭证、移除缩放、阶段自动命名、最近克隆回显；详情增加 Commit 卡片；弹框统一 Kite 分隔线与遮罩 |
| v0.4 | 2026-10-18 | 编辑页任务名称与类型只读（含本次新加任务）；新建页仍可编辑 |
| v0.5 | 2026-10-18 | 代码克隆任务隐藏“高级设置”（技术栈/运行时/工具版本） |
| v0.6 | 2026-10-18 | 移除流水线列表 `⋯` 菜单中的“历史”入口；运行历史仍可从详情页页签进入 |
| v0.7 | 2026-10-18 | 修复编辑器原型缺少阶段间连接箭头的问题，与前端实现 `PipelineEditorPage` 对齐 |
| v0.8 | 2026-10-18 | 任务名称/类型在新建页也设为只读；新增 `21-editor-new.png` |
| v0.9 | 2026-10-18 | 详情/运行历史头部返回按钮改为图标按钮，标题恢复 sans，与 Kite 扁平头对齐 |
| v0.10 | 2026-10-18 | 详情指标移除“阶段”卡；原型角标同步版本 |
| v0.11 | 2026-10-18 | 运行历史页签移除指标行，只在详情页签显示 |

---

## 1. 文件说明

| 文件 | 用途 |
|------|------|
| [`index.html`](index.html) | 单文件可点击原型，无外部依赖，直接打开即可操作 |
| `shots/01-list.png` ～ `shots/17-modal-discard.png` | 17 张高保真画板，2880×1800（1440×900 @2x），可直接拖入 Figma |
| `shots/15-flow-map.svg` | 交互流程矢量图，拖入 Figma 后文字和图形可编辑 |
| [`../pipeline-management-prototype-spec.json`](../pipeline-management-prototype-spec.json) | 机器可读规格，供 Figma Make / 脚本 / MCP 使用 |
| [`../pipeline-management-prototype-spec.md`](../pipeline-management-prototype-spec.md) | 字段、组件、状态、交互说明 |

## 2. 打开可点击原型

```bash
open docs/pipeline/prototype/index.html
```

或起一个本地静态服务（推荐给 html.to.design 等插件使用）：

```bash
cd docs/pipeline/prototype
python3 -m http.server 8099
# 浏览器打开 http://localhost:8099/
```

### 2.1 路由

| 页面 | Hash |
|------|------|
| 流水线列表 | `#/list` |
| 流水线详情 | `#/detail/1` |
| 新建流水线 | `#/editor/new` |
| 编辑流水线 | `#/editor/1` |
| 运行历史 | `#/history/1` |
| 运行详情 | `#/run/1/128` |
| 凭证 | `#/credentials` |
| 交互流程总览 | `#/flow` |

### 2.2 状态与弹层参数

| 参数 | 可选值 | 作用 |
|------|--------|------|
| `state` | `empty` / `loading` / `invalid` / `waiting` / `failed` / `succeeded` | 空态、加载、校验失败、待审批、失败、成功 |
| `overlay` | `run-params` / `delete` / `cancel` / `discard` / `log` / `row-menu` / `credential-new` | 直接打开对应弹层或行内菜单 |
| `panel` | `add` | 编辑页右侧显示任务市场 |
| `focus` | `overall` / `stage` / `job` | 日志抽屉标题 |
| ~~`zoom`~~ | — | v0.3 移除画布缩放，固定 100% |
| `capture` | `1` | 隐藏右下角原型导航面板（截图用） |

示例：

```text
index.html?overlay=run-params#/list
index.html?state=waiting#/run/1/128
index.html?panel=add&zoom=75#/editor/1
```

页面右下角的深色面板可以快速跳转页面、弹层和状态；`?capture=1` 时会自动隐藏。

## 3. 画板清单

| 文件 | Frame | 内容 |
|------|-------|------|
| `01-list.png` | `P01_PipelineList_Desktop` | 列表正常态 |
| `02-list-empty.png` | `P01_PipelineList_Empty` | 列表空态 |
| `03-detail.png` | `P02_PipelineDetail_Desktop` | 详情 + 只读定义画布 |
| `04-editor.png` | `P03B_PipelineEditor_Edit_Desktop` | 编辑页，75% 缩放，任务检查器 |
| `05-editor-add-task.png` | `P03B_PipelineEditor_AddTask` | 编辑页，添加任务面板 |
| `06-history.png` | `P04_PipelineHistory_Desktop` | 运行历史正常态 |
| `07-history-empty.png` | `P04_PipelineHistory_Empty` | 运行历史空态 |
| `08-run-running.png` | `P05_RunDetail_Running` | 运行中 |
| `09-run-waiting.png` | `P05_RunDetail_WaitingApproval` | 待审批，显示「通过」 |
| `10-run-failed.png` | `P05_RunDetail_Failed` | 失败，错误信息 + 取消态任务 |
| `11-modal-run-params.png` | `O01_RunParams_Dialog` | 运行参数弹窗 |
| `12-modal-delete.png` | `O03_ConfirmDeletePipeline` | 删除流水线确认 |
| `13-modal-cancel-run.png` | `O04_ConfirmCancelRun` | 取消运行确认 |
| `14-log-drawer.png` | `O02_LogDrawer_Right` | 右侧日志抽屉 |
| `15-flow-map.png` | `P00_FlowMap` | 页面与弹窗交互总览 |
| `16-editor-invalid.png` | `P03B_PipelineEditor_Invalid` | 表单校验失败 |
| `17-modal-discard.png` | `O05_ConfirmDiscardChanges` | 放弃修改确认 |
| `18-credentials.png` | `P06_PipelineCredentials_Desktop` | 凭证列表 |
| `19-credential-invalid.png` | `O07_CreateCredential_Dialog` | 新建凭证，名称校验失败 |
| `20-list-row-menu.png` | `P01_PipelineList_RowMenu` | 列表行内操作菜单 |
| `21-editor-new.png` | `P03_PipelineEditor_New_Restructured` | 新建流水线：名称内联、任务身份只读、克隆仓库配置 |

## 4. 导入 Figma

### 4.1 图片方式（最快）

1. 把 `shots/*.png` 直接拖入 Figma。
2. 每张图片按上表重命名 Frame。
3. 流程总览优先拖入 `shots/15-flow-map.svg`，它是矢量图，文字和图形可以直接编辑。
4. 按第 5 节连线表添加 Prototype 交互。

图片是 2 倍图，导入后如需按真实尺寸排布，把 Frame 设为 1440×900，图片缩放到 50%。

### 4.2 可编辑图层（html.to.design）

1. 在 `docs/pipeline/prototype` 下执行 `python3 -m http.server 8099`。
2. Figma 安装并打开 **html.to.design** 插件。
3. 输入 `http://localhost:8099/index.html#/list`，导入当前页面。
4. 切换 hash 重复导入 `#/detail/1`、`#/editor/1`、`#/history/1`、`#/run/1/128`。
5. 弹层页使用带 `?overlay=` 的 URL 导入。

### 4.3 Design Token

Figma Variables 建议按 [`../pipeline-management-prototype-spec.md`](../pipeline-management-prototype-spec.md) 第 1.2 节建立：

```text
color/background、color/foreground、color/card、color/primary
color/muted、color/muted-foreground、color/border、color/destructive
color/term-bg、color/term-fg
radius/sm=4、radius/md=6、radius/lg=8、radius/xl=12
```

等宽字体使用 Maple Mono；未安装时可临时替换为 JetBrains Mono。

## 5. Figma 连线表

| 起始 Frame | 点击元素 | 目标 Frame |
|------------|----------|------------|
| `P01` | 新建流水线 | `P03_PipelineEditor_New_Desktop` |
| `P01` | 流水线名称 | `P02_PipelineDetail_Desktop` |
| `P01` | 最近运行 | `P05_RunDetail_Running` |
| `P01` | 运行 | `O01_RunParams_Dialog` |
| `P01` | 编辑 | `P03B_PipelineEditor_Edit_Desktop` |
| `P01` | 历史 | `P04_PipelineHistory_Desktop` |
| `P01` | 删除 | `O03_ConfirmDeletePipeline` |
| `P02` | 返回 | `P01_PipelineList_Desktop` |
| `P02` | 编辑 | `P03B_PipelineEditor_Edit_Desktop` |
| `P02` | 运行 | `O01_RunParams_Dialog` |
| `P02` | 运行历史页签 | `P04_PipelineHistory_Desktop` |
| `P03` / `P03B` | 取消（有改动） | `O05_ConfirmDiscardChanges` |
| `P03` / `P03B` | 保存并运行 | `O01_RunParams_Dialog` |
| `P03` / `P03B` | 添加任务 | `P03B_PipelineEditor_AddTask` |
| `P03` / `P03B` | 任务卡 | 回到 `P03B_PipelineEditor_Edit_Desktop` 的检查器态 |
| `P04` | 行 / 查看详情 | `P05_RunDetail_Running` |
| `P04` | 编辑 | `P03B_PipelineEditor_Edit_Desktop` |
| `P04` | 运行 | `O01_RunParams_Dialog` |
| `P05 Running` | 返回 | `P04_PipelineHistory_Desktop` |
| `P05 Running` | 取消 | `O04_ConfirmCancelRun` |
| `P05 Running` | 查看全程日志 / 阶段日志 / 任务日志 | `O02_LogDrawer_Right` |
| `P05 WaitingApproval` | 通过 | `P05 RunDetail` 成功态或展示 Toast |
| `O01` | 取消 | Close Overlay |
| `O01` | 运行 | `P05_RunDetail_Running` |
| `O03` | 取消 | Close Overlay |
| `O03` | 删除 | `P01`（列表刷新态） |
| `O04` | 继续运行 | Close Overlay |
| `O04` | 取消运行 | `P05_RunDetail` 已取消态 |
| `O05` | 取消 | Close Overlay |
| `O05` | 放弃 | `P01_PipelineList_Desktop` |
| `O02` | X / 遮罩 / Esc | Close Overlay |

## 6. 重新生成截图

macOS + Chrome：

```bash
cd docs/pipeline/prototype
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
BASE="file://$PWD/index.html"
"$CHROME" --headless=new --disable-gpu --hide-scrollbars \
  --force-device-scale-factor=2 --window-size=1440,900 --virtual-time-budget=3000 \
  --screenshot=shots/01-list.png "$BASE?capture=1#/list"
```

其余画板只需替换 `$BASE` 后的查询参数和 hash，对应关系见第 2.2 节与第 3 节。

## 7. 已知简化

- 数据是静态示例，搜索、状态筛选、分页只在当前页做客户端演示。
- 编辑器的拖拽排序、表单双向绑定只做了可点击演示，没有真实持久化。
- 任务市场、任务类型详情不在本次原型范围；凭证页自 v0.2 起纳入原型。
- 图片方式导入 Figma 得到的是位图，不是可编辑组件；需要图层级编辑请走 4.2 的 html.to.design。
- Figma 原生不支持 Markdown 原型；规格文档用于 AI 原型工具或手工搭建，图片和 HTML 才是可直接使用的交付物。

## 8. 评审记录

| 版本 | 日期 | 评审人 | 结论 | 说明 |
|------|------|--------|------|------|
| v0.2 | 2026-10-18 | 用户确认 | **已冻结** | 页面调教第 1 批；用户确认“开始实施”，作为代码实现依据 |
| v0.3 | 2026-10-18 | 待产品 / 前端 / 测试 | **待评审** | 编辑器重构与弹框 Kite 化；评审通过后冻结 |
| v0.4 | 2026-10-18 | 待产品 / 前端 / 测试 | **待评审** | 编辑页任务名称/类型只读；评审通过后冻结 |
| v0.5 | 2026-10-18 | 待产品 / 前端 / 测试 | **待评审** | 克隆任务隐藏高级设置；评审通过后冻结 |
| v0.6 | 2026-10-18 | 待产品 / 前端 / 测试 | **待评审** | 移除列表历史入口；评审通过后冻结 |
| v0.7 | 2026-10-18 | 待产品 / 前端 / 测试 | **待评审** | 补齐编辑器阶段间箭头；评审通过后冻结 |
| v0.8 | 2026-10-18 | 用户确认 | **已冻结** | 用户确认“开始实施”，作为代码实现依据 |
| v0.9 | 2026-10-18 | 待产品 / 前端 / 测试 | **待评审** | 详情头部扁平化；纯样式修复，附前后截图即可 |
| v0.10 | 2026-10-18 | 待产品 / 前端 / 测试 | **待评审** | 移除详情“阶段”指标 |
| v0.11 | 2026-10-18 | 待产品 / 前端 / 测试 | **待评审** | 历史页签隐藏指标行 |
