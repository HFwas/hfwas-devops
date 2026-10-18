# 容器平台 API 接口文档

> 日期：2026-10-18
> 版本：v0.1
> 状态：生效（覆盖 P0 租户隔离变更）
> 基础路径：`/api/container`；WebSocket：`/api/ws/container`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-18 | 初版：P0 租户隔离后的容器 HTTP/WS/Argo 契约与失败示例 |

---

## 1. 认证与租户

| Header / 参数 | 必填 | 说明 |
|---------------|------|------|
| `Authorization` | 是 | `Bearer {JWT}` |
| `X-Tenant-Id` | 是 | 当前租户 ID；缺失返回 `401` + `code=11010` |

- 所有集群、Registry、Helm、Monitor、ImageSearch 接口都按租户隔离。
- 集群不属于当前租户：`403` + `code=30005`。
- 镜像仓库不属于当前租户：`403` + `code=30202`。
- 未登录：`401` + `code=11001`。

失败示例：

```json
{ "code": 11010, "msg": "未登录或租户上下文缺失", "data": null, "requestId": "a1b2c3d4" }
```

```json
{ "code": 30005, "msg": "无权访问该集群", "data": null, "requestId": "a1b2c3d4" }
```

## 2. 集群

### 2.1 分页查询

`POST /api/container/clusters/page`（请求体：`{"pageNo":1,"pageSize":20,"keyword":""}`）

正常响应：

```json
{ "code": 0, "msg": null, "data": { "records": [], "total": 0 }, "requestId": "a1b2c3d4" }
```

### 2.2 集群详情

`GET /api/container/clusters/{id}`

- 跨租户：`403` + `code=30005`。

### 2.3 创建集群

`POST /api/container/clusters`

请求体：

```json
{ "name": "prod-shanghai", "alias": "生产集群", "kubeconfig": "apiVersion: v1\n..." }
```

- `X-Tenant-Id` 缺失时拒绝，不会写入 `tenant_id=null`。

## 3. Registry / Image

- `POST /api/container/registries/page`：仅当前租户。
- `POST /api/container/registries`：`tenant_id` 由服务端写入。
- `GET /api/container/images/search?keyword=nginx`：仅聚合当前租户仓库；无租户返回 `401`。

## 4. Helm

- `GET /api/container/helm/charts`、`GET /api/container/clusters/{clusterId}/helm/releases`：按租户过滤。
- 无租户上下文不再回落 `PLATFORM_TENANT_ID=0`，直接 `401` + `code=11010`。

## 5. Monitor

- `GET /api/container/clusters/{clusterId}/monitor/overview` 等：先校验集群归属，再查询 Prometheus。
- 跨租户：`403` + `code=30005`。

## 6. Argo Workflows

- `/argo/**` 全部接口仅平台管理员可用；非管理员 `403` + `code=11003`。
- 当前按共享 Argo 集群处理，按租户 namespace 隔离留待后续版本。

## 7. WebSocket

浏览器不能设置自定义 header，JWT 通过 `Sec-WebSocket-Protocol` 传递；租户通过 query 参数传递并由服务端校验：

| 端点 | 说明 |
|------|------|
| `/api/ws/container/logs/{clusterId}?tenantId={tenantId}&namespace={ns}&pod={pod}` | Pod 日志 |
| `/api/ws/container/watch/{clusterId}?tenantId={tenantId}&namespace={ns}&resources=Pod` | 资源 Watch |
| `/api/ws/container/shell/{clusterId}/{namespace}/{pod}?tenantId={tenantId}&container={name}` | Pod Shell |

握手规则：

1. JWT 无效/缺失：`401`。
2. 用户停用：`403`。
3. `tenantId` 缺失或不是用户成员：`403`，不回落默认租户。
4. 集群不属于该租户：握手阶段 `403`，不建立连接。
5. 通过后 attributes 写入 `Long userId`/`Long tenantId`，handler 不再读取 `SecurityContext`。

## 8. Postman

`docs/container-platform/container-api.postman_collection.json` 覆盖集群/Registry/Helm/Monitor 的代表性请求与正常/失败示例。WebSocket 不适用 Postman，使用本文件第 7 节与浏览器 DevTools 验证。
