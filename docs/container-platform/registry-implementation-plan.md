# 镜像仓库功能 — 实施计划

> 日期：2026-09-10  
> 状态：已实施  
> 关联： [镜像仓库功能设计文档](./registry-design.md)、[Harbor 部署文档](./harbor-deployment.md)、[容器管理平台总体设计方案](./container-platform-design.md)

---

## Context

根据 `docs/container-platform/registry-design.md` 设计文档，实现镜像仓库功能 Phase 1。当前 k3s 集群已内置 Harbor v2.14.4（NodePort 30002），本项目容器管理平台（Phase 1 已实施）需扩展镜像仓库浏览和部署联动能力。

---

## 实施步骤（按顺序执行）

### Step 1: Backend — Schema 扩展

**修改** `backend/container-core/src/main/resources/db/container-schema.sql`
- 在 `cluster_info` 表后追加 `registry_info` 表
- 字段：id, tenant_id, name, alias, type, url, insecure, credential_username, credential_password, source, cluster_id, status, last_error, labels, created_at, updated_at
- UNIQUE(tenant_id, name)，FOREIGN KEY cluster_id → cluster_info(id)

### Step 2: Backend — ErrorCode 扩展

**修改** `ContainerErrorCode.java` — 追加镜像仓库相关错误码（30xxx 序列）：
- REGISTRY_NOT_FOUND(30201), REGISTRY_FORBIDDEN(30202), REGISTRY_CONNECTION_FAILED(30203), REGISTRY_CREDENTIAL_INVALID(30204), REGISTRY_ARTIFACT_DELETE_FAILED(30205), REGISTRY_DEPLOY_FAILED(30206), REGISTRY_NAMESPACE_REQUIRED(30207)

### Step 3: Backend — Entity + Mapper

**新建文件**（遵循 ClusterEntity + ClusterMapper 模式）：
- `entity/RegistryEntity.java` — `@Data`, `@TableName("registry_info")`
- `mapper/RegistryMapper.java` — `extends BaseMapper<RegistryEntity>`

### Step 4: Backend — RegistryCredentialCipher

**新建文件** `service/registry/RegistryCredentialCipher.java`
- 完全复用 KubeconfigCipher 的 AES-256-GCM 实现
- 使用同一 `container.credential-key` 配置项

### Step 5: Backend — RegistryAdapter 接口 + HarborAdapter 实现

**新建接口** `adapter/RegistryAdapter.java`
- 方法：listProjects, listRepositories, listArtifacts, deleteArtifact, getScanOverview, getVulnerabilities, health

**新建实现** `adapter/HarborAdapter.java`
- 使用 Spring RestClient（Spring Boot 3.4 内置）调用 Harbor REST API v2.0
- 请求路径示例：`/api/v2.0/projects`, `/api/v2.0/projects/{p}/repositories`, `/api/v2.0/projects/{p}/repositories/{r}/artifacts`
- Basic Auth 注入（从 RegistryCredentialCipher 解密）
- 全部 DTO 映射为内部 VO（不暴露 Harbor 模型）

### Step 6: Backend — RegistryService

**新建文件** `service/registry/RegistryService.java`
- CRUD: create(page, tenantId), getById, update, delete (with tenant check — 与 ClusterService 模式一致)
- testConnection: 调用 adapter.health()
- adapter 路由: 根据 type 返回 HarborAdapter 或 RegistryV2Adapter
- 内置仓库自动发现: `discoverBuiltin(clusterId)` — 用 fabric8 查 harbor namespace 下的 Service 和 Secret

### Step 7: Backend — RegistryHeartbeatJob

**新建文件** `service/registry/RegistryHeartbeatJob.java`
- `@Scheduled(fixedRate = 300_000)` 每 5 分钟
- 遍历所有 registry_info，调用 health()，更新 status
- 与 ClusterHeartbeatJob 模式一致

### Step 8: Backend — RegistryController + ImageController

**新建** `controller/RegistryController.java`
- `POST /container/registries/page`, `GET /container/registries/{id}`, `POST`, `PUT`, `DELETE`, `POST /{id}/test`
- 模式与 ClusterController 完全一致

**新建** `controller/ImageController.java`（镜像浏览 + 扫描）
- `GET /container/registries/{registryId}/projects`
- `GET /container/registries/{registryId}/projects/{project}/repositories`
- `GET /container/registries/{registryId}/projects/{project}/repositories/{repo}/artifacts`
- `DELETE /container/registries/{registryId}/.../artifacts/{reference}`
- `GET /container/registries/{registryId}/.../artifacts/{reference}/scan`
- `GET /container/registries/{registryId}/.../scan/{reportId}/vulnerabilities`

### Step 9: Backend — DeployFromImageService + DeployController

**新建** `service/registry/DeployFromImageService.java`
- deploy(registryId, request) → 构建完整镜像地址 → 创建 ImagePullSecret → 注入 ServiceAccount → 创建 Deployment → 可选创建 Service
- 完整流程参见设计文档 §8.2

**新建** `controller/DeployController.java`
- `POST /container/registries/{registryId}/deploy`

### Step 10: Backend — DTO VO 类

**新建** registry DTO 包下的全部 VO 类（遵循已有模式）：
- `RegistrySaveDTO`, `RegistryUpdateDTO`, `RegistryVO` — 不含 credentialPassword
- `RegistryProjectVO`, `RegistryRepoVO`
- `ArtifactVO`, `TagVO`
- `ScanOverviewVO`, `VulnerabilityVO`
- `DeployFromImageRequest`, `DeployResultVO`

### Step 11: Frontend — 类型和 API

**新建**：
- `types/registry.ts` — RegistryVO, RegistrySaveDTO, ArtifactVO 等接口定义
- `api/registry.ts` — registryApi.page/get/create/update/delete/test（与 clusterApi 模式一致）
- `api/image.ts` — imageApi.listProjects/listRepositories/listArtifacts/deleteArtifact/getScanOverview/getVulnerabilities
- `api/deploy.ts` — deployApi.deployFromImage

### Step 12: Frontend — 路由注册

**修改** `containerRoutes.ts` — 追加 registry 相关路由：
- `/container/registries` → RegistryListView
- `/container/registries/:id` → RegistryDetailView (projects + scans tabs)
- `/container/registries/:registryId/projects/:project/repos/:repo` → RepoDetailView

### Step 13: Frontend — 仓库列表页 + 注册对话框

**新建** `views/Registry/RegistryListView.vue`
- 表格展示所有已注册仓库（名称/地址/类型/状态/项目数/操作）
- "注册仓库"按钮 → RegistryFormDialog
- 行点击进入详情
- 测试连接、删除（内置仓库不可删除）

**新建** `components/Registry/RegistryFormDialog.vue`
- 表单：名称、别名、类型(harbor/registry_v2)、URL、是否跳过TLS、用户名、密码
- 注册后自动测试连接

### Step 14: Frontend — 仓库详情 + 镜像浏览

**新建** `views/Registry/RegistryDetailView.vue` — Tab 容器
- Tab1: 项目列表（加载自 Harbor API）
- Tab2: 扫描结果概览
- 基本信息卡片（URL / 类型 / 状态 / 错误信息）

**新建** `views/Registry/RepoDetailView.vue`
- Tag 列表：Digest、大小、推送时间
- 扫描状态徽标（ScanSeverityBadge）
- "部署"按钮 + "删除"按钮（二次确认）

### Step 15: Frontend — 扫描可视化

**新建**：
- `components/Registry/VulnerabilityTable.vue` — CVE 列表表格（ID、严重级别、组件、修复版本、描述）
- `components/Registry/ScanSeverityBadge.vue` — 严重级别彩色徽标（严重/高危/中危/低危/安全）

### Step 16: Frontend — 部署对话框

**新建** `components/Registry/DeployFromImageDialog.vue`
- 集群选择器（从 store 加载可用的集群列表）
- Namespace 选择器（选中集群后通过 namespaceApi 加载）
- 镜像名称（只读）、服务名称、副本数、容器端口
- 自动创建拉取凭据开关（默认开启）
- 环境变量（可折叠，动态增删行）、资源限制（CPU/内存）
- 提交后跳转到 Deployment 详情页

---

## 验证

1. **编译**: `cd backend && mvn compile -pl container-core,server -am`
2. **启动**: 启动后端后检查 `registry_info` 表已创建
3. **注册仓库**: 通过 API 注册内置 Harbor: `POST /container/registries` (url: http://harbor.harbor.svc:80, type: harbor, username: admin, password: Harbor12345)
4. **连接测试**: `POST /container/registries/{id}/test` → true
5. **镜像浏览**: `GET /container/registries/{id}/projects` → library 等项目
6. **Tag 列表**: `GET /container/registries/{id}/projects/library/repositories/nginx/artifacts` → Tag 列表
7. **部署**: 从 Tag 部署到集群，检查 ImagePullSecret 和 Deployment 是否创建成功
8. **前端**: 手动验证路由、表单、列表渲染

---

## 文件变更清单

### 新增文件（后端）

```
backend/container-core/src/main/java/com/hfwas/devops/container/
├── adapter/RegistryAdapter.java
├── adapter/HarborAdapter.java
├── controller/RegistryController.java
├── controller/ImageController.java
├── controller/DeployController.java
├── dto/RegistrySaveDTO.java
├── dto/RegistryUpdateDTO.java
├── dto/RegistryVO.java
├── dto/RegistryProjectVO.java
├── dto/RegistryRepoVO.java
├── dto/ArtifactVO.java
├── dto/TagVO.java
├── dto/ScanOverviewVO.java
├── dto/VulnerabilityVO.java
├── dto/DeployFromImageRequest.java
├── dto/DeployResultVO.java
├── entity/RegistryEntity.java
├── mapper/RegistryMapper.java
├── service/registry/RegistryCredentialCipher.java
├── service/registry/RegistryService.java
├── service/registry/RegistryHeartbeatJob.java
└── service/registry/DeployFromImageService.java
```

### 修改文件（后端）

```
backend/container-core/src/main/resources/db/container-schema.sql
backend/container-core/src/main/java/com/hfwas/devops/container/error/ContainerErrorCode.java
backend/container-core/pom.xml
```

### 新增文件（前端）

```
frontend/src/modules/container/
├── types/registry.ts
├── api/registry.ts
├── api/image.ts
├── api/deploy.ts
├── views/Registry/
│   ├── RegistryListView.vue
│   ├── RegistryDetailView.vue
│   └── RepoDetailView.vue
└── components/Registry/
    ├── RegistryFormDialog.vue
    ├── RegistryStatusBadge.vue
    ├── DeployFromImageDialog.vue
    ├── ScanSeverityBadge.vue
    └── VulnerabilityTable.vue
```

### 修改文件（前端）

```
frontend/src/modules/container/router/containerRoutes.ts
```