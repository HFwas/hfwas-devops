# AI 用户走查试点设计

> 日期：2026-10-18
> 版本：v0.3
> 状态：已完成
> 数据来源：`docs/pipeline/prototype/`、`frontend/package.json`、`docs/product/05-testing/test-spec.md`、`docs/frontend/prototype-first-standard.md`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-18 | 初版：确定原型优先的 AI 走查试点范围、工具链、5 条 E2E、persona/rubric 与验收标准 |
| v0.2 | 2026-10-18 | 按独立校验修订：spec 数量表述、失败时保留证据的口径、文档状态改为「待评审」 |
| v0.3 | 2026-10-18 | 独立复审通过，状态更新为「已完成」 |

---

## 1. 背景与目标

当前仓库的前端测试以 Vitest 组件测试为主，端到端链路仍为手工（见 `docs/product/05-testing/test-spec.md` 第 1 节）。流水线已有冻结的高保真可点击原型 `docs/pipeline/prototype/index.html`，支持 hash 路由、状态参数和主要弹层，适合先验证“AI 能否像真实用户一样操作并给出有证据的 UX 评审”。

本试点要回答三个问题：

1. AI 能否稳定地按用户任务操作页面，并留下可复查的操作轨迹？
2. AI 发现的“卡点”是否能落到具体页面元素、文案和状态，而不是泛泛而谈？
3. 这套方法能否沉淀为仓库可复用的标准与脚本，而不是一次性演示？

### 目标

- 在 `frontend/` 安装并配置 Playwright。
- 对冻结原型编写 5 条流水线 E2E，覆盖列表、编排、运行、日志、审批/取消。
- 提供 Playwright MCP 配置，支持后续 agent 探索式走查。
- 定义 persona、任务脚本、评分 rubric 与证据格式。
- 产出第一份 AI 走查报告，包含带证据的问题清单。

### 非目标

- 本轮不启动完整后端 + Keycloak + k3s 栈，不做真实接口/权限/持久化 E2E。
- 本轮不接入 CI 门禁，不把 LLM 评分作为合并条件。
- 不让 AI 自动修改页面或自动提交代码。
- 不覆盖原型未包含的凭证页、任务市场页（原型中仅为提示占位）。

## 2. 范围

### 2.1 测试对象

| 环境 | 地址 | 用途 |
|------|------|------|
| 冻结原型 | `docs/pipeline/prototype/index.html` | 本轮 5 条 E2E 与首次 AI 走查 |
| 真实前端 | `frontend/` + 后端 + Keycloak | 下一阶段；本轮只保留配置入口 |

原型路由：

| 页面 | Hash |
|------|------|
| 列表 | `#/list` |
| 详情 | `#/detail/1` |
| 新建 | `#/editor/new` |
| 编辑 | `#/editor/1` |
| 运行历史 | `#/history/1` |
| 运行详情 | `#/run/1/128` |

状态参数：`state=empty|loading|invalid|waiting|failed|succeeded`；弹层参数：`overlay=run-params|delete|cancel|discard|log`。

### 2.2 5 条 E2E

| 编号 | 场景 | 关键断言 |
|------|------|----------|
| E2E-PIPE-01 | 列表浏览、搜索、状态筛选 | 行数变化、筛选提示、空结果 |
| E2E-PIPE-02 | 新建流水线编排并保存 | 编辑器标题、添加阶段提示、任务面板、保存提示 |
| E2E-PIPE-03 | 运行参数弹窗并启动 | 弹窗必填项、提交后进入运行详情 |
| E2E-PIPE-04 | 运行详情查看全程/阶段/任务日志 | 日志抽屉标题、日志内容、关闭方式 |
| E2E-PIPE-05 | 待审批通过、取消运行、删除确认 | 通过提示、取消确认、删除危险操作确认 |

## 3. 技术方案

```text
Playwright Test
  ├─ webServer: Node 静态服务器（e2e/support/prototype-server.mjs）
  ├─ 1 个 spec 文件、5 条 test（e2e/pipeline-prototype.spec.ts）
  ├─ 失败时保留 trace / video / screenshot；走查脚本额外全量截图
  └─ walkthrough 脚本（e2e/walkthrough.mjs）
        └─ 产出 actions.json + 截图 + trace，供 AI 评审

Playwright MCP
  └─ .mcp.json 配置 npx @playwright/mcp@latest
        └─ 供 Claude Code / 其它 MCP 客户端做探索式走查
```

### 3.1 为什么先测原型

- 原型零依赖、无需登录、无需后端，当前环境可稳定复现；
- 原型覆盖了本轮要验证的主要交互与状态；
- 先用低成本环境验证“AI 走查方法”本身，再投入真实栈。

### 3.2 后续如何切到真实前端

- 新增 `E2E_BASE_URL` 与 `E2E_STORAGE_STATE` 环境变量；
- 用测试 Keycloak realm 或 `storageState` 复用登录态；
- 启动 docker-compose 后端 + 前端；
- 复用同一套 persona、任务脚本和 rubric，只替换 `baseURL` 与测试数据准备。

## 4. 交付物

| 交付物 | 位置 |
|--------|------|
| Playwright 配置 | `frontend/playwright.config.ts` |
| 原型静态服务器 | `frontend/e2e/support/prototype-server.mjs` |
| 5 条 E2E | `frontend/e2e/pipeline-prototype.spec.ts` |
| 走查脚本 | `frontend/e2e/walkthrough.mjs` |
| MCP 配置 | `.mcp.json` |
| persona / 任务 / rubric 标准 | `docs/frontend/ai-ux-review-standard.md` |
| 第一份走查报告 | `docs/superpowers/features/20261018-ai-ux-walkthrough-pilot/walkthrough-report.md` |
| 证据 | `frontend/test-results/pilot/` |

## 5. 异常与边界

| 场景 | 处理 |
|------|------|
| Playwright 浏览器下载失败 | 走本机代理 `127.0.0.1:7890` 重试 |
| 端口 8099 被占用 | 配置 `PLAYWRIGHT_PROTOTYPE_PORT` 或复用已有服务 |
| 原型交互变化 | 以 `data-go` / `data-action` / 可见文案定位，避免依赖易变结构 |
| MCP 当前会话未加载 | 记录为“需重启会话生效”，不伪造 MCP 调用结果 |
| 真实后端不可用 | 本轮不阻塞；报告中明确“原型结论，不代表后端真实性” |

## 6. 验收标准

| 编号 | 验收项 |
|------|--------|
| AC-01 | `npm run test:e2e` 在 `frontend/` 下 5 条用例全部通过 |
| AC-02 | 失败时自动保留 trace、截图、视频 |
| AC-03 | `npx playwright show-trace` 可打开 trace |
| AC-04 | `.mcp.json` 可被 MCP 客户端解析，Playwright MCP 可启动 |
| AC-05 | persona、任务脚本、rubric、证据格式文档齐全 |
| AC-06 | 走查报告至少包含 5 条带证据的发现，并标注严重级与建议 |
| AC-07 | 不修改任何产品页面代码；仅新增测试、配置与文档 |

## 7. 风险

| 风险 | 影响 | 缓解 |
|------|------|------|
| 原型与真实实现存在差异 | 结论不能直接等同于生产 | 报告注明范围；下一阶段跑真实栈 |
| LLM 评审主观 | 结论不稳定 | 固定 rubric、要求证据、多 persona、人工复核 |
| 原型使用静态数据 | 无法验证接口与权限 | E2E 只验证前端交互；真实链路留给下一阶段 |
| 测试选择器脆弱 | 原型改版后失败 | 优先用 `data-*` 与可见文案；失败时保留 trace |
| Node 版本告警 | 依赖要求 Node 22，当前 20 | 本轮 Playwright 可运行；在 README 记录版本要求 |
