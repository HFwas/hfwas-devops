# Step 13：事项类型 Scheme + 可增删类型

> 状态：已完成  
> 日期：2026-07-10  
> 路线图：[pm-evolution-roadmap.md](../pm-evolution-roadmap.md) Step 13  
> 对应对比文档：[pm-jira-comparison.md](../pm-jira-comparison.md) §5 / §14.4  
> 原则：绿野项目，前端去掉硬编码四类型

---

## 1. 动机与范围

### 1.1 动机

事项类型固定 4 种且前端硬编码，无法按项目配置启用集合，也无法新增类型。

### 1.2 范围内

| 层 | 内容 |
|----|------|
| 全局 | `pm_work_item_type` CRUD（含 `color`） |
| 项目 | `pm_project_issue_type` Scheme |
| 引导 | 新建类型时复制 `task` 系统默认工作流 |
| 前端 | 导航 / 设置 / 工作流 Tab 走 API |

### 1.3 非目标

- Epic / Sub-task 层级  
- 图标上传  
- 每类型 Java Plugin  

---

## 2. 数据模型

- `pm_work_item_type.color`
- `pm_project_issue_type(project_id, type_code, sort_order)`
- 项目无 scheme 行时视为启用全部全局 `enabled=1` 类型；新建项目自动写入当前全部启用类型

---

## 3. API

| 路径 | 说明 |
|------|------|
| POST `/pm/meta/types` | 全局启用类型；`includeDisabled` 可选 |
| POST `/pm/meta/types/save` | 新建/更新 |
| POST `/pm/meta/types/delete` | 无事项时可删 |
| POST `/pm/projects/issue-types/list` | 项目启用类型 |
| POST `/pm/projects/issue-types/save` | 全量替换 scheme |

---

## 4. 验收

1. 新建 `story` 并启用后侧栏可见，可配字段与工作流。  
2. Scheme 去掉类型后导航不再出现；已有事项仍可打开。  
3. 有事项不可删、可停用。  

---

## 5. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-07-10 | 初版并落地 |
