# PM 工作台前端改版 — AI 协作进度日志

> 日期：2026-10-19
> 版本：v1.0
> 说明：本文件为追加式日志。`版本` 表示格式版本，不随每轮追加递增；每轮记录见文末。

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v1.0 | 2026-10-19 | 建立日志，定义每轮记录格式 |

---

## 第 1 轮 · 2026-10-19 21:35 · 完成 PM 工作台高保真原型

- **状态**：待评审
- **触发**：用户要求参考 Jira / PingCode 等成熟项目管理前端，封装统一的列表、搜索、展示、分页组件，先出一版看效果
- **目标**：在不写生产代码的前提下，产出可点击的高保真原型、原型规格与契约，覆盖列表/搜索/筛选/展示/分页/批量及异常状态
- **读取**：`AGENTS.md`、`docs/frontend/prototype-first-standard.md`、`docs/frontend/shared-ui-patterns.md`、`docs/pm/pm-design.md`、`docs/pm/pm-api.md`、`frontend/src/modules/pm/*`、`frontend/src/components/console/{DataTable,Pagination,PageHeader}.tsx`、`frontend/src/styles/themes/default.css`、Jira Issue Navigator / PingCode 列表视图公开资料

### 做了什么

1. 调研 Jira Issue Navigator、PingCode 列表视图与 Linear 的列表工作台模式，归纳出视图页签、搜索、条件 chip、列配置、批量栏、分页六类统一能力。
2. 对比现有 PM 前端，确认当前只有单关键词搜索、固定 5 列、基础分页，缺少保存视图、列配置、批量操作与快速预览。
3. 新增功能设计与原型规格（机器可读 + 人读）。
4. 生成单文件可点击原型 `prototype/index.html`：8 个页面状态、5 个弹层，hash 路由 + 状态参数，亮暗主题。
5. 用 Chrome headless 输出 17 张 1440×900 @2x 画板。
6. 用 Playwright 在本机 HTTP 服务上逐项验证交互。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `docs/superpowers/features/20261019-pm-workbench-redesign/design.md` | 新增 | 功能设计：背景、目标、信息架构、统一组件、交互、状态矩阵、接口、验收 |
| `docs/superpowers/features/20261019-pm-workbench-redesign/prototype/prototype-spec.json` | 新增 | 机器可读原型规格 |
| `docs/superpowers/features/20261019-pm-workbench-redesign/prototype/prototype-spec.md` | 新增 | 人读规格 |
| `docs/superpowers/features/20261019-pm-workbench-redesign/prototype/index.html` | 新增 | L2 单文件可点击原型 |
| `docs/superpowers/features/20261019-pm-workbench-redesign/prototype/README.md` | 新增 | 路由、状态参数、画板清单、评审记录 |
| `docs/superpowers/features/20261019-pm-workbench-redesign/prototype/shots/*.png` | 新增 | 17 张画板 |
| `docs/superpowers/features/20261019-pm-workbench-redesign/prototype-contract.md` | 新增 | 原型契约（A2，待冻结） |
| `docs/superpowers/features/20261019-pm-workbench-redesign/progress.md` | 新增 | 本日志 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| 画板生成 | Chrome headless `--force-device-scale-factor=2 --window-size=1440,900` | 通过，17/17 张生成 |
| 控制台错误 | Playwright `console_messages` | 仅 favicon 404，无脚本错误 |
| 搜索 | 输入 `Helm`，等待防抖 | 通过，2 条结果，总数与页签计数同步 |
| 筛选 | 打开筛选 → 应用 | 通过，chip 为「状态 是 进行中」「优先级 是 高」，视图页签出现脏标记 |
| 勾选 | 点击第 1 行复选框 | 通过，URL `sel=1`，批量栏显示 1 项 |
| 批量删除 | 批量删除 → 确认 | 通过，确认框显示 3 项（选中态样例），确认后清空选择 |
| 列配置 | 隐藏「更新时间」列 | 通过，`cols` 参数更新，表格即时变化 |
| 分页 | 点击第 2 页 | 通过，`page=2`，列表数据轮转 |
| 排序 | 点击「优先级」表头 | 通过，`sort=priority:ASC`，首行变为「最高」，箭头高亮 |
| 新建校验 | 空标题提交 → 填写后提交 | 通过，先进入 `state=invalid`，填写后关闭弹层并提示创建成功 |
| 行菜单 | 更多 → 删除 | 通过，打开单条删除确认 |
| 项目搜索 | 输入 `API` | 通过，1 条结果，分页隐藏 |
| 单元测试 | 无（原型为可丢弃规格，不进入构建） | 未执行 |

### 决策与取舍

- **决策**：先交付 L2 可点击原型，不先写生产代码。**理由**：遵循「原型先行」门禁，且用户明确要先看效果。**未选方案**：直接改 `frontend/src/modules/pm`。
- **决策**：技术栈按 React 19 + Tailwind v4 + shadcn/ui 设计，忽略 `docs/pm/pm-design.md` 中过时的 Vue 3 + Naive UI 描述。**理由**：`AGENTS.md` v0.24 与现有 `frontend/` 均为 React 轨。
- **决策**：组件采用「扩展现有 `DataTable` / `Pagination`」而非重写。**理由**：容器、流水线、接口测试等列表已依赖这两个组件，必须保持向后兼容。
- **决策**：本轮不写 `test-cases.md` 与 `changes.md`。**理由**：规范要求原型冻结后才进入测试用例与编码；待评审冻结后立即补。**未选方案**：文档一次性全部产出。

### 偏差与假设

- **ASSUMPTION A1**：分页响应可补负责人显示名，当前 `PmWorkItem` 只有 `assigneeId`。
- **ASSUMPTION A2**：`dueDate` 作为系统字段加入列表与查询；现有类型无此字段。
- **ASSUMPTION A3**：批量操作首版逐条调用，后续补批量接口。
- **ASSUMPTION A4**：保存视图接口可用；未就绪时先用 localStorage。
- **ASSUMPTION A5**：跨页选择不做，批量栏注明「已选当前页」。
- 原型分页使用轮转样例数据，不代表真实分页行为。
- 原型中的头像为首字占位，实现时使用真实头像并保留首字降级。

### 未完成 / 风险

- 原型尚未经过产品 / 前端 / 测试三方评审，未冻结。
- 「分组展示」在 `design.md` 中定义但原型未落地，需确认是否纳入首版。
- 负责人显示名、截止时间、保存视图、批量接口四项依赖后端确认。

### 下一步

- **唯一动作**：用户查看 `prototype/index.html` 与画板，确认信息结构、列与交互；确认后冻结 v1.0 并补 `test-cases.md` 与 `changes.md`。
- 需要用户确认：列表默认列是否符合团队习惯；批量操作首版范围；是否保留「分组」能力。
