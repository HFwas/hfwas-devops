# 接口管理：HTTP 测试审查与改造

对照 Postman 工作台、Hoppscotch 请求编排，以及现有 `api-test` 壳层（`ApiTestShell` / `RequestWorkspace`）。本文是审查结论 + 实施清单。

**结论：壳层像 Postman，编排不像 Hoppscotch。先改请求编排，不要再扩侧栏。**

集合树、多 Tab、Send/Save、环境 `{{var}}`、History、cURL 导入、结构化断言、后端代理已经能用。真正的差距是单次 HTTP 调试：Params 不能禁行/重复键，Body 只是 Content-Type + textarea，Auth / Settings 仍是 Coming Soon，超时字段后端有、引擎未真正生效。

效果图：[HTTP 测试审查 canvas](/Users/hfwas/.cursor/projects/Users-hfwas-WebstormProjects-hfwas-devops/canvases/api-test-http-review.canvas.tsx)

---

## 产品取舍

| 学谁 | 学什么 | 不学什么 |
|------|--------|----------|
| Postman | 集合优先、Tab、History、环境、Save、集合 Run | OAuth2 全家桶、Visualize、独立深色侧栏主题 |
| Hoppscotch | 更少 Tab、永远可编辑的 KV 行、Body 分模式、扁平响应区 | 纯浏览器直连、无断言 |
| 我们自己 | 结构化断言 + 变量提取（比 Postman 脚本更适合平台） | 不要改成纯 JS 测试 |

主 Tab 只保留 HTTP 本体：`Query` · `Path` · `Headers` · `Auth` · `Body`。Scripts / Tests / Docs / Settings 收到 **More**。响应区打平为 Body / Headers / Cookies / Tests。

绿野项目：草稿结构直接改，不做 `Record` 双写兼容。

---

## 能力对照

| 能力 | 改造前 | Postman | Hoppscotch | 处理 |
|------|--------|---------|------------|------|
| 集合树 + 多 Tab + Send/Save | 有 | 有 | 有 | 保留 |
| 环境 `{{var}}` + 后端代理 | 有 | 有 | 浏览器/拦截器 | 保留；URL 下展示解析预览 |
| History / cURL 导入 | 有 | 有 | 有 | 补 **复制为 cURL** |
| Query / Header KV | `Record` 表格 + 添加 | 禁用行 + 空行 | 禁用行 + 空行 | **P0 重写** |
| Path 变量 / URL 同步 | 加载时丢弃 path | 有 | 有 | **P1** |
| Auth Bearer/Basic/API Key | Coming Soon | 有 | 有 | **P0** |
| Body 分模式 | 仅 textarea | 有 | 有 | **P0** |
| 超时 / 跟随重定向 | DTO 有，UI 无，引擎未用 timeout | 有 | 有 | **P1**，前后端一起修 |
| Copy as cURL / Ctrl+Enter | 无 | 有 | 有 | **P1** |
| 结构化断言 + 提取 | 有 | 脚本为主 | 弱 | **保留** |
| 响应 Pretty/Raw/Preview | 有 | 有 | 有 | 补 Cookies、响应体搜索 |
| 集合 Run | 有 | 有 | 弱 | 保留 |
| GraphQL / OAuth2 / Visualize | 无 | 有 | 部分 | **本期不做** |

---

## P0 — HTTP 测试不能缺

### P0-1. KV 编辑器

- **现象：** `KeyValueEditor` 用 `Record<string,string>`。空键互相覆盖，不能重复 Header，没有行级启用，必须点「添加」。
- **位置：** `frontend/src/modules/api-test/shared/components/KeyValueEditor.vue`；`RequestDraft.headers` / `queryParams`。
- **修复：**
  1. 草稿改为数组 `{ enabled, key, value }`。
  2. 底部永远留一行空行，打字即增行。
  3. 未勾选的行不发送。
  4. 发送时转 `Record`（同名键后者覆盖；HTTP 引擎仍是 Map）。

### P0-2. Body 分模式

- **现象：** Content-Type 下拉 + textarea。选 `multipart/form-data` 仍是纯文本。
- **位置：** `RequestWorkspace.vue` Body pane。
- **修复：** `none` / `json` / `raw` / `form-data` / `urlencoded`。JSON 用等宽编辑 + 格式化；form / urlencoded 复用 KV 表。form-data 文本 part 编成 multipart body（文件上传本期不做，type=file 行跳过并提示）。GET 默认 none。

### P0-3. Auth

- **现象：** 请求 Tab 与集合 Overview 都是 Coming Soon。只能手写 Header。
- **位置：** `RequestWorkspace.vue` Auth pane。
- **修复：** None / Bearer / Basic / API Key。发送时写入 Header（API Key 可选 Query）。Headers 里展示只读的生成行。集合级继承本期不做（Overview Authorization 仍可占位）。

---

## P1 — 体验与已有后端能力

### P1-1. Path 变量 + URL ↔ Query 同步

- **现象：** `loadDefinitionIntoTab` 忽略 `paramType=path`。URL 与 Query 表不同步。
- **修复：** 从 URL 抽出 `:id` / `{id}`（不含 `{{env}}`）；发送前替换。Query 表勾选行写回 URL search；改 URL 时回填启用行，禁用行保留。

### P1-2. Settings：超时 / 跟随重定向

- **现象：** `ApiDebugExecuteDTO.timeoutMs` / `followRedirects` 已有；`HttpDebugEngine` 建了 `SimpleClientHttpRequestFactory` 却没用，RestClient 在构造时写死。
- **修复：** UI 放进 More → Settings；每次 execute 按请求建 RestClient；默认 timeout 30000ms、followRedirects true。

### P1-3. 变量预览

- **现象：** `VariablePreview.vue` 未挂到请求页。
- **修复：** URL 栏下方展示当前环境解析后的 URL（secret 仍打码）。

### P1-4. 复制为 cURL + Ctrl+Enter

- **修复：** Save 旁「cURL」写入剪贴板；工作台聚焦时 Ctrl/Cmd+Enter 发送。

### P1-5. 响应区打平

- **现象：** 外层「响应 / Visualize(空)」，内层再套响应体/头/断言。
- **修复：** 去掉 Visualize 外套。响应 Tab：Body / Headers / Cookies / Tests。Cookies 从响应头 `Set-Cookie` 解析。Body 内保留 Pretty/Raw/Preview，增加关键字过滤。断言结果放 Tests。

### P1-6. 请求 Tab 收束

- **修复：** 主区 Query / Path / Headers / Auth / Body / More。More 内 Scripts、Tests（断言+提取）、Docs、Settings。

### P1-7. 删除遗留调试页

- **现象：** `DebugTab.vue` / `RequestEditor.vue` 不在壳层路由。
- **修复：** 删除这两个文件，避免两套编辑器。

---

## 非目标（明确不做）

- OAuth2 / AWS SigV4
- GraphQL / binary 上传落地
- Visualize
- 集合级 Auth 继承、集合 Variables
- 后端 Header/Query 改成 List（重复键发送仍 last-win）
- 响应 Header 搜索以外的高级过滤

---

## 信息架构

```
┌ Collections|History ─┬─────────────────────────────────────────┐
│                      │  集合 › 文件夹 › [请求名]                  │
│                      │  METHOD │ URL {{var}} │ Send │ Save │ cURL │
│                      │  解析预览: https://dev.example/v1/users    │
│                      │  Query | Path | Headers | Auth | Body | More │
│                      │  ────────────── 分割 ──────────────       │
│                      │  201 · 124ms · 2.1KB                      │
│                      │  Body | Headers | Cookies | Tests         │
└──────────────────────┴─────────────────────────────────────────┘
```

---

## 实施状态

- [x] P0-1 KV 数组编辑器
- [x] P0-2 Body 分模式
- [x] P0-3 Auth
- [x] P1-1 Path + URL/Query 同步
- [x] P1-2 超时 / 重定向（UI + 引擎）
- [x] P1-3 URL 变量预览
- [x] P1-4 cURL 导出 + Ctrl+Enter
- [x] P1-5 响应区打平
- [x] P1-6 Tab 收束
- [x] P1-7 删除 DebugTab / RequestEditor

（勾选随代码落地更新。）
