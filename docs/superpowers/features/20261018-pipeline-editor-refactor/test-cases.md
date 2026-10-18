# 流水线编辑器重构与全局弹框样式 —— 测试用例

> 日期：2026-10-18
> 版本：v0.3
> 状态：长期维护；需求变更时先改本文件，再改代码
> 对应设计：[`design.md`](./design.md) v0.5

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-18 | 初版：覆盖编辑器结构、克隆参数归属、回显、阶段命名、移除缩放、身份只读、弹框样式、迁移与回归 |
| v0.2 | 2026-10-18 | 实施后对齐自动化位置至真实测试名；凭证选择标为手工用例 |
| v0.3 | 2026-10-18 | 迁移策略调整：17 只补种子，回填移到 `CloneConfigBackfill`；补 `migrate.py` 路径与幂等用例 |

---

## 0. 约定

- 前端命令在 `frontend/` 下执行；后端在仓库根执行。
- 自动化位置写作 `文件::测试名`。
- 手工用例标注“手工”。

## 1. 后端保存与派生

| 编号 | 前置条件 | 步骤 | 输入 | 预期结果 | 自动化位置 |
|------|----------|------|------|----------|------------|
| TC-BE-01 | 请求含一个 CLONE 任务 | 保存流水线 | `REPO_URL=https://git.example.com/a/b.git`、`GIT_REF=dev`、`CREDENTIAL_ID=7` | 实体 `repoUrl/gitRef/credentialId` 分别为上述值 | `PipelineDefinitionServiceTest::derivesCloneConfigFromJobBindings` |
| TC-BE-02 | 请求无 CLONE 任务 | 保存流水线 | 仅 BUILD 任务 | 实体 `repoUrl=""`、`gitRef="main"`、`credentialId=null` | `PipelineDefinitionServiceTest::createWithoutCloneAllowsEmptyRepoUrl` |
| TC-BE-03 | CLONE 存在但缺 `REPO_URL` | 保存 | 绑定中没有 `REPO_URL` | 400 “仓库地址不能为空” | `PipelineDefinitionServiceTest::createWithCloneRequiresValidRepoUrl` |
| TC-BE-04 | CLONE 的 `REPO_URL` 非 http/https | 保存 | `ftp://...` | 400 | `PipelineDefinitionServiceTest::createWithCloneRequiresValidRepoUrl` |
| TC-BE-05 | `CREDENTIAL_ID` 非数字 | 保存 | `abc` | 400 | `PipelineDefinitionServiceTest::rejectsInvalidCredentialId` |
| TC-BE-06 | 依赖扫描提交 | 创建临时流水线 | repo/gitRef/credential | CLONE 任务绑定包含三项；DTO 不再有顶层字段 | `DependencyScanServiceTest::submitSingleCreatesPipelineAndStartsRun` |

## 2. 数据库迁移

| 编号 | 前置条件 | 步骤 | 输入 | 预期结果 | 自动化位置 |
|------|----------|------|------|----------|------------|
| TC-DB-01 | 旧库无 `REPO_URL` 参数种子 | 执行 `17-clone-job-config.sql` | — | 参数表新增 `REPO_URL`、`CREDENTIAL_ID` | 手工：sqlite3 临时库执行 |
| TC-DB-02 | 旧 CLONE 任务仅有 `GIT_REF` 绑定 | 执行回填 | pipeline 列有 repo/branch/credential | `CloneConfigBackfill` 补上 `REPO_URL`/`GIT_REF`/`CREDENTIAL_ID` | `backend/server/src/test/java/com/hfwas/devops/config/CloneConfigBackfillTest.java::backfillsLegacyCloneBindingsAndIsIdempotent` |
| TC-DB-03 | 已回填库 | 再次执行回填 | — | 绑定不被改写，无报错 | 同上（同一测试连续执行两次并比较） |
| TC-DB-04 | external/Helm 全目录迁移 | 执行 `migrate.py` | 含 `05` 与 `17` | 迁移成功，17 不含 UPDATE | `scripts/tests/test_migrate.py`（套件级：全目录迁移 + 幂等，14 passed） |
| TC-DB-05 | 已有 `REPO_URL` 的任务 | 执行回填 | 自定义仓库 | 已有值不被覆盖 | `CloneConfigBackfillTest.java::doesNotOverwriteAnExistingRepoUrl` |

## 3. 编辑器结构

| 编号 | 前置条件 | 步骤 | 输入 | 预期结果 | 自动化位置 |
|------|----------|------|------|----------|------------|
| TC-ED-01 | 打开新建/编辑页 | 查看头部 | — | 标题后紧跟名称输入框；无“基本信息”行 | `PipelineEditorPage.test.tsx::swaps the right panel between config and a click-to-add task list` |
| TC-ED-02 | 新建页 | 修改名称并保存 | `后端发布` | 创建请求 `name=后端发布` | `PipelineEditorPage.test.tsx::swaps the right panel between config and a click-to-add task list` |
| TC-ED-03 | 新建/编辑页头部 | 查找仓库/分支/凭证输入 | — | 页头不存在这三个字段 | `PipelineEditorPage.test.tsx::swaps the right panel between config and a click-to-add task list` |
| TC-ED-04 | 编辑页选中 CLONE | 查看检查器 | — | 显示仓库地址、代码分支、凭证 | `PipelineEditorPage.test.tsx::swaps the right panel between config and a click-to-add task list` |
| TC-ED-05 | 编辑页 CLONE | 修改仓库/分支/凭证 | 合法值 | 保存请求的 CLONE `paramBindings` 同步更新 | `PipelineEditorPage.test.tsx::swaps the right panel between config and a click-to-add task list` |
| TC-ED-06 | 选中 BUILD | 查看检查器 | — | 不显示仓库配置 | `PipelineEditorPage.test.tsx::swaps the right panel between config and a click-to-add task list` |
| TC-ED-07 | 新建页 | 保存 | 合法定义 | 请求体不含 `repoUrl/gitRef/credentialId` | `PipelineEditorPage.test.tsx::swaps the right panel between config and a click-to-add task list` |
| TC-ED-08 | CLONE 无仓库地址 | 保存 | `REPO_URL` 空 | 提示“仓库地址不能为空”，不发请求 | `PipelineEditorPage.test.tsx::blocks save when clone repo url is empty` |
| TC-ED-09 | CLONE 仓库非 http/https | 保存 | `ftp://x` | 提示仓库地址错误，不发请求 | `pipelineDraft.test.ts::validates clone repo url from job bindings` |
| TC-ED-10 | CLONE 有凭证 | 选择凭证后保存 | `credentialId=7` | 绑定值为 `7` | 手工：AsyncSelect 凭证搜索与选择需真实数据 |

## 4. 最近克隆回显与详情

| 编号 | 前置条件 | 步骤 | 输入 | 预期结果 | 自动化位置 |
|------|----------|------|------|----------|------------|
| TC-ED-11 | 编辑页最近运行有 `commitSha` | 查看头部 | — | 显示“最近克隆 repo · branch · 9f2c1ab0”（8 位短 SHA） | `PipelineEditorPage.test.tsx::shows last clone summary in edit mode` |
| TC-ED-12 | 流水线详情有运行记录 | 查看指标卡 | — | 显示 8 位 Commit 短 SHA；无记录显示 `—` | `PipelineDetailPage.test.tsx::puts tabs above the repo meta strip and opens the definition canvas` / `::shows a dash for commit when no run exists` |
| TC-ED-12b | 编辑页最近运行无 commit | 查看头部 | — | 不显示“最近克隆” | `PipelineEditorPage.test.tsx::omits last clone summary without commit` |

## 5. 阶段命名与缩放

| 编号 | 前置条件 | 步骤 | 输入 | 预期结果 | 自动化位置 |
|------|----------|------|------|----------|------------|
| TC-ED-13 | 任意编辑器页 | 点击“添加阶段” | — | 新阶段名为 `阶段 N` | `pipelineDraft.test.ts::names a new stage 阶段 N and renames it after the first job` |
| TC-ED-14 | 空阶段 `阶段 N` | 添加第一个任务 | CLONE | 阶段名自动改为“拉取” | `pipelineDraft.test.ts::names a new stage 阶段 N and renames it after the first job` |
| TC-ED-15 | 任意阶段 | 修改阶段名 | `发布` | 名称可编辑并保留 | `pipelineDraft.test.ts::keeps a user edited stage name when adding the first job` |
| TC-ED-16 | 打开编辑器 | 查找缩放控件 | — | 不存在缩放下拉，画布无 `transform: scale` | `PipelineEditorPage.test.tsx::has no zoom control` |

## 6. 任务身份只读

| 编号 | 前置条件 | 步骤 | 输入 | 预期结果 | 自动化位置 |
|------|----------|------|------|----------|------------|
| TC-ED-17 | 新建页选中任务 | 查看任务名称 | — | 禁用，不可编辑 | `PipelineEditorPage.test.tsx::locks task identity on new and edit pages` |
| TC-ED-18 | 新建页选中任务 | 查看任务类型 | — | 禁用，不可编辑 | 同上 |
| TC-ED-19 | 编辑页选中任务 | 查看任务名称/类型 | — | 均禁用，并显示提示 | 同上 |
| TC-ED-20 | CLONE 任务 | 查看高级设置 | — | 不显示技术栈/运行时/工具版本 | `PipelineEditorPage.test.tsx::shows advanced settings for build but not for clone` |
| TC-ED-21 | BUILD 任务 | 查看高级设置 | — | 仍显示技术栈/运行时/工具版本 | `PipelineEditorPage.test.tsx::shows advanced settings for build but not for clone` |

## 7. 全局弹框样式

| 编号 | 前置条件 | 步骤 | 输入 | 预期结果 | 自动化位置 |
|------|----------|------|------|----------|------------|
| TC-UI-01 | 打开任意 Dialog | 检查根节点 class | — | 含 `rounded-xl`、`bg-card`、`border`、`shadow-lg` | `components/ui/dialog.test.tsx::uses the kite surface tokens` |
| TC-UI-02 | 打开任意 AlertDialog | 检查根节点 class | — | 与 Dialog 相同的圆角、底色、阴影、遮罩 | `components/ui/alert-dialog.test.tsx::matches the dialog surface tokens` |
| TC-UI-03 | 暗色模式 | 打开弹框 | — | 无硬编码颜色，跟随 token | 手工：切换暗色抽查 |

## 8. 回归

| 编号 | 检查 | 命令 | 预期 |
|------|------|------|------|
| TC-REG-01 | 后端测试 | `mvn -pl backend/pipeline-core -am test` | 全部通过 |
| TC-REG-02 | 前端测试 | `cd frontend && npm test` | 全部通过 |
| TC-REG-03 | 类型检查 | `cd frontend && npx tsc --noEmit` | 无错误 |
| TC-REG-04 | 构建 | `cd frontend && npm run build` | 成功 |
