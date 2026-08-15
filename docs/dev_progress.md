# 接口测试平台 — 开发进度

> 项目：hfwas-devops · api-test-core
> 更新日期：2026-08-13

---

## Phase1 · 接口管理模块（已完成 ✅）

### 完成内容
- 接口分组 CRUD（树形结构）
- 接口定义 CRUD（Path/Query/Header/Body 参数）
- 响应定义管理
- 接口版本管理
- 状态流转（草稿→发布→废弃）
- 前端接口列表、详情页、自定义表单弹窗

### 验收文档
- [Phase1 验收报告](report/module_acceptance_api_definition.md)

---

## Phase2 · 调试引擎与环境管理（已完成 ✅）

### 阶段1：文档更新（✅ 已完成）
- [x] 更新 `docs/db_design.md`，追加环境变量、调试历史等12张表结构
- [x] 更新 `docs/module_arch.md`，补充Phase2完整架构说明
- [x] 更新 `docs/api_spec.md`，追加调试执行、环境管理、调试历史共12个REST接口

### 阶段2：后端实体/Mapper/Service/Controller（✅ 已完成）
- [x] 新增 5 个枚举类：ScriptTypeEnum、AssertionSourceEnum、CompareTypeEnum、ExtractSourceEnum、DebugStatusEnum
- [x] 新增 6 个 Entity：ApiDefinitionScriptEntity、ApiDefinitionAssertionEntity、ApiDefinitionExtractEntity、EnvironmentEntity、EnvironmentVariableEntity、DebugHistoryEntity
- [x] 新增 6 个 Mapper：对应以上 Entity
- [x] 新增 5 个 Service：ApiDefinitionScriptService、ApiDefinitionAssertionService、ApiDefinitionExtractService、EnvironmentService、DebugHistoryService
- [x] 新增 3 个 Controller：ApiDebugController、EnvironmentController、DebugHistoryController
- [x] 新增 13 个 DTO/VO：调试请求/响应、环境CRUD、历史查询/详情

### 阶段3：后端核心引擎实现（✅ 已完成）
- [x] HTTP请求引擎（HttpDebugEngine）— 基于 RestClient
- [x] JS脚本沙箱（ScriptSandbox）— GraalVM 架构，安全隔离
- [x] 前置脚本执行器（PreRequestScriptExecutor）
- [x] 后置脚本执行器（PostResponseScriptExecutor）
- [x] 变量渲染引擎（VariableRenderer）— 支持 {{var}} 占位符、默认值、优先级
- [x] 断言执行器（AssertionExecutor）— 9种比较方式
- [x] 变量提取器（VariableExtractor）— 响应体/头/状态码提取

### 阶段4：前端类型定义 + API封装 + Pinia状态（✅ 已完成）
- [x] TS类型定义：debug.ts、debugHistory.ts、environment.ts
- [x] API请求封装：debug.ts、debugHistory.ts、environment.ts
- [x] Pinia状态管理：debug.ts、environment.ts

### 阶段5：前端调试模块界面（✅ 已完成）
- [x] DebugTab.vue — 调试 Tab（嵌入详情页）
- [x] RequestEditor.vue — 请求编辑器（URL/Method/Params/Body）
- [x] ResponseViewer.vue — 响应展示面板（状态码/耗时/头/体/断言/变量）
- [x] ScriptEditor.vue — 脚本编辑器
- [x] JsonEditor.vue — JSON 可视化编辑器 + 校验
- [x] VariablePreview.vue — 变量预览渲染
- [x] EnvironmentSelector.vue — 环境选择器
- [x] AssertionEditor.vue — 断言编辑器
- [x] ExtractEditor.vue — 变量提取编辑器
- [x] KeyValueEditor.vue — 键值对编辑器（共享组件）

### 阶段6：环境变量管理页面（✅ 已完成）
- [x] EnvironmentList.vue — 环境列表页（分页/搜索/CRUD）
- [x] EnvironmentFormDialog.vue — 环境新增/编辑弹窗
- [x] VariableList.vue — 变量编辑列表（支持敏感变量掩码）
- [x] 路由注册：/api-test/environments

### 阶段7：边界场景与异常处理（✅ 已完成）
- [x] DTO参数校验（@Valid + validation rules）
- [x] 后端异常捕获（ApiTestException）
- [x] 前端类型错误修复（vue-tsc 无错误）

### 阶段8：全模块编译自检（✅ 已完成）
- [x] 后端 `mvn compile -pl api-test-core -am` — 编译通过
- [x] 前端 `vue-tsc --noEmit` — 无类型错误
- [x] 修复全部报错与警告

### 阶段9：文档整理与验收（✅ 已完成）
- [x] 统一整理所有文档
- [x] 更新 `dev_progress.md`
- [ ] 生成 Phase2 验收报告（待产出）

---

## Phase3 · 集合管理模块（已完成 ✅）

### 阶段1：文档更新（✅ 已完成）
- [x] 更新 `docs/db_design.md`，追加集合相关5张表DDL（api_collection、api_collection_folder、api_collection_item、api_collection_run、api_collection_run_item）
- [x] 更新 `docs/module_arch.md`，补充Phase3完整架构说明
- [x] 更新 `docs/api_spec.md`，追加集合管理相关共18个REST接口

### 阶段2：后端实体/Mapper/Service/Controller（✅ 已完成）
- [x] 新增 5 个 Entity：CollectionEntity、CollectionFolderEntity、CollectionItemEntity、CollectionRunEntity、CollectionRunItemEntity
- [x] 新增 5 个 Mapper：对应以上 Entity
- [x] 新增 4 个 Service：CollectionService、CollectionFolderService、CollectionItemService、CollectionRunService
- [x] 新增 4 个 Controller：CollectionController、CollectionFolderController、CollectionItemController、CollectionRunController
- [x] 新增 7 个 DTO：CollectionCreateDTO、CollectionUpdateDTO、CollectionFolderCreateDTO、CollectionFolderUpdateDTO、CollectionItemAddDTO、CollectionItemBatchDTO、CollectionRunQueryDTO
- [x] 新增 7 个 VO：CollectionVO、CollectionDetailVO、CollectionFolderVO、CollectionItemVO、CollectionRunVO、CollectionRunDetailVO、CollectionRunItemVO

### 阶段3：前端类型定义 + API封装 + Pinia状态（✅ 已完成）
- [x] TS类型定义：collection.ts（含所有VO/DTO类型）
- [x] API请求封装：collection.ts（包含18个API端点）
- [x] Pinia状态管理：collection.ts（集合CRUD、文件夹管理、项管理、执行、历史）

### 阶段4：前端组件与视图（✅ 已完成）
- [x] CollectionTree.vue — 集合+文件夹树形组件（基于 Naive UI n-tree）
- [x] CollectionItemList.vue — 集合项列表（方法标签、启用开关、操作）
- [x] CollectionRunResult.vue — 运行结果展示（概况+执行详情列表）
- [x] CollectionList.vue — 集合列表页（分页/搜索/CRUD弹窗）
- [x] CollectionDetail.vue — 集合详情页（树形结构、项管理、文件夹管理、执行）
- [x] CollectionRunHistory.vue — 运行历史页（分页列表、详情弹窗）
- [x] 路由注册：集合列表、详情、运行历史

### 阶段5：编译自检（✅ 已完成）
- [x] 后端 `mvn clean compile -pl api-test-core -am` — 编译通过
- [x] 前端 `vue-tsc --noEmit` — 无集合模块类型错误
- [x] 修复全部报错与警告

---

## 后续阶段（规划中）

| 阶段 | 模块 | 说明 |
|------|------|------|
| Phase3 | 集合管理 | 接口集合、批量执行 |
| Phase4 | 场景编排 | 多接口场景编排、流程测试 |
| Phase5 | 压测 | 性能测试、并发执行 |
| Phase6 | 报告 | 测试报告生成与导出 |