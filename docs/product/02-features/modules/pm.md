# 项目管理（PM）功能规格

> 日期：2026-10-17
> 版本：v0.1
> 状态：骨架
> 数据来源：`frontend/src/router.tsx`、`backend/server/.../pm/api/controller/`、`docs/pm/pm-design.md`、`README.md`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：PM 功能清单、页面、核心流程、规则与异常 |

---

## 1. 概述

PM 提供 Jira 式项目协作：项目 → 事项（需求/任务/缺陷）→ 动态字段 → 状态工作流 → 看板 → 评论/关联/活动。所有数据按 `tenant_id` + `project_id` 隔离。

## 2. 角色与场景

| 角色 | 场景 |
|------|------|
| 项目负责人 | 创建项目、配置事项类型/字段/工作流、查看看板 |
| 研发/测试 | 创建与流转事项、评论、关联、导入导出 |
| 平台管理员 | 管理租户下的全部项目 |

## 3. 功能清单

| 功能项 | 说明 | 页面/路由 | 状态 |
|--------|------|-----------|------|
| 项目列表与 CRUD | 分页、创建/编辑、删除、访问上下文 | `/pm/projects` | 可用 |
| 项目监控 | 项目指标总览 | `/pm/monitor` | 可用 |
| 事项列表 | 按类型分页、组合查询、保存视图 | `/pm/projects/:projectId/items/:typeCode` | 可用 |
| 事项详情 | 字段、评论、活动、关联、流转 | `.../items/:typeCode/:itemId` | 可用 |
| 看板 | 按状态分列、拖拽流转 | `.../board/:typeCode` | 可用 |
| 工作流设计 | 状态、流转、校验器、后置动作 | `.../settings/workflow/:typeCode` | 可用 |
| 字段配置 | 字段定义、选项、类型绑定、布局 | 详情页/工作流页内 | 可用 |
| 模块树 | 树形模块维护 | 事项列表侧栏 | 可用 |
| 导入导出 | Excel 模板、预览、导入、导出 | 事项列表 | 可用 |
| 类型方案 | 方案导入导出 | 项目设置 | 可用 |

## 4. 核心流程

### 4.1 事项创建

```text
选择项目与事项类型
  → 加载类型字段布局
  → 校验必填字段与字段类型
  → 分配 item_no（pm_project_sequence）
  → 生成 itemKey = 项目编码 + "-" + item_no
  → 写入 pm_work_item 与自定义字段 custom_fields
  → 记录创建活动
```

### 4.2 状态流转

```text
读取 pm_status_definition 的 transitions
  → 校验当前状态是否允许目标流转
  → 执行 validator（如必填字段、权限）
  → 更新 status
  → 执行 post-function（如通知、赋值）
  → 写入 pm_work_item_activity
```

### 4.3 看板拖拽

```text
拖拽事项到目标列
  → 调用 /pm/work-items/{id}/transition
  → 后端复用流转校验
  → 成功后刷新列数据
```

## 5. 业务规则

| 规则 | 说明 |
|------|------|
| 项目编码唯一 | 同一租户内 `code` 唯一 |
| 事项键 | `itemKey` 由项目编码与递增序号组成，创建后不可变 |
| 字段作用域 | `pm_field_definition.scope` 区分系统/项目级；`applicable_types` 控制适用类型 |
| 必填校验 | `required_flag` 为真时创建/流转必须提供值 |
| 逻辑删除 | `del_flag`（0 正常 / 1 删除），由 MyBatis-Plus 全局逻辑删除处理 |
| 状态一致性 | 流转必须存在于 `transitions` 配置中 |
| 事项类型 | 项目必须先启用类型（`pm_project_issue_type`）才能创建该类型事项 |

## 6. 异常与边界

| 场景 | 系统行为 | 错误码/提示 |
|------|----------|-------------|
| 项目编码重复 | 拒绝保存 | `10004 DUPLICATE` |
| 项目不存在 | 返回空/报错 | `10003 NOT_FOUND` |
| 字段 key 非法 | 拒绝保存 | `10010 FIELD_KEYS_INVALID` |
| 导入内容为空 | 拒绝导入 | `10006 IMPORT_EMPTY` |
| 导入 schema 不支持 | 拒绝导入 | `10007 SCHEMA_UNSUPPORTED` |
| 无权限访问项目 | 拒绝 | `11002 FORBIDDEN` / `11005 TENANT_FORBIDDEN` |
| 流转不在配置中 | 拒绝 | 业务错误（具体 code 待确认） |

## 7. 接口与数据

- 接口：`/pm/projects/*`、`/pm/work-items/*`、`/pm/fields/*`、`/pm/status/workflow/*`、`/pm/project-modules/*`、`/pm/views/*`、`/pm/meta/*`、`/pm/issue-type-schemes/*`
- 详见 [04-api/modules/pm.md](../../04-api/modules/pm.md)
- 数据表：`pm_project`、`pm_project_member`、`pm_work_item`、`pm_work_item_type`、`pm_project_issue_type`、`pm_project_module`、`pm_field_definition`、`pm_field_option`、`pm_work_item_link`、`pm_work_item_comment`、`pm_work_item_activity`、`pm_status_definition`、`pm_saved_view`、`pm_type_field_layout`、`pm_project_sequence`
- 详见 [03-database/tables/pm.md](../../03-database/tables/pm.md)

## 8. 验收标准

| 编号 | 验收项 |
|------|--------|
| AC-PM-01 | 可创建项目并在列表/详情中正确展示 |
| AC-PM-02 | 创建事项后 itemKey 连续且可读 |
| AC-PM-03 | 未启用的类型不能创建事项 |
| AC-PM-04 | 看板拖拽触发的流转与详情页流转结果一致 |
| AC-PM-05 | 导入模板与预览、正式导入结果一致 |
| AC-PM-06 | 跨租户访问项目被拒绝 |

## 9. 待确认

1. 项目成员角色（`pm_project_member.role`）的取值与能力。
2. 自定义字段类型全集与前端渲染映射（以 `PmFieldDefinitionController` 的 catalog 为准）。
3. 流转校验失败的统一错误码。
