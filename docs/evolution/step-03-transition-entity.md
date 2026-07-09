# Step 03：Transition 实体化

> 状态：已完成  
> 日期：2026-07-09  
> 路线图：[pm-evolution-roadmap.md](../pm-evolution-roadmap.md) Step 3  
> 原则：绿野项目，不做旧数据双写兼容

---

## 1. 动机与范围

### 1.1 动机

此前边仅用 `toStatus` 标识，无法命名（如「开始处理」），也无法为后续多条同目标边（Condition）铺路。本步将流转升级为独立 Transition 实体。

### 1.2 范围内

| 层 | 内容 |
|----|------|
| 模型 | `Transition`：`id`、`name`、`toStatus`、`validators`、`postFunctions`；`from` = 所属状态行 |
| 存储 | `pm_status_definition.transitions` 存 Transition JSON 数组；**删除** `transition_rules` 列与 string[] 双写 |
| API | `allowed` 返回带名称的流转选项；`transition` / `transition-meta` 以 `transitionId` 为主 |
| 前端 | 规则 Drawer 可编辑名称；看板/详情下拉展示流转名 |

### 1.3 非目标

- 可视化设计器（Step 10）
- 同一 from→to 多条边（留给 Condition）
- 独立 `pm_transition` 表

---

## 2. 数据模型

```text
StatusDefinition
  └── transitions: Transition[]     // 唯一权威

Transition
  ├── id: string                    // UUID
  ├── name: string                  // 显示名，如「开始处理」
  ├── toStatus: string
  ├── validators: TransitionValidator[]
  └── postFunctions: TransitionPostFunction[]
```

`fromStatus` 由所属状态行的 `statusCode`（含 `__any__`）隐含。

空 `name` 保存时默认：`→ {toStatusName}`。

---

## 3. API

| 方法 | 路径 | 变更 |
|------|------|------|
| GET/save workflow | `/pm/status/workflow/*` | statuses[].transitions 为 Transition[] |
| POST allowed | `/pm/status/workflow/allowed` | 返回 `transitions: [{ id, name, toStatus, toStatusName }]` |
| POST transition-meta | `/pm/status/workflow/transition-meta` | 请求 `transitionId`（可附 fromStatus 校验） |
| POST work-items transition | `/pm/work-items/{id}/transition` | `{ transitionId, fields? }` |

---

## 4. 前端

- 矩阵 / 规则列表展示 `name`
- Drawer 顶部编辑流转名称
- 看板「移动」、详情改状态：选项文案用 `name`，提交 `transitionId`

---

## 5. 验收

1. 配置流转并命名后，看板下拉显示自定义名称。
2. 按 `transitionId` 流转成功；校验器 / 后置函数仍生效。
3. 库表仅 `transitions` 一列 JSON；无 `syncTransitionPayload` 双写。
4. 方案导入导出含 id/name。

---

## 6. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-07-09 | 初版：绿野单列 Transition 实体 |
| 2026-07-09 | 前后端落地完成，标记已完成 |
