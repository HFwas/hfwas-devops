# 接口测试平台 — 单元测试策略

> 项目：hfwas-devops · api-test-core
> 测试框架：JUnit 5 + AssertJ + Mockito + SQLite
> 更新日期：2026-08-13

---

## 一、测试架构

### 1.1 技术栈

| 组件 | 选型 | 说明 |
|------|------|------|
| 测试框架 | JUnit 5 (Jupiter) | `@Test`, `@ParameterizedTest` |
| 断言库 | AssertJ 3.x | 流式断言，`assertThat().isEqualTo().hasSize()` |
| Mock 框架 | Mockito 5.x | `@Mock`, `@InjectMocks`, `@Spy` |
| 内存数据库 | SQLite (xerial JDBC) | `jdbc:sqlite::memory:`，cache=shared |
| ORM 测试 | MyBatis-Plus Spring Boot Test | Mapper 集成测试 |
| Web 层测试 | Spring MockMvc | Controller 接口测试 |
| JSON 处理 | Jackson | 序列化/反序列化 |

### 1.2 测试分层

```
┌─────────────────────────────────────────────┐
│          Controller 层 (MockMvc)             │  ← 接口校验、参数校验、异常包装
├─────────────────────────────────────────────┤
│          Service 层 (Mock/SQLite)            │  ← 业务规则、状态流转、事务边界
├─────────────────────────────────────────────┤
│          Mapper 层 (SQLite)                  │  ← SQL、索引、约束、审计字段
├─────────────────────────────────────────────┤
│          Entity / Converter 层               │  ← 实体构造、转换、JSON序列化
├─────────────────────────────────────────────┤
│          公共组件 / 工具类 / 枚举             │  ← 工具函数、枚举解析、异常
└─────────────────────────────────────────────┘
```

### 1.3 测试隔离策略

- 每个测试类使用 `@Transactional` + `@Rollback` 确保方法间隔离
- Mapper 测试使用 SQLite 内存数据库，每次测试方法独立事务
- 测试数据通过 `@BeforeEach` 统一初始化，或通过 `@Sql` 脚本加载
- Service 测试中，依赖 Mapper 的用 SQLite 真实数据库，依赖外部组件的用 Mockito

---

## 二、测试范围

### 2.1 Entity / 枚举测试

| 测试类 | 测试范围 |
|--------|---------|
| `ApiGroupEntityTest` | 实体构造、getter/setter、默认值、逻辑删除标记 |
| `ApiDefinitionEntityTest` | 同上，tags JSON字段序列化 |
| `ApiDefinitionParamEntityTest` | 参数实体，必填字段校验 |
| `ApiDefinitionResponseEntityTest` | 响应实体，body_example JSON |
| `ApiDefinitionVersionEntityTest` | 版本实体，snapshot JSON |
| `ApiDefinitionScriptEntityTest` | 脚本实体 |
| `ApiDefinitionAssertionEntityTest` | 断言实体 |
| `ApiDefinitionExtractEntityTest` | 提取实体 |
| `EnvironmentEntityTest` | 环境实体，变量实体 |
| `DebugHistoryEntityTest` | 调试历史实体，JSON字段 |
| `CollectionEntityTest` | 集合实体集合 |
| `EnumTest` | 所有枚举值的解析、非法值转换 |

### 2.2 Mapper 持久层测试

| 测试类 | 测试范围 |
|--------|---------|
| `ApiGroupMapperTest` | 分组CRUD、树形查询、唯一约束、逻辑删除 |
| `ApiDefinitionMapperTest` | 接口定义CRUD、分页、条件筛选、状态查询 |
| `ApiDefinitionParamMapperTest` | 参数批量保存、按类型查询 |
| `ApiDefinitionResponseMapperTest` | 响应CRUD |
| `ApiDefinitionVersionMapperTest` | 版本CRUD、版本快照 |
| `ApiDefinitionScriptMapperTest` | 脚本CRUD |
| `ApiDefinitionAssertionMapperTest` | 断言CRUD |
| `ApiDefinitionExtractMapperTest` | 提取CRUD |
| `EnvironmentMapperTest` | 环境CRUD、唯一性约束 |
| `EnvironmentVariableMapperTest` | 变量CRUD、批量 |
| `DebugHistoryMapperTest` | 调试历史CRUD、分页查询 |

### 2.3 Service 业务层测试

| 测试类 | 测试范围 |
|--------|---------|
| `ApiGroupServiceTest` | 分组CRUD、树形查询、递归删除、子节点迁移、重名校验 |
| `ApiDefinitionServiceTest` | 接口定义CRUD、状态流转(DRAFT→PUBLISHED→DEPRECATED)、版本管理 |
| `ApiDefinitionParamServiceTest` | 参数批量保存、覆盖保存 |
| `ApiDefinitionResponseServiceTest` | 响应CRUD |
| `ApiDefinitionVersionServiceTest` | 版本快照、版本递增 |
| `EnvironmentServiceTest` | 环境CRUD、变量管理、变量映射、敏感变量掩码 |
| `DebugHistoryServiceTest` | 调试历史CRUD、分页查询、批量删除 |
| `CollectionServiceTest` | 集合CRUD、文件夹树、集合项管理 |
| `CollectionRunServiceTest` | 集合执行、运行历史 |

### 2.4 Controller 接口测试

| 测试类 | 测试范围 |
|--------|---------|
| `ApiGroupControllerTest` | 分组API参数校验、CRUD接口、异常响应 |
| `ApiDefinitionControllerTest` | 接口定义API、状态流转、参数校验 |
| `EnvironmentControllerTest` | 环境API、参数校验 |
| `DebugHistoryControllerTest` | 调试历史API |
| `CollectionControllerTest` | 集合API |
| `CollectionFolderControllerTest` | 文件夹API |
| `CollectionItemControllerTest` | 集合项API |
| `CollectionRunControllerTest` | 集合执行API |

### 2.5 核心引擎组件测试

| 测试类 | 测试范围 |
|--------|---------|
| `HttpDebugEngineTest` | HTTP请求：正常、超时、状态码、异常捕获 |
| `ScriptSandboxTest` | JS脚本：正常执行、语法错误、超时、安全限制 |
| `VariableRendererTest` | 变量渲染：正常替换、未定义变量、默认值、嵌套变量 |
| `AssertionExecutorTest` | 断言：9种比较方式、JSONPath、空值 |
| `VariableExtractorTest` | 变量提取：响应体JSONPath、响应头、状态码 |

### 2.6 公共组件测试

| 测试类 | 测试范围 |
|--------|---------|
| `ApiTestExceptionTest` | 异常构造、code/message |
| `BaseResultTest` | 成功/失败响应构造 |
| `ExceptionAdviceTest` | 全局异常处理 |
| `EnumTest` | 枚举值解析、非法值 |

---

## 三、测试维度矩阵

### 3.1 通用测试维度

| 维度 | 说明 | 覆盖方式 |
|------|------|---------|
| T1: 正常流程 | 标准输入→预期输出 | 每个方法正向用例 |
| T2: 异常流程 | 非法输入→业务异常 | 参数校验、业务规则校验 |
| T3: 边界场景 | 空值、null、空集合、超长值 | 参数化测试 |
| T4: 状态流转 | 状态机各状态转换 | Service 状态测试 |
| T5: 唯一性约束 | 重复名称、重复path+method | Mapper/Service 唯一性测试 |
| T6: 逻辑删除 | 已删除数据不可查、过滤 | Mapper 逻辑删除测试 |
| T7: 审计字段 | createBy/updateBy/createTime 自动填充 | Mapper 审计测试 |
| T8: 事务回滚 | 业务异常→数据回滚 | Service 事务测试 |
| T9: JSON字段 | 空对象、空数组、畸形JSON | Entity/JSON 测试 |
| T10: 分页查询 | 第1页、超大页、pageSize=0、超限 | Mapper/Service 分页测试 |

### 3.2 特殊测试维度

| 维度 | 说明 | 覆盖模块 |
|------|------|---------|
| 树形结构 | 递归查询、递归删除、子节点迁移 | 分组、文件夹 |
| 脚本沙箱 | 安全隔离、超时、语法错误、非法API调用 | 调试引擎 |
| 变量渲染 | 未定义变量、默认值、循环引用、嵌套 | 变量渲染引擎 |
| 断言引擎 | 9种比较方式、JSONPath、null值 | 断言执行器 |
| HTTP引擎 | 连接超时、DNS失败、SSL错误、大响应体 | HTTP调试引擎 |
| 并发冲突 | 同一数据并发修改 | Service 乐观锁（如适用） |

---

## 四、测试数据准备

### 4.1 SQLite 内存数据库

- 测试数据库：`jdbc:sqlite::memory:?cache=shared&foreign_keys=on`
- 建表脚本：`src/test/resources/sql/init.sql`
- 每个测试类执行前自动建表，结束后自动销毁
- 使用 `@Transactional` 确保方法间数据隔离

### 4.2 测试数据模板

```java
// 通用测试数据工厂
public class TestDataFactory {
    public static ApiGroupEntity createGroup(String name) {
        ApiGroupEntity entity = new ApiGroupEntity();
        entity.setProjectId(1001L);
        entity.setName(name);
        entity.setSortOrder(0);
        return entity;
    }
    // ... 其他工厂方法
}
```

---

## 五、测试执行

### 执行命令

```bash
# 执行全部测试
mvn test -pl api-test-core -am

# 执行特定测试类
mvn test -pl api-test-core -am -Dtest=ApiGroupServiceTest

# 执行特定测试方法
mvn test -pl api-test-core -am -Dtest=ApiGroupServiceTest#createGroup_success_returnGroupVO

# 跳过测试编译
mvn test-compile -pl api-test-core -am
```

### 测试报告

```bash
# 生成测试报告（target/surefire-reports/）
mvn test -pl api-test-core -am
```

---

## 六、用例清单

> 此清单在测试编写完成后更新，记录每个测试类的实际测试方法数量。

### Entity 测试 (X 个方法)
- [ ] EnumTest — 枚举值解析
- [ ] EntityTest — 实体构造/默认值

### Mapper 测试 (X 个方法)
- [ ] ApiGroupMapperTest — 分组CRUD
- [ ] ApiDefinitionMapperTest — 接口定义CRUD
- [ ] EnvironmentMapperTest — 环境CRUD
- [ ] DebugHistoryMapperTest — 调试历史CRUD
- [ ] CollectionMapperTest — 集合CRUD

### Service 测试 (X 个方法)
- [ ] ApiGroupServiceTest — 分组业务
- [ ] ApiDefinitionServiceTest — 接口定义业务
- [ ] EnvironmentServiceTest — 环境业务
- [ ] DebugHistoryServiceTest — 调试历史业务
- [ ] CollectionServiceTest — 集合业务

### Controller 测试 (X 个方法)
- [ ] ApiGroupControllerTest — 分组API
- [ ] ApiDefinitionControllerTest — 接口定义API
- [ ] EnvironmentControllerTest — 环境API
- [ ] CollectionControllerTest — 集合API

### 引擎组件测试 (X 个方法)
- [ ] HttpDebugEngineTest — HTTP引擎
- [ ] ScriptSandboxTest — JS沙箱
- [ ] VariableRendererTest — 变量渲染
- [ ] AssertionExecutorTest — 断言
- [ ] VariableExtractorTest — 变量提取

---

## 七、覆盖率目标

| 层级 | 目标覆盖率 | 说明 |
|------|-----------|------|
| Entity | 100% | 所有实体类构造/getter/setter |
| Mapper | 90%+ | 核心CRUD方法 |
| Service | 85%+ | 业务逻辑、异常分支 |
| Controller | 80%+ | 接口参数校验、异常包装 |
| 核心引擎 | 90%+ | 调试引擎、脚本沙箱、变量渲染 |
| 公共组件 | 100% | 枚举、异常、工具类 |