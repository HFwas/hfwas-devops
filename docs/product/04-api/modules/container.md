# 容器平台接口骨架

> 日期：2026-10-17
> 版本：v0.1
> 状态：骨架（路径与用途已核对；请求/响应/失败示例待补）
> 数据来源：`backend/container-core/.../controller/`、`docs/container-platform/`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：20 个 REST Controller + WebSocket 路径清单 |

---

基础路径：`/container`、`/argo`。除特别说明外均要求已认证，按 `tenant_id` 隔离。`{clusterId}` 为平台集群 ID，不是 K8s 集群名。

## 集群

| 方法 | 路径 | 用途 |
|------|------|------|
| POST | `/container/clusters/page` | 分页 |
| POST | `/container/clusters` | 纳管集群 |
| GET | `/container/clusters/{id}` | 详情 |
| PUT | `/container/clusters/{id}` | 更新 |
| DELETE | `/container/clusters/{id}` | 删除 |
| POST | `/container/clusters/{id}/test` | 连接测试 |
| GET | `/container/clusters/{id}/stats` | 集群统计 |
| GET | `/container/clusters/{id}/components` | 集群组件状态 |

## 命名空间与事件

| 方法 | 路径 | 用途 |
|------|------|------|
| GET | `/container/clusters/{clusterId}/namespaces` | 命名空间列表 |
| GET | `/container/clusters/{clusterId}/namespaces/{namespace}/events` | 事件列表 |

## 节点

| 方法 | 路径 | 用途 |
|------|------|------|
| GET | `/container/clusters/{clusterId}/nodes` | 节点列表 |
| GET | `/container/clusters/{clusterId}/nodes/{name}` | 节点详情 |

## Pod

| 方法 | 路径 | 用途 |
|------|------|------|
| GET | `/container/clusters/{clusterId}/pods` | Pod 列表 |
| GET | `/container/clusters/{clusterId}/namespaces/{namespace}/pods/{name}` | Pod 详情 |
| GET | `/container/clusters/{clusterId}/namespaces/{namespace}/pods/{name}/yaml` | Pod YAML |
| GET | `/container/clusters/{clusterId}/namespaces/{namespace}/pods/{name}/logs` | Pod 日志 |
| DELETE | `/container/clusters/{clusterId}/namespaces/{namespace}/pods/{name}` | 删除 Pod |
| POST | `/container/clusters/{clusterId}/namespaces/{namespace}/pods/{name}/upload` | 上传文件到 Pod |
| GET | `/container/clusters/{clusterId}/namespaces/{namespace}/pods/{name}/download` | 从 Pod 下载文件 |

## Deployment

| 方法 | 路径 | 用途 |
|------|------|------|
| GET | `/container/clusters/{clusterId}/deployments` | 列表 |
| GET | `/container/clusters/{clusterId}/namespaces/{namespace}/deployments/{name}` | 详情 |
| GET | `.../deployments/{name}/pods` | Pod 列表 |
| GET | `.../deployments/{name}/env` | 环境变量 |
| PUT | `.../deployments/{name}/env` | 更新环境变量 |
| GET | `.../deployments/{name}/volumes` | 卷 |
| PUT | `.../deployments/{name}/volumes` | 更新卷 |
| GET | `.../deployments/{name}/yaml` | YAML |
| PUT | `.../deployments/{name}/yaml` | 更新 YAML |
| PUT | `.../deployments/{name}/scale` | 扩缩容 |
| PUT | `.../deployments/{name}/restart` | 重启 |
| DELETE | `.../deployments/{name}` | 删除 |

（`...` 省略前缀 `/container/clusters/{clusterId}/namespaces/{namespace}`）

## StatefulSet

| 方法 | 路径 | 用途 |
|------|------|------|
| GET | `/container/clusters/{clusterId}/statefulsets` | 列表 |
| GET | `.../statefulsets/{name}/pods` | Pod 列表 |
| GET | `.../statefulsets/{name}/env` | 环境变量 |
| PUT | `.../statefulsets/{name}/env` | 更新环境变量 |
| GET | `.../statefulsets/{name}/volumes` | 卷 |
| PUT | `.../statefulsets/{name}/volumes` | 更新卷 |
| GET | `.../statefulsets/{name}/yaml` | YAML |
| PUT | `.../statefulsets/{name}/yaml` | 更新 YAML |
| DELETE | `.../statefulsets/{name}` | 删除 |

## Service / ConfigMap / Secret / PVC / StorageClass

| 方法 | 路径 | 用途 |
|------|------|------|
| GET | `/container/clusters/{clusterId}/services` | Service 列表 |
| GET | `.../services/{name}` | Service 详情 |
| GET | `.../services/{name}/yaml` | Service YAML |
| GET | `/container/clusters/{clusterId}/configmaps` | ConfigMap 列表 |
| GET | `.../configmaps/{name}/yaml` | ConfigMap YAML |
| PUT | `.../configmaps/{name}/yaml` | 更新 ConfigMap |
| DELETE | `.../configmaps/{name}` | 删除 ConfigMap |
| GET | `/container/clusters/{clusterId}/secrets` | Secret 列表 |
| DELETE | `.../secrets/{name}` | 删除 Secret |
| GET | `/container/clusters/{clusterId}/pvcs` | PVC 列表 |
| DELETE | `.../pvcs/{name}` | 删除 PVC |
| GET | `/container/clusters/{clusterId}/storageclasses` | StorageClass 列表 |

## 监控

| 方法 | 路径 | 用途 |
|------|------|------|
| GET | `/container/clusters/{clusterId}/monitor/overview` | 集群概览 |
| GET | `/container/clusters/{clusterId}/monitor/nodes/{name}/cpu` | 节点 CPU |
| GET | `.../nodes/{name}/memory` | 节点内存 |
| GET | `.../nodes/{name}/network` | 节点网络 |
| GET | `.../nodes/{name}/connections` | 节点连接数 |
| GET | `.../nodes/{name}/disk` | 节点磁盘 |
| GET | `.../nodes/{name}/load1` | 节点负载 |
| GET | `/container/clusters/{clusterId}/monitor/namespaces/{namespace}/pods/{name}/cpu` | Pod CPU |
| GET | `.../pods/{name}/memory` | Pod 内存 |
| GET | `.../pods/{name}/network` | Pod 网络 |
| GET | `.../pods/{name}/jvm/check` | JVM 可用性 |
| GET | `.../pods/{name}/jvm/heap` | JVM 堆 |
| GET | `.../pods/{name}/jvm/nonheap` | JVM 非堆 |
| GET | `.../pods/{name}/jvm/gc` | JVM GC |
| GET | `.../pods/{name}/jvm/thread` | JVM 线程 |
| GET | `.../pods/{name}/jvm/memory-pools` | JVM 内存池 |
| GET | `.../pods/{name}/jvm/class` | JVM 类加载 |
| GET | `.../pods/{name}/jvm/cpu` | JVM CPU |
| GET | `.../pods/{name}/jvm/after-gc` | GC 后内存 |

## 镜像仓库

| 方法 | 路径 | 用途 |
|------|------|------|
| POST | `/container/registries/page` | 分页 |
| POST | `/container/registries` | 创建 |
| GET | `/container/registries/{id}` | 详情 |
| PUT | `/container/registries/{id}` | 更新 |
| DELETE | `/container/registries/{id}` | 删除 |
| POST | `/container/registries/{id}/test` | 连接测试 |
| GET | `/container/registries/{registryId}/projects` | project 列表 |
| GET | `/container/registries/{registryId}/projects/{project}/repositories` | 仓库列表 |
| GET | `.../repositories/{repo}/artifacts` | 制品列表 |
| DELETE | `.../repositories/{repo}/artifacts/{reference}` | 删除制品 |
| GET | `.../artifacts/{reference}/scan` | 扫描概览 |
| GET | `.../artifacts/{reference}/scan/{reportId}/vulnerabilities` | 漏洞列表 |
| POST | `/container/registries/{registryId}/deploy` | 从镜像部署 |
| GET | `/container/images/search` | 镜像搜索 |

## Helm

| 方法 | 路径 | 用途 |
|------|------|------|
| GET | `/container/helm/repositories` | 仓库列表 |
| GET | `/container/helm/repositories/page` | 仓库分页 |
| POST | `/container/helm/charts/upload` | 上传 chart |
| GET | `/container/helm/charts` | chart 列表 |
| GET | `/container/helm/charts/{name}` | chart 详情 |
| GET | `/container/helm/charts/{name}/versions` | 版本列表 |
| GET | `/container/helm/charts/{name}/versions/{version}/values` | values |
| GET | `/container/helm/charts/{name}/versions/{version}` | 版本详情 |
| GET | `/container/clusters/{clusterId}/helm/releases` | release 列表 |
| POST | `/container/clusters/{clusterId}/helm/releases/{namespace}/dry-run` | 安装 dry-run |
| POST | `/container/clusters/{clusterId}/helm/releases/{namespace}` | 安装 |
| GET | `.../{namespace}/{name}` | release 详情 |
| GET | `.../{namespace}/{name}/history` | 历史 |
| GET | `.../{namespace}/{name}/values` | values |
| GET | `.../{namespace}/{name}/manifest` | manifest |
| GET | `.../{namespace}/{name}/resources` | 关联资源 |
| PUT | `.../{namespace}/{name}/upgrade/dry-run` | 升级 dry-run |
| PUT | `.../{namespace}/{name}/upgrade` | 升级 |
| PUT | `.../{namespace}/{name}/rollback` | 回滚 |
| DELETE | `.../{namespace}/{name}` | 卸载 |

## WebSocket

| 路径 | 用途 |
|------|------|
| `/ws/container/watch/**` | 资源 watch |
| `/ws/container/logs/**` | 日志流 |
| `/ws/container/shell/**` | Web Shell |

## 待补

1. 每个接口的查询参数、请求体、响应结构。
2. 失败示例：集群不可达、资源不存在、K8s RBAC 拒绝、镜像仓库认证失败。
3. Postman collection：`docs/api/container/container-api.postman_collection.json`，需按集群维度使用变量。
4. WebSocket 消息协议与鉴权。
