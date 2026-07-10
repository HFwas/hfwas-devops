# Step 08：Phase D — Transition Condition（QuerySpec 可见性）

> 状态：已完成  
> 日期：2026-07-09  
> 路线图：[pm-evolution-roadmap.md](../pm-evolution-roadmap.md) Step 8  
> 对应对比文档：[pm-jira-comparison.md](../pm-jira-comparison.md) §5.1 Phase D  
> 原则：绿野项目，不做旧数据双写兼容

---

## 1. 动机与范围

### 1.1 动机

Jira Condition 控制「这条流转对当前用户/事项是否可见」。Step 3 已实体化 Transition；本步用已有 **QuerySpec** 协议表达条件，在 `allowed` 与 `transition` 两侧统一评估。

### 1.2 范围内

| 层 | 内容 |
|----|------|
| 模型 | `Transition.conditions`：`logic` + `conditions[]` + `groups[]`（与 QuerySpec 同形，无分页/排序） |
| 后端 | 内存匹配事项字段；`allowed` 按事项过滤；`transition` 再校验；允许同 from→to 多条边 |
| 前端 | 规则 Drawer 嵌入条件编辑器；看板/详情 `allowed` 传 `workItemId` |
| 文档 | 本文件 + 对比文档 + pm-api + 路线图 |

### 1.3 非目标

- 项目角色 / RBAC Condition（Step 7）
- 关联事项 / 子任务全部完成类条件
- 自定义脚本 Condition / SPI（Step 12）
- 可视化设计器（Step 10）

---

## 2. 数据模型

```text
Transition
  ├── id, name, toStatus
  ├── conditions: TransitionConditionSpec   // NEW；空 = 始终可见
  ├── validators[]
  └── postFunctions[]

TransitionConditionSpec
  ├── logic: AND | OR          // 默认 AND
  ├── conditions: QueryCondition[]
  └── groups: QueryConditionGroup[]
```

仍存于 `pm_status_definition.transitions` JSON，**无需新列**。

语义：

- `conditions` / `groups` 皆空 → 始终可见、可执行
- 非空 → 对当前事项求值；不满足则 `allowed` 不返回该边，`transition` 拒绝
- 同 from 可有多条指向同一 `toStatus` 的边（靠不同 Condition / 名称区分）
- 特殊值 `__current_user__`：在 `assignee_id` / `reporter_id`（及同类用户字段）的 EQ/NE/IN/NOT_IN 中解析为当前登录用户 ID

执行顺序（相对 Step 2）：

```text
validateTransition(path + id)
→ evaluate conditions（不满足则 4xx）
→ apply submitted fields
→ validators
→ UPDATE status
→ post-functions
```

---

## 3. API

| 方法 | 路径 | 变更 |
|------|------|------|
| POST | `/pm/status/workflow/allowed` | 请求增加可选 `workItemId`；有事项时按 Condition 过滤 |
| POST | `/pm/work-items/{id}/transition` | 执行前评估 Condition |
| POST | `/pm/status/workflow/save` | 校验 Condition 字段/运算符；允许同 toStatus 多边 |

无 `workItemId` 时：仅返回 **无 Condition** 的流转（避免把受限边暴露给无上下文调用）。

---

## 4. 前端 UI

- **配置**：`PmTransitionRuleDrawer` 增加「可见条件」区块（复用 `PmConditionGroup`）
- **快捷**：一键「仅负责人可见」→ `assignee_id EQ __current_user__`
- **矩阵**：单元格仍表示「至少一条边」；规则 Tab 列出全部边（含条件摘要）
- **运行时**：看板 / 详情调用 `allowed` 时传当前 `workItemId`

---

## 5. 验收标准

1. 配置「priority = high 才可见紧急关闭」后，低优先级事项的 `allowed` 不含该边；高优先级含有。
2. 直接调用 `transition` 绕过 UI 时，不满足 Condition 返回错误。
3. 「仅负责人」：非负责人看不到该流转；负责人可见并可执行。
4. 同 from→to 可保存两条不同名称/条件的边；方案导入导出保留 `conditions`。
5. 空 Condition 行为与 Step 3 一致（始终可见）。

---

## 6. 风险

| 风险 | 缓解 |
|------|------|
| 列表/看板批量 allowed 无事项上下文 | API 强制按 workItemId 过滤；调用方逐事项请求 |
| 详情侧栏按 toStatus 选边 | 多条同目标时取 `allowed` 中第一条匹配 |
| QueryEngine 为 SQL | Condition 用内存匹配，避免为单事项拼 SQL |
| 角色条件缺失 | 文档标明待 Step 7 |

---

## 7. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-07-09 | 初版：QuerySpec 可见性 + `__current_user__` |
| 2026-07-09 | 前后端落地完成，标记已完成 |
