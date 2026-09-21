# cn-app-operator

云原生应用交付 Operator — 基于 CRD + Helm 的应用生命周期管理。

## 架构

```
kubectl get app -A           ← 用户入口：应用聚合状态
         │
         ▼
CloudService (产品/云服务)     ← 服务蓝图：组件编排、参数、依赖
         │
         ▼
CloudComponent (组件实例)     ← 部署单元：对应一个 Helm release
```

## 快速开始

### 1. 安装 CRD

```bash
kubectl apply -f config/crd/
```

### 2. 部署 Operator

```bash
make deploy
# 或直接
kubectl apply -f config/rbac/
kubectl apply -f config/manager/
```

### 3. 部署示例

```bash
# 部署独立组件
kubectl apply -f config/samples/cloudcomponent-redis.yaml

# 部署完整产品（推荐）
kubectl apply -f config/samples/cloudservice-order-core.yaml
kubectl apply -f config/samples/app-order-platform.yaml
```

### 4. 查看状态

```bash
kubectl get app -A
kubectl get cloudservice -A
kubectl get cloudcomponent -A
```

## CRD 定义

| CRD | 作用域 | 用途 |
|-----|--------|------|
| `apps.delivery.hfwas.io` | Namespaced | 顶层聚合，用户入口 |
| `cloudservices.delivery.hfwas.io` | Namespaced | 产品/服务蓝图 |
| `cloudcomponents.delivery.hfwas.io` | Namespaced | 组件实例 = Helm release |

## 核心流程

```
1. 用户创建 App CR（或 CloudService CR）
2. CloudService 控制器 → 生成 CloudComponent CR（拓扑排序）
3. CloudComponent 控制器 → 检查依赖 → Helm install/upgrade
4. LabelMarker 打标 → 聚合 Workload 状态 → 回写 Phase
5. App 控制器 → 聚合所有 CloudService 状态
```

## 参数 Merge 顺序

Chart 默认值 → 组件默认 → 产品级覆盖 → 服务级覆盖 → 组件覆盖 → 全局参数 → Webhook 改写

## 项目结构

```
cn-app-operator/
├── cmd/operator/          # 入口
├── api/v1/               # CRD 类型定义
├── controllers/          # 控制器
├── pkg/
│   ├── helm/             # Helm SDK 封装
│   ├── merge/            # 参数合并
│   ├── labelmarker/      # 资源打标
│   ├── status/           # 状态聚合
│   └── deps/             # 依赖拓扑排序
├── config/
│   ├── crd/              # CRD YAML
│   ├── rbac/             # RBAC 配置
│   ├── manager/          # Deployment
│   └── samples/          # 示例 CR
└── Makefile
```