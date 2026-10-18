# 流水线编辑器重构与全局弹框样式 —— 代码变更文档

> 日期：2026-10-18
> 版本：v0.8
> 状态：已通过第 2 轮独立复核；含原型契约 D05/D06/D07/D17 对齐修复
> 对应设计：[`design.md`](./design.md) v0.5
> 对应用例：[`test-cases.md`](./test-cases.md) v0.4

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-18 | 开工骨架：列出计划新增/修改文件及对应设计章节 |
| v0.2 | 2026-10-18 | 补全实际改动、偏差与手工用例；前后端、迁移、测试全部落地 |
| v0.3 | 2026-10-18 | 按首轮独立校验修正：17 迁移零 UPDATE，回填移到 `CloneConfigBackfill`；补测试与文档偏差 |
| v0.4 | 2026-10-18 | 第 2 轮独立复核通过；修正剩余 8 条非阻断文档项 |
| v0.5 | 2026-10-18 | 后续纯样式调整：详情/历史头部扁平化（图标返回、sans 标题、指标走 DetailShell 槽） |
| v0.6 | 2026-10-18 | 详情指标移除“阶段”卡 |
| v0.7 | 2026-10-18 | 运行历史页签隐藏指标行 |
| v0.8 | 2026-10-18 | 按原型契约关闭 D05/D06/D07/D17；D13 复核为与原型一致；补测试与真实页面验证 |

---

## 1. 计划改动

| 文件 | 动作 | 对应设计 | 说明 |
|------|------|----------|------|
| `frontend/src/modules/pipeline/pipelineDraft.ts` | 修改 | §4、§5.3、§5.4 | 仓库配置移入克隆绑定；阶段自动命名；身份只读辅助 |
| `frontend/src/modules/pipeline/types/pipeline.ts` | 修改 | §4.2 | `PipelineSavePayload` 移除三个字段 |
| `frontend/src/modules/pipeline/pages/PipelineEditorPage.tsx` | 修改 | §5.1～§5.5 | 头部内联名称、克隆配置、移除缩放、身份只读、最近克隆 |
| `frontend/src/modules/pipeline/pages/PipelineDetailPage.tsx` | 修改 | §5.6 | Commit 指标卡 |
| `frontend/src/components/ui/dialog.tsx` | 修改 | §5.7 | Kite 浮层样式 |
| `frontend/src/components/ui/alert-dialog.tsx` | 修改 | §5.7 | 与 Dialog 对齐 |
| `frontend/src/modules/pipeline/pipelineDraft.test.ts` | 修改 | 全部 | 新数据模型用例 |
| `frontend/src/modules/pipeline/pages/PipelineEditorPage.test.tsx` | 修改 | 全部 | 编辑器新交互 |
| `frontend/src/modules/pipeline/pages/PipelineDetailPage.test.tsx` | 修改 | §5.6 | Commit 指标 |
| `backend/pipeline-core/.../dto/PipelineSaveDTO.java` | 修改 | §4.2 | 移除三个入参 |
| `backend/pipeline-core/.../service/PipelineDefinitionService.java` | 修改 | §4.2 | 从克隆绑定派生 |
| `backend/pipeline-core/.../service/DependencyScanService.java` | 修改 | §4.3 | 写入克隆绑定 |
| `backend/pipeline-core/src/test/.../PipelineDefinitionServiceTest.java` | 修改 | §4.2 | 派生与校验用例 |
| `backend/pipeline-core/src/test/.../DependencyScanServiceTest.java` | 修改 | §4.3 | 绑定断言 |
| `deploy/charts/backend/files/db/05-pipeline-schema.sql` | 不修改（计划已调整） | §4.1 | 外部路径种子由 17 提供 |
| `deploy/charts/backend/files/db/17-clone-job-config.sql` | 新增 | §4.4 | 参数种子（零 UPDATE） |
| `backend/server/src/main/resources/db/pipeline-schema.sql` | 修改 | §4.4 | embedded 种子 |
| `backend/server/src/main/java/.../CloneConfigBackfill.java` | 新增 | §4.4 | 所有 schema 模式启动回填；`SqliteSchemaInitializer` 不再承担回填 |
| `docs/pipeline/prototype/**` | 修改 | §8.1 | 原型 v0.8 冻结 |

## 2. 实际改动

### 2.1 前端

| 文件 | 改动 |
|------|------|
| `pipelineDraft.ts` | `PipelineDraft` 移除 `repoUrl/gitRef/credentialId`；新增 `cloneJob / cloneConfigValue / draftRepoUrl / draftGitRef`；阶段新增预填 `阶段 N`，空阶段首任务按类型改名；校验从 `REPO_URL` 绑定读取；`toSavePayload` 只提交 `name + stages`；删除顶层 GIT_REF 同步 |
| `types/pipeline.ts` | `PipelineSavePayload` 移除三个字段 |
| `PipelineEditorPage.tsx` | 标题旁内联名称；删除基本信息行；克隆检查器内配置仓库地址/代码分支/凭证；任务名称/类型禁用；克隆隐藏高级设置；移除缩放状态、工具栏、`ScalableTrack`；编辑页显示“最近克隆” |
| `PipelineDetailPage.tsx` | 指标卡新增 Commit（取最近运行 `commitSha`，无则 `—`）；后续扁平化：返回改为图标按钮、标题恢复 sans、指标改用 `DetailShell.metrics` 槽；移除“阶段”指标；指标行仅在详情页签显示 |
| `dialog.tsx` | `bg-card`、`rounded-xl`、`shadow-lg`、`bg-black/40`；标题/底部分隔线 |
| `alert-dialog.tsx` | 圆角 `rounded-xl`、`bg-card`、遮罩 `/40`；标题/底部分隔线 |
| `ResourceOverview.tsx` | `OverviewMetricCards` 新增 `columns` 参数（2–6 的静态类名映射），默认 6；流水线详情传 5，消除末行留空；新增 `data-columns` 供测试断言 |
| `DetailShell.tsx` | 新增 `metricColumns` 并透传给 `OverviewMetricCards`，默认行为不变 |
| `PipelineDetailPage.tsx`（续） | 标题下恢复最近运行状态与相对时间；指标卡传 `metricColumns={metrics.length}` |
| `PipelineDefinitionCanvas.tsx` | 定义画布阶段卡 `w-56` → `w-[196px]`，对齐原型 |
| `PipelineEditorPage.tsx`（续） | CLONE 任务不再渲染 `kindMeta.hint`，只保留一条“克隆命令由平台生成”提示 |
| 测试 | `pipelineDraft.test.ts`、`PipelineEditorPage.test.tsx`、`PipelineDetailPage.test.tsx`、`PipelineFlow.test.tsx` 更新；新增 `dialog.test.tsx`、`alert-dialog.test.tsx`；本轮补 5 列指标、标题状态行与克隆提示去重断言 |

### 2.2 后端与数据库

| 文件 | 改动 |
|------|------|
| `PipelineSaveDTO.java` | 移除 `repoUrl/gitRef/credentialId` |
| `PipelineDefinitionService.java` | `cloneConfig(dto)` 从 CLONE 参数绑定派生三列；无克隆时置空/`main`；凭证 ID 解析失败 400 |
| `DependencyScanService.java` | 克隆任务写入 `REPO_URL/GIT_REF/CREDENTIAL_ID` 绑定，不再设置 DTO 顶层字段 |
| `pipeline-schema.sql`（embedded） | CLONE 参数新增 `REPO_URL`（id 6）与 `CREDENTIAL_ID`（id 7） |
| `17-clone-job-config.sql` | 新增迁移：仅补参数种子（零 UPDATE，兼容 `migrate.py` 安全策略） |
| `CloneConfigBackfill.java` | 新增启动组件：在所有 schema 模式下把旧流水线列回填到 CLONE 绑定，幂等 |
| `05-pipeline-schema.sql` | **未修改**：外部路径由 17 提供种子，避免改动既有迁移文件 |
| 测试 | `PipelineDefinitionServiceTest` 助手改为写入克隆绑定；新增派生/凭证校验用例；`DependencyScanServiceTest` 断言三项绑定；新增 `CloneConfigBackfillTest`（回填、幂等、不覆盖已有值） |

### 2.3 文档与原型

- 原型 v0.8 冻结（README §8）。
- `design.md` v0.5、`test-cases.md` v0.3、`changes.md` v0.3、`progress.md` 追加轮次。

## 3. 计划外偏差

| 偏差 | 原因 | 影响 |
|------|------|------|
| `CREDENTIAL_ID` 种子参数类型使用 `input` 而非自定义 `credential` | 任务市场参数编辑器没有 `credential` 类型，避免引入第二套参数类型 | 流水线编辑器按 `paramKey` 特殊渲染为 AsyncSelect；任务市场仍显示为普通输入 |
| 编辑页历史遗留的 CLONE `stack/runtimeVersion/toolVersion` 不清空 | 执行不使用这些字段，清空无收益 | 无 |
| `PipelineSaveDTO` 移除字段后，旧请求体中的顶层仓库字段被忽略；缺少克隆绑定会返回 400 | 按“不做存量兼容”约定推进 | 旧客户端需改用 CLONE 参数 |
| 新增原型画板 `21-editor-new.png` | 用截图验证新建页身份只读 | 原型画板从 20 张增至 21 张 |
| 编辑页最近克隆仓库优先取 CLONE `REPO_URL`，缺失时回退 `pipeline.repoUrl` | 兼容回填尚未执行的本地库，展示不空白 | 无 |
| `DialogFooter` 外边距 `mt-4` → `mt-2` | 新增 `border-t pt-3` 后保持视觉间距 | 全站弹框底部略紧凑 |
| 新增 `prototype/README.md`（v0.2）引用原型 v0.8 | 功能目录按规范保留原型引用 | 文档新增 |
| 详情/历史头部扁平化：图标返回、sans 标题、指标走 `DetailShell` 槽 | 用户要求参考 Kite；按原型先行规范 §2.2“纯样式修复”豁免，附前后画板 `03-detail.png`、`06-history.png` | 仅视觉，无行为变化 |
| 按原型契约修复 D05/D06/D07/D17 | 契约登记为实现漂移，按冻结原型对齐 | 详情状态行、5 列指标、196px 阶段卡、克隆提示去重；无行为变化 |
| D13 阶段名称输入框复核为非偏差 | 原型 `.stage-name-input` 同样为透明边框、hover 才显示；此前分类有误 | 未改代码 |
| 新增模块原型契约 `docs/pipeline/prototype/prototype-contract.md` | 把原型 ↔ 实现差异变成可追踪、可关闭的记录 | 文档新增 |

## 4. 豁免说明

- 无。

## 5. 校验结果

### 第 1 轮独立校验（2026-10-18）

**结论：不通过。** 功能实现与前后端测试通过；阻断项在数据库交付路径。

| # | 问题 | 处理 |
|---|------|------|
| D1/D2 | `17-clone-job-config.sql` 的 UPDATE 被 `migrate.py` 拒绝，Helm 默认迁移会失败 | 17 改为只补种子；回填移到 `CloneConfigBackfill` 启动组件 |
| D3 | TC-ED-14 预期“代码检查”，实现为“拉取” | 文档改为“拉取” |
| D4 | design 写 7 位短 SHA，实现为 8 位 | design 统一为 `formatCommit` 8 位，测试断言精确值 |
| D5/D10 | 原型版本引用仍写 v0.3 | 同步 v0.8 |
| D6 | TC-BE-04 用 `not-a-url`，未覆盖协议分支 | 改为 `ftp://` 并断言协议错误 |
| D7 | 依赖扫描测试未覆盖 `CREDENTIAL_ID` | 增加 credentialId 与绑定断言 |
| D8/D9 | 未记录最近克隆 repo 回退、DialogFooter `mt-2` | 补入 §3 |
| D11 | 修改了既有 `05-pipeline-schema.sql` | 恢复未修改，外部种子由 17 提供 |
| D12 | `draftCredentialId` 未使用 | 删除 |

修复后重新执行：`pytest scripts/tests/test_migrate.py` 14 passed；`mvn -pl backend/server -am test` 通过；前端 254 passed / 1 skipped；`tsc`、`build` 通过。

### 第 2 轮独立校验（2026-10-18）

**结论：通过。** D1～D12 全部关闭；剩余 8 条非阻断文档项已在本轮修正。

| 检查 | 结果 |
|------|------|
| `migrate.py` 全目录迁移 + 二次执行 | exit 0；17 被记录并 skip；无 UPDATE |
| `pytest scripts/tests/test_migrate.py` | 14 passed |
| `mvn -pl backend/server -am test` | BUILD SUCCESS；server 31 tests，含 2 个回填测试 |
| `mvn -pl backend/pipeline-core -am clean test` | 260 tests，0 failures |
| `npm test` | 254 passed / 1 skipped |
| `npx tsc --noEmit` / `npm run build` | 通过 |
| 自动化位置 | 全部真实存在并通过 |

剩余非阻断项处理：

| # | 处理 |
|---|------|
| R1 | test-cases 对应设计版本改为 v0.5 |
| R2 | changes §2.1 删除 `draftCredentialId`；§2.3 版本引用更新 |
| R3 | design §2.1/§9 改为“17 只补种子 + 启动回填” |
| R4 | feature prototype README 文首版本改为 v0.2（原型已冻结） |
| R5 | changes §1 计划表更新为实际方案（05 不改、17 零 UPDATE、新增 CloneConfigBackfill） |
| R6 | TC-DB-04 标注为套件级验证 |
| R7 | 清理 `SqliteSchemaInitializer` 尾部空行 |
| R8 | 详情 Commit 测试增加“不显示全长 SHA”断言 |

### 第 3 轮：原型契约对齐（2026-10-18）

依据 [`prototype-contract.md`](../../../pipeline/prototype/prototype-contract.md) 关闭 D05、D06、D07、D17；D13 复核为与原型一致，未改代码。

| 检查 | 结果 |
|------|------|
| 受影响用例 | `npx vitest run patterns.test.tsx PipelineDetailPage.test.tsx PipelineEditorPage.test.tsx`：27 passed |
| 全量前端 | `npm test`：255 passed / 1 skipped |
| 类型检查 | `npx tsc --noEmit`：通过 |
| 构建 | `npm run build`：通过 |
| 容器产物 | `docker compose build frontend-build` + `docker compose run --rm frontend-build`；`http://localhost:8000` 提供 `index-D48zBzR5.js` |
| 真实页面 | 详情状态行、5 列指标、196px 阶段卡、克隆提示去重均通过截图确认；证据见 `docs/pipeline/prototype/actual/03-detail.png`、`04-editor.png`、`06-history.png` |

剩余：D01、D02、D03、D04、D10、D12、D19 待产品确认或处理；D14、D18 需回写原型并升版。
