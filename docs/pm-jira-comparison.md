# HFWAS DevOps PM 与 Jira 产品功能对比

> 版本：1.0  
> 更新日期：2026-07-07  
> 适用范围：hfwas-devops 项目管理（PM）子系统 vs Atlassian Jira Software  
> 依据：当前代码实现（`pm-core`、前端 PM 模块、[pm-api.md](./pm-api.md)、[pm-design.md](./pm-design.md)）

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
| 工作流 | 可视化设计器、条件 / 校验 / 后置函数 | 🟡 状态矩阵 + 流转目标列表 | ❌ 无条件、Validator、Post-function |
| 工作流 Scheme | 按类型 / 项目绑定 | 🟡 按项目 + 类型覆盖 | 无独立 Scheme 管理 UI |
| 字段 Scheme | 按类型绑定字段 | ✅ 类型绑定 + 布局 | 基本对齐 |
| 事项类型 Scheme | 可配置类型集合 | 🟡 固定 4 类型 | ❌ 不可增删类型 |
| 配置导入导出 | 脚本 / Marketplace | 🟡 事项类型方案 JSON（**后端完整**） | ❌ Import 组件未挂载；无 Export UI |
| 远程字段选项 | 有限 | ✅ 远程 SELECT + SSRF 防护 + 预览 | **相对亮点** |
| 通知 Scheme | 按事件配置邮件 / 站内 | 🟡 仅 **负责人变更** 站内信 | ❌ 无评论 / 状态 / @ 通知配置 |

**HFWAS 已支持字段类型：** TEXT、TEXTAREA、MARKDOWN、NUMBER、SELECT、MULTI_SELECT、DATE、DATETIME、USER、BOOLEAN、PRIORITY、STATUS、MODULE。

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
| **工作流条件 / 后置函数** | 对标 Jira Workflow 高级能力 |
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
| [pm-design.md](./pm-design.md) | PM 架构与领域设计 |
| [pm-api.md](./pm-api.md) | PM REST API 接口文档 |
| [error-code-design.md](./error-code-design.md) | 全局错误码规范 |

---

## 18. 修订记录

| 版本 | 日期 | 说明 |
|------|------|------|
| 1.0 | 2026-07-07 | 初版：基于代码实现与 Jira Software 典型能力对比 |
