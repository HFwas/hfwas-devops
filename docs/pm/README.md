# 项目管理（PM）文档索引

> 日期：2026-10-21
> 版本：v1.5
> 状态：设计阶段（原型角色评审通过，可冻结；冻结提交待用户确认）
> 适用范围：`docs/pm/`、PM 功能目录与 PM 原型的阅读入口

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v1.0 | 2026-10-20 | 初版：按原型 v1.7 梳理 PM 文档地图、阅读顺序、权威边界、当前状态与待确认事项 |
| v1.1 | 2026-10-20 | 记录已确认决策：分页 10/20/50/100、支持迭代、截止时间 `end_time`、迁移编号 18；启动独立原型评审 |
| v1.2 | 2026-10-20 | 原型升级 v1.8：评审发现 2 P0 / 7 P1 已修复，画板扩至 54 张，等待复评冻结 |
| v1.3 | 2026-10-20 | 原型升级 v1.9：复评 4 个阻断项已修复，等待二次复评 |
| v1.4 | 2026-10-20 | 第三次独立验证通过，4 个阻断项关闭；等待产品 / 前端 / 测试签字冻结 |
| v1.5 | 2026-10-21 | 角色复评通过，4 项 P1 关闭；原型可冻结，等待用户确认冻结提交 |

---

## 1. 这份索引解决什么问题

PM 的设计内容分布在 `docs/pm/`、三个功能目录和原型目录中。本索引回答三件事：

1. **读哪份**：按角色和问题找到唯一权威文档；
2. **什么状态**：哪些已冻结、哪些待评审、哪些未开始；
3. **还缺什么**：接口、数据、测试与原型冻结的缺口。

模块级规则以 [pm-design.md](./pm-design.md) 为准；本索引不重复设计内容。

## 2. 按角色阅读

| 角色 | 建议顺序 |
|------|----------|
| 产品 / 设计 | [pm-design.md](./pm-design.md) §1–§6 → [原型入口](../product/06-prototype/pm/index.html#/overview) → [原型契约](../product/06-prototype/pm/prototype-contract.md) |
| 前端 | [pm-design.md](./pm-design.md) §3–§6、§9 → [列表配置设计](../superpowers/features/20261019-pm-list-config-bulk/design.md) → [工作台组件模型](../superpowers/features/20261019-pm-workbench-redesign/design.md) → 原型截图 `shots/` |
| 后端 | [pm-design.md](./pm-design.md) §2、§7、§8 → [pm-api.md](./pm-api.md) → `pm-schema.sql` |
| 测试 | [pm-design.md](./pm-design.md) §10–§13 → 三份功能设计的验收标准 → [产品测试用例](../product/05-testing/cases/pm.md) |
| 新人 | [产品功能概览](../product/02-features/modules/pm.md) → [pm-design.md](./pm-design.md) §1–§3 → 原型总览 |

## 3. 文档清单

| 文档 | 版本 | 状态 | 权威范围 |
|------|------|------|----------|
| [pm-design.md](./pm-design.md) | v2.0 | 设计完成，待评审 | 模块级规则、领域模型、信息架构、接口与数据缺口、实施顺序 |
| [pm-api.md](./pm-api.md) | v1.4 | 已完成（P0 隔离） | HTTP 接口、请求/响应、错误码、示例 |
| [pm-api.postman_collection.json](./pm-api.postman_collection.json) | — | 已完成 | Postman 调试与回归 |
| [pm-jira-comparison.md](./pm-jira-comparison.md) | — | 历史参考 | 竞品对比，不作为验收依据 |
| [pm-evolution-roadmap.md](./pm-evolution-roadmap.md) | — | 历史参考 | 演进路线，优先级以功能设计为准 |
| [PM 模块总原型](../product/06-prototype/pm/README.md) | v1.11 | **评审通过，可冻结** | 视觉与交互基线、画板、状态参数 |
| [prototype-spec.md](../product/06-prototype/pm/prototype-spec.md) | v1.9 | 评审通过 | 页面/弹层、交互规则、验收标准 |
| [prototype-contract.md](../product/06-prototype/pm/prototype-contract.md) | v0.12 | 评审通过，可冻结 | A2 对齐、必须一致项、允许差异、组件对账 |
| [列表配置与批量设计](../superpowers/features/20261019-pm-list-config-bulk/design.md) | v0.8 | 待评审 | 视图配置、筛选构建器、批量、导入导出、详情与操作记录 |
| [工作台改版设计](../superpowers/features/20261019-pm-workbench-redesign/design.md) | v0.1 | 待评审 | 列表骨架、共享组件、搜索/筛选/视图/分页交互 |
| [事项类型设计](../superpowers/features/20261019-pm-work-item-types/design.md) | v0.1 | 待评审 | 类型 CRUD、模板、字段布局、项目启用范围 |

功能目录结构（设计 → 测试 → 变更 → 进度）：

```text
docs/superpowers/features/20261019-pm-workbench-redesign/
├── design.md        ✅
├── test-cases.md    ❌ 未创建
├── changes.md       ❌ 未创建
├── progress.md      ✅
└── prototype/       ✅

docs/superpowers/features/20261019-pm-list-config-bulk/
├── design.md        ✅
├── test-cases.md    ❌ 未创建
├── changes.md       ❌ 未创建
└── progress.md      ✅

docs/superpowers/features/20261019-pm-work-item-types/
├── design.md        ✅
├── test-cases.md    ❌ 未创建
├── changes.md       ❌ 未创建
├── progress.md      ✅
└── prototype/       ✅
```

## 4. 当前状态

| 里程碑 | 状态 | 说明 |
|--------|------|------|
| 原型设计与截图 | 完成 | 54 张画板，1440×900 @2x + 1280×800 @2x |
| 原型评审与冻结 | 评审通过，可冻结 | 角色复评通过；冻结提交待用户确认 |
| 功能设计 | 完成 | 三份功能设计，待与原型一起评审 |
| prototype-contract | 完成初稿 | A2；冻结提交待填 |
| 测试用例文档 | 未开始 | 冻结后先写 `test-cases.md` |
| 代码变更文档 | 未开始 | 冻结后列骨架 |
| 前端实现 | 未开始（改版） | 现有 PM 页面为旧版固定列实现 |
| 后端增量接口 | 未开始 | 见 `pm-design.md` §7.2 |
| 数据库迁移 | 未开始 | 见 `pm-design.md` §8.2 |

## 5. 已确认决策与剩余待确认

### 5.1 已确认（2026-10-20）

| 决策 | 内容 | 落地位置 |
|------|------|----------|
| D-01 | 分页每页条数 **10 / 20 / 50 / 100**，默认 10 | `pm-design.md` §4.8 |
| D-02 | **支持迭代**，`iteration` 为内置事项类型 | `pm-design.md` §2.2、§7.2 A-11、§8.2 G-01 |
| D-03 | 截止时间字段名 **`end_time`**，作为系统字段新增列 | `pm-design.md` §2.4、§8.2 G-02 |
| D-04 | 迁移编号从 **18** 开始，`AGENTS.md` 已更新 v0.25 | `AGENTS.md`「数据库变更」、`pm-design.md` §8.3 |

### 5.2 剩余待确认

完整清单见 [pm-design.md](./pm-design.md) §13.2：

1. **原型冻结**：角色评审通过，等待用户确认冻结提交号后写入 README §7 与 contract §9。
2. **批量接口**：`batch-update` / `batch-transition` 是否本期落地。
3. **操作记录分页**：`activities` 接口是否补 `pageNo` / `pageSize`。
4. **乐观锁**：`pm_work_item` 是否新增 `version` 列。
5. **批量配置存储**：项目 `settings` JSON 还是新表。

## 6. 维护规则

1. **单一入口**：PM 模块级设计只维护 [pm-design.md](./pm-design.md)；功能细节留在对应功能目录，不复制到本文档。
2. **权威顺序**：业务规则 → `pm-design.md` → 功能 `design.md`；接口 → `pm-api.md`；视觉 → 冻结原型；数据 → schema。
3. **改文档先升版**：按 `AGENTS.md` 的版本号与变更记录要求，变更记录放文首。
4. **接口同步**：新增/修改接口时，`pm-api.md` 与 Postman collection 必须在同一分支更新。
5. **实现前冻结**：原型未冻结不得写页面；冻结后修改必须升版并重新评审。
6. **每轮留痕**：功能实施每轮向对应 `progress.md` 追加记录，不等到功能完成。
