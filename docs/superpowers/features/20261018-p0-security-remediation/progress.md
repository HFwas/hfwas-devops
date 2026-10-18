# P0 安全与数据完整性修复 — AI 协作进度日志

> 日期：2026-10-18
> 版本：v1.0
> 说明：本文件为追加式日志。`版本` 表示格式版本，不随每轮追加递增；每轮记录见文末。

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v1.0 | 2026-10-18 | 建立日志，定义每轮记录格式 |

---

## 第 1 轮 · 2026-10-18 11:05 · 输出 P0 修复 spec

- **状态**：进行中
- **触发**：用户要求在存量代码审查后，先输出修改代码 spec，再由新 agent/workflow 审查。
- **目标**：产出覆盖 5 个 P0 的可评审代码变更 spec，明确改法、文件、迁移策略与验收标准。
- **读取**：`docs/review/20261018-code-review-standards-compliance.md`；`AGENTS.md`；`docs/backend/error-code-design.md`；`docs/product/03-database/database-documentation-standard.md`；`docs/superpowers/features/README.md`；PM/API 测试/容器 WS/迁移相关源码与配置。

### 做了什么

1. 把审查报告中的 5 个 P0 归并为 5 个独立修复包。
2. 补齐实现细节：PM 复用 `ProjectService` 守卫；API 测试新增 `ProjectAccessValidator` SPI；容器 WS 通过 query `tenantId` + 服务端成员校验；迁移统一到 `migrate.py` 与 `_schema_migrations`。
3. 写入 `design.md` v0.1，包含范围/非目标、逐项改法、接口影响、迁移与回滚、测试计划、验收标准、开放决策。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `docs/superpowers/features/20261018-p0-security-remediation/design.md` | 新增 | P0 修复代码变更 spec v0.1 |
| `docs/superpowers/features/20261018-p0-security-remediation/progress.md` | 新增 | 本进度日志 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| 代码事实核对 | 读取 `ProjectService`、`WorkItemService`、`ApiDefinitionController`、`ContainerWsAuthInterceptor`、`migrate.py`、`migrate.sh`、`05-pipeline-schema.sql` | 已完成 |
| 错误码存在性 | `grep ResultCode` | `TENANT_CONTEXT_MISSING(11010)`、`PROJECT_ACCESS_DENIED(20002)`、`WORK_ITEM_NOT_FOUND(21001)` 均存在 |
| 自动化测试 | 未执行 | 本轮仅输出 spec，尚未改代码 |

### 决策与取舍

- **决策**：交付平台采用 Keycloak JWT，而不是静态共享密钥。**理由**：SPA 不暴露密钥、可审计到用户。**未选方案**：静态 Bearer Token（无法区分用户）。
- **决策**：API 测试通过 `user-api` 的 SPI 复用 PM 项目守卫。**理由**：避免 api-test-core 直接依赖 pm-core。**未选方案**：复制一套项目查询逻辑。
- **决策**：容器 WS 用 query 参数传候选租户 + 服务端成员校验。**理由**：浏览器 WebSocket 不能自定义 header。**未选方案**：把租户写进 JWT（Keycloak token 当前无该 claim）。
- **决策**：修改基线 `05-pipeline-schema.sql` 删除 DROP。**理由**：不修改则任何 checksum 变化都可能丢数据。**未选方案**：只加新迁移（无法消除 DROP 地雷）。

### 偏差与假设

- `ASSUMPTION-1`：API 测试的 `projectId` 指向 PM 项目。
- `ASSUMPTION-2`：交付平台可复用主平台 Keycloak。
- `ASSUMPTION-3`：评审批准一次性修改基线迁移 05。

### 未完成 / 风险

- 尚未写 `test-cases.md`、`changes.md`（按流程在 spec 评审通过后编写）。
- 未实现代码，所有验收标准尚未执行。
- 修改 05 与删除 `userId` 参数均为破坏性/例外变更，需评审明确批准。

### 下一步

- **唯一动作**：由全新上下文的独立 agent 审查 `design.md`，重点检查守卫覆盖、跨租户语义、迁移例外与验收可测性。

---

## 第 2 轮 · 2026-10-18 11:13 · 三份独立评审与用户决策

- **状态**：已完成
- **触发**：用户要求新开 agent 审查 spec。
- **目标**：拿到独立评审结论，识别 v0.1 的错误与漏项。
- **读取**：`design.md` v0.1、审查报告、PM/API 测试/容器源码、迁移脚本、Keycloak realm、交付平台部署清单。

### 做了什么

1. 并行启动 3 个全新上下文只读评审 agent：
   - 租户隔离（P0-2/3/4）
   - 认证与迁移（P0-1/5）
   - 规范与可测性
2. 汇总三份报告：全部“不通过”；去重后约 12 项阻断。
3. 主会话独立复核关键事实：05 的 11 条 UPDATE、旧 `dependency_component` schema、Keycloak realm 无 client/role、chart 默认 NodePort。
4. 向用户提出 4 个决策问题并取得回答。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| 无 | - | 本轮仅评审，未改代码 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| 05 UPDATE 事实 | `grep -n "^UPDATE" 05-pipeline-schema.sql` | 11 条，确认会覆盖用户模板 |
| 旧 schema 风险 | `SqliteSchemaInitializer.java:52-76` | 旧表缺 pipeline_id 时确实需要重建 |
| Keycloak 前置 | `grep clientId/roles realm.json` | 只有 `hfwas-devops-web`，无 realm role |
| 部署形态 | chart `values.yaml` / raw manifest | 默认 NodePort 30881 |

### 决策与取舍

- **用户决策**：P0-5 采用“修改 05 + 条件化 UPDATE”。
- **用户决策**：权限错误纳入本 P0，统一 401/403/404。
- **用户决策**：交付运维平台认证“不加”，P0-1 移出本轮并记录残余风险。
- **用户决策**：容器租户隔离一次修完（WS + Cluster/Monitor + Registry + Helm + null tenant）。

### 偏差与假设

- v0.1 的“05 全部幂等”是错误假设，已由评审推翻并在 v0.2 修正。
- v0.1 的 P0-1 Keycloak 方案不再实施；用户接受残余风险。

### 未完成 / 风险

- 交付平台仍无认证，NodePort 暴露风险仍在。
- API 测试 `projectId` 是否等于 PM 项目 ID 仍未确认（ASSUMPTION-1）。

### 下一步

- **唯一动作**：按用户决策重写 spec 为 v0.2。

---

## 第 3 轮 · 2026-10-18 11:13 · 重写 spec v0.2

- **状态**：待评审
- **触发**：三份评审不通过 + 用户四项决策。
- **目标**：产出覆盖全部阻断项的 v0.2，可进入二次评审。

### 做了什么

1. 移出 P0-1，单列残余风险与临时缓解，明确不得写成“已修复”。
2. 新增 P0-0：权限错误 HTTP 401/403/404 映射与测试。
3. P0-2：补齐 PM 全部入口（board、comments counts、activities、views、modules、fields、layout、workflow、issue-type schemes）；修正 save 源/目标双校验；QueryEngine 区分显式 projectId 与当前租户。
4. P0-3：SPI 增加 `accessibleProjectIds()`；覆盖环境变量、definition、collection run、debug history、OR 过滤；补测试上下文 allow/deny Bean。
5. P0-4：修正 Monitor 根因；Registry/Helm/null tenant 一次修完；WS 用 `KeycloakUserProvisioningService` 解析平台 SysUser，不回落默认租户。
6. P0-5：05 保留原文件结构，删除 DROP，11 条 UPDATE 条件化；旧 schema 受控备份重建；迁移器逐语句执行、统一记录表、DROP 硬保护；新增数据保留测试与 CI 门禁。
7. 增加发布顺序、回滚约束、原型豁免表、v0.1 问题闭环表。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `docs/superpowers/features/20261018-p0-security-remediation/design.md` | 修改 | v0.1 → v0.2，按评审与用户决策重写 |
| `docs/superpowers/features/20261018-p0-security-remediation/progress.md` | 修改 | 追加第 2、3 轮 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| 文档结构 | 人工核对 AGENTS 必需章节 | 通过 |
| 阻断项闭环 | 对照三份评审逐条 | 已覆盖，待二次评审确认 |
| 代码/测试 | 未执行 | 本轮仅 spec |

### 决策与取舍

- **决策**：05 的模板 UPDATE 改为“仅空值初始化”，已存在的旧默认模板不自动升级。**理由**：避免覆盖用户编辑。**未选方案**：精确匹配旧默认字符串（需要 16-* 迁移）。
- **决策**：旧 `dependency_component` 缺列时先备份为 legacy 表再重建。**理由**：数据可重新解析，但保留原表以便追溯。
- **决策**：API 测试旧客户端多传的 userId 被忽略。**理由**：提供兼容窗口，便于后端先发布。
- **决策**：WS 租户不回落默认租户，必须显式提供且通过成员校验。

### 偏差与假设

- `ASSUMPTION-1` 仍未确认：API 测试 projectId 是否就是 PM 项目 ID。
- `ASSUMPTION-3` 已由用户选择“修改 05”确认。

### 未完成 / 风险

- 需要二次评审确认 v0.2 无新增阻断。
- 交付平台认证未实施，风险由用户接受。
- `test-cases.md`、`changes.md` 尚未创建，需在 spec 通过后补齐。

### 下一步

- **唯一动作**：用全新上下文的独立 agent 二次评审 `design.md` v0.2，重点核对 v0.1 阻断项是否真正闭环、是否引入新漏洞。

---

## 第 4 轮 · 2026-10-18 11:24 · v0.2 二次评审

- **状态**：已完成
- **触发**：v0.2 完成后按流程重新校验。
- **目标**：确认 v0.1 阻断项是否闭环，并找 v0.2 新引入的问题。

### 做了什么

1. 启动 2 个全新上下文只读评审 agent：
   - 租户与越权闭环（P0-0/2/3/4）
   - 迁移、HTTP 映射与规范（P0-5/0、文档、发布）
2. 汇总：两份均“不通过”，合计 11 个阻断项（去重后）。
3. 主会话复核关键事实：05 的 `options_json` 默认 `'[]'`、Registry/HelmChart/ImageSearch/Argo 的 fail-open、WS 握手未解析 clusterId、`command_template=''` 的语义、Helm rollback 会回退 ConfigMap。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| 无 | - | 本轮仅评审 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| options 默认值 | 读 `05:144`、`05:159-161` | 默认 `'[]'`，INSERT 不含该列；v0.2 条件永不命中 |
| 容器 fail-open | 读 `RegistryService.java:290-295`、`HelmChartService.java:374-376`、`ImageSearchController.java` | 确认仍有 null 放行 |
| WS cluster 校验 | 读 `ContainerWsAuthInterceptor.java` | 未解析 clusterId |
| Helm rollback | 读 `configmap-schema.yaml`、`deployment.yaml` | 旧 migrate.py/05 会随 ConfigMap 回退 |

### 决策与取舍

- **决策**：把“条件化 UPDATE”收敛为 **05 零 UPDATE**。**理由**：`command_template=''` 与 `default_command` 的 `-q` 无法区分旧默认值/用户清空/用户自定义。**未选方案**：继续加更复杂的条件。

### 偏差与假设

- v0.2 的“条件化 UPDATE 足够安全”被证明不成立。
- Helm rollback 的技术硬保护不现实，改为流程约束 + 备份 runbook。

### 未完成 / 风险

- 05 零 UPDATE 意味着历史库不会自动获得新模板；需要时另开 `16-*`。
- embedded 与 external 的 KUBECTL/DT 参数差异（P1-D4）仍存在。

### 下一步

- **唯一动作**：按二轮评审重写 spec 为 v0.3。

---

## 第 5 轮 · 2026-10-18 11:24 · 重写 spec v0.3

- **状态**：待评审
- **触发**：v0.2 两份二次评审均不通过。
- **目标**：产出覆盖全部二轮阻断项的 v0.3。

### 做了什么

1. 05 改为零 UPDATE：删除 11 条 UPDATE 与 DROP；种子值并入 `INSERT OR IGNORE`（command_template、options_json、default_command）；历史 `pipeline_job.command` 不改。
2. embedded 同步：classpath pipeline-schema 删除 UPDATE；`SqliteSchemaInitializer` 老表改为 legacy 备份后重建。
3. PM 补齐只读入口（fields/layout/workflow/issue-type/project-issue-type/board/views/modules）与所有 update-by-id 的旧项目校验。
4. API 测试补 `ApiGroup.getDetail`、列表参数改可选、fixture 驱动 validator、server MockMvc 测试基础设施。
5. 容器补 Registry `listByTenant/create`、ImageSearch、HelmChart、Argo（本轮 admin 门禁）；WS 握手阶段校验 cluster 与停用用户；attributes 改 Long。
6. P0-0 改为 `ResponseEntity` + `resolveHttpStatus`；补 30005/30202。
7. 迁移器增加锁内二次 checksum、migrations.lock、Recreate、busy_timeout、备份 runbook；明确 Helm rollback 只做流程约束。
8. 更新 §16 v0.2 问题闭环表与文件清单。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `docs/superpowers/features/20261018-p0-security-remediation/design.md` | 修改 | v0.2 → v0.3 |
| `docs/superpowers/features/20261018-p0-security-remediation/progress.md` | 修改 | 追加第 4、5 轮 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| 文档结构 | 人工核对 | 通过 |
| 二轮阻断项 | 对照两份评审逐条 | 已写入 §16，待三次评审确认 |
| 代码/测试 | 未执行 | 本轮仅 spec |

### 决策与取舍

- **决策**：05 零 UPDATE。**理由**：彻底避免历史数据改写。**未选方案**：条件化 UPDATE（空值语义歧义）。
- **决策**：Argo 本轮只做 admin 门禁。**理由**：按租户 namespace 隔离需要产品设计；admin 门禁先止血。
- **决策**：Helm rollback 只做流程约束。**理由**：rollback 会回退 ConfigMap 中的执行器，技术硬保护不可靠。

### 偏差与假设

- 与用户选择的“条件化 UPDATE”有偏差：进一步收敛为零 UPDATE；需三次评审确认。
- 历史库不自动升级模板，属于有意权衡。

### 未完成 / 风险

- 三次评审未执行。
- `test-cases.md`、`changes.md` 仍未创建。

### 下一步

- **唯一动作**：用全新上下文 agent 三次评审 `design.md` v0.3，只关注“是否仍有阻断项/新引入漏洞”，不再做全文重审。

---

## 第 6 轮 · 2026-10-18 11:55 · v0.3 第三次评审与用户决策

- **状态**：已完成
- **触发**：v0.3 完成后按流程重新校验。
- **目标**：确认二轮阻断项是否闭环，只找剩余/新引入阻断。

### 做了什么

1. 启动 2 个全新上下文只读评审 agent（租户/API/容器、迁移/HTTP/发布）。
2. 两份均“不通过”，剩余阻断约 13 项，去重后主要集中在：
   - `TenantContextFilter` 不捕获 `BizException`（403→500）。
   - `GET /pm/work-items/{id}`、page、字段 delete-by-id、FieldLayout 回退仍漏。
   - IMAGE_COSIGN 在 05 中没有种子行。
   - embedded/external 种子差异与 AC-5.6 冲突。
   - busy_timeout 未接入 configmap/deployment。
   - container Markdown/Postman 不同目录。
   - legacy 备份事务边界矛盾。
   - Argo admin 门禁与前端菜单不一致。
   - 05 零 UPDATE 与用户原选择的偏差。
3. 向用户提出 4 个决策并取得回答：
   - 05：零 UPDATE + 16-* 升级。
   - Argo：admin-only + 前端隐藏。
   - PM：禁止改归属 + 禁止新建全局字段。
   - 双模式：本轮补齐差异。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| 无 | - | 本轮仅评审与决策 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| datasource 注入点 | `grep SPRING_DATASOURCE_URL` | `configmap.yaml:14`、`deployment.yaml:127-128` |
| Argo 菜单 | `grep argo` navigation/products | `navigation.ts:204`、`products.ts:95` |
| FieldLayout 回退 | 读 `FieldLayoutService.java:36-51` | 存在 `byType.size()==1` 跨项目回退 |
| 字段 delete | 读 `FieldDefinitionService.java:174-214` | 无项目守卫 |

### 决策与取舍

- **用户决策**：四项均选择推荐方案。
- **决策**：v0.4 把 05 零 UPDATE 与 16-* 升级同时落地；16-* 是唯一允许 UPDATE 的迁移文件。

### 偏差与假设

- 无新增假设；ASSUMPTION-1 已在 v0.3 确认。

### 未完成 / 风险

- v0.4 尚未评审。
- 交付平台认证仍未做（用户接受）。

### 下一步

- **唯一动作**：写入 v0.4 并做一次最终聚焦评审。

---

## 第 7 轮 · 2026-10-18 11:55 · 写入 spec v0.4

- **状态**：待最终评审
- **触发**：用户四项决策。
- **目标**：把三轮评审剩余阻断项与四项决策全部写入 v0.4。

### 做了什么

1. 05 零 UPDATE + 16-* 升级：种子进 INSERT；新增 IMAGE_COSIGN；16-* 精确匹配旧默认模板。
2. embedded/external 补齐：KUBECTL、6 个 DT 参数、IMAGE_COSIGN、command_template/options_json/default_command。
3. PM 补 `GET /pm/work-items/{id}`、page 显式 assert、字段 delete-by-id、FieldLayout 回退删除、全局字段策略、禁止改归属。
4. P0-0：filter 同时捕获 `BizException`；`ResponseEntity` 映射；blob 错误处理。
5. 容器：WS handler 从 attributes 取 Long；Registry/ImageSearch/HelmChart/Release；Argo 后端 admin + 前端菜单/路由隐藏。
6. 迁移：legacy 事务 1 备份 COMMIT、事务 2 DROP/05；busy_timeout 接入 configmap/deployment；CI 禁止 01-06 UPDATE，16-* 白名单。
7. 文档：container Markdown/Postman 统一 `docs/container-platform/`。
8. 更新 §16 v0.3 问题闭环表。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `docs/superpowers/features/20261018-p0-security-remediation/design.md` | 修改 | v0.3 → v0.4 |
| `docs/superpowers/features/20261018-p0-security-remediation/progress.md` | 修改 | 追加第 6、7 轮 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| 文档结构 | 人工核对 | 通过 |
| 三轮阻断项 | 对照两份报告逐条 | 已写入 §16，待最终评审 |
| 代码/测试 | 未执行 | 本轮仅 spec |

### 决策与取舍

- **决策**：16-* 是唯一允许 UPDATE 的迁移文件。**理由**：历史模板升级必须显式、可回滚。**未选方案**：在 05 里条件化 UPDATE。
- **决策**：Argo 后端 admin + 前端 menu/router 同步。**理由**：避免“能进页面全 403”。
- **决策**：PM 全局字段禁止新建、既有只读。**理由**：语义简单、可测，避免 null 归属歧义。

### 偏差与假设

- 无。

### 未完成 / 风险

- 最终评审未执行。
- `test-cases.md`、`changes.md` 仍未创建，需 spec 通过后补齐。

### 下一步

- **唯一动作**：用单个全新上下文 agent 做最终聚焦评审，只核对 §16 与“新引入阻断”；通过后创建 `test-cases.md`、`changes.md`。

---

## 第 8 轮 · 2026-10-18 12:03 · v0.4 聚焦评审

- **状态**：已完成
- **触发**：v0.4 完成后的定稿评审。
- **目标**：核对 §16 的 17 项闭环，只找剩余/新引入阻断。

### 做了什么

1. 启动 1 个全新上下文只读评审 agent，只核对 §16 与新增阻断。
2. 结论“不通过”：17 项中 14 项实质闭环，3 项未闭环（FieldLayout、Argo 前端、legacy 事务），新增 2 项阻断（IMAGE_COSIGN enabled 语义、AC-5.3 计数）。
3. 其他主要问题：blob 错误修复位置、11006/12012 未映射、16-* 旧值清单缺失、CI UPDATE 范围、ApiGroup.getDetail 规则。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| 无 | - | 本轮仅评审 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| FieldLayout | 读 `FieldLayoutService.java:36-50` | `byType.size()==1` 回退仍在 |
| Argo 前端 | 读 `products.ts:9-17`、`ProductSwitcher.tsx:25`、`navigation.ts:204`、`router.tsx:171` | 无 adminOnly 字段/路由 |
| legacy | 读 `migrate.py:23-76` | 只有 `main()`，无 ensure 函数 |

### 决策与取舍

- **决策**：采纳评审建议，IMAGE_COSIGN `enabled=0`；Argo 前端三层隐藏；16-* 纳入 DT_FAIL_ON。
- **决策**：CI UPDATE 拦截扩展为“除 16-* 外全部禁止”，并接入 push CI。

### 偏差与假设

- 无。

### 未完成 / 风险

- v0.5 未评审。

### 下一步

- **唯一动作**：写入 v0.5 并做最后一次聚焦复审。

---

## 第 9 轮 · 2026-10-18 12:03 · 写入 spec v0.5

- **状态**：待最终聚焦复审
- **触发**：v0.4 评审的 3 个残留 + 2 个新增阻断。
- **目标**：把全部修改意见落到 v0.5。

### 做了什么

1. §5.2 正文明确删除 `FieldLayoutService.findLayoutRow` 的 `byType.size()==1` 回退。
2. §7.4 Argo 三层隐藏：`ConsoleProduct.adminOnly`、ProductSwitcher/groupProducts 过滤、逐菜单项、新增 `argo/*` 路由守卫。
3. §8.1.4 明确新增 `ensure_dependency_component_schema`；事务 1 独立提交备份，事务 2 失败 ROLLBACK 恢复原表、legacy 保留。
4. §8.1.2 IMAGE_COSIGN `enabled=0`，不进入前端 JobKind/任务市场；§8.8 AC 计数改为 19 行/18 enabled。
5. §4.1 blob 错误改到响应拦截器 `:47-61` async 处理；§4.2 补 11006/12012。
6. §8.1.3 16-* 引用 git 历史导出旧值，纳入 DT_FAIL_ON options_json。
7. §8.6 除 16-* 外全部禁止 UPDATE；maven.yml push CI 同步。
8. §6.3 ApiGroup.getDetail 明确先加载分组再 assert。
9. §12.1 container 作为既有模块放 `docs/container-platform/` 并同目录同名。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `docs/superpowers/features/20261018-p0-security-remediation/design.md` | 修改 | v0.4 → v0.5 |
| `docs/superpowers/features/20261018-p0-security-remediation/progress.md` | 修改 | 追加第 8、9 轮 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| 文档结构 | 人工核对 | 通过 |
| 11 项修正 | 对照评审报告 | 已写入，待复审 |
| 代码/测试 | 未执行 | 本轮仅 spec |

### 决策与取舍

- **决策**：IMAGE_COSIGN 仅作为 IMAGE 子步骤模板，`enabled=0`。**理由**：前端/后端 JobKind 没有该值，避免用户创建无法保存的任务。
- **决策**：Argo 前端三层隐藏。**理由**：只做菜单隐藏仍会从产品切换器进入。
- **决策**：legacy 备份独立提交。**理由**：保证失败时数据可追溯。

### 偏差与假设

- 无。

### 未完成 / 风险

- 最终复审未执行。
- `test-cases.md`、`changes.md` 仍未创建。

### 下一步

- **唯一动作**：最终聚焦复审 v0.5；确认无 Blocker 后创建 `test-cases.md`、`changes.md`。

---

## 第 10 轮 · 2026-10-18 12:07 · v0.5 最终验收通过，v0.6 修 4 条 Major

- **状态**：已完成（spec 评审门禁通过）
- **触发**：v0.5 最终聚焦复审。
- **目标**：确认无 Blocker，并修掉 4 条 Major。

### 做了什么

1. 最终验收 agent 结论：**通过**，无剩余 Blocker、无新引入 Blocker；11 项核对全部闭环。
2. 按 4 条 Major 修 v0.6：
   - §4.1 映射清单补 11006/12012→400，与 §4.2 一致。
   - §7.4/§12.3 Argo 补 `WorkbenchPage.tsx:10` 的 `groupProducts(isAdmin)` 过滤；明确当前仓库无 `/argo` 页面/路由，本轮不新增占位路由，后续新增时挂 admin 守卫；修正 `navigation.ts` 菜单项行号。
   - §8.6 迁移门禁只匹配语句开头关键字，避免 `ON DELETE SET NULL`、`deleted`、`update_time` 误报。
   - §8.2 明确 classpath 与 external 的 IMAGE_COSIGN 均为 `enabled=0`。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `docs/superpowers/features/20261018-p0-security-remediation/design.md` | 修改 | v0.5 → v0.6，修 4 条 Major |
| `docs/superpowers/features/20261018-p0-security-remediation/progress.md` | 修改 | 追加第 10 轮 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| 最终评审 | 独立 agent 核对 11 项 | 通过，无 Blocker |
| Major 修正 | 人工逐条 | 已修 |
| 代码/测试 | 未执行 | 本轮仅 spec |

### 决策与取舍

- **决策**：Argo 本轮不新增占位路由。**理由**：仓库当前没有 Argo 页面，新增空页面没有价值；后端 admin 门禁 + 前端产品/菜单/工作台隐藏已足够止血。
- **决策**：迁移门禁只匹配语句开头。**理由**：避免列名与 `ON DELETE` 误报。

### 偏差与假设

- 无。

### 未完成 / 风险

- `test-cases.md`、`changes.md` 尚未创建。
- 尚未开始编码；未跑任何测试。
- 交付平台认证未实施（用户接受）。

### 下一步

- **唯一动作**：创建 `test-cases.md`（每条 AC 标注 `文件::测试名`）与 `changes.md` 骨架，然后按阶段开始编码。

---

## 第 11 轮 · 2026-10-18 12:17 · 实施 P0-0 权限错误映射

- **状态**：已完成
- **触发**：用户指令“开始实施”。
- **目标**：完成 P0-0 并跑通测试。

### 做了什么

1. 新增 `test-cases.md` 与 `changes.md` 骨架。
2. `ApiErrorWriter.resolveHttpStatus` 补 11010/11006/12012/20002/30005/30202/21001/30001。
3. `ExceptionAdvice` 四个 handler 改为 `ResponseEntity` 动态状态。
4. `TenantContextFilter` 分别捕获 `BizException` 与 `IllegalArgumentException`，用动态状态写响应。
5. `DefaultTenantAccessValidator` 改抛 `BizException(TENANT_FORBIDDEN)`。
6. 前端 `request.ts` 错误拦截器改 async，支持 Blob 错误体。
7. 新增 3 个测试类，共 15 条。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `backend/server/.../ApiErrorWriter.java` | 修改 | 映射补全 |
| `backend/server/.../ExceptionAdvice.java` | 修改 | `ResponseEntity` |
| `backend/server/.../TenantContextFilter.java` | 修改 | 捕获 BizException |
| `backend/user-core/.../DefaultTenantAccessValidator.java` | 修改 | BizException |
| `frontend/src/shared/api/request.ts` | 修改 | blob 错误处理 |
| `backend/server/src/test/.../ApiErrorWriterTest.java` | 新增 | 7 条 |
| `backend/server/src/test/.../ExceptionAdviceTest.java` | 新增 | 6 条 |
| `backend/server/src/test/.../TenantContextFilterTest.java` | 新增 | 2 条 |
| `docs/superpowers/features/20261018-p0-security-remediation/test-cases.md` | 新增 | 用例 |
| `docs/superpowers/features/20261018-p0-security-remediation/changes.md` | 新增 | 变更骨架 + P0-0 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| 后端定向测试 | `mvn -B -f backend/pom.xml -pl server -am test -Dtest=ApiErrorWriterTest,ExceptionAdviceTest,TenantContextFilterTest` | 15/15 通过 |
| 前端类型 | `cd frontend && npx tsc --noEmit` | 通过 |

### 决策与取舍

- **决策**：`ExceptionAdvice` 用 `ResponseEntity` 而非 `HttpServletResponse`。**理由**：保留统一返回体与现有签名风格。
- **决策**：`ApiTestException` 也走 `resolveHttpStatus`，400 仍返回 200，权限码会映射。**理由**：避免 P1-B1 的范围扩散。

### 偏差与假设

- 无。

### 未完成 / 风险

- P0-5、P0-2、P0-3、P0-4 尚未实施。
- 前端 `request.ts` 的 Blob 错误用例尚未补自动化测试（TC-0-08 待补）。

### 下一步

- **唯一动作**：实施 P0-5（05 零 UPDATE + 16-* + migrate.py + 双模式 + 测试）。

---

## 第 12 轮 · 2026-10-18 12:38 · 实施 P0-5 迁移安全

- **状态**：已完成
- **触发**：P0-0 完成后按顺序实施 P0-5。
- **目标**：05 零 UPDATE、16-* 精确升级、migrate.py 重写、双模式一致、门禁与测试。

### 做了什么

1. 用一次性脚本把 05 的 8 段 command_template、DT_FAIL_ON options、DEPENDENCY_ANALYSIS default_command 并入 INSERT；删除 DROP、11 条 UPDATE；新增 IMAGE_COSIGN（enabled=0）。
2. 新增 `16-upgrade-task-template-seeds.sql`：旧库补列 + 精确匹配旧默认值的 UPDATE。
3. 重写 `migrate.py`：逐语句、事务、legacy 重建、`_schema_migrations`、旧表合并、DROP/UPDATE 硬保护、busy_timeout。
4. `migrate.sh` 改为薄包装。
5. classpath `pipeline-schema.sql` 同步：补 KUBECTL/DEPENDENCY_TRACK/IMAGE_COSIGN、6 个 DT 参数、8 个模板；删除 2 条 UPDATE。
6. `SqliteSchemaInitializer` 旧表改为 legacy 备份后重建。
7. datasource URL 增加 `busy_timeout=5000`；Helm strategy 改 Recreate。
8. 新增 `scripts/check-migrations.sh`、`scripts/migrations.lock`、`scripts/tests/test_migrate.py`。
9. PR/push CI 增加迁移门禁与测试。
10. 更新 AGENTS、data-dictionary、standard、deployment-ops、tables/pipeline。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `deploy/charts/backend/files/db/05-pipeline-schema.sql` | 修改 | 零 UPDATE |
| `deploy/charts/backend/files/db/16-upgrade-task-template-seeds.sql` | 新增 | 精确升级 |
| `deploy/charts/backend/files/db/migrate.py` | 修改 | 重写 |
| `deploy/charts/backend/files/db/migrate.sh` | 修改 | 包装 |
| `backend/server/src/main/resources/db/pipeline-schema.sql` | 修改 | 双模式补齐 |
| `backend/server/.../SqliteSchemaInitializer.java` | 修改 | legacy 备份 |
| `deploy/charts/backend/templates/configmap.yaml`、`deployment.yaml`、`values.yaml` | 修改 | busy_timeout/Recreate |
| `backend/server/src/main/resources/application.yml` | 修改 | busy_timeout |
| `scripts/check-migrations.sh`、`scripts/migrations.lock`、`scripts/tests/test_migrate.py` | 新增 | 门禁与测试 |
| `.github/workflows/pr-backend.yml`、`maven.yml` | 修改 | 迁移 job |
| `AGENTS.md` 与数据库/部署文档 | 修改 | 规范同步 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| 迁移单测 | `python3 -m unittest discover -s scripts/tests -p 'test_*.py'` | 11/11 通过 |
| 门禁 | `bash scripts/check-migrations.sh` | 13 个迁移通过 |
| 后端测试 | `mvn -B -f backend/pom.xml -pl server -am test` | 27/27 通过 |
| 全新库 | 临时 SQLite 执行 01-16 | 19 kind/18 enabled，integrity ok |
| 重跑 | 修改用户模板后重跑 | 全部 skip，用户值保留 |
| 05 checksum 变化 | 复制 SQL 目录并改 05 | 05 重跑，用户值保留 |
| 旧 schema | 构造缺列 dependency_component | legacy 保留数据，新表重建 |

### 决策与取舍

- **决策**：05 零 UPDATE；16-* 唯一允许 UPDATE。**理由**：彻底避免重跑改写历史数据。
- **决策**：classpath 与 external 补齐 KUBECTL/DT/IMAGE_COSIGN。**理由**：双模式一致，用户要求本轮补齐。
- **决策**：IMAGE_COSIGN `enabled=0`。**理由**：只作为 IMAGE 子步骤，避免用户创建无法编译的顶层任务。

### 偏差与假设

- `test_migrate.py` 的并发用例目前通过；在低配 CI 上若出现锁竞争，需观察。
- `pipeline_task_kind_version` 的 embedded/external 差异仍未补齐（P1-D4 的剩余部分）。

### 未完成 / 风险

- P0-2、P0-3、P0-4 尚未实施。
- 迁移文档中的历史 checksum 锁文件需随任何后续 SQL 变更更新。

### 下一步

- **唯一动作**：实施 P0-2 PM 全入口守卫。

---

## 第 13 轮 · 2026-10-18 12:49 · 实施 P0-2 PM 全入口守卫

- **状态**：已完成核心守卫；MockMvc 全入口测试待补
- **触发**：P0-5 完成后的下一阶段。
- **目标**：PM 读/写/删除全入口 fail closed，补核心测试。

### 做了什么

1. `ProjectService` 新增 `assertProjectAccess`/`accessibleProjectIds`；`resolveAccessContext`/`getById`/`requireTenantId` 改抛 `BizException`。
2. `WorkItemService` page/getById/save/delete/transition/links/listByStatus 全守卫；save 禁止改归属。
3. `QueryEngine` 改为 `execute(spec, tenantId)`；无 projectId 按租户子查询；排序白名单。
4. 评论/活动/导入导出/视图/模块/字段/布局/工作流/事项类型方案全部加入守卫。
5. `FieldLayoutService` 删除跨项目唯一回退。
6. `PmMetaController` board 一次校验；全局类型写入口仅 admin。
7. `pm-core` 增加 starter-test + surefire 3.6.0；新增 `ProjectServiceTest`（4）与 `QueryEngineTest`（3）。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `backend/pm-core/pom.xml` | 修改 | 测试依赖/surefire |
| `ProjectService.java`、`WorkItemService.java`、`QueryEngine.java` 等 13 个 PM 文件 | 修改 | 全入口守卫 |
| `ProjectServiceTest.java`、`QueryEngineTest.java` | 新增 | 7 条测试 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| pm-core 测试 | `mvn -B -f backend/pom.xml -pl pm-core -am test` | 7/7 通过 |
| server 编译 | `mvn -B -f backend/pom.xml -pl server -am compile` | 通过 |

### 决策与取舍

- **决策**：`listByStatus` 保留守卫，新增 `listByStatusForBoard` 供 board 复用，避免每状态重复查询。
- **决策**：全局字段禁止新建；既有全局字段允许已登录只读，写/删仅 admin。

### 偏差与假设

- 无。

### 未完成 / 风险

- 尚未补 `server` MockMvc 全入口测试；当前只有 7 条 pm-core 单测。
- `PmFieldDefinitionController` 的 add/remove 绑定路径未单独写测试。

### 下一步

- **唯一动作**：实施 P0-4 容器租户隔离。

---

## 第 14 轮 · 2026-10-18 12:49 · 实施 P0-4 容器租户隔离

- **状态**：已完成核心实现；HTTP 集成测试待补
- **触发**：P0-2 完成后的下一阶段。
- **目标**：容器 HTTP/WS/Argo 全部 fail closed。

### 做了什么

1. `SecurityHelper.requireTenantId()`；`ClusterService.getById/page` fail closed；`getByIdInternal`→`getByIdForSystem`。
2. Registry/DeployFromImage/HelmChart/HelmRelease 删除 nullable 回落。
3. 容器所有 Controller 的 `currentTenantId()` 批量替换为 `requireTenantId()`。
4. 新增 `ArgoAdminInterceptor` + `ContainerWebMvcConfig`，`/argo/**` 仅 admin。
5. `ContainerWsAuthInterceptor` 解析平台 SysUser、停用用户、租户成员、集群归属；三个 handler 从 attributes 取 Long tenantId。
6. `MonitorService` 删除 `getPrometheusUrlInternal` 死代码。
7. 前端 PodLogPanel/PodShell WS URL 带 tenantId；Argo 产品/菜单/工作台卡片 adminOnly。
8. 修复 Helm 测试的固定租户 SecurityContext；新增 3 个安全测试类。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `SecurityHelper.java`、`ClusterService.java`、`RegistryService.java`、`HelmChartService.java`、`HelmReleaseService.java`、`MonitorService.java` | 修改 | fail closed |
| 容器 Controller 20+ 文件 | 修改 | requireTenantId |
| `ArgoAdminInterceptor.java`、`ContainerWebMvcConfig.java` | 新增 | Argo admin |
| `ContainerWsAuthInterceptor.java`、`LogTailHandler.java`、`ResourceWatchHandler.java`、`PodShellWebSocketHandler.java` | 修改 | WS 身份/租户/集群 |
| `PodLogPanel.tsx`、`PodShell.tsx`、`products.ts`、`ProductSwitcher.tsx`、`navigation.ts`、`WorkbenchPage.tsx` | 修改 | 前端 |
| 3 个新测试类 + Helm 测试 | 新增/修改 | 9 条新测试 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| 容器测试 | `mvn -B -f backend/pom.xml -pl container-core -am test` | 71/71 通过 |
| 前端定向 | `npx vitest run` 容器/console | 14/14 通过 |
| 类型检查 | `npx tsc --noEmit` | 通过 |

### 决策与取舍

- **决策**：Argo 用 WebMvc 拦截器而不是逐方法加 admin。**理由**：覆盖所有现有/未来 `/argo/**` 入口。
- **决策**：WS 握手直接用 `KeycloakUserProvisioningService` 得到平台用户，保持与 HTTP 一致。**理由**：JWT `sub` 是 UUID，不能当 `userId`。
- **决策**：`getByIdForSystem` 仍保留在 `ClusterService`，仅启动自检调用；未拆独立类。**理由**：本轮时间/改动面控制，后续可拆。

### 偏差与假设

- 前端当前没有 `/argo/*` 页面/路由，admin 仍会看到产品入口但落到 `*` 兜底；这是存量缺口，本轮不新增占位页面。

### 未完成 / 风险

- Registry/ImageSearch/Helm 的 MockMvc 集成测试尚未补；当前靠 service 单测与编译。
- P0-3 agent 仍在实施，需等其完成后再跑全量构建。
- `changes.md` 的 P0-3 章节待补。

### 下一步

- **唯一动作**：等待 P0-3 agent 完成，跑全量后端/前端测试并补文档/Postman。

---

## 第 15 轮 · 2026-10-18 13:07 · P0-3 API 测试隔离完成

- **状态**：已完成
- **触发**：P0-3 实施 agent 返回。
- **目标**：确认 API 测试模块全入口隔离，并修复列表过滤的规模风险。

### 做了什么

1. agent 删除 8 个 Controller 实际 18 处 `@RequestParam Long userId`，新增 `ApiTestAccessGuard`。
2. 定义/分组/集合/文件夹/条目/运行/环境/调试/历史全入口守卫；run 写入 projectId/environmentId。
3. 前端删除 api-test 模块 userId 与调用方传参。
4. 测试新增 `TestProjectAccessValidator`/`TestCurrentUserAccessor`、5 个隔离测试类。
5. 主会话尝试把列表过滤改为 `pm_project` 子查询，但 api-test 测试库无该表，回退为 `IN (accessibleProjectIds)`；分块优化记为后续风险。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `backend/api-test-core/...` 30+ 文件 | 修改/新增 | 全入口守卫 |
| `frontend/src/modules/api-test/...` 7 文件 | 修改 | 删除 userId |
| `changes.md` | 修改 | P0-3 章节 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| 全量后端 | `mvn -B -f backend/pom.xml clean verify` | 10 模块全部通过；api-test 1065/1065 |
| 全量前端 | `npm test && npm run build` | 224 通过 / 1 skipped；构建通过 |
| userId 残留 | `grep @RequestParam.*userId` | 0 处 |

### 决策与取舍

- **决策**：保留 `IN (accessibleProjectIds)`。**理由**：避免 api-test 依赖 `pm_project` 表，测试上下文不需要建 PM schema。**后续**：项目数极大时加分块。
- **决策**：`run.projectId=null` 反查分支保留，测试库 NOT NULL 无法 fixture 覆盖。

### 偏差与假设

- 与 spec 的“子查询/分块”有偏差，已记录为已知限制。

### 未完成 / 风险

- 接口文档与 Postman 尚未更新。
- 独立 agent 实现校验未执行。

### 下一步

- **唯一动作**：补接口文档/Postman 与独立 agent 校验。

---

## 第 16 轮 · 2026-10-18 13:07 · 全量验证通过

- **状态**：已完成
- **触发**：P0-3 完成后的统一验证。
- **目标**：确认所有模块测试/构建通过。

### 做了什么

1. `mvn -B -f backend/pom.xml clean verify` → 10 个模块全部 SUCCESS。
2. `cd frontend && npm test` → 63 个测试文件、224 通过、1 skipped。
3. `npm run build` → 构建成功（仅有 chunk >600KB 的存量警告）。
4. `bash scripts/check-migrations.sh` → 13 个迁移通过。
5. `python3 -m unittest discover -s scripts/tests` → 11/11 通过。
6. 静态 grep：api-test 无 userId 参数、容器无 nullable currentTenantId、无 getByIdInternal。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `backend/server/src/main/resources/db/pipeline-schema.sql` | 修改 | 清理 EOF 空行/过期注释 |
| `changes.md`、`progress.md` | 修改 | 记录 P0-3 与全量验证 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| 后端全量 | `mvn -B -f backend/pom.xml clean verify` | SUCCESS |
| 前端测试 | `npm test` | 224 passed / 1 skipped |
| 前端构建 | `npm run build` | SUCCESS |
| 迁移门禁 | `check-migrations.sh` | ok |
| 迁移单测 | `unittest` | 11/11 |

### 决策与取舍

- 无。

### 偏差与假设

- 无。

### 未完成 / 风险

- 接口 Markdown/Postman 未补。
- 独立 agent 实现校验未执行。
- 前端 build 有存量 chunk 过大警告，不在本轮范围。

### 下一步

- **唯一动作**：补接口文档/Postman，并启动独立 agent 校验实现与文档一致性。

---

## 第 17 轮 · 2026-10-18 13:09 · 接口文档与 Postman

- **状态**：已完成
- **触发**：全量测试通过后的文档收尾。
- **目标**：按 AGENTS「接口变更」补齐 Markdown + Postman。

### 做了什么

1. `docs/backend/api_spec.md` 升 v0.2，删除 23 处 userId 路径参数，新增隔离/401/403/404 契约。
2. 新建 `docs/backend/api_spec.postman_collection.json`。
3. `docs/pm/pm-api.md` 升 1.4，补变更记录与 P0 项目归属说明；新建 PM Postman。
4. 新建 `docs/container-platform/container-api.md` + Postman，覆盖租户隔离、WS 握手、Argo admin。
5. `api-overview.md` v0.2、`error-code-design.md` 1.1、`api-module-matrix.md` v0.3。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| API 文档 3 份 + Postman 3 份 | 新增/修改 | 见 changes.md §4.6 |
| `api-overview.md`、`error-code-design.md`、`api-module-matrix.md` | 修改 | 版本与覆盖状态 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| Postman JSON | `python3 -m json.tool` × 3 | 通过 |
| 文档结构 | 人工核对版本头/变更记录 | 通过 |

### 决策与取舍

- **决策**：Postman 覆盖 P0 变更的代表接口，而不是一次性补齐全量 67 个 Controller。**理由**：先保证本轮变更可回归；全量补齐另立任务。

### 偏差与假设

- `docs/backend/api_spec.md` 初版无版本头，按规范记为 v0.1，本次 v0.2。

### 未完成 / 风险

- 用户/租户、Docgen、File Parser、Image 仍无 Postman。
- 独立 agent 实现校验未执行。

### 下一步

- **唯一动作**：启动独立 agent 做实现/文档/测试一致性校验。

---

## 第 18 轮 · 2026-10-18 13:21 · 独立校验与修复

- **状态**：已完成修复；待第二轮复验
- **触发**：独立校验 agent 第一轮结论“不通过”。
- **目标**：修复测试映射、文档路径、迁移语义与残留偏差。

### 做了什么

1. 全局字段 `addToType/removeFromType` 改为任何角色禁止修改绑定。
2. 前端 `PodLogPanel`/`PodShell` 缺 tenantId 时提示先选择租户，不静默。
3. `HelmReleaseService.tenantId()` 删除死回退。
4. `migrate.py` 的 checksum 读取移入 `BEGIN IMMEDIATE`。
5. 16-* 的 `default_command` 改为精确匹配旧值；明确不升级 `command_template`。
6. 新增 `test_upgrade_old_defaults`、`WorkItemServiceTest`、`FieldLayoutServiceTest`、`RegistryServiceSecurityTest`、`request.test.ts`。
7. 修正 Postman/container 文档方法与路径；PM 20001/404 表述；data-dictionary 记录表。
8. `test-cases.md` 映射改为实际测试名，未落地项标手工/待补。
9. `changes.md` 补 §5 偏差与 §7 校验记录；复核报告升 v0.2。
10. 清理 `__pycache__`/`javac.*.args`，`.gitignore` 增加 Python 缓存规则。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `FieldDefinitionService.java`、`HelmReleaseService.java`、`migrate.py`、`16-*` | 修改 | 校验修复 |
| `PodLogPanel.tsx`、`PodShell.tsx`、`request.ts` + 测试 | 修改/新增 | WS 提示、blob 测试 |
| `test-cases.md`、`changes.md`、`design.md`、接口文档、Postman | 修改 | 映射/路径/偏差 |
| `WorkItemServiceTest`、`FieldLayoutServiceTest`、`RegistryServiceSecurityTest` | 新增 | 补测试 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| 后端全量 | `mvn -B -f backend/pom.xml clean verify` | SUCCESS（server 29，pm 10，container 73） |
| 前端全量 | `npm test && npm run build` | 227 通过 / 1 skipped；构建成功 |
| 迁移单测 | `python3 -m unittest` | 12/12 |
| 迁移门禁 | `bash scripts/check-migrations.sh` | ok |
| Postman JSON | `python3 -m json.tool` × 3 | 通过 |

### 决策与取舍

- **决策**：16-* 不升级 `command_template`。**理由**：空模板是用户主动选择 DEFAULT_TEMPLATE，无法与未初始化区分；已在文件头与 changes.md §5 记录。
- **决策**：legacy 重建保留两个事务，legacy 先提交。**理由**：确保备份一定保留；原表由 05 重建，失败时下次启动重试。

### 偏差与假设

- 与 design 的“移到 ClusterSystemAccess”“>900 分块”“合并前校验”有偏差，已在 changes.md §5 记录。

### 未完成 / 风险

- 第二轮独立复验未执行。
- PM MockMvc/部分容器集成测试仍为手工。

### 下一步

- **唯一动作**：第二轮独立复验，确认修复项无新问题。

---

## 第 19 轮 · 2026-10-18 13:25 · 第二轮复验与残留修复

- **状态**：已完成（复验有条件通过，残留已修）
- **触发**：第二轮独立复验报告。
- **目标**：修完 4 个非阻断残留，形成最终交付。

### 做了什么

1. 修正 container Helm releases 路径为 `GET /api/container/clusters/{clusterId}/helm/releases`。
2. `git rm --cached` 旧 `backend/scripts/__pycache__/generate_doc.cpython-313.pyc`；`.gitignore` 在 deploy negation 后重新忽略 `__pycache__/`。
3. `changes.md` 升 v0.2、更新状态/阶段表、删除陈旧“前端 待执行”行；补 §7.3 第二轮结论。
4. `test-cases.md` 修正 TC-5-03/5-06/4-11/4-12 映射。
5. 复核报告 224→227，并补第二轮复验结论。
6. 新增 `test_empty_template_preserved`、`test_duplicate_column_continues`。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `docs/container-platform/container-api.md` | 修改 | Helm releases 路径 |
| `.gitignore` | 修改 | 重新忽略 deploy 下 __pycache__ |
| `backend/scripts/__pycache__/...pyc` | 删除（cached） | 旧跟踪文件 |
| `changes.md`、`test-cases.md`、复核报告 | 修改 | 版本/映射/数字 |
| `scripts/tests/test_migrate.py` | 修改 | 2 条新测试 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| 迁移单测 | `python3 -m unittest` | 14/14 通过 |
| 迁移门禁 | `bash scripts/check-migrations.sh` | ok |
| 缓存状态 | `git status --short \| grep pycache` | 仅保留预期的 cached 删除，无未跟踪 pyc |

### 决策与取舍

- **决策**：旧 pyc 用 `git rm --cached` 取消跟踪，文件删除；不 commit。
- **决策**：`.gitignore` 在 `!deploy/charts/**/files/**` 后增加 `deploy/charts/**/files/**/__pycache__/` 反向规则。

### 偏差与假设

- 无。

### 未完成 / 风险

- PM MockMvc/部分容器集成测试仍为手工（见 changes.md §5）。
- 交付平台认证未实施（用户决定）。

### 下一步

- **唯一动作**：向用户交付实施摘要；等待是否继续补 PM MockMvc/交付平台认证。
