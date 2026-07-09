# Step 01：工作流 Post-function 收口 + 设置页 UI 标准

> 状态：已完成  
> 日期：2026-07-09  
> 路线图：[pm-evolution-roadmap.md](../pm-evolution-roadmap.md) Step 1  
> 对应对比文档：[pm-jira-comparison.md](../pm-jira-comparison.md) §5.1 Phase C

---

## 1. 动机与范围

### 1.1 动机

工作区已具备接近 Jira Post-function 的实现（`transition_rules`、Executor、配置 Drawer），但对比文档仍标为缺失；设置页壳层不统一；方案 Import/Export UI 未挂载。本步收口为可演示闭环，避免半成品堆积。

### 1.2 范围内

| 层 | 内容 |
|----|------|
| 数据 | `pm_status_definition.transition_rules`（JSON）与 `transitions` 双写同步 |
| 后端 | 保存时校验后置函数；执行路径稳定；`post-function-meta`；scheme 含规则 |
| 前端 | 规则 Drawer 打磨；设置页 UI 标准；事项配置页挂载方案导入导出 |
| 文档 | 本文件 + 对比文档 §5.1 + pm-api 工作流章节 |

### 1.3 非目标（留给后续 Step）

- Transition Validator / Condition / 流转名称与 ID
- 可视化工作流设计器
- 项目 RBAC、独立 Webhook URL 配置
- Post-function 插件 SPI（Phase F）

---

## 2. 数据模型

```text
pm_status_definition
  ├── transitions: string[]              // 目标 statusCode（兼容旧客户端）
  └── transition_rules: TransitionRule[] // 权威边配置

TransitionRule
  ├── toStatus: string
  └── postFunctions: TransitionPostFunction[]

TransitionPostFunction
  ├── type: SET_FIELD | NOTIFY_ASSIGNEE | NOTIFY_USER | WEBHOOK
  ├── fieldKey?, value?                  // SET_FIELD
  ├── userId?                            // NOTIFY_USER
  └── title?, content?                   // 通知类模板，支持占位符
```

占位符：`{title}`、`{itemKey}`、`{fromStatus}`、`{toStatus}`。

**WEBHOOK 边界：** 不配置独立 URL；推送到租户已启用的钉钉/飞书渠道（`ExternalNotifyPublisher`）。

---

## 3. API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/pm/status/workflow/get` | 返回 statuses，含 `transitionRules` |
| POST | `/pm/status/workflow/save` | 持久化；非法 type / 缺必填字段 → 4xx |
| POST | `/pm/status/workflow/post-function-meta` | presets + 可写字段 + placeholders |
| POST | `/pm/work-items/{id}/transition` | 流转后执行 post-functions |
| POST | `/pm/work-items`（save） | 若 status 变更，同样执行 post-functions |

执行顺序：显式 `fromStatus` 行规则 + `__any__` 行规则，按配置顺序执行。

---

## 4. 前端 UI

- **状态流转**：矩阵（路径）+ 规则列表 + `PmTransitionRuleDrawer`
- **Drawer**：快捷预设 +「添加动作」空白项；无 emoji；WEBHOOK 文案标明租户渠道
- **事项配置枢纽**：导出 / 导入方案（含 `statusWorkflow.transitionRules`）
- **功能模块等设置页**：对齐 `n-page-header` 壳层（见路线图 §4）

---

## 5. 验收标准

1. 配置「→ 已关闭：SET_FIELD(priority) + NOTIFY_ASSIGNEE」后，看板 `transition` 与详情改状态均生效。
2. 保存含非法 `type` 或缺少 `fieldKey`/`userId` 的规则时，接口返回明确错误。
3. 方案导出 JSON 含 `transitionRules`；导入后规则可还原。
4. 设置页（模块 / 字段 / 类型 / 工作流）壳层与交互模式一致，无 emoji 图标。
5. 对比文档 §5.1 将 Post-function 更新为已有（内置四类，无 SPI）。

---

## 6. 风险

| 风险 | 缓解 |
|------|------|
| 旧库无 `transition_rules` 列 | `PmModuleMigration.ensureTransitionRulesColumn` |
| 仅写 `transitions` 的旧客户端 | `syncTransitionPayload` 合成空规则 |
| WEBHOOK 未配置渠道时静默无效果 | UI 明确说明依赖租户通知渠道 |

---

## 7. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-07-09 | 初版：与路线图 Step 1 同步落地 |
