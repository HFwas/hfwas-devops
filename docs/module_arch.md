# 接口管理模块 — 模块架构

> 模块：api-management（接口管理）
> 所属：hfwas-devops 后端 api-test-core 子模块
> 日期：2026-08-12

---

## 一、模块定位

接口管理模块是**接口测试平台**的基础模块，负责接口定义的全生命周期管理，包括：
- 接口分组分类
- 接口基本信息管理（CRUD）
- 请求参数结构化定义（Path/Query/Header/Body）
- 响应定义（状态码/响应体/示例）
- 接口版本管理
- 接口状态流转（草稿→发布→废弃）

## 二、模块目录结构

```
backend/api-test-core/src/main/java/com/hfwas/devops/apitest/
├── config/
│   └── ApiTestAutoConfiguration.java     # 自动配置
│
├── apidefine/                             # 接口管理
│   ├── controller/
│   │   ├── ApiGroupController.java       # 分组 CRUD
│   │   └── ApiDefinitionController.java  # 接口定义 CRUD
│   │
│   ├── service/
│   │   ├── ApiGroupService.java          # 分组业务
│   │   ├── ApiDefinitionService.java     # 接口定义业务
│   │   ├── ApiDefinitionParamService.java   # 参数业务
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
│   ├── dto/                              # 数据传输对象（请求）
│   │   ├── ApiGroupCreateDTO.java
│   │   ├── ApiGroupUpdateDTO.java
│   │   ├── ApiDefinitionCreateDTO.java
│   │   ├── ApiDefinitionUpdateDTO.java
│   │   ├── ApiDefinitionParamDTO.java
│   │   ├── ApiDefinitionResponseDTO.java
│   │   └── ApiDefinitionQueryDTO.java
│   │
│   ├── vo/                               # 视图对象（响应）
│   │   ├── ApiGroupVO.java
│   │   ├── ApiDefinitionVO.java
│   │   ├── ApiDefinitionDetailVO.java
│   │   ├── ApiDefinitionParamVO.java
│   │   └── ApiDefinitionResponseVO.java
│   │
│   └── convert/                          # 对象转换
│       ├── ApiGroupConvert.java
│       └── ApiDefinitionConvert.java
│
└── common/                               # 模块公共
    ├── enums/
    │   ├── ApiStatusEnum.java
    │   ├── HttpMethodEnum.java
    │   ├── ParamTypeEnum.java
    │   └── ParamDataTypeEnum.java
    └── exception/
        └── ApiTestException.java
```

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

## 四、数据流转

### 4.1 创建接口

```
前端表单 → ApiDefinitionCreateDTO
    → Controller (@Valid 校验)
    → Service (参数校验 + 状态设 DRAFT + 雪花ID)
    → Entity (MyBatis Plus 插入)
    → ApiDefinitionVO (返回前端)
```

### 4.2 查询接口列表

```
前端查询条件 → ApiDefinitionQueryDTO
    → Controller
    → Service (MyBatis Plus Page + 条件构造)
    → Page<ApiDefinitionVO>
    → 前端表格展示
```

### 4.3 状态流转

```
DRAFT   → PUBLISHED  : 发布操作
PUBLISHED → DEPRECATED : 废弃操作
PUBLISHED → DRAFT     : 重新编辑
DEPRECATED → DRAFT    : 恢复编辑
```

## 五、前端模块结构

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

## 六、依赖关系

```
api-test-core
    ├── user-api (用户/认证/权限)
    ├── spring-boot-starter-web
    ├── mybatis-plus-spring-boot3-starter
    ├── mysql-connector-j
    ├── redisson-spring-boot-starter
    └── hutool-all
```

## 七、与测试平台的关系

```
api-test 平台（整体）
├── api-management（接口管理）← 本期开发
├── debugger（接口调试）← 依赖接口定义
├── collection（集合管理）← 依赖接口定义
├── environment（环境变量）← 独立
├── scene（场景编排）← 依赖接口定义
├── stress（压测）← 依赖接口定义
└── report（报告）← 依赖以上所有
```