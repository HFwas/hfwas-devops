# HFWAS DevOps PM 与 Jira 产品功能对比

> 版本：1.2  
> 更新日期：2026-07-09  
> 适用范围：hfwas-devops 项目管理（PM）子系统 vs Atlassian Jira Software  
> 依据：当前代码实现（`pm-core`、前端 PM 模块、[pm-api.md](./pm-api.md)、[pm-design.md](./pm-design.md)）  
> 演进实施顺序见 [pm-evolution-roadmap.md](./pm-evolution-roadmap.md)

---

## 1. 文档说明

本文对比 **HFWAS DevOps PM 模块** 与 **Jira Software** 的典型产品能力，用于：

- 明确当前产品边界与差异化定位
- 识别功能缺失与优先级
- 指导后续迭代规划

### 1.1 图例

| 状态 | 含义 |
|------|------|
| ✅ 已有 | 端到端可用（UI + API + 领域逻辑） |
| 🟡 部分 | 有基础能力，明显弱于 Jira 或未贯通 |
| ❌ 缺失 | 未实现，或仅数据库/schema 预留 |
| ➖ 不适用 | Jira 具备但本产品当前定位不覆盖 |

### 1.2 对比基准

- **Jira**：以 Jira Software（Cloud / Data Center）常见能力为参照，不含 Jira Service Management、Jira Product Discovery 等独立产品线全部能力。
- **HFWAS**：以仓库内 **已实现或可调用** 的功能为准，不含设计文档中「规划/预留」但未落地的能力。

---

## 2. 核心事项（Issue）管理

| 能力 | Jira | HFWAS DevOps | 差距说明 |
|------|------|--------------|----------|
| 多类型事项 | Story / Task / Bug / Epic / Sub-task 等可扩展 | ✅ 4 种固定类型（需求 / 任务 / 缺陷 / 测试用例） | ❌ 无 Epic、Sub-task；无自定义类型管理 |
| 事项编号 | `PROJ-123` | ✅ `itemKey`（项目 code + 序号，如 `DEMO-1`） | 基本对齐 |
| 标题 | 可编辑 | 🟡 列表/侧栏可改部分字段；详情页标题只读 | 弱于 Jira 详情体验 |
| 描述 | 富文本 + @mention | 🟡 Markdown；详情页 **只读预览** | ❌ 详情内不可编辑；无 @mention |
| 状态流转 | 工作流 + 校验 | ✅ 可配置工作流 + `transition` 校验 | 基本对齐 |
| 优先级 | 内置 | ✅ `PRIORITY` 系统字段 | 基本对齐 |
| 负责人 Assignee | 内置 | ✅ `assignee_id` + 用户选择 | 基本对齐 |
| 报告人 Reporter | 内置 | 🟡 DB / 导入导出支持 | ❌ 前端无 Reporter 字段 |
| 事项关联 | blocks / duplicates / relates + 面板展示 | 🟡 三种 link 类型，可增可查 | ❌ 无删链 API；无链接图 / 反向链接 UI |
| 子任务 / 层级 | Epic → Story → Sub-task | ❌ `parent_id` 字段已预留 | ❌ 无父子树、无 Epic 视图 |
| 附件 | 多附件、预览 | ❌ | 完全缺失 |
| 标签 Labels | 内置 | ❌ | 完全缺失 |
| 组件 Components | 内置 | 🟡 **功能模块**（Component 类似能力） | 有模块树，无 Jira 式 Component 字段 |
| 删除 / 归档 | 软删、归档 | 🟡 硬删除 | ❌ 无回收站 / 归档 |
| 批量操作 | 批量编辑 / 流转 / 删除 | 🟡 列表勾选 + 批量导出 | ❌ 无批量编辑 / 流转 |

---

## 3. 项目与组织

| 能力 | Jira | HFWAS DevOps | 差距说明 |
|------|------|--------------|----------|
| 项目 Project | CRUD、分类、模板 | ✅ CRUD、编码、描述 | 无项目分类 / 模板 |
| 多租户 / 多站点 | Cloud 站点 / DC 实例 | ✅ 租户隔离 + `X-Tenant-Id` + 深链租户对齐 | 架构不同，隔离能力具备 |
| 项目分类 Category | 有 | ❌ | 缺失 |
| 项目权限 | Project Role（Admin / Developer / …） | ❌ `pm_project_member` 表已预留 | **重大缺失**：无项目级 RBAC |
| 项目模板 | 从模板创建 | ❌ | 缺失 |
| 跨项目看板 | 多项目 Board | ❌ 单项目 + 单类型看板 | 缺失 |

---

## 4. 敏捷 / 看板 / 计划

| 能力 | Jira | HFWAS DevOps | 差距说明 |
|------|------|--------------|----------|
| Kanban 看板 | 拖拽、WIP 限制、泳道 | 🟡 按状态分列 + 下拉改状态 | ❌ 无拖拽、WIP、Swimlane |
| Scrum 板 | Sprint、Backlog | ❌ `sprint_id` 字段已预留 | **重大缺失** |
| 迭代规划 | Sprint Planning、容量 | ❌ | 完全缺失 |
| 燃尽图 / 速率 | Burndown、Velocity | ❌ | 完全缺失 |
| 路线图 / Timeline | Advanced Roadmaps | ❌ | 完全缺失 |
| 甘特图 | 插件 / Advanced | ❌ | 完全缺失 |
| 发布版本 | Fix Version / Affects Version | ❌ | 完全缺失 |

---

## 5. 自定义与配置

| 能力 | Jira | HFWAS DevOps | 差距说明 |
|------|------|--------------|----------|
| 自定义字段 | 丰富类型 + 全局 / 项目级 | ✅ 14 种字段类型 + 项目级 | 类型数量少于 Jira，核心场景覆盖 |
| 字段布局 | Screen / Issue Layout | ✅ list / search / create 布局 | 无独立「详情 Screen」配置 |
| 工作流 | 可视化设计器、条件 / 校验 / 后置函数 | 🟡 状态矩阵 + **后置函数** + **REQUIRED_FIELDS 校验** | ❌ 无可视化设计器；无条件；无 SPI |
| 工作流 Scheme | 按类型 / 项目绑定 | 🟡 按项目 + 类型覆盖 | 无独立 Scheme 管理 UI |
| 字段 Scheme | 按类型绑定字段 | ✅ 类型绑定 + 布局 | 基本对齐 |
| 事项类型 Scheme | 可配置类型集合 | 🟡 固定 4 类型 | ❌ 不可增删类型 |
| 配置导入导出 | 脚本 / Marketplace | ✅ 事项类型方案 JSON + 设置页导入导出 UI | 含字段与状态流转（含规则） |
| 远程字段选项 | 有限 | ✅ 远程 SELECT + SSRF 防护 + 预览 | **相对亮点** |
| 通知 Scheme | 按事件配置邮件 / 站内 | 🟡 仅 **负责人变更** 站内信 | ❌ 无评论 / 状态 / @ 通知配置 |

**HFWAS 已支持字段类型：** TEXT、TEXTAREA、MARKDOWN、NUMBER、SELECT、MULTI_SELECT、DATE、DATETIME、USER、BOOLEAN、PRIORITY、STATUS、MODULE。

### 5.1 工作流深度对比（可视化设计器 / 条件 / 校验 / 后置函数）

本节展开 §5 中「工作流」一行的差距，对照 Jira Workflow 的核心概念。

#### 5.1.1 概念对照

| Jira 概念 | 含义 | HFWAS 现状 |
|-----------|------|------------|
| **Status（状态）** | 事项生命周期节点 | ✅ `pm_status_definition`：编码、名称、初始/终态 |
| **Transition（流转）** | 状态间有向边，可命名（如「开始处理」） | 🟡 仅「源状态 → 目标状态」布尔矩阵，**无流转名称 / ID** |
| **Workflow 可视化设计器** | 拖拽节点与连线，编辑 Transition 属性 | ❌ 表格矩阵勾选（`StatusWorkflowView.vue`），非图编辑器 |
| **Condition（条件）** | 决定 Transition **是否对用户可见/可点** | ✅ QuerySpec 条件 + `__current_user__`；`allowed` 需传 `workItemId` |
| **Validator（校验器）** | Transition **提交时**必须满足的约束 | ✅ 路径校验 + `REQUIRED_FIELDS`（关单必填等） |
| **Post-function（后置函数）** | Transition **成功后**自动执行（改字段、发通知等） | ✅ 内置四类：`SET_FIELD` / `NOTIFY_ASSIGNEE` / `NOTIFY_USER` / `WEBHOOK`；存于 `transition_rules`；无插件 SPI |
| **Workflow Scheme** | 将 Workflow 绑定到项目 / 事项类型 | 🟡 按 `projectId + typeCode` 覆盖，无独立 Scheme 实体 |

#### 5.1.2 Jira 三类规则详解

**1. Condition（条件）— 控制「能不能看到这条流转」**

典型示例：

- 仅 Assignee 本人或 Project Lead 可见「关闭」
- 仅当优先级为 High 时可见「紧急处理」
- 仅当关联的 Sub-task 全部 Done 时可见「完成 Epic」

HFWAS：**已支持**（见 [step-08](./evolution/step-08-transition-conditions.md)）。`Transition.conditions` 复用 QuerySpec 条件形状；`allowed` 传入 `workItemId` 时按事项字段过滤；`transition` 执行前再校验。支持 `__current_user__`（如仅负责人可见）。尚无项目角色 / 关联事项类条件。

**2. Validator（校验器）— 控制「点了流转能不能成功」**

典型示例：

- 流转到 Done 前 Resolution 必填
- 流转到 In Review 前至少有一个附件
- 自定义脚本校验业务规则

HFWAS：**已支持路径校验 + Transition 级必填字段**（见 [step-02](./evolution/step-02-transition-validators.md)）。

1. `validateTransition()`：`fromStatus → toStatus` 是否在矩阵中允许（含 `__any__`）
2. `TransitionValidatorExecutor`：对配置的 `REQUIRED_FIELDS` 检查事项字段是否已填（`transition` 可附带 `fields` 一并写入）

尚无附件必填、自定义脚本等其它 Validator 类型。创建/编辑时的 `FieldValidator` 仍独立于 Transition。

**3. Post-function（后置函数）— 流转成功后的副作用**

典型示例：

- 自动 Assign 给 Reporter
- 发送邮件 / Slack 通知
- 写入自定义字段、触发 Webhook
- 自动创建 Sub-task

HFWAS：**已支持内置后置函数**（见 [step-01](./evolution/step-01-workflow-post-functions.md)）。`WorkItemService.transition()` 与状态变更的 `save()` 成功后：

1. 更新 `pm_work_item.status`
2. 调用 `TransitionPostFunctionExecutor.execute()`（按 `fromStatus` 与 `__any__` 规则顺序执行）
3. 再次写库（若 SET_FIELD 改动了字段）+ `activityService.recordChanges()`

内置类型：`SET_FIELD`、`NOTIFY_ASSIGNEE`、`NOTIFY_USER`、`WEBHOOK`（租户钉钉/飞书渠道，非独立 URL）。尚无自定义脚本 / 插件 SPI。

#### 5.1.3 配置 UI 对比

| 维度 | Jira Workflow Designer | HFWAS `StatusWorkflowView` |
|------|------------------------|----------------------------|
| 呈现形式 | 有向图（节点 + 连线） | 二维矩阵表格 + 状态列表 |
| 添加状态 | 画布上添加节点 | 弹窗表单（编码、名称、初始/终态） |
| 配置流转 | 点击连线 → 编辑 Transition 面板 | 勾选矩阵单元格 |
| 全局流转 | 需显式建模 | ✅ `__any__`（任何状态）行 |
| Transition 名称 | 有（如「Resolve Issue」） | ❌ 无；前端/API 直接用目标 statusCode |
| 规则配置入口 | Transition 面板 → Conditions / Validators / Post Functions | 🟡 矩阵 +「流转规则」Tab → Drawer（条件 / 校验 / 后置）；无图编辑器 |
| 项目覆盖 | Workflow Scheme 绑定 | ✅ 项目级 save / reset，回退系统默认 |
| 导入导出 | XML / 部分 Cloud API | 🟡 含在事项类型方案 JSON 的 `statusWorkflow` 段（后端） |

#### 5.1.4 数据模型对比

**Jira（简化）**

```text
Workflow
  ├── Status nodes[]
  └── Transitions[]
        ├── name, from, to
        ├── conditions[]      ← 可见性
        ├── validators[]      ← 提交校验
        └── postFunctions[]   ← 成功后动作
```

**HFWAS（当前）**

```text
pm_status_definition（每行一个状态）
  ├── statusCode, statusName
  ├── isInitial, isFinal, sortOrder
  └── transitions: Transition[]
        ├── id, name, toStatus
        ├── conditions          ← QuerySpec 可见性
        ├── validators[]
        └── postFunctions[]
```

流转执行路径：

```text
POST /pm/work-items/{id}/transition { transitionId, fields? }
  → StatusDefinitionService.validateTransition()
  → TransitionConditionEvaluator.assertMatches()
  → apply fields + TransitionValidatorExecutor
  → UPDATE status
  → TransitionPostFunctionExecutor.execute()
  → UPDATE（字段副作用）+ ActivityLog
```

#### 5.1.5 能力矩阵（工作流专项）

| 能力 | Jira | HFWAS | 状态 |
|------|------|-------|------|
| 自定义状态集合 | ✅ | ✅ | 已有 |
| 初始 / 终态标记 | ✅ | ✅ | 已有 |
| 项目级工作流覆盖 | ✅ | ✅ | 已有 |
| 恢复系统默认 | ✅ | ✅ | 已有 |
| 流转路径校验 | ✅ | ✅ | 已有（矩阵级） |
| 按用户/角色隐藏 Transition | ✅ | 🟡 | `__current_user__` 已有；角色待 Step 7 |
| 按字段值隐藏 Transition | ✅ | ✅ | QuerySpec Condition |
| Transition 级必填字段 | ✅ | ✅ | 已有（REQUIRED_FIELDS） |
| 流转前自定义校验脚本 | ✅ | ❌ | 缺失 |
| 流转后自动改字段 | ✅ | ✅ | 已有（SET_FIELD） |
| 流转后通知 | ✅ | ✅ | 已有（NOTIFY_ASSIGNEE / NOTIFY_USER） |
| 流转后 Webhook | ✅ | 🟡 | 租户钉钉/飞书渠道；无独立 URL |
| 可视化流程图编辑器 | ✅ | ❌ | 缺失 |
| Transition 显示名 | ✅ | ✅ | 已有（Transition.name） |
| 工作流版本 / 草稿发布 | ✅ (Cloud) | ❌ | 缺失 |
| Post-function 插件 SPI | ✅ | ❌ | 缺失（Phase F） |

#### 5.1.6 与 HFWAS 已有能力的复用点

若后续补齐 Jira 式工作流规则，可复用：

| 已有模块 | 可复用于 |
|----------|----------|
| `QueryEngine` + `QueryCondition` | Condition：按字段表达式决定 Transition 可见性 |
| `FieldValidator` | Validator：Transition 提交前字段校验 |
| `NotifyChannelService` / 站内信 | Post-function：流转后通知 |
| `activityService` | Post-function：审计已覆盖，可扩展触发点 |
| 事项类型方案 Import/Export | 工作流规则 JSON 一并迁移 |

#### 5.1.7 演进建议（若要对标 Jira Workflow）

实施顺序与状态见 **[pm-evolution-roadmap.md](./pm-evolution-roadmap.md)**。摘要：

| 阶段 | 目标 | 路线图 | 状态 |
|------|------|--------|------|
| **Phase C** | 基础 Post-function | Step 1 | ✅ 已收口 |
| **Phase B** | 基础 Validator | Step 2 | ✅ 已完成 |
| **Phase A** | Transition 实体化 | Step 3 | ✅ 已完成 |
| **Phase D** | Condition | Step 8 | ✅ 已完成 |
| **Phase E** | 可视化设计器 | Step 10 | 待做 |
| **Phase F** | 扩展 SPI | Step 12 | 待做 |

**投入产出建议：** Phase C 收口后优先 Phase B（关单必填）；可视化设计器（Phase E）偏体验，可晚于规则引擎。

---

## 6. 搜索、筛选与视图

| 能力 | Jira | HFWAS DevOps | 差距说明 |
|------|------|--------------|----------|
| JQL | 强大查询语言 | 🟡 可视化 QueryBuilder | ❌ 无 JQL / 无文本查询语法 |
| 高级筛选 | 嵌套 AND / OR | 🟡 扁平 AND / OR | ❌ 无嵌套条件组 UI（后端支持 groups） |
| 运算符 | 完整 | 🟡 UI 缺 BETWEEN、GTE、LTE、NOT_IN 等 | 后端部分已支持 |
| 多字段排序 | 有 | 🟡 后端 `sort[]` 支持 | ❌ 列表无排序 UI |
| 保存筛选器 Filter | 保存 + 订阅 | 🟡 后端 Saved View API | ❌ **无前端**：不能保存 / 切换个人视图 |
| 全局快速搜索 | Cmd+K | ❌ | 缺失 |
| 最近浏览 | Recently viewed | ❌ | 缺失 |

---

## 7. 协作与沟通

| 能力 | Jira | HFWAS DevOps | 差距说明 |
|------|------|--------------|----------|
| 评论 | @mention、反应 | ✅ 线程评论 + 作者可删 | ❌ 无 @mention、无表情 |
| 活动历史 | 完整审计 | ✅ CREATE / FIELD_CHANGE / LINK_ADD | 基本对齐 |
| 关注人 Watchers | 订阅事项变更 | ❌ | 缺失 |
| 站内通知 | 多事件 | 🟡 负责人变更 | 覆盖远少于 Jira |
| 邮件 / IM | 可配置 | 🟡 钉钉 / 飞书 Webhook 基础设施 | PM 事件未全面接入 |
| 外链分享 | 分享链接 | ❌ | 缺失 |

---

## 8. 报表与分析

| 能力 | Jira | HFWAS DevOps | 差距说明 |
|------|------|--------------|----------|
| 仪表板 Dashboard | Gadget 可定制 | ❌ | 完全缺失 |
| 内置报表 | 燃尽 / 累积流 / 控制图等 | ❌ | 完全缺失 |
| 数据导出 | CSV / Excel | 🟡 事项 Excel 导入导出 | 非分析报表 |
| 度量 Insight | Velocity、Lead Time 等 | ❌ | 完全缺失 |

---

## 9. 权限与安全

| 能力 | Jira | HFWAS DevOps | 差距说明 |
|------|------|--------------|----------|
| 全局权限 | Jira Administrators | ✅ admin / user 平台角色 | 粒度较粗 |
| 项目权限 | Browse / Edit / Transition 等 | ❌ | **重大缺失** |
| 字段级安全 | Field Security Scheme | ❌ | 缺失 |
| 工作流权限 | 按状态限制编辑 | ❌ | 缺失 |
| 审计日志 | Audit Log | 🟡 操作日志 OperLog | 无 Jira 级 Permission 审计 |

当前隔离主要依赖 **租户级** 数据过滤，尚不具备 Jira 式 **项目成员 + 细粒度权限**。

---

## 10. 集成与 DevOps

| 能力 | Jira | HFWAS DevOps | 差距说明 |
|------|------|--------------|----------|
| Git 集成 | 提交关联、Development 面板 | ❌ | 完全缺失 |
| CI/CD | Bamboo / GitHub Actions 等 | ❌ | 完全缺失 |
| 分支 / PR 面板 | Development panel | ❌ | 完全缺失 |
| Webhook | 出站事件 | ❌ PM 事件 Webhook | 缺失 |
| REST API | REST v3 + 文档 | 🟡 PM REST 较完整（见 [pm-api.md](./pm-api.md)） | 无 OpenAPI 发布；无 API Token 管理 |
| 插件生态 | Marketplace | ❌ | 有 SPI 扩展点，无插件市场 |
| LDAP / 目录 | 企业目录 | ✅ LDAP 连接器 + JWT 登录 | SSO / OAuth 弱于 Jira DC |
| 从 Jira 迁移 | 官方 / 第三方工具 | ❌ | 缺失 |

---

## 11. 导入导出与迁移

| 能力 | Jira | HFWAS DevOps | 差距说明 |
|------|------|--------------|----------|
| CSV / Excel 导入 | 有 | ✅ Excel 导入（CREATE / UPSERT by itemKey） | 基本对齐 |
| CSV / Excel 导出 | 有 | ✅ 可选字段、选中 / 全量导出 | 基本对齐 |
| 配置迁移 | 部分 XML / JSON | 🟡 事项类型方案 JSON（后端） | UI 未接通 |
| 导入模板 | 有 | ✅ 带字段映射 meta sheet | 基本对齐 |

---

## 12. 能力雷达（定性）

```
维度              Jira    HFWAS    说明
─────────────────────────────────────────
事项 CRUD          ████    ███░    核心可用，缺层级 / 附件
工作流             ████    ███░    可配但无高级规则
字段自定义         ████    ████    强项，远程选项是亮点
看板 / 敏捷        ████    █░░░    仅静态看板
搜索 / 视图        ████    ██░░    无 JQL、无保存视图 UI
协作               ████    ██░░    评论 / 活动有，通知弱
权限               ████    █░░░    仅租户级，无项目 RBAC
报表               ████    ░░░░    空白
DevOps 集成        ████    ░░░░    空白
多租户 / 企业      ████    ███░    租户隔离是优势
```

---

## 13. HFWAS 相对 Jira 的优势（差异化）

| 维度 | 说明 |
|------|------|
| **多租户原生** | 租户 Header、浏览器缓存、深链 `access-context` 自动对齐，适合 SaaS 多客户 |
| **统一 WorkItem 模型** | 多类型共用一张表 + 类型插件，API 统一（`/pm/work-items/*`） |
| **远程字段选项** | 可配置 HTTP 拉取 + SSRF 防护 + 预览，适合对接内部字典 / 主数据 |
| **轻量部署** | SQLite 单文件，脚本一键启停，零外部中间件依赖 |
| **事项 Excel IO** | 字段可选、模板下载、预览导入，贴合国内企业习惯 |
| **用户中心一体** | 租户 / 成员 / LDAP / 站内信 / 操作日志与 PM 同平台 |
| **可扩展内核** | 字段类型 SPI、事项类型 SPI、查询引擎，便于二次开发 |

---

## 14. 缺失功能与优先级建议

### 14.1 P0 — 基础体验差距最大

| 功能 | 理由 | 现状线索 |
|------|------|----------|
| **项目成员与权限** | 多团队无法安全协作 | `pm_project_member` 表已预留 |
| **详情页完整编辑** | Jira 核心交互 | 描述只读；标题详情不可改 |
| **看板拖拽 + 进入详情** | Kanban 标志能力 | 看板仅有列 + 下拉改状态 |
| **保存视图 / 筛选器 UI** | 后端已有，投入产出高 | `PmSavedViewController` + `pmViewApi` 无 UI |

### 14.2 P1 — 敏捷与协作

| 功能 | 理由 | 现状线索 |
|------|------|----------|
| **Sprint / Backlog** | Scrum 团队刚需 | `sprint_id` 字段已预留 |
| **子任务 / Epic 层级** | 事项组织 | `parent_id` 字段已预留 |
| **评论 / 状态变更通知** | 协作闭环 | 站内信基础设施已有 |
| **链接删除 + 链接 UI 优化** | 日常关联管理 | 仅有 add + list API |

### 14.3 P2 — 企业级与扩展

| 功能 | 理由 |
|------|------|
| **附件** | 缺陷 / 需求场景几乎必备 |
| **基础报表**（按状态、负责人、趋势） | 管理可见性 |
| **配置方案 Import / Export UI** | 后端已就绪 |
| **Webhook / Git 提交关联** | DevOps 定位需要 |

### 14.4 P3 — 长期对标

| 功能 | 理由 |
|------|------|
| **JQL 或高级查询语法** | Power user 需求 |
| **自定义事项类型** | 超越固定 4 类型 |
| **工作流条件 / 后置函数** | 对标 Jira Workflow；Condition / Validator / Post-function 已有；待角色条件与 SPI |
| **路线图 / 甘特** | Advanced Roadmaps 级别 |
| **从 Jira 迁移工具** | 降低替换成本 |

---

## 15. 产品定位结论

| 维度 | 结论 |
|------|------|
| **当前定位** | 轻量级、可配置的 **Issue Tracker + 基础 Kanban**，具备 Jira 的部分配置化能力 |
| **最接近 Jira 的部分** | 事项模型、字段体系、工作流配置、组合查询（后端）、Excel IO、活动 / 评论 |
| **差距最大的部分** | 项目权限、敏捷（Sprint / Backlog）、看板交互、报表、DevOps 集成、保存视图 / JQL、附件与层级 |
| **对标 Team-managed project** | 补齐 **P0 + 看板增强 + 保存视图** 可形成可演示 MVP |
| **对标 Company-managed + Scrum** | 需推进 **P0 + P1** 全量能力 |

---

## 16. 已实现功能清单（摘要）

便于与上文「缺失」对照，以下为当前 **已落地** 的主要能力。

### 16.1 前端页面

| 路由 | 功能 |
|------|------|
| `/pm/projects` | 项目列表、搜索、新建、删除 |
| `/pm/projects/:id/items/:typeCode` | 事项列表、查询、新建、导入导出 |
| `/pm/projects/:id/items/:itemId` | 事项详情、侧栏字段、评论、活动 |
| `/pm/projects/:id/board/:typeCode` | 看板（按状态分列） |
| `/pm/projects/:id/settings/modules` | 功能模块树 |
| `/pm/projects/:id/settings/fields` | 字段目录 |
| `/pm/projects/:id/settings/types` | 事项类型入口 |
| `/pm/projects/:id/settings/types/:typeCode` | 类型字段布局 |
| `/pm/projects/:id/settings/workflow/:typeCode` | 状态流转配置 |

### 16.2 后端 API 模块

| Base Path | 能力 |
|-----------|------|
| `/pm/projects` | 项目 CRUD、`access-context` |
| `/pm/project-modules` | 模块树 / 扁平 / CRUD |
| `/pm/work-items` | 事项 CRUD、查询、流转、关联 |
| `/pm/work-items` | 评论、活动 |
| `/pm/work-items/io` | Excel 导入导出 |
| `/pm/fields/definitions` | 字段定义、选项、远程预览 |
| `/pm/fields/layout` | 字段布局 |
| `/pm/status/workflow` | 工作流 CRUD |
| `/pm/issue-type-schemes` | 配置方案导入导出（后端） |
| `/pm/views` | 保存视图（后端） |
| `/pm/meta/types`、`/pm/board` | 类型元数据、看板数据 |

### 16.3 与用户模块联动

- JWT 登录 + `X-Tenant-Id` 租户上下文
- 租户切换、深链自动对齐项目租户
- 用户列表供 Assignee 字段使用
- 负责人变更站内信通知
- 操作日志（OperLog）记录关键 PM 写操作

---

## 17. 相关文档

| 文档 | 说明 |
|------|------|
| [pm-evolution-roadmap.md](./pm-evolution-roadmap.md) | 分步演进路线图与设置页 UI 标准 |
| [evolution/step-01-workflow-post-functions.md](./evolution/step-01-workflow-post-functions.md) | Step 1：后置函数收口 |
| [evolution/step-03-transition-entity.md](./evolution/step-03-transition-entity.md) | Step 3：Transition 实体化 |
| [evolution/step-08-transition-conditions.md](./evolution/step-08-transition-conditions.md) | Step 8：Condition 可见性 |
| [pm-design.md](./pm-design.md) | PM 架构与领域设计 |
| [pm-api.md](./pm-api.md) | PM REST API 接口文档 |
| [error-code-design.md](./error-code-design.md) | 全局错误码规范 |

---

## 18. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| 1.0 | 2026-07-07 | 初版：基于代码实现与 Jira Software 典型能力对比 |
| 1.1 | 2026-07-07 | 新增 §5.1 工作流深度对比（可视化设计器 / 条件 / 校验 / 后置函数） |
| 1.2 | 2026-07-09 | 修正 Post-function 为已实现；关联演进路线图；更新能力矩阵 |
| 1.3 | 2026-07-09 | Step 2：Transition Validator（REQUIRED_FIELDS）已落地 |
| 1.4 | 2026-07-09 | Step 8：Condition（QuerySpec 可见性）已落地 |
