# 自定义事项类型原型规格（人读版）

> 日期：2026-10-19
> 版本：v1.0
> 机器可读版本：[prototype-spec.json](./prototype-spec.json)
> 功能设计：[../design.md](../design.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v1.0 | 2026-10-19 | 初版：事项类型列表、四步创建向导、字段布局、工作流模板、项目启用范围与状态矩阵 |

---

## 1. 范围

本原型验证「事项类型管理」：内置 5 类（需求 / 任务 / 缺陷 / 迭代 / 测试用例）+ 自定义类型的新建、复制、编辑、停用、删除、字段配置、工作流模板与项目启用范围。

不在本版：工作流画布、字段定义新建、类型层级、权限配置。

## 2. 内置类型规则

| code | 名称 | 图标 | 颜色 | 可删除 | 可改 code |
|------|------|------|------|--------|-----------|
| `requirement` | 需求 | bookmark | green | 否 | 否 |
| `task` | 任务 | check | blue | 否 | 否 |
| `bug` | 缺陷 | bug | red | 否 | 否 |
| `iteration` | 迭代 | sprint | purple | 否 | 否 |
| `test_case` | 测试用例 | flask | amber | 否 | 否 |

- 可配置字段布局与工作流，可在项目启用范围中关闭。
- 停用后新建入口隐藏，历史事项保留。

## 3. 页面

| ID | 页面 | 路由（规划） | 状态 |
|----|------|--------------|------|
| P01 | 事项类型列表 | `/pm/projects/:projectId/settings/types` | default |
| P02 | 空态 | 同上 | empty |
| P03 | 加载 | 同上 | loading |
| P04 | 失败 | 同上 | error |
| P05 | 无权限 | 同上 | permission |
| P06 | 大数据量 | 同上 | dense |
| P07 | 新建 · 基本信息 | `.../settings/types/new` | default / invalid |
| P08 | 新建 · 字段与布局 | 同上 | default |
| P09 | 新建 · 工作流 | 同上 | default |
| P10 | 新建 · 预览 | 同上 | default |
| P11 | 编辑类型 | `.../settings/types/:code` | default |

### 3.1 列表信息层级

```text
页头：事项类型 / 说明                                  [项目启用范围] [从模板创建] [新建自定义类型]
指标：类型总数 | 内置类型 | 自定义类型 | 项目已启用
页签：全部类型 | 内置类型 | 自定义类型 | 已停用
工具栏：搜索 | 状态筛选 | 共 N 个类型
表格：类型（图标+名称+code）| 来源 | 启用状态 | 字段 | 工作流 | 使用中 | 顺序 | 操作
```

列宽：类型弹性、来源 88、启用 104、字段 72、工作流 132、使用中 88、顺序 56、操作 170。

### 3.2 编辑器

```text
左侧步骤 176px | 主面板弹性 | 右侧摘要 268px
步骤：基本信息 → 字段与布局 → 工作流 → 预览
```

## 4. 弹层

| ID | 名称 | 触发 |
|----|------|------|
| O01 | 复制事项类型 | 行「复制」/ 页头「从模板创建」 |
| O02 | 删除被阻止 | 有引用的自定义类型「删除」 |
| O03 | 删除确认 | 无引用的自定义类型「删除」 |
| O04 | 停用 / 启用确认 | 行「停用」或开关 |
| O05 | 项目启用范围 | 页头按钮；抽屉 |
| O06 | 模板切换确认 | 基本信息切换模板 |

## 5. 查询参数

| 参数 | 取值 | 说明 |
|------|------|------|
| `state` | `empty` / `loading` / `error` / `permission` / `dense` / `invalid` | 页面状态 |
| `tab` | `all` / `builtin` / `custom` / `disabled` | 页签 |
| `q` | 文本 | 搜索名称/编码/描述 |
| `status` | `enabled` / `disabled` | 状态筛选 |
| `off` | code 列表 | 停用的类型（模拟开关与范围） |
| `overlay` | `duplicate` / `delete` / `disable` / `scheme` / `template-reset` | 弹层 |
| `code` | 类型编码 | 复制/编辑/删除对象 |
| `blocked` | `1` | 删除被阻止样式 |
| `step` | `basic` / `fields` / `workflow` / `preview` | 编辑器步骤 |
| `tpl` / `wft` | 模板编码 | 模板与工作流选择 |
| `lf` / `cf` / `rf` | 字段 key 列表 | 列表 / 创建 / 必填字段 |
| `icon` / `color` | 图标名 / 颜色名 | 类型视觉 |
| `theme` | `dark` | 暗色 |
| `capture` | `1` | 截图模式 |

## 6. 状态矩阵

| 状态 | URL |
|------|-----|
| 默认 | `#/types` |
| 空态 | `#/types?state=empty` |
| 加载 | `#/types?state=loading` |
| 失败 | `#/types?state=error` |
| 无权限 | `#/types?state=permission` |
| 大数据量 | `#/types?state=dense` |
| 新建基本信息 | `#/types/new?step=basic` |
| 字段与布局 | `#/types/new?step=fields` |
| 工作流 | `#/types/new?step=workflow` |
| 预览 | `#/types/new?step=preview` |
| 编辑 | `#/types/edit?code=story&step=basic` |
| 校验失败 | `#/types/new?state=invalid` |
| 停用 | `#/types?overlay=disable&code=story` |
| 删除阻止 | `#/types?overlay=delete&code=story&blocked=1` |
| 删除确认 | `#/types?overlay=delete&code=research&blocked=0` |
| 复制 | `#/types?overlay=duplicate&code=story` |
| 项目启用范围 | `#/types?overlay=scheme` |
| 模板切换 | `#/types/new?step=basic&overlay=template-reset` |

## 7. 组件映射

| 组件 | 位置 | 说明 |
|------|------|------|
| `TypeIcon` | `modules/pm/components/TypeIcon.tsx` | 图标 + 色彩组合 |
| `BuiltinBadge` | `modules/pm/components/BuiltinBadge.tsx` | 内置 / 自定义 |
| `SummaryStrip` | 复用 `OverviewMetricCards` | 四项指标 |
| `DataTable` | 扩展既有组件 | 固定 52px 行高 |
| `Switch` | shadcn `Switch` | 启用状态 |
| `TypeEditorShell` | `modules/pm/components/TypeEditorShell.tsx` | 步骤 + 主面板 + 摘要 |
| `FieldLayoutTable` | `modules/pm/components/FieldLayoutTable.tsx` | 显示位置、必填、顺序 |
| `WorkflowPreview` | `modules/pm/components/WorkflowPreview.tsx` | 状态流只读预览 |
| `Sheet` | shadcn | 项目启用范围 |
| `AlertDialog` | shadcn | 删除 / 停用 / 模板切换 |
| `EmptyState` / `ErrorState` / `NoPermissionState` | `components/console/` | 状态 |

## 8. 验收标准

见 `prototype-spec.json` 的 `acceptance`（AC-01–AC-12）。

## 9. 假设

| 编号 | 假设 | 确认方 |
|------|------|--------|
| A1 | 后端补 `builtIn` 标识；未就绪时前端按 5 个 code 兜底 | 后端 |
| A2 | 类型列表补 `workItemCount` | 后端 |
| A3 | 类型接口补 `fieldCount` 或前端聚合字段 Schema | 后端 |
| A4 | 类型补 `description` | 后端 |
| A5 | 类型补 `templateCode` / `basedOn` | 后端 |
| A6 | 新建类型按所选模板复制工作流，而非固定复制 task | 后端 |
| A7 | 新增 `iteration` 内置类型与默认工作流 | 后端 / 产品 |
| A8 | 删除失败返回引用数，前端展示阻止原因 | 后端 |
| A9 | 项目类型方案按数组顺序写入排序 | 后端 |
