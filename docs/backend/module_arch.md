# 接口测试平台 — 模块架构

> 模块：api-test-core（接口测试核心）
> 所属：hfwas-devops 后端 api-test-core 子模块
> 日期：2026-08-13

---

## 一、模块定位

接口测试平台是**hfwas-devops**的核心测试功能模块，由以下子模块组成：

### Phase1 · 接口管理模块（已完成）
负责接口定义的全生命周期管理，包括：
- 接口分组分类
- 接口基本信息管理（CRUD）
- 请求参数结构化定义（Path/Query/Header/Body）
- 响应定义（状态码/响应体/示例）
- 接口版本管理
- 接口状态流转（草稿→发布→废弃）

### Phase2 · 调试引擎与环境管理（已完成）
负责接口调试执行、脚本处理、环境变量管理，包括：
- HTTP调试引擎：发送请求、捕获响应报文、耗时统计
- 前置JS脚本执行（Pre-request Script）
- 后置响应脚本、响应断言、响应字段变量提取
- 环境变量管理：环境CRUD、变量作用域、自动变量渲染替换
- 调试历史记录持久化存储
- 调试结果导出功能

### Phase3 · 集合管理（本期开发）
负责接口集合的批量管理、组织与执行，包括：
- 集合CRUD：接口集合的新增、编辑、删除、分页查询
- 集合文件夹：树形文件夹组织，支持多级嵌套
- 集合项管理：引用接口定义到集合，支持排序与启用/禁用
- 批量执行：顺序执行集合内所有启用的接口
- 执行结果：记录每次执行的详细结果与断言状态

---

## 二、模块目录结构

### Phase1 · 接口管理

```
backend/api-test-core/src/main/java/com/hfwas/devops/apitest/
├── config/
│   └── ApiTestAutoConfiguration.java         # 自动配置
│
├── apidefine/                                # 接口管理
│   ├── controller/
│   │   ├── ApiGroupController.java           # 分组 CRUD
│   │   └── ApiDefinitionController.java      # 接口定义 CRUD
│   │
│   ├── service/
│   │   ├── ApiGroupService.java              # 分组业务
│   │   ├── ApiDefinitionService.java         # 接口定义业务
│   │   ├── ApiDefinitionParamService.java    # 参数业务
│   │   ├── ApiDefinitionResponseService.java # 响应业务
│   │   └── ApiDefinitionVersionService.java  # 版本业务
│   │
│   ├── mapper/
│   │   ├── ApiGroupMapper.java
│   │   ├── ApiDefinitionMapper.java
│   │   ├── ApiDefinitionParamMapper.java
│   │   ├── ApiDefinitionResponseMapper.java
│   │   └── ApiDefinitionVersionMapper.java
│   │
│   ├── entity/
│   │   ├── ApiGroupEntity.java
│   │   ├── ApiDefinitionEntity.java
│   │   ├── ApiDefinitionParamEntity.java
│   │   ├── ApiDefinitionResponseEntity.java
│   │   └── ApiDefinitionVersionEntity.java
│   │
│   ├── dto/                                  # 数据传输对象（请求）
│   │   ├── ApiGroupCreateDTO.java
│   │   ├── ApiGroupUpdateDTO.java
│   │   ├── ApiDefinitionCreateDTO.java
│   │   ├── ApiDefinitionUpdateDTO.java
│   │   ├── ApiDefinitionParamDTO.java
│   │   ├── ApiDefinitionResponseDTO.java
│   │   └── ApiDefinitionQueryDTO.java
│   │
│   ├── vo/                                   # 视图对象（响应）
│   │   ├── ApiGroupVO.java
│   │   ├── ApiDefinitionVO.java
│   │   ├── ApiDefinitionDetailVO.java
│   │   ├── ApiDefinitionParamVO.java
│   │   └── ApiDefinitionResponseVO.java
│   │
│   └── convert/                              # 对象转换
│       ├── ApiGroupConvert.java
│       └── ApiDefinitionConvert.java
│
└── common/                                   # 模块公共
    ├── enums/
    │   ├── ApiStatusEnum.java
    │   ├── HttpMethodEnum.java
    │   ├── ParamTypeEnum.java
    │   └── ParamDataTypeEnum.java
    └── exception/
        └── ApiTestException.java
```

### Phase2 · 调试引擎与环境管理

```
backend/api-test-core/src/main/java/com/hfwas/devops/apitest/
├── apidefine/                                # （已有）接口管理
│   ├── controller/
│   │   └── ApiDebugController.java           # [新增] 调试执行控制器
│   ├── service/
│   │   ├── ApiDefinitionScriptService.java   # [新增] 脚本业务
│   │   ├── ApiDefinitionAssertionService.java# [新增] 断言业务
│   │   └── ApiDefinitionExtractService.java  # [新增] 变量提取业务
│   ├── mapper/
│   │   ├── ApiDefinitionScriptMapper.java    # [新增]
│   │   ├── ApiDefinitionAssertionMapper.java # [新增]
│   │   └── ApiDefinitionExtractMapper.java   # [新增]
│   ├── entity/
│   │   ├── ApiDefinitionScriptEntity.java    # [新增]
│   │   ├── ApiDefinitionAssertionEntity.java # [新增]
│   │   └── ApiDefinitionExtractEntity.java   # [新增]
│   ├── dto/
│   │   ├── ApiDebugExecuteDTO.java           # [新增] 调试执行请求
│   │   └── ApiDebugExportDTO.java            # [新增] 调试导出请求
│   └── vo/
│       ├── ApiDebugResultVO.java             # [新增] 调试结果VO
│       └── ApiDebugHistoryVO.java            # [新增] 调试历史VO
│
├── debugger/                                 # [新增] 调试引擎
│   ├── engine/
│   │   └── HttpDebugEngine.java              # [新增] HTTP请求执行引擎
│   ├── script/
│   │   ├── ScriptSandbox.java                # [新增] JS沙箱（GraalVM）
│   │   ├── PreRequestScriptExecutor.java     # [新增] 前置脚本执行器
│   │   └── PostResponseScriptExecutor.java   # [新增] 后置脚本执行器
│   ├── assertion/
│   │   └── AssertionExecutor.java            # [新增] 断言执行器
│   ├── extract/
│   │   └── VariableExtractor.java            # [新增] 变量提取器
│   ├── variable/
│   │   └── VariableRenderer.java             # [新增] 变量渲染替换引擎
│   └── model/
│       ├── DebugRequest.java                 # [新增] 调试请求模型
│       ├── DebugResponse.java                # [新增] 调试响应模型
│       └── DebugResult.java                  # [新增] 调试结果模型
│
├── environment/                              # [新增] 环境变量管理
│   ├── controller/
│   │   └── EnvironmentController.java        # [新增] 环境CRUD控制器
│   ├── service/
│   │   └── EnvironmentService.java           # [新增] 环境变量业务
│   ├── mapper/
│   │   ├── EnvironmentMapper.java            # [新增]
│   │   └── EnvironmentVariableMapper.java    # [新增]
│   ├── entity/
│   │   ├── EnvironmentEntity.java            # [新增]
│   │   └── EnvironmentVariableEntity.java    # [新增]
│   ├── dto/
│   │   ├── EnvironmentCreateDTO.java         # [新增]
│   │   ├── EnvironmentUpdateDTO.java         # [新增]
│   │   ├── EnvironmentVariableDTO.java       # [新增]
│   │   └── EnvironmentQueryDTO.java          # [新增]
│   └── vo/
│       ├── EnvironmentVO.java                # [新增]
│       └── EnvironmentDetailVO.java          # [新增]
│
├── history/                                  # [新增] 调试历史
│   ├── controller/
│   │   └── DebugHistoryController.java       # [新增] 调试历史控制器
│   ├── service/
│   │   └── DebugHistoryService.java          # [新增] 调试历史业务
│   ├── mapper/
│   │   └── DebugHistoryMapper.java           # [新增]
│   ├── entity/
│   │   └── DebugHistoryEntity.java           # [新增]
│   ├── dto/
│   │   ├── DebugHistoryQueryDTO.java         # [新增] 历史查询
│   │   └── DebugHistoryExportDTO.java        # [新增] 历史导出
│   └── vo/
│       └── DebugHistoryDetailVO.java         # [新增] 历史详情VO
│
├── common/                                   # （已有）模块公共
│   └── enums/
│       ├── ScriptTypeEnum.java               # [新增]
│       ├── AssertionSourceEnum.java          # [新增]
│       ├── CompareTypeEnum.java              # [新增]
│       ├── ExtractSourceEnum.java            # [新增]
│       └── DebugStatusEnum.java              # [新增]
│
├── collection/                               # [新增] 集合管理
│   ├── controller/
│   │   ├── CollectionController.java         # [新增] 集合CRUD
│   │   ├── CollectionFolderController.java   # [新增] 文件夹CRUD
│   │   ├── CollectionItemController.java     # [新增] 集合项管理
│   │   └── CollectionRunController.java      # [新增] 集合执行+历史
│   │
│   ├── service/
│   │   ├── CollectionService.java            # [新增] 集合业务
│   │   ├── CollectionFolderService.java      # [新增] 文件夹业务（树形）
│   │   ├── CollectionItemService.java        # [新增] 集合项业务（排序/批量）
│   │   └── CollectionRunService.java         # [新增] 批量执行引擎
│   │
│   ├── mapper/
│   │   ├── CollectionMapper.java             # [新增]
│   │   ├── CollectionFolderMapper.java       # [新增]
│   │   ├── CollectionItemMapper.java         # [新增]
│   │   ├── CollectionRunMapper.java          # [新增]
│   │   └── CollectionRunItemMapper.java      # [新增]
│   │
│   ├── entity/
│   │   ├── CollectionEntity.java             # [新增]
│   │   ├── CollectionFolderEntity.java       # [新增]
│   │   ├── CollectionItemEntity.java         # [新增]
│   │   ├── CollectionRunEntity.java          # [新增]
│   │   └── CollectionRunItemEntity.java      # [新增]
│   │
│   ├── dto/
│   │   ├── CollectionCreateDTO.java          # [新增]
│   │   ├── CollectionUpdateDTO.java          # [新增]
│   │   ├── CollectionFolderCreateDTO.java    # [新增]
│   │   ├── CollectionFolderUpdateDTO.java    # [新增]
│   │   ├── CollectionItemAddDTO.java         # [新增]
│   │   ├── CollectionItemBatchDTO.java       # [新增]
│   │   └── CollectionRunQueryDTO.java        # [新增]
│   │
│   └── vo/
│       ├── CollectionVO.java                 # [新增]
│       ├── CollectionDetailVO.java           # [新增]
│       ├── CollectionFolderVO.java           # [新增]
│       ├── CollectionItemVO.java             # [新增]
│       ├── CollectionRunVO.java              # [新增]
│       └── CollectionRunDetailVO.java        # [新增]
│
└── common/                                   # （已有）模块公共
```

---

## 三、分层职责

```
Controller      接收请求参数 → 调用 Service → 返回 VO
    │
    ▼
Service         业务逻辑 → 调用 Mapper → 返回 DTO/Entity
    │
    ▼
Mapper          MyBatis Plus 数据访问
    │
    ▼
Entity          数据库表映射
```

### Phase2 新增组件职责

| 组件 | 包路径 | 职责 |
|------|--------|------|
| HttpDebugEngine | `debugger.engine` | 基于RestClient发送HTTP请求，捕获完整请求/响应报文 |
| ScriptSandbox | `debugger.script` | GraalVM JS沙箱，隔离执行用户脚本 |
| PreRequestScriptExecutor | `debugger.script` | 执行前置脚本，修改请求参数 |
| PostResponseScriptExecutor | `debugger.script` | 执行后置脚本，处理响应数据 |
| AssertionExecutor | `debugger.assertion` | 执行断言规则，返回断言结果 |
| VariableExtractor | `debugger.extract` | 从响应中提取变量值 |
| VariableRenderer | `debugger.variable` | 将 `{{varName}}` 占位符替换为实际变量值 |

### Phase3 新增组件职责

| 组件 | 包路径 | 职责 |
|------|--------|------|
| CollectionService | `collection.service` | 集合CRUD业务，分页查询 |
| CollectionFolderService | `collection.service` | 文件夹树形结构管理，含子节点查询 |
| CollectionItemService | `collection.service` | 集合项增删改查，批量添加，排序 |
| CollectionRunService | `collection.service` | 批量执行引擎，顺序执行集合内所有接口 |

### Phase3 数据流

---

## 四、数据流转

### 4.1 创建接口（Phase1）
```
前端表单 → ApiDefinitionCreateDTO
    → Controller (@Valid 校验)
    → Service (参数校验 + 状态设 DRAFT + 雪花ID)
    → Entity (MyBatis Plus 插入)
    → ApiDefinitionVO (返回前端)
```

### 4.2 查询接口列表（Phase1）
```
前端查询条件 → ApiDefinitionQueryDTO
    → Controller
    → Service (MyBatis Plus Page + 条件构造)
    → Page<ApiDefinitionVO>
    → 前端表格展示
```

### 4.3 状态流转（Phase1）
```
DRAFT   → PUBLISHED  : 发布操作
PUBLISHED → DEPRECATED : 废弃操作
PUBLISHED → DRAFT     : 重新编辑
DEPRECATED → DRAFT    : 恢复编辑
```

### 4.4 接口调试执行（Phase2 — 核心流程）

```
前端触发调试
    → ApiDebugController.execute()
    → 1. VariableRenderer 渲染请求参数中的 {{varName}} 占位符
    → 2. PreRequestScriptExecutor 执行前置JS脚本（可修改参数）
    → 3. HttpDebugEngine 发送HTTP请求，计时
    → 4. PostResponseScriptExecutor 执行后置JS脚本
    → 5. AssertionExecutor 执行断言规则
    → 6. VariableExtractor 从响应提取变量
    → 7. DebugHistoryService 保存调试历史记录
    → 8. 返回 ApiDebugResultVO（含请求/响应报文、耗时、断言结果）
```

### 4.5 变量渲染流程（Phase2）

```
请求参数中的 {{base_url}}/api/users?page={{page}}
    → VariableRenderer 查询当前环境变量
    → 递归替换所有 {{varName}} 占位符
    → 支持嵌套变量引用
    → 渲染失败时记录错误信息，返回部分渲染结果
```

### 4.6 前置脚本执行流程（Phase2）

```
JS脚本内容（用户编写）
    → ScriptSandbox 初始化 GraalVM 上下文
    → 注入 pm.request / pm.environment 等全局对象
    → 执行用户脚本
    → 脚本可修改：请求头、请求体、Query参数
    → 安全限制：禁止网络访问、文件系统、系统调用
    → 超时限制：默认 5 秒
    → 返回修改后的请求参数
```

### 4.7 断言执行流程（Phase2）

```
断言规则列表（来源/比较方式/表达式/期望值）
    → 逐条执行
    → 根据比较方式（EQUALS/CONTAINS/REGEX/GT等）执行对应比较
    → 记录每条断言结果（passed/actual/expected）
    → 汇总全部通过/失败状态
    → 返回断言结果列表
```

### 4.8 集合执行流程（Phase3 — 核心流程）

```
前端触发集合执行
    → CollectionRunController.run()
    → 1. 查询集合内所有启用的集合项（按 sort_order 排序）
    → 2. 创建 CollectionRunEntity 记录（状态 RUNNING）
    → 3. 逐条顺序执行集合项：
         a. 加载接口定义参数
         b. 使用 HttpDebugEngine 发送请求
         c. 执行断言（复用 AssertionExecutor）
         d. 记录执行结果到 CollectionRunItemEntity
         e. 更新运行统计（passed/failed/error）
    → 4. 更新集合执行状态（COMPLETED / FAILED）
    → 5. 返回执行结果摘要
```

### 4.9 集合项管理流程（Phase3）

```
前端添加接口到集合
    → CollectionItemController.add()
    → 1. 校验接口定义是否存在
    → 2. 检查是否已添加（防重复）
    → 3. 分配 sort_order（末尾追加）
    → 4. 记录引用关系 collection_id → definition_id
    → 5. 返回集合项信息

前端查看集合详情
    → CollectionController.getDetail()
    → 1. 查询集合基本信息
    → 2. 查询文件夹树形结构（含子文件夹）
    → 3. 查询所有集合项（按文件夹分组）
    → 4. 组装树形结构：文件夹 → 集合项
    → 5. 返回 CollectionDetailVO
```

### 4.10 文件夹树形结构管理（Phase3）

```
前端创建文件夹（指定 parent_id 或 null 为根级）
    → CollectionFolderController.create()
    → 1. 校验父文件夹是否属于同一集合
    → 2. 设置 parent_id 层级
    → 3. 分配 sort_order
    → 4. 保存

前端查询文件夹树
    → CollectionFolderController.getTree()
    → 1. 查询该集合所有文件夹
    → 2. 递归组装树形结构（parent_id → children）
    → 3. 返回树形节点列表
```

---

## 五、前端模块结构

### Phase1 · 接口管理

```
src/modules/api-test/define/            # 接口管理（前端子模块）
├── api/
│   ├── group.ts                        # 分组 API
│   └── definition.ts                   # 接口定义 API
│
├── components/
│   ├── ApiGroupTree.vue                # 分组树组件
│   ├── ApiDefinitionForm.vue           # 接口定义表单
│   ├── ParamEditor.vue                 # 参数编辑器
│   └── ResponseEditor.vue              # 响应编辑器
│
├── views/
│   ├── ApiDefinitionList.vue           # 接口列表页
│   ├── ApiDefinitionDetail.vue         # 接口详情页
│   └── ApiDefinitionFormDialog.vue     # 新增/编辑弹窗
│
├── types/
│   ├── group.ts                        # 分组类型定义
│   └── definition.ts                   # 接口定义类型定义
│
└── stores/
    ├── group.ts                        # 分组状态
    └── definition.ts                   # 接口定义状态
```

### Phase2 · 调试与环境管理

```
src/modules/api-test/                    # 接口测试平台
├── define/                              # （已有）接口管理
│   └── ... (同上)
│
├── debug/                               # [新增] 调试模块
│   ├── api/
│   │   ├── debug.ts                     # [新增] 调试 API
│   │   └── debugHistory.ts              # [新增] 调试历史 API
│   │
│   ├── components/
│   │   ├── RequestEditor.vue            # [新增] 请求编辑器（URL/Headers/Body）
│   │   ├── ResponseViewer.vue           # [新增] 响应展示面板
│   │   ├── ScriptEditor.vue             # [新增] 脚本编辑器（CodeMirror）
│   │   ├── AssertionEditor.vue          # [新增] 断言编辑器
│   │   ├── ExtractEditor.vue            # [新增] 变量提取编辑器
│   │   ├── JsonEditor.vue               # [新增] JSON可视化编辑器
│   │   ├── VariablePreview.vue          # [新增] 变量预览渲染
│   │   └── EnvironmentSelector.vue      # [新增] 环境选择器
│   │
│   ├── views/
│   │   ├── DebugTab.vue                 # [新增] 调试 Tab（嵌入详情页）
│   │   └── DebugHistoryList.vue         # [新增] 调试历史列表页
│   │
│   ├── types/
│   │   ├── debug.ts                     # [新增] 调试类型定义
│   │   ├── debugHistory.ts              # [新增] 调试历史类型定义
│   │   └── environment.ts               # [新增] 环境变量类型定义
│   │
│   └── stores/
│       ├── debug.ts                     # [新增] 调试状态
│       └── environment.ts               # [新增] 环境变量状态
│
├── environment/                         # [新增] 环境管理模块
│   ├── api/
│   │   └── environment.ts               # [新增] 环境变量 API
│   │
│   ├── components/
│   │   ├── EnvironmentFormDialog.vue    # [新增] 环境新增/编辑弹窗
│   │   └── VariableList.vue             # [新增] 变量列表编辑
│   │
│   ├── views/
│   │   └── EnvironmentList.vue          # [新增] 环境变量管理页
│   │
│   ├── types/
│   │   └── environment.ts               # [新增] 环境变量类型定义
│   │
│   └── stores/
│       └── environment.ts               # [新增] 环境变量状态
│
└── shared/                              # [新增] 共享组件
    └── components/
        └── JsonViewer.vue               # [新增] JSON格式化查看组件

### Phase3 · 集合管理

```
src/modules/api-test/                    # 接口测试平台
├── define/                              # （已有）接口管理
├── debug/                               # （已有）调试模块
├── environment/                         # （已有）环境管理
│
├── collection/                          # [新增] 集合管理模块
│   ├── api/
│   │   └── collection.ts               # [新增] 集合 API
│   │
│   ├── components/
│   │   ├── CollectionTree.vue           # [新增] 集合+文件夹树形组件
│   │   ├── CollectionItemList.vue       # [新增] 集合项列表（拖拽排序）
│   │   └── CollectionRunResult.vue      # [新增] 运行结果展示
│   │
│   ├── views/
│   │   ├── CollectionList.vue           # [新增] 集合列表页
│   │   ├── CollectionDetail.vue         # [新增] 集合详情页（含文件夹/项管理）
│   │   └── CollectionRunHistory.vue     # [新增] 运行历史页
│   │
│   ├── types/
│   │   └── collection.ts                # [新增] 集合类型定义
│   │
│   └── stores/
│       └── collection.ts                # [新增] 集合状态管理
│
└── shared/                              # （已有）共享组件
```

---

## 六、Phase2 核心引擎设计

### 6.1 HTTP调试引擎（HttpDebugEngine）

```
HttpDebugEngine
    ├── 基于 Spring RestClient（非阻塞 + 可配置）
    ├── 支持 HTTP/HTTPS 协议
    ├── 支持自定义超时（连接/读取/写入）
    ├── 支持重定向跟随配置
    ├── 支持 SSL 证书验证开关
    ├── 完整捕获：
    │   ├── 请求 URL（含 Query 参数）
    │   ├── 请求头
    │   ├── 请求体
    │   ├── 响应状态码
    │   ├── 响应头
    │   ├── 响应体（文本/二进制）
    │   └── 响应耗时（毫秒精度）
    └── 异常处理：
        ├── 连接超时 → 记录 ERROR 状态
        ├── DNS 解析失败 → 记录 ERROR 状态
        ├── SSL 握手失败 → 记录 ERROR 状态
        └── 响应体截断保护（最大 10MB）
```

### 6.2 JS脚本沙箱（ScriptSandbox）

```
ScriptSandbox (基于 GraalVM JavaScript)
    ├── 隔离执行环境（每个请求独立上下文）
    ├── 注入的全局对象：
    │   ├── pm.request        — 当前请求对象（可读写）
    │   │   ├── .url          — 请求URL
    │   │   ├── .method       — 请求方法
    │   │   ├── .headers      — 请求头
    │   │   ├── .body         — 请求体
    │   │   └── .query        — Query参数
    │   ├── pm.response       — 响应对象（后置脚本只读）
    │   │   ├── .status       — 状态码
    │   │   ├── .headers      — 响应头
    │   │   ├── .body         — 响应体
    │   │   └── .responseTime — 响应耗时
    │   ├── pm.environment    — 环境变量（读写）
    │   ├── pm.variables      — 临时变量（后置脚本写入）
    │   └── console           — 日志输出
    ├── 安全限制：
    │   ├── 禁止 java.* 包访问
    │   ├── 禁止文件系统操作
    │   ├── 禁止网络连接
    │   ├── 禁止线程创建
    │   └── 执行超时 5 秒
    └── 脚本执行结果：
        └── 返回修改后的请求参数 + 执行日志
```

### 6.3 变量渲染引擎（VariableRenderer）

```
VariableRenderer
    ├── 识别模板语法：{{ variableName }}
    ├── 支持嵌套变量：{{ {{outer}}_suffix }}
    ├── 变量作用域优先级（高→低）：
    │   1. 临时变量（pm.variables 设置）
    │   2. 环境变量（当前选中环境）
    │   3. 全局变量（预留扩展）
    │   4. 默认值语法：{{varName:defaultValue}}
    ├── 渲染范围：
    │   ├── URL
    │   ├── Headers 值
    │   ├── Query 参数值
    │   ├── Body 内容
    │   └── Path 参数
    └── 错误处理：
        ├── 未匹配变量 → 保留原样/报错（可配置）
        ├── 循环引用检测
        └── 类型错误提示
```

---

## 七、依赖关系

```
api-test-core
    ├── user-api (用户/认证/权限)
    ├── spring-boot-starter-web
    ├── mybatis-plus-spring-boot3-starter
    ├── mysql-connector-j
    ├── redisson-spring-boot-starter
    ├── hutool-all
    ├── graalvm-js (JS沙箱)          ← Phase2 新增
    └── graalvm-sdk                  ← Phase2 新增
```

---

## 八、与测试平台的关系

```
api-test 平台（整体）
├── api-management（接口管理）        ← Phase1 已完成
├── debugger（接口调试）              ← Phase2 已完成
├── environment（环境变量）           ← Phase2 已完成
├── collection（集合管理）            ← Phase3 本期开发
├── scene（场景编排）                 ← 后续阶段
├── stress（压测）                    ← 后续阶段
└── report（报告）                    ← 后续阶段
```