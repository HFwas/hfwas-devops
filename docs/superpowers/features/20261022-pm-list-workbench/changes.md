# PM 列表工作台代码变更

> 日期：2026-10-22
> 版本：v0.4
> 状态：已交付（独立校验通过）
> 关联：[design.md](./design.md)、[test-cases.md](./test-cases.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-22 | 开工骨架：计划文件与设计章节映射 |
| v0.2 | 2026-10-22 | 回填实际改动、偏差与真实验证结果 |
| v0.3 | 2026-10-22 | 第 2 次校验修复：恢复默认预览回写、补字段元数据/恢复默认用例；补文档轮次与计数 |
| v0.4 | 2026-10-22 | 第 3 次校验通过；预览排序驱动查询；统一文档措辞与计数 |

---

## 1. 变更清单（计划）

### 1.1 后端

| 文件 | 动作 | 对应设计 | 说明 |
|---|---|---|---|
| `backend/pm-core/.../query/model/QueryOperator.java` | 修改 | §5.2 | 新增 `NOT_LIKE` |
| `backend/pm-core/.../query/engine/JsonSqlDialect.java` | 修改 | §5.2 | `notLike` |
| `backend/pm-core/.../query/engine/FieldResolver.java` | 修改 | §5.2 | 系统字段 `NOT_LIKE` |
| `backend/pm-core/.../field/spi/handler/AbstractFieldTypeHandler.java` | 修改 | §5.2 | 自定义字段 `NOT_LIKE` |
| `backend/pm-core/.../workitem/service/TransitionConditionEvaluator.java` | 修改 | §5.2 | 流转条件 `NOT_LIKE` |
| `backend/pm-core/src/test/.../FieldResolverTest.java` | 修改 | TC-40/41 | NOT LIKE 用例 |
| `backend/pm-core/src/test/.../TransitionConditionEvaluatorTest.java` | 新增 | TC-42/时间比较 | end_time 时间比较 |
| `docs/pm/pm-api.md`、Postman | 修改 | §5.2 | 运算符与示例 |

### 1.2 前端契约与入口

| 文件 | 动作 | 说明 |
|---|---|---|
| `frontend/src/modules/pm/types/index.ts` | 修改 | 补字段、批量/筛选/视图类型 |
| `frontend/src/modules/pm/api/index.ts` | 修改 | 批量更新/流转、批量配置、IO 列 |
| `frontend/src/modules/pm/nav.ts` | 修改 | `all` 路径与 iteration 图标 |
| `frontend/src/modules/pm/utils/format.ts` | 修改 | `formatDate` |
| `frontend/src/router.tsx` | 不改 | 复用 `:typeCode`，`all` 为哨兵 |

### 1.3 纯函数

| 文件 | 说明 |
|---|---|
| `frontend/src/modules/pm/utils/listUrlState.ts` + test | URL 解析/序列化/裁剪 |
| `frontend/src/modules/pm/utils/viewConfig.ts` + test | 视图定义、保存形状、脏状态 |
| `frontend/src/modules/pm/utils/filterTree.ts` + test | 条件树、校验、表达式、快捷筛选 |
| `frontend/src/modules/pm/utils/querySpec.ts` + test | QuerySpec 构建 |
| `frontend/src/modules/pm/utils/columns.tsx` + test | 列目录与单元格 |

### 1.4 组件与页面

| 文件 | 说明 |
|---|---|
| `frontend/src/modules/pm/hooks/usePmListUrlState.ts` | URL 状态 Hook |
| `frontend/src/modules/pm/components/WorkItemViewTabs.tsx` | 视图页签与脏点 |
| `frontend/src/modules/pm/components/ViewConfigDrawer.tsx` | 视图配置抽屉 |
| `frontend/src/modules/pm/components/FilterBuilder.tsx` | 筛选构建器 |
| `frontend/src/modules/pm/components/BatchResultList.tsx` | 批量结果 |
| `frontend/src/modules/pm/components/BulkEditDialog.tsx` | 批量编辑 |
| `frontend/src/modules/pm/components/TransitionDialog.tsx` | 批量流转 |
| `frontend/src/modules/pm/components/BulkDeleteDialog.tsx` | 批量删除 |
| `frontend/src/modules/pm/pages/WorkItemListPage.tsx` | 页面重写 |
| `frontend/src/components/console/AppSidebar.tsx` | 全部工作项与计数 |
| `frontend/src/modules/pm/pages/ProjectListPage.tsx` | 入口改 `items/all` |

### 1.5 测试

| 文件 | 覆盖 |
|---|---|
| `listUrlState.test.ts` | TC-01–05 |
| `viewConfig.test.ts` | TC-06–09 |
| `filterTree.test.ts` | TC-10–14 |
| `querySpec.test.ts` | TC-15–20 |
| `columns.test.tsx` | TC-21–24 |
| `WorkItemListPage.test.tsx` | TC-25–36 |
| `nav.test.ts` / `AppShell.test.tsx` | TC-37–39 |
| `FieldResolverTest.java` | TC-40–41 |

## 2. 原型 → 路由 → 实现文件 → 共享组件

| 原型 | 路由 | 实现 | 共享组件 |
|---|---|---|---|
| `#/workbench` | `/pm/projects/:projectId/items/all` | `WorkItemListPage` | `ListPageShell`、`SearchInput`、`FilterBar`、`BulkActionBar`、`DataTable`、`Pagination` |
| `#/workbench?type=bug` | `/pm/projects/:projectId/items/bug` | 同上 | 类型导航（AppSidebar） |
| 视图配置 O01 | `?overlay=view-config` | `ViewConfigDrawer` | `Sheet`、`Tabs`、`Checkbox` |
| 筛选 O07 | `?overlay=filter` | `FilterBuilder` | `Dialog`、`Select`、`Input` |
| 批量编辑 O02 | `?overlay=bulk-edit` | `BulkEditDialog` | `Dialog`、`BatchResultList` |
| 批量流转 O10 | `?overlay=transition` | `TransitionDialog` | `Dialog`、`BatchResultList` |
| 批量删除 O08 | `?overlay=delete` | `BulkDeleteDialog` | `ConfirmDialog`/`Dialog` |

## 3. 计划外偏差

| # | 偏差 | 原因 |
|---|------|------|
| 1 | `ListPageShell` 新增 `tabs`、`emptyTitle` 两个可选 props | 视图页签需位于页头与工具栏之间、空态标题需可配置；均向后兼容 |
| 2 | 视图配置保存为 `columns: [单对象]`，兼容旧 `[{fieldKey}]` | 后端 `PmSavedView.columns` 为 `List<Map>`，无需迁移 |
| 3 | 内置视图保存按「同名 + 同 typeCode」创建/更新已保存视图 | 内置视图无数据库行；现有接口无个人/共享隔离 |
| 4 | 无项目角色接口，只读态由列表 403 驱动 | 项目成员模型未落地 |
| 5 | 批量删除逐条调用删除接口，默认关闭 | 后端无 batch-delete；设置页在后续阶段 |
| 6 | 故事点/风险/迭代按动态列处理，缺失时显示 `—` | 用户确认不新增迁移 |
| 7 | 批量编辑字段受默认白名单限制（priority/assignee_id/module_id/end_time） | 后端默认配置；自定义字段需 `batch_editable=1` |
| 8 | 搜索的 itemKey 仅解析编号、负责人仅精确匹配姓名 | 后端无 item_key 模糊查询，assignee 为 ID |
| 9 | 批量更新不接受 `status`，状态统一走批量流转 | 后端 `save/batch-update` 禁止直接改状态 |
| 10 | 未实现原型的 `state=empty/loading/...` 演示参数 | 以真实加载/空/错态、density 与 sel 覆盖等价状态 |
| 11 | 无真实登录环境截图对比 | 本机未启动后端；以自动化测试与构建为准 |
| 12 | 「管理事项类型」侧栏入口未加入 | 类型管理页在阶段 3 |
| 13 | 视图配置改为本地即时预览，保存才落库并写 URL | 对齐 AC-04；关闭抽屉丢弃未保存预览，避免预览与 URL 回写 |
| 14 | 「全部工作项」限定项目启用类型 | 保证侧栏类型计数合计等于全部工作项 |
| 15 | 批量 versions 兼容后端 Long→String 序列化 | 避免版本号永远是字符串导致乐观锁失效 |
| 16 | 流转条件 `end_time` 改为时间比较 | 支持日期 GT/GTE/LT/LTE/BETWEEN，原实现按数字解析会异常 |
| 17 | 未知/跨类型保存视图 id 自动回退 `dev` | 避免无激活页签与错误配置 |
| 18 | 「恢复默认」不再清空筛选树 | 按钮语义只恢复列/搜索/密度/排序 |

## 4. 验证结果

| 检查 | 命令 | 结果 |
|------|------|------|
| 迁移规范 | `bash scripts/check-flyway-migrations.sh` | 通过：3 个迁移，最新 V3 |
| 后端 pm-core | `mvn -B -f backend/pom.xml -pl pm-core -am test` | 通过：57 tests（NOT_LIKE 3 条、流转时间比较 2 条） |
| 后端 server | `mvn -B -f backend/pom.xml -pl server -am test` | 通过：34 tests，5 skipped（Docker 未启动） |
| 前端类型检查 | `npx tsc --noEmit` | 通过 |
| 前端测试 | `npm test` | 通过：80 files，327 passed / 1 skipped |
| 前端构建 | `npm run build` | 通过（既有 chunk 体积警告） |
| 手工浏览器验证 | — | 未执行：本机后端/登录环境未启动 |

新增/修改测试：

- `frontend/src/modules/pm/utils/{listUrlState,viewConfig,filterTree,querySpec}.test.ts`、`columns.test.tsx`
- `frontend/src/modules/pm/pages/WorkItemListPage.test.tsx`（11 条）
- `frontend/src/modules/pm/nav.test.ts`、`frontend/src/router.pm.test.ts`、`frontend/src/components/console/AppShell.test.tsx`
- `backend/pm-core/.../FieldResolverTest.java`（2 条 NOT_LIKE）

## 5. 校验结果

### 第 1 次独立校验（2026-10-22）

结论：**失败**。命令全部通过，但发现以下问题，已修复：

| # | 问题 | 处理 |
|---|------|------|
| 1 | 日期筛选「为空/不为空」被静默丢弃 | `querySpec` 日期分支先映射 `IS_NULL/IS_NOT_NULL`；补 `mapsDateEmptyOperators` |
| 2 | `pm-api.md` 未升版、无 NOT_LIKE 失败示例；Postman 失败示例请求/响应不自洽 | 升 v1.6；补 403 失败示例；Postman 改为真实可复现的 403 |
| 3 | 测试用例引用 5 个不存在的测试名 | 改为真实测试名，并补 TC-28/29/32/33/42/43/44 |
| 4 | 批量 `version` 因 Long→String 永远不发送 | 类型放宽为 `number \| string`，转换 `Number` 后发送；补字符串版本用例 |
| 5 | 流转条件 `end_time` 按数字解析异常 | 改为时间比较并补 `TransitionConditionEvaluatorTest` |
| 6 | URL 自定义筛选/排序在字段元数据到达前丢失 | 增加 `fieldsReady`，未就绪时保留原始树/排序 |
| 7 | 阻塞风险视图把 risk 与 status 混为 OR；本周上界非下周一 | 改为 risk 优先、否则 status；上界改为下周一 |
| 8 | 搜索防抖 300ms、列配置非即时 | 页面传 350ms；视图配置变更即时写 URL |
| 9 | 未知保存视图 id 无回退；恢复默认清筛选 | 回退 `dev`；恢复默认保留筛选 |
| 10 | 「全部工作项」包含停用类型事项，计数不一致 | 全部视图限定项目启用类型 |

### 第 2 次独立校验（2026-10-22）

结论：**失败**，仅剩 1 个功能回归与文档轮次记录：

| # | 问题 | 处理 |
|---|------|------|
| 1 | 「恢复默认」被即时预览回写，实际失效 | 改为本地 `previewConfig`（不写 URL），保存才落库；补 `restoreViewConfigResetsWithoutPreviewEcho` |
| 2 | `progress.md` 未记录修复轮次、`test-cases.md`/`changes.md` 未升版 | 本轮补齐版本与变更记录 |
| 3 | TC-33 引用不精确、TC-43/44 表格断裂 | 修正引用并合并表格 |

### 第 3 次独立校验（2026-10-22）

结论：**通过**。B1 专项复核确认恢复默认不再回写；后端 57、server 34（5 skipped）、前端 327、`tsc` 与构建全部通过；TC-01–TC-46 引用与断言核对通过；文档版本与轮次记录补齐。

剩余非阻断项：MV-01~05 与真实截图未执行（无登录环境）；快捷筛选「阻塞风险」与内置视图语义后续可统一；关闭抽屉未保存预览直接丢弃（有意行为）。
