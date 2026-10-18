# 流水线页面调教 · 第 1 批 —— 测试用例

> 日期：2026-10-18
> 版本：v0.3
> 状态：长期维护；需求变更时先改本文件，再改代码
> 对应设计：[`design.md`](./design.md) v0.2

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-18 | 初版：覆盖 UX-01/02/06/08/09/19/20 的正常、边界与异常路径 |
| v0.2 | 2026-10-18 | 按独立校验修正：项目搜索仍为 `?keyword=`；自动化位置改为真实测试名；补“共 N 个任务类型”与名称链接点击断言 |
| v0.3 | 2026-10-18 | 按用户要求移除列表 `⋯` 菜单中的“历史”入口；TC-UX08-04 预期改为编辑/删除 |

---

## 0. 约定

- 自动化位置写作 `文件::测试名`；本轮新增或修改的测试文件均在 `frontend/src/`。
- 手工验证步骤用于无法稳定自动化的交互（如真实浏览器返回栈）。
- 所有用例默认在 `frontend/` 下执行：`npm test`。

## 1. UX-01 全局搜索

| 编号 | 前置条件 | 步骤 | 输入 | 预期结果 | 自动化位置 |
|------|----------|------|------|----------|------------|
| TC-UX01-01 | 位于 `/pipeline/pipelines` | 读取顶栏搜索框 | — | placeholder 为“搜索流水线名称”，aria-label 为“搜索流水线” | `shared/console/shellSearch.test.ts::pipeline 作用域返回流水线搜索文案` |
| TC-UX01-02 | 位于 `/pipeline/pipelines` | 输入关键词并提交 | `后端` | 跳转 `/pipeline/pipelines?q=%E5%90%8E%E7%AB%AF` | `components/console/AppShell.test.tsx::pipeline search routes to pipeline list` |
| TC-UX01-03 | 位于 `/pm/projects` | 读取搜索文案并提交 | `项目` | 仍为“搜索项目名称或编码”，跳 `/pm/projects?keyword=` | `components/console/AppShell.test.tsx::project search keeps existing behavior` |
| TC-UX01-04 | 位于 `/pipeline/pipelines` | 不输入内容直接提交 | 空 | 不跳转，URL 不变 | `components/console/AppShell.test.tsx::empty search does nothing` |

## 2. UX-02 运行详情返回

| 编号 | 前置条件 | 步骤 | 输入 | 预期结果 | 自动化位置 |
|------|----------|------|------|----------|------------|
| TC-UX02-01 | 从列表进入运行详情（历史栈有上一页） | 点击“返回” | — | 回到进入前页面（列表） | `modules/pipeline/pages/PipelineRunDetailPage.test.tsx::TC-UX02-01: back uses history when available` |
| TC-UX02-02 | 直接打开运行详情（无应用内历史） | 点击“返回” | — | 跳 `/pipeline/pipelines/:pipelineId/history` | `modules/pipeline/pages/PipelineRunDetailPage.test.tsx::TC-UX02-02: back falls back to run history` |
| TC-UX02-03 | 从运行历史进入运行详情 | 点击“返回” | — | 回到运行历史 | `modules/pipeline/pages/PipelineRunDetailPage.test.tsx::TC-UX02-03: back returns to history source` |
| TC-UX02-04 | 打开运行详情 | 打开并关闭日志抽屉后再点“返回” | — | 返回目标不受抽屉影响 | 手工验证：列表 → 运行详情 → 打开任务日志 → 关闭 → 返回，应回列表 |

## 3. UX-06 凭证表单错误

| 编号 | 前置条件 | 步骤 | 输入 | 预期结果 | 自动化位置 |
|------|----------|------|------|----------|------------|
| TC-UX06-01 | 凭证列表已加载 | 打开“新建凭证”，名称留空点“保存” | 名称=空 | 名称下方显示“请填写名称”；不调用保存接口 | `modules/pipeline/pages/CredentialPages.test.tsx::PIPE-147: blocks create when name is empty` |
| TC-UX06-02 | 同上 | 校验失败后检查焦点 | — | 焦点在名称输入框 | `modules/pipeline/pages/CredentialPages.test.tsx::PIPE-147: blocks create when name is empty` |
| TC-UX06-03 | 同上 | 名称为空时检查无障碍属性 | — | 输入框 `aria-invalid=true` 且 `aria-describedby` 指向错误文本 | `modules/pipeline/pages/CredentialPages.test.tsx::PIPE-147: blocks create when name is empty` |
| TC-UX06-04 | 名称已填、用户名/密钥已填 | 点“保存” | 合法值 | 调用保存接口（回归） | `modules/pipeline/pages/CredentialPages.test.tsx::PIPE-147: creates credential from dialog` |
| TC-UX06-05 | 名称为空 | 先提交失败，再输入名称后提交 | 名称=`git-dev` | 错误消失并按成功路径保存 | `modules/pipeline/pages/CredentialPages.test.tsx::PIPE-147: clears error after name is provided` |

## 4. UX-08 列表整行可点与操作收敛

| 编号 | 前置条件 | 步骤 | 输入 | 预期结果 | 自动化位置 |
|------|----------|------|------|----------|------------|
| TC-UX08-01 | 列表有数据 | 点击行内非控件区域 | — | 进入 `/pipeline/pipelines/:id` | `modules/pipeline/pages/PipelineListPage.test.tsx::TC-UX08-01: row click opens pipeline detail` |
| TC-UX08-02 | 列表有数据 | 点击名称链接 | — | 进入详情（链接可聚焦、可点击） | `modules/pipeline/pages/PipelineListPage.test.tsx::TC-UX08-02: name link remains keyboard accessible` |
| TC-UX08-03 | 列表有数据 | 点击行内“运行”按钮 | — | 启动本次运行并进入运行详情；不进入流水线详情页（不触发整行跳转） | `modules/pipeline/pages/PipelineListPage.test.tsx::TC-UX08-03: clicking run starts a run instead of opening the pipeline row` |
| TC-UX08-04 | 列表有数据 | 点击 `⋯` | — | 菜单显示“编辑 / 删除”，不再提供“历史”（运行历史从详情页页签进入） | `modules/pipeline/pages/PipelineListPage.test.tsx::TC-UX08-04: row menu exposes edit and delete` |
| TC-UX08-05 | 菜单已打开 | 点击“删除”并确认 | — | 调用删除接口（回归） | `modules/pipeline/pages/PipelineListPage.test.tsx::PIPE-019: deletes a pipeline after confirmation` |
| TC-UX08-06 | 列表有数据 | 检查操作列常驻内容 | — | 只有“运行”和 `⋯`，编辑/历史/删除不在行内常驻 | `modules/pipeline/pages/PipelineListPage.test.tsx::shows a dense list with mono name, repo, branch, run and overflow actions` |

## 5. UX-09 触发方式

| 编号 | 前置条件 | 步骤 | 输入 | 预期结果 | 自动化位置 |
|------|----------|------|------|----------|------------|
| TC-UX09-01 | 运行详情返回 `trigger=MANUAL` | 查看“触发”字段 | — | 显示“手动”，不出现 `MANUAL` | `modules/pipeline/pages/PipelineRunDetailPage.test.tsx::TC-UX09-01: renders localized trigger label` |
| TC-UX09-02 | 运行详情返回未知触发值 `API` | 查看“触发”字段 | — | 显示原文 `API` | `modules/pipeline/runHistory.test.ts::labels triggers and keeps the default 7 day window` |

## 6. UX-19 侧栏分组

| 编号 | 前置条件 | 步骤 | 输入 | 预期结果 | 自动化位置 |
|------|----------|------|------|----------|------------|
| TC-UX19-01 | 进入任意流水线页面 | 查看侧栏 | — | “流水线”在主组；“配置”组内含“任务市场 / 凭证” | `shared/console/navigation.test.ts::pipeline menu groups work objects and config` |
| TC-UX19-02 | 同上 | 展开菜单项序列 | — | 仍为 `流水线 / 任务市场 / 凭证` | `shared/console/navigation.test.ts::shows only the current product menu` |
| TC-UX19-03 | 任意页面 | 侧栏折叠为图标态 | — | 三个入口图标与 tooltip 正常 | 手工验证：点击侧栏折叠按钮，悬停任务市场/凭证 |

## 7. UX-20 术语

| 编号 | 前置条件 | 步骤 | 输入 | 预期结果 | 自动化位置 |
|------|----------|------|------|----------|------------|
| TC-UX20-01 | 任务市场已加载 | 查看页头统计、空态、搜索框 | — | 出现“任务类型”表述，不再显示“共 N 种任务 / 没有匹配的任务” | `modules/pipeline/pages/TaskKindPages.test.tsx::renders cards, filters by group, and shows toggle only for admins` |
| TC-UX20-02 | 搜索无结果 | 输入不存在的关键词 | `不存在` | 显示“没有匹配的任务类型” | `modules/pipeline/pages/TaskKindPages.test.tsx::PIPE-149: shows empty task market state` |

## 8. 回归与全局检查

| 编号 | 检查 | 命令 | 预期 |
|------|------|------|------|
| TC-REG-01 | 单元/组件测试 | `cd frontend && npm test` | 全部通过 |
| TC-REG-02 | 类型检查 | `cd frontend && npx tsc --noEmit` | 无错误 |
| TC-REG-03 | 生产构建 | `cd frontend && npm run build` | 成功 |
| TC-REG-04 | 现有 URL 行为 | 手工打开 `/pipeline/pipelines?q=x&page=2` | 列表仍按关键词和页码查询 |
