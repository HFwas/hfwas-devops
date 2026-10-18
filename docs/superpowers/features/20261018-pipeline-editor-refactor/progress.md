# 流水线编辑器重构与全局弹框样式 — AI 协作进度日志

> 日期：2026-10-18
> 版本：v1.0
> 说明：本文件为追加式日志。`版本` 表示格式版本，不随每轮追加递增；每轮记录见文末。

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v1.0 | 2026-10-18 | 建立日志；记录设计与原型 v0.3 产出，等待原型评审冻结 |

---

## 第 1 轮 · 2026-10-18 16:30 · 设计与原型 v0.3

- **状态**：待评审
- **触发**：用户提出 4 项编辑器/弹框改造要求，并确认仓库配置真正移入克隆任务参数
- **目标**：完成功能设计，更新可点击原型到 v0.3
- **读取**：`pipelineDraft.ts`、`PipelineEditorPage.tsx`、`PipelineDetailPage.tsx`、`PipelineDefinitionService.java`、`05-pipeline-schema.sql`、`SqliteSchemaInitializer.java`、`kite-frontend-style-guide.md`

### 做了什么

1. 明确 4 项诉求的范围与数据归属；确认仓库/分支/凭证移入 CLONE 参数，流水线列作为保存时派生缓存。
2. 设计后端保存派生、`DependencyScanService` 适配、迁移 `17-*` 与 embedded 回填。
3. 设计编辑器头部、克隆配置、阶段自动命名、移除缩放、最近克隆回显与详情 Commit 卡片。
4. 设计 Dialog / AlertDialog 统一 Kite 浮层样式。
5. 更新模块原型到 v0.3 并重新生成受影响画板。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `docs/superpowers/features/20261018-pipeline-editor-refactor/design.md` | 新增 | 功能设计 |
| `docs/superpowers/features/20261018-pipeline-editor-refactor/progress.md` | 新增 | 本日志 |
| `docs/pipeline/prototype/index.html` | 修改 | 原型 v0.3 |
| `docs/pipeline/prototype/README.md` | 修改 | 版本、参数、评审记录 |
| `docs/pipeline/pipeline-management-prototype-spec.json` / `.md` | 修改 | v0.3 规格 |
| `docs/pipeline/prototype/shots/03,04,05,11,12,13,16,17,19` | 修改 | 重新生成 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| 规格 JSON | `node -e JSON.parse(...)` | 通过 |
| 画板 | Chrome headless | 通过 |
| 视觉抽查 | `04-editor.png`、`11-modal-run-params.png` | 通过 |

### 决策与取舍

- **决策**：仓库/分支/凭证真正移入 CLONE 参数，流水线列改为保存时派生。**理由**：符合用户选择，执行器无需改动。**未选方案**：仅移动编辑入口。
- **决策**：迁移 `17-*` + embedded 回填。**理由**：保留现有流水线定义，且两种 schema 模式一致。
- **决策**：弹框只改共享组件。**理由**：全站统一，避免业务页私有皮肤。

### 偏差与假设

- `ASSUMPTION: PipelineSaveDTO 移除三个入参后，仓库内调用方只有 DependencyScanService 与测试需要同步。`
- 原型为静态演示，克隆完成态用示例 commit 表示。

### 未完成 / 风险

- 原型未冻结，代码未开始。
- 迁移依赖 SQLite JSON1；已在 Python sqlite3 验证可用。

### 下一步

- **唯一动作**：评审并冻结原型 v0.3（重点看编辑器头部、克隆检查器、弹框分隔线、详情 Commit 卡）。
- 冻结后编写 `test-cases.md`、`changes.md` 并开始实现。

---

## 第 2 轮 · 2026-10-18 16:45 · 新增编辑页任务身份只读

- **状态**：待评审
- **触发**：用户要求“编辑时候的任务名称和任务类型禁止修改”，并确认编辑页全部锁定（含本次新加任务）
- **目标**：把该约束纳入设计与原型
- **读取**：`PipelineEditorPage.tsx` 检查器、`pipelineDraft` 的任务创建逻辑

### 做了什么

1. 设计新增 §5.4：编辑页任务名称/类型只读，类型通过“添加任务”选定，名称自动生成；新建页可编辑。
2. 原型升到 v0.4：编辑页检查器两个字段禁用并显示提示；新建页保持可编辑。
3. 重新生成 `04-editor.png`、`16-editor-invalid.png`。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `design.md` | 修改 | v0.2；新增需求 5、§5.4、AC-09 |
| `docs/pipeline/prototype/index.html` | 修改 | v0.4；只读身份字段 |
| `docs/pipeline/prototype/README.md` | 修改 | v0.4 |
| `docs/pipeline/pipeline-management-prototype-spec.json` / `.md` | 修改 | v0.4 |
| `docs/pipeline/prototype/shots/04,16` | 修改 | 重新生成 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| 规格 JSON | `node -e JSON.parse(...)` | 通过 |
| 视觉抽查 | `04-editor.png` | 通过：名称/类型禁用且有提示 |

### 决策与取舍

- **决策**：编辑页全部锁定，包括本次新加任务。**理由**：用户确认；名称在添加时由类型自动生成，无需再改。**未选方案**：仅锁定已保存任务。

### 偏差与假设

- 该约束为前端交互约束，后端仍允许更新；暂不引入任务版本模型。

### 未完成 / 风险

- 原型仍未冻结，代码未开始。

### 下一步

- **唯一动作**：冻结原型 v0.4 后，编写 `test-cases.md`、`changes.md` 并开始实现。

---

## 第 3 轮 · 2026-10-18 16:55 · 克隆任务隐藏高级设置

- **状态**：待评审
- **触发**：用户指出代码克隆不需要技术栈/运行时/工具版本
- **目标**：确认并移除克隆任务的高级设置
- **读取**：`TektonCompiler` 的 CLONE Step 处理（固定 `CLONE_IMAGE`）、编辑器检查器

### 做了什么

1. 确认克隆步骤使用固定镜像，与 stack/runtime/toolVersion 无关。
2. 设计 §5.2 增加“CLONE 不显示高级设置”；构建/测试/镜像等任务保留。
3. 原型升到 v0.5 并重新生成 `04-editor.png`、`16-editor-invalid.png`。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `design.md` | 修改 | v0.3；§5.2、AC-02 |
| `docs/pipeline/prototype/index.html` | 修改 | v0.5 |
| `docs/pipeline/prototype/README.md` | 修改 | v0.5 |
| `docs/pipeline/pipeline-management-prototype-spec.json` / `.md` | 修改 | v0.5 |
| `docs/pipeline/prototype/shots/04,16` | 修改 | 重新生成 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| 规格 JSON | `node -e JSON.parse(...)` | 通过 |
| 视觉抽查 | `04-editor.png` | 通过：克隆检查器无高级设置 |

### 决策与取舍

- **决策**：仅隐藏前端字段，不清除历史值。**理由**：执行不使用这些字段，清空无收益。**未选方案**：保存时清空 CLONE 的 stack/runtime/toolVersion。

### 偏差与假设

- `ASSUMPTION: CLONE 步骤的镜像与参数完全由平台常量与克隆参数决定，不使用任务的高级设置。`

### 未完成 / 风险

- 原型仍未冻结，代码未开始。

### 下一步

- **唯一动作**：冻结原型 v0.5 后，编写 `test-cases.md`、`changes.md` 并开始实现。

---

## 第 6 轮 · 2026-10-18 17:10 · 前后端与迁移实施完成

- **状态**：待评审（等待独立 agent 校验）
- **触发**：用户确认“开始实施”，原型 v0.8 冻结
- **目标**：按设计与用例完成实现，测试与构建通过
- **读取**：`design.md`、`test-cases.md`、`changes.md`、`PipelineEditorPage`、`PipelineDefinitionService`、schema 与迁移

### 做了什么

1. 后端：`PipelineSaveDTO` 移除三个字段；保存时从 CLONE 绑定派生；依赖扫描改写绑定。
2. 数据库：新增 CLONE 参数种子、迁移 `17-clone-job-config.sql`、embedded 回填。
3. 前端：编辑器头部/克隆配置/身份只读/阶段命名/移除缩放/最近克隆；详情 Commit；弹框统一。
4. 更新与新增测试；迁移脚本用临时 SQLite 验证幂等。

### 变更文件

见 `changes.md` §2。

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| 后端 pipeline 测试 | `mvn -pl backend/pipeline-core -am clean test` | 260 passed / 7 skipped |
| 后端 server 测试 | `mvn -pl backend/server -am test` | 通过 |
| 前端全量 | `npm test` | 254 passed / 1 skipped |
| 类型检查 | `npx tsc --noEmit` | 通过 |
| 构建 | `npm run build` | 通过 |
| 迁移 SQL | Python sqlite3 临时库执行两次 | 首次回填、二次幂等 |

### 决策与取舍

- **决策**：`CREDENTIAL_ID` 种子用 `input` 类型，前端按 key 特殊渲染。**理由**：不扩散任务市场参数类型。
- **决策**：旧顶层字段不做兼容。**理由**：项目约定不做存量兼容；迁移负责数据回填。

### 偏差与假设

- 见 `changes.md` §3。

### 未完成 / 风险

- 独立 agent 校验未执行；手工用例（凭证搜索选择、真实浏览器弹框、embedded 启动回填）未执行。

### 下一步

- **唯一动作**：用独立 agent 校验文档一致性并逐条执行用例，结论写回 `changes.md` §5。

---

## 第 7 轮 · 2026-10-18 17:20 · 按首轮独立校验修复

- **状态**：待复核
- **触发**：独立 agent 首轮不通过（迁移 17 的 UPDATE 被 `migrate.py` 拒绝）
- **目标**：恢复 external/Helm 迁移路径，关闭文档偏差
- **读取**：`migrate.py`、`scripts/tests/test_migrate.py`、校验报告

### 做了什么

1. **修复阻断**：`17-clone-job-config.sql` 移除 UPDATE，只保留参数种子；旧数据回填改由新启动组件 `CloneConfigBackfill` 在所有 schema 模式下执行。
2. 移除 `SqliteSchemaInitializer` 中的重复回填；`05-pipeline-schema.sql` 恢复未修改。
3. 新增 `CloneConfigBackfillTest`（回填、幂等、不覆盖已有值）。
4. 修正文档：TC-ED-14 改为“拉取”；Commit 统一 8 位；原型版本引用 v0.8；补 TC-BE-04/06 覆盖。
5. 重新执行：`pytest scripts/tests/test_migrate.py` 14 passed；后端 server 测试通过；前端 254 passed；tsc/build 通过。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `17-clone-job-config.sql` | 修改 | 零 UPDATE |
| `CloneConfigBackfill.java` | 新增 | 启动回填 |
| `SqliteSchemaInitializer.java` | 修改 | 移除重复回填 |
| `05-pipeline-schema.sql` | 恢复 | 不改既有迁移 |
| `CloneConfigBackfillTest.java` | 新增 | 回填与幂等 |
| 后端测试 | 修改 | ftp 协议、凭证绑定断言 |
| 文档 | 修改 | design v0.5、test-cases v0.3、changes v0.3 |

### 验证

| 检查 | 命令 | 结果 |
|------|------|------|
| 迁移路径 | `python3 -m pytest scripts/tests/test_migrate.py -q` | 14 passed |
| 后端 | `mvn -pl backend/server -am test` | 通过 |
| 前端 | `npm test` | 254 passed / 1 skipped |
| 类型/构建 | `npx tsc --noEmit`、`npm run build` | 通过 |

### 决策与取舍

- **决策**：回填放应用启动组件，而不是放宽 `migrate.py` 的 UPDATE 白名单。**理由**：保持迁移零 UPDATE 的安全基线，embedded/external 行为一致。**未选方案**：把 17 加入 UPDATE 白名单。

### 偏差与假设

- 回填在任何 schema 模式下都会执行一次；依赖 `pipeline_job` 表存在。

### 未完成 / 风险

- 独立复核未执行；凭证 AsyncSelect 与暗色弹框仍为手工用例。

### 下一步

- **唯一动作**：重新运行独立 agent 校验，结论写回 `changes.md` §5。

---

## 第 8 轮 · 2026-10-18 17:30 · 第 2 轮复核通过

- **状态**：已完成
- **触发**：第 2 轮独立复核报告通过
- **目标**：关闭剩余非阻断文档项，确认交付
- **读取**：复核报告 R1～R8

### 做了什么

1. 修正 test-cases / design / changes / feature prototype README 的版本与迁移表述。
2. 清理 `SqliteSchemaInitializer` 尾部空行；详情 Commit 测试补“不显示全长 SHA”断言。
3. 把第 2 轮结论与 R1～R8 处理写入 `changes.md` §5。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `test-cases.md` | 修改 | v0.3；版本引用、DB 用例 |
| `design.md` | 修改 | v0.5；迁移与风险表述 |
| `changes.md` | 修改 | v0.4；计划表、实际改动、两轮校验结论 |
| `prototype/README.md` | 修改 | v0.2；原型已冻结 |
| `SqliteSchemaInitializer.java` | 修改 | 仅清尾部空行 |
| `PipelineDetailPage.test.tsx` | 修改 | 全长 SHA 断言 |

### 验证

| 检查 | 结果 |
|------|------|
| 第 2 轮独立复核 | 通过，D1～D12 全部关闭 |
| 后端 / 前端 / 迁移 / 类型 / 构建 | 全部通过 |

### 决策与取舍

- 无新增决策。

### 偏差与假设

- 手工用例（凭证 AsyncSelect、暗色弹框、真实浏览器）仍留待人工验收。

### 未完成 / 风险

- 运行中的前端容器尚未重建；后端容器未部署本次改动。

### 下一步

- **唯一动作**：重建并部署前后端容器，完成 3 条手工验收用例。

---

## 第 9 轮 · 2026-10-18 17:55 · 详情头部与指标扁平化

- **状态**：已完成（纯样式）
- **触发**：用户提供详情页截图，要求参考 Kite 优化布局；选择“头部与指标扁平化”
- **目标**：降低头部视觉重量，指标回到 `DetailShell` 槽位
- **读取**：`PipelineDetailPage.tsx`、`DetailShell.tsx`、Kite 规范 §6.3

### 做了什么

1. 详情/运行历史返回按钮改为图标按钮（`aria-label="返回流水线列表"`），标题去掉 `font-console`，恢复 sans。
2. 指标卡从 children 内的手写 `OverviewMetricCards` 改为 `DetailShell.metrics` 槽，去掉重复外边距与嵌套。
3. 原型升到 v0.9：返回图标化、指标 6 列；重新生成 `03-detail.png`、`06-history.png`。
4. 补充 `PipelineDetailPage` 测试对 `aria-label` 的断言。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `frontend/src/modules/pipeline/pages/PipelineDetailPage.tsx` | 修改 | 头部与指标槽位 |
| `frontend/src/modules/pipeline/pages/PipelineDetailPage.test.tsx` | 修改 | 断言返回图标 |
| `docs/pipeline/prototype/**` | 修改 | v0.9 |
| `changes.md` | 修改 | v0.5；纯样式豁免记录 |

### 验证

| 检查 | 命令 | 结果 |
|------|------|------|
| 详情测试 | `npx vitest run PipelineDetailPage.test.tsx` | 4 passed |
| 前端全量 | `npm test` | 254 passed / 1 skipped |
| 类型/构建 | `npx tsc --noEmit`、`npm run build` | 通过 |

### 决策与取舍

- **决策**：保留返回能力但改为图标按钮。**理由**：Kite 头部不放大段文字返回，但上下文感知返回仍有价值。
- **决策**：仅做头部与指标扁平化，不改两栏结构或画布高度。**理由**：用户只选择了该范围。

### 偏差与假设

- 按 `prototype-first-standard.md` §2.2“纯样式修复”豁免可点击原型评审，以 `03-detail.png` / `06-history.png` 作为前后对照。

### 未完成 / 风险

- 未部署到运行中的前端容器。

### 下一步

- **唯一动作**：确认样式后提交本次调整并重建前端容器。

---

## 第 10 轮 · 2026-10-18 18:00 · 详情移除“阶段”指标

- **状态**：已完成（纯样式）
- **触发**：用户截图指出详情页“阶段”指标多余
- **目标**：详情指标只保留仓库、分支、Commit、最近运行、凭证

### 做了什么

1. `PipelineDetailPage` 删除“阶段”指标及 `stageNames` 计算。
2. 测试改为断言指标区不含“阶段”。
3. 原型升到 v0.10：移除阶段卡、指标 5 列、角标版本同步。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `frontend/src/modules/pipeline/pages/PipelineDetailPage.tsx` | 修改 | 移除阶段指标 |
| `frontend/src/modules/pipeline/pages/PipelineDetailPage.test.tsx` | 修改 | 断言不含阶段 |
| `docs/pipeline/prototype/**`、spec | 修改 | v0.10 |
| `design.md` / `changes.md` | 修改 | v0.6；指标 5 张 |

### 验证

| 检查 | 命令 | 结果 |
|------|------|------|
| 详情测试 | `npx vitest run PipelineDetailPage.test.tsx` | 4 passed |
| 前端全量 | `npm test` | 254 passed / 1 skipped |
| 类型/构建 | `npx tsc --noEmit`、`npm run build` | 通过 |

### 决策与取舍

- **决策**：只移除指标卡，阶段信息仍由画布表达。**理由**：指标区重复画布已有信息。

### 偏差与假设

- 纯样式/信息层级调整，按原型规范豁免，画板 `03-detail.png` 为对照。

### 未完成 / 风险

- 未部署到运行中的前端容器；本次调整未提交。

### 下一步

- **唯一动作**：确认后提交并重建前端容器。

---

## 第 11 轮 · 2026-10-18 18:05 · 运行历史页签隐藏指标行

- **状态**：已完成（纯样式）
- **触发**：用户要求运行历史 tab 移除仓库/分支/阶段等指标行
- **目标**：指标只在“详情”页签显示

### 做了什么

1. `PipelineDetailPage` 的 `metrics` 仅在 `tab === 'definition'` 时传给 `DetailShell`。
2. 测试改为切换运行历史后断言指标区不存在。
3. 原型升到 v0.11：`renderHistory` 移除指标块，重新生成 `06-history.png`。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `PipelineDetailPage.tsx` | 修改 | 指标按页签条件渲染 |
| `PipelineDetailPage.test.tsx` | 修改 | 历史页签无指标断言 |
| `docs/pipeline/prototype/**`、spec | 修改 | v0.11 |
| `design.md` / `test-cases.md` / `changes.md` | 修改 | 记录页签条件 |

### 验证

| 检查 | 命令 | 结果 |
|------|------|------|
| 详情测试 | `npx vitest run PipelineDetailPage.test.tsx` | 4 passed |
| 前端全量 | `npm test` | 254 passed / 1 skipped |
| 类型/构建 | `npx tsc --noEmit`、`npm run build` | 通过 |

### 决策与取舍

- **决策**：保留详情页签的 5 张指标，仅历史页签隐藏。**理由**：用户只要求历史 tab 移除。

### 偏差与假设

- 纯样式/信息层级调整，豁免原型评审，以 `06-history.png` 对照。

### 未完成 / 风险

- 未提交、未部署。

### 下一步

- **唯一动作**：确认后与头部扁平化一起提交并重建前端容器。

---

## 第 4 轮 · 2026-10-18 17:20 · 补齐编辑器阶段间箭头

- **状态**：待评审
- **触发**：用户指出原型编辑器页面缺少箭头，质疑为未经要求的重大变更
- **目标**：定位箭头来源并修复原型与实现的漂移
- **读取**：`git show HEAD:docs/pipeline/prototype/index.html`、提交版 `04-editor.png`、`PipelineEditorPage.tsx`

### 做了什么

1. 核对结论：前端实现一直有阶段间箭头（`PipelineEditorPage.tsx:696`、`:830`）；提交版原型 v0.1 的编辑器本来就没有该箭头，本次改动没有删除它。
2. 承认这是原型与实现的既有漂移，本轮补齐：阶段之间与“添加阶段”前增加 `ChevronRight`。
3. 原型升到 v0.7，重新生成 `04`、`05`、`16`。
4. 未修改前端编辑器代码（箭头原本存在）。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `docs/pipeline/prototype/index.html` | 修改 | v0.7；阶段箭头样式与节点 |
| `docs/pipeline/prototype/README.md` | 修改 | v0.7 |
| `docs/pipeline/pipeline-management-prototype-spec.json` / `.md` | 修改 | v0.7；`stageArrow` |
| `docs/pipeline/prototype/shots/04,05,16` | 修改 | 重新生成 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| 规格 JSON | `node -e JSON.parse(...)` | 通过 |
| 视觉抽查 | `04-editor.png` | 通过：阶段之间箭头可见 |

### 决策与取舍

- **决策**：只改原型，不改前端实现。**理由**：实现已有箭头，改动会引入无需求变更。

### 偏差与假设

- 原型 v0.1～v0.6 的编辑器缺少阶段箭头，属于历史漂移，已在本轮修正。

### 未完成 / 风险

- 原型仍未冻结。

### 下一步

- **唯一动作**：冻结原型 v0.7 后，编写 `test-cases.md`、`changes.md` 并开始实现。

---

## 第 5 轮 · 2026-10-18 17:30 · 新建页任务身份也改为只读

- **状态**：待评审
- **触发**：用户以 `#/editor/new` 截图指出任务名称/类型仍可更改
- **目标**：取消新建页与编辑页的差异，任务身份统一只读
- **读取**：原型 `renderEditor`、设计 §5.4

### 做了什么

1. 确认截图是新建页；此前按“编辑页锁定、新建页可编辑”实现。
2. 设计改为新建/编辑一致只读；类型由“添加任务”选定，名称自动生成。
3. 原型升到 v0.8；新增 `21-editor-new.png`，重新生成 `04`、`16`。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `design.md` | 修改 | v0.4；§5.4、需求 5、AC-09 |
| `docs/pipeline/prototype/index.html` | 修改 | v0.8 |
| `docs/pipeline/prototype/README.md` | 修改 | v0.8；新增画板 |
| `docs/pipeline/pipeline-management-prototype-spec.json` / `.md` | 修改 | v0.8 |
| `docs/pipeline/prototype/shots/04,16,21` | 修改 / 新增 | 新建页只读 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| 规格 JSON | `node -e JSON.parse(...)` | 通过 |
| 视觉抽查 | `21-editor-new.png` | 通过：新建页名称/类型禁用 |

### 决策与取舍

- **决策**：新建页也锁定。**理由**：用户明确要求，类型与名称由任务市场统一决定。**未选方案**：保留新建页可编辑。

### 偏差与假设

- 更正第 2 轮“编辑页锁定、新建页可编辑”的假设。

### 未完成 / 风险

- 原型仍未冻结；实现未开始。

### 下一步

- **唯一动作**：冻结原型 v0.8 后，编写 `test-cases.md`、`changes.md` 并开始实现。
