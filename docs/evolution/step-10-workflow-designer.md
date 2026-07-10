# Step 10：Phase E — 可视化工作流设计器

> 状态：已完成  
> 日期：2026-07-10  
> 路线图：[pm-evolution-roadmap.md](../pm-evolution-roadmap.md) Step 10  
> 对应对比文档：[pm-jira-comparison.md](../pm-jira-comparison.md) §5.1 Phase E  
> 原则：绿野项目，不做旧布局兼容

---

## 1. 动机与范围

### 1.1 动机

矩阵适合快速勾选路径，但难以一眼看清状态流转图。本步用 Vue Flow 提供可编辑有向图，矩阵保留为简易模式。

### 1.2 范围内

| 层 | 内容 |
|----|------|
| 模型 | `StatusDefinition.layoutX` / `layoutY` 持久化节点坐标 |
| 前端 | `PmWorkflowCanvas`：连线增删、拖节点、点边打开规则 Drawer |
| UI | 默认「图编辑」；保留「矩阵」「流转规则」 |
| 文档 | 本文件 + 对比文档 + pm-api + 路线图 |

### 1.3 非目标

- 工作流草稿 / 版本发布
- 画布内联编辑 Condition / Validator（仍走 Drawer）
- 从工具箱拖新状态到画布
- 力导向自动布局（仅无坐标时网格兜底）

---

## 2. 数据模型

```text
pm_status_definition
  ├── …既有字段
  ├── layout_x REAL     // NEW
  └── layout_y REAL     // NEW
```

VO / 方案导出同步携带 `layoutX` / `layoutY`。无坐标时前端按 `sortOrder` 网格兜底；`__any__` 单独放底行。

---

## 3. 前端 UI

- **图编辑（默认）**：Vue Flow 节点 = 状态，边 = Transition；拖动手柄连线；点边 → `PmTransitionRuleDrawer`；双击节点 → 状态弹窗
- **矩阵**：原简易勾选模式
- **流转规则**：列表编辑（含同路径多边）

仍需点「保存配置」才持久化。

---

## 4. 验收

1. 默认图编辑：节点/边与工作流一致；无坐标时自动排布可读。
2. 连线新增、删边移除；点边配置规则后标签更新。
3. 拖节点保存后刷新位置保留；方案 I/O 含坐标。
4. 矩阵与图编辑共用同一 `statuses`，互相同步。

---

## 5. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-07-10 | 初版并落地：Vue Flow + layoutX/Y |
