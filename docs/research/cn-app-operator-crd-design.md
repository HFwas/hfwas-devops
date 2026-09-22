# cn-app-operator CRD 完整技术实现方案

> 日期：2026-09-22
> 版本：v0.3
> 状态：已实施（代码在 `cn-app-operator/`），本版增量吸收 AAP 四类 CRD 的可用机制
> 对应实现：`cn-app-operator/`（CRD YAML + Go 控制器 + Helm 封装 + 样本）
> 研究来源：
>   - `docs/research/yunyou-operator-helm-package.md`
>   - `docs/research/operator-job-deployment-system.md`
>   - CNStack/ADP 公开资料
> 领域：delivery.hfwas.io

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-09-21 | 初版：三层 CRD YAML 定义、参数 merge 策略、Operator 调和循环、App 聚合逻辑、Helm 交互、依赖排序 |
| v0.2 | 2026-09-21 | API Group 改为 delivery.hfwas.io，移除 License 校验相关字段与章节 |
| v0.3 | 2026-09-22 | 对照 `aap-product-operator 四类 CRD.md` 增量吸收：新增 ProductTask CRD（集群级 before/after 步骤排序）；App 增 releaseID/planRevision；CloudComponent 增 podsDetail/workloadDiffs/PersistentVolumeConfigs/refResources；定论 lastAppliedManifest 不存整份 manifest；明确 enum 策略；修正 §12–§17 子节编号错位 |

---

## 目录

1. [概述](#1-概述)
2. [CRD 分组与命名约定](#2-crd-分组与命名约定)
3. [App CRD 完整定义](#3-app-crd-完整定义)
4. [CloudService CRD 完整定义](#4-cloudservice-crd-完整定义)
5. [CloudComponent CRD 完整定义](#5-cloudcomponent-crd-完整定义)
6. [ProductTask CRD 完整定义](#6-producttask-crd-完整定义)
7. [参数 Schema 设计](#7-参数-schema-设计)
8. [参数 Merge 策略](#8-参数-merge-策略)
9. [Operator 调和循环](#9-operator-调和循环)
10. [LabelMarker 打标规范](#10-labelmarker-打标规范)
11. [状态聚合策略](#11-状态聚合策略)
12. [失败重试与回滚策略](#12-失败重试与回滚策略)
13. [App 聚合逻辑与 kubectl get app 展示](#13-app-聚合逻辑与-kubectl-get-app-展示)
14. [Helm 交互](#14-helm-交互)
15. [组件依赖排序](#15-组件依赖排序)
16. [Webhook 集成](#16-webhook-集成)
17. [多集群策略](#17-多集群策略)
18. [ControllerRevision 历史管理](#18-controllerrevision-历史管理)
19. [开放问题与建议](#19-开放问题与建议)

---

## 1. 概述

### 1.1 三层 CRD 体系

```
kubectl get app -A           # ─── 用户入口：应用聚合状态
         │
         ▼
CloudService (产品/云服务)     # ─── 服务定义：参数 schema、组件列表、依赖、镜像清单
                                  CloudService 定义是只读的「蓝图」
         │
         ▼
CloudComponent (组件实例)     # ─── 部署单元：对应一个 Helm release
                                 一个 CloudComponent = 一个 helm release
                                 组件分类：云服务组件、集群组件、项目组件
```

**ProductTask 不在这条链路里**（v0.3 新增，见第 6 节）。它是集群级的旁路资源，只声明「某一次发布里各步骤的先后」，不参与 App → CloudService → CloudComponent 的聚合与状态汇总。

### 1.2 当前约束

- **应用定义只支持 Helm Chart**。OAM 是预留扩展，不是当前执行模型。
- 一个 CloudComponent 对应一个 Helm release
- 组件分为三类：云服务组件（随产品部署）、集群组件（全局唯一，只升不降）、项目组件（项目级）

### 1.3 API Group 定义

```yaml
# 备选：早期曾考虑与 CNStack/ADP 生态对齐的分组名（如 antstack.alipay.com，见
#   docs/research/aap-product-operator 四类 CRD.md），最终未采用——避免暗示与阿里云绑定，
#   并便于接入自己的控制面。
#
# 本方案使用 delivery.hfwas.io，适配时可通过 sed 快速替换。
```

---

## 2. CRD 分组与命名约定

| CRD | Group | Version | Kind | 作用域 | shortNames |
|-----|-------|---------|------|--------|------------|
| App | delivery.hfwas.io | v1 | App | Namespaced | app |
| CloudService | delivery.hfwas.io | v1 | CloudService | Namespaced | cs, csvc |
| CloudComponent | delivery.hfwas.io | v1 | CloudComponent | Namespaced | cc, ccmp |
| ProductTask | delivery.hfwas.io | v1 | ProductTask | **Cluster** | pt, ptask |

---

## 3. App CRD 完整定义

App 是用户面向的顶层聚合资源。它不直接定义任何部署行为，而是通过 `spec.services` 引用下属 CloudService，并从所有下属资源聚合状态到 `status`。

```yaml
apiVersion: apiextensions.k8s.io/v1
kind: CustomResourceDefinition
metadata:
  name: apps.delivery.hfwas.io
spec:
  group: delivery.hfwas.io
  names:
    kind: App
    listKind: AppList
    plural: apps
    singular: app
    shortNames:
      - app
  scope: Namespaced
  versions:
    - name: v1
      served: true
      storage: true
      subresources:
        status: {}
      additionalPrinterColumns:
        - name: Display Name
          type: string
          jsonPath: .spec.displayName
        - name: Status
          type: string
          jsonPath: .status.phase
        - name: Services
          type: integer
          jsonPath: .status.aggregatedSummary.totalServices
        - name: Ready
          type: integer
          jsonPath: .status.aggregatedSummary.readyServices
        - name: Age
          type: date
          jsonPath: .metadata.creationTimestamp
      schema:
        openAPIV3Schema:
          type: object
          required:
            - spec
          properties:
            spec:
              type: object
              required:
                - displayName
              properties:
                # 应用显示名称（中文描述，展示在 UI 和 kubectl 列表）
                displayName:
                  type: string
                  minLength: 1
                  maxLength: 128
                  pattern: '^[a-zA-Z0-9_\-一-龥]+$'
                # 这一次发布的关联号。下属 CloudComponent 与 ProductTask 用同一个值串起来。
                # 留空表示「非发布态」的直接创建。
                releaseID:
                  type: string
                  description: "这一次发布的关联号；ProductTask 与 CloudComponent 与之对齐"
                # 现场规划修订号（对应 siteplan 里该产品实例的 revision）。
                planRevision:
                  type: string
                  description: "现场规划修订号，用于追溯这一次发布用的是哪一版现场规划"

                # 应用描述
                description:
                  type: string
                  maxLength: 2048

                # 应用版本标识（全局版本号，产品方每次发布递增）
                version:
                  type: string
                  pattern: '^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(-[a-zA-Z0-9]+)?$'
                  example: "1.2.0"

                # 关联的 CloudService 列表
                services:
                  type: array
                  items:
                    type: object
                    required:
                      - name
                    properties:
                      name:
                        type: string
                        description: "CloudService 名称（同一 namespace）"
                      alias:
                        type: string
                        description: "可选别名，用于 UI 展示"
                      revision:
                        type: string
                        description: "// TODO: design decision needed — 是否允许 App 锁定 CloudService 版本？"
                      # 服务级参数覆盖（更高优先级覆盖 CloudService spec.parameters）
                      parameters:
                        type: object
                        x-kubernetes-preserve-unknown-fields: true
                        description: "服务级参数覆盖，merge 到该 CloudService 的 spec.parameters"

                # 全局参数（跨所有服务生效）
                globalParameters:
                  type: object
                  properties:
                    imageRegistry:
                      type: string
                      description: "全局镜像仓库前缀，如 registry.cn-hangzhou.aliyuncs.com"
                    defaultStorageClass:
                      type: string
                      description: "默认 StorageClass 名称"
                    domainSuffix:
                      type: string
                      description: "全局域名后缀，如 example.com"
                  x-kubernetes-preserve-unknown-fields: true

                # 资源清理策略
                terminationPolicy:
                  type: string
                  enum:
                    - Delete
                    - Orphan
                  default: Delete
                  description: "删除 App 时是否级联删除下属 CloudService/CloudComponent"

            status:
              type: object
              properties:
                # 总体阶段
                phase:
                  type: string
                  enum:
                    - Pending
                    - Deploying
                    - Ready
                    - Degraded
                    - Failed
                    - Unknown
                    - Deleting
                  default: Pending

                # 聚合摘要
                aggregatedSummary:
                  type: object
                  properties:
                    totalServices:
                      type: integer
                      minimum: 0
                    readyServices:
                      type: integer
                      minimum: 0
                    degradedServices:
                      type: integer
                      minimum: 0
                    failedServices:
                      type: integer
                      minimum: 0
                    totalComponents:
                      type: integer
                      minimum: 0
                    readyComponents:
                      type: integer
                      minimum: 0

                # 实际观察到的发布号（从下属 CloudComponent 收敛；与 spec.releaseID 不一致表示还在切换中）
                observedReleaseID:
                  type: string
                  description: "当前实际生效的发布号"

                # 每个下属服务的简要状态
                serviceStatuses:
                  type: array
                  items:
                    type: object
                    properties:
                      name:
                        type: string
                      releaseID:
                        type: string
                        description: "期望的发布号"
                      observedReleaseID:
                        type: string
                        description: "该服务实际观察到的发布号"
                      phase:
                        type: string
                      errorMessage:
                        type: string
                      lastUpdateTime:
                        type: string
                        format: date-time

                # 标准化 conditions
                conditions:
                  type: array
                  items:
                    type: object
                    required:
                      - type
                      - status
                      - lastTransitionTime
                    properties:
                      type:
                        type: string
                        enum:
                          - Available
                          - Progressing
                          - Degraded
                      status:
                        type: string
                        enum:
                          - "True"
                          - "False"
                          - "Unknown"
                      reason:
                        type: string
                      message:
                        type: string
                      lastTransitionTime:
                        type: string
                        format: date-time
                      observedGeneration:
                        type: integer

                observedGeneration:
                  type: integer
                  format: int64

                # 操作审计历史
                history:
                  type: array
                  items:
                    type: object
                    properties:
                      revision:
                        type: integer
                        description: "递增的版本号"
                      version:
                        type: string
                        description: "App spec.version"
                      operation:
                        type: string
                        enum:
                          - Created
                          - Updated
                          - Deleted
                          - RolledBack
                      operator:
                        type: string
                        description: "操作人（从 JWT/身份信息提取）"
                      message:
                        type: string
                      parameterSnapshot:
                        type: object
                        x-kubernetes-preserve-unknown-fields: true
                      timestamp:
                        type: string
                        format: date-time
                  # // TODO: design decision needed — history 最大条目数（建议 50）
```

---

## 4. CloudService CRD 完整定义

CloudService 定义了一个产品/云服务的完整蓝图：组件构成、参数 schema、依赖关系、镜像清单和多集群拓扑。

```yaml
apiVersion: apiextensions.k8s.io/v1
kind: CustomResourceDefinition
metadata:
  name: cloudservices.delivery.hfwas.io
spec:
  group: delivery.hfwas.io
  names:
    kind: CloudService
    listKind: CloudServiceList
    plural: cloudservices
    singular: cloudservice
    shortNames:
      - cs
      - csvc
  scope: Namespaced
  versions:
    - name: v1
      served: true
      storage: true
      subresources:
        status: {}
      additionalPrinterColumns:
        - name: Display Name
          type: string
          jsonPath: .spec.displayName
        - name: Status
          type: string
          jsonPath: .status.phase
        - name: Components
          type: integer
          jsonPath: .status.componentCount
        - name: Ready
          type: integer
          jsonPath: .status.readyCount
        - name: Age
          type: date
          jsonPath: .metadata.creationTimestamp
      schema:
        openAPIV3Schema:
          type: object
          required:
            - spec
          properties:
            spec:
              type: object
              properties:
                # 产品显示名称
                displayName:
                  type: string
                  minLength: 1
                  maxLength: 128

                # 产品版本
                version:
                  type: string
                  pattern: '^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(-[a-zA-Z0-9]+)?$'
                  description: "CloudService 版本号，递增触发 Operator 发现组件变更"

                # 产品描述
                description:
                  type: string
                  maxLength: 4096

                # 图标 URL（UI 展示用）
                icon:
                  type: string
                  format: uri

                # 组件定义列表
                components:
                  type: array
                  minItems: 1
                  items:
                    type: object
                    required:
                      - name
                      - chart
                      - componentType
                    properties:
                      # 组件名称（在 CloudService 内唯一）
                      name:
                        type: string
                        pattern: '^[a-z]([a-z0-9-]*[a-z0-9])?$'
                        maxLength: 63
                        description: "组件名称，也是生成的 CloudComponent 名称"

                      # 组件显示名称
                      displayName:
                        type: string
                        maxLength: 128

                      # 组件类型
                      componentType:
                        type: string
                        enum:
                          - Service           # 云服务组件（随产品部署）
                          - Cluster           # 集群组件（全局唯一）
                          - Project           # 项目组件
                        description: "组件类型决定生命周期行为，分类见 1.2"

                      # Helm Chart 引用
                      chart:
                        type: object
                        required:
                          - repository
                          - version
                        properties:
                          repository:
                            type: string
                            description: "Chart 仓库 URL 或 OCI 引用"
                            example: "oci://registry.example.com/charts/redis"
                          version:
                            type: string
                            description: "Chart 版本号"
                            example: "17.3.0"
                          name:
                            type: string
                            description: "Helm Chart 名称（仓库中有多个 chart 时需要）"

                      # 组件默认 Values（chart 默认值之外的产品级默认值）
                      defaultValues:
                        type: object
                        x-kubernetes-preserve-unknown-fields: true
                        description: "组件的默认 values，将与 chart 默认 values.yaml merge"

                      # 该组件暴露给用户的可覆盖参数（参数 schema）
                      parameters:
                        type: array
                        items:
                          type: object
                          properties:
                            name:
                              type: string
                              description: "参数名"
                            displayName:
                              type: string
                              description: "UI 展示名称"
                            description:
                              type: string
                            type:
                              type: string
                              enum:
                                - string
                                - integer
                                - number
                                - boolean
                                - array
                                - object
                            defaultValue: {}
                            required:
                              type: boolean
                              default: false
                            path:
                              type: string
                              description: "在 values 中的点号路径，如 `resources.limits.memory`"
                            validation:
                              type: object
                              properties:
                                pattern:
                                  type: string
                                  description: "正则表达式校验（string 类型）"
                                minLength:
                                  type: integer
                                maxLength:
                                  type: integer
                                minimum:
                                  type: number
                                maximum:
                                  type: number
                                enum:
                                  type: array
                                  items:
                                    type: string
                            # 参数作用域标记
                            scope:
                              type: string
                              enum:
                                - global
                                - service
                                - component
                              default: component
                              description: "组件的参数作用域"

                      # 依赖声明
                      dependencies:
                        type: array
                        items:
                          type: object
                          properties:
                            component:
                              type: string
                              description: "被依赖的组件名称"
                            type:
                              type: string
                              enum:
                                - Hard       # 硬依赖：被依赖组件必须 Ready
                                - Soft       # 软依赖：被依赖组件先部署，不阻塞
                                - Optional   # 可选：存在则等待，不存在则跳过
                              default: Hard
                            condition:
                              type: string
                              enum:
                                - Ready
                                - Deployed
                              default: Ready
                              description: "被依赖组件达到什么状态才算满足"

                      # 健康检查
                      healthCheck:
                        type: object
                        properties:
                          prometheus:
                            type: object
                            properties:
                              query:
                                type: string
                                description: "PromQL 查询，返回 1 表示健康"
                                example: "up{job=~\"{{ .Release.Name }}\"} == 1"
                              interval:
                                type: string
                                default: "30s"
                              timeout:
                                type: string
                                default: "10s"
                          http:
                            type: object
                            properties:
                              url:
                                type: string
                                description: "健康检查 URL，支持 Go template"
                                example: "http://{{ .Release.Name }}-svc:8080/health"
                              interval:
                                type: string
                                default: "30s"
                              timeout:
                                type: string
                                default: "10s"
                              expectedCodes:
                                type: array
                                items:
                                  type: integer
                                default: [200]
                          pod:
                            type: object
                            properties:
                              minReadySeconds:
                                type: integer
                                default: 0
                              requiredReplicasRatio:
                                type: number
                                default: 1.0
                                description: "就绪副本比例，如 0.5 表示 50% 就绪即视为健康"

                      # 部署策略
                      deploymentStrategy:
                        type: object
                        properties:
                          updateType:
                            type: string
                            enum:
                              - RollingUpdate
                              - Recreate
                            default: RollingUpdate
                          timeout:
                            type: string
                            default: "600s"
                            description: "Helm install/upgrade 超时时间"
                          rollback:
                            type: object
                            properties:
                              enabled:
                                type: boolean
                                default: true
                              maxRetries:
                                type: integer
                                default: 3
                                minimum: 1
                              waitForReady:
                                type: boolean
                                default: true
                          retry:
                            type: object
                            properties:
                              maxRetries:
                                type: integer
                                default: 3
                              backoffStrategy:
                                type: string
                                enum:
                                  - Linear
                                  - Exponential
                                default: Exponential
                          revisionHistoryLimit:
                            type: integer
                            default: 10
                            minimum: 1
                          atomic:
                            type: boolean
                            default: false
                            description: "// TODO: design decision needed — 是否支持 Atomic（--atomic 等价于 --wait + rollback on failure）"

                      # 多集群分发
                      clusterAffinity:
                        type: object
                        properties:
                          clusterLabelSelector:
                            type: object
                            description: "Label selector 匹配 OCM ManagedCluster"
                            x-kubernetes-preserve-unknown-fields: true
                          placement:
                            type: string
                            enum:
                              - ControlPlane    # 只在主集群
                              - Workload        # 下发到工作集群
                              - Both            # 主集群和匹配的工作集群都安装
                            default: ControlPlane

                      # 资源需求（工堪用）
                      resourceRequirements:
                        type: object
                        properties:
                          cpu:
                            type: string
                            pattern: '^[0-9]+m?$'
                            description: "CPU 需求，如 2000m"
                          memory:
                            type: string
                            pattern: '^[0-9]+(Mi|Gi)$'
                            description: "内存需求，如 4Gi"
                          storage:
                            type: string
                            pattern: '^[0-9]+(Mi|Gi|Ti)$'
                            description: "存储需求，如 20Gi"
                          minNodeCount:
                            type: integer
                            minimum: 1

                # 产品级参数覆盖
                parameters:
                  type: array
                  items:
                    type: object
                    properties:
                      name:
                        type: string
                      displayName:
                        type: string
                      defaultValue: {}
                      description:
                        type: string
                      path:
                        type: string
                        description: "在 values 中的点号路径"
                      targetComponents:
                        type: array
                        items:
                          type: string
                        description: "此参数影响哪些组件；空表示所有组件"
                      required:
                        type: boolean
                        default: false

                # 镜像清单
                imageList:
                  type: array
                  items:
                    type: object
                    properties:
                      name:
                        type: string
                        description: "镜像逻辑名"
                      image:
                        type: string
                        description: "完整镜像路径"
                      tag:
                        type: string
                      digest:
                        type: string
                        description: "SHA256 摘要（断网环境校验）"
                      component:
                        type: string
                        description: "所属组件名称"


            status:
              type: object
              properties:
                phase:
                  type: string
                  enum:
                    - Pending
                    - Deploying
                    - Ready
                    - Degraded
                    - Failed
                    - Unknown
                    - Deleting
                  default: Pending

                componentCount:
                  type: integer
                  minimum: 0
                readyCount:
                  type: integer
                  minimum: 0

                # 每个组件的状态摘要
                componentStatuses:
                  type: array
                  items:
                    type: object
                    properties:
                      name:
                        type: string
                      phase:
                        type: string
                      chartVersion:
                        type: string
                      deployedVersion:
                        type: string
                      errorMessage:
                        type: string
                      lastTransitionTime:
                        type: string
                        format: date-time

                # 当前生效的版本
                observedVersion:
                  type: string

                # 所有组件的调和顺序（拓扑排序结果）
                reconciliationOrder:
                  type: array
                  items:
                    type: string
                  description: "按依赖排序后的组件名称列表"

                conditions:
                  type: array
                  items:
                    type: object
                    required:
                      - type
                      - status
                      - lastTransitionTime
                    properties:
                      type:
                        type: string
                        enum:
                          - Available
                          - Progressing
                          - Degraded
                          - DependenciesResolved
                          - Deploying
                      status:
                        type: string
                        enum:
                          - "True"
                          - "False"
                          - "Unknown"
                      reason:
                        type: string
                      message:
                        type: string
                      lastTransitionTime:
                        type: string
                        format: date-time
                      observedGeneration:
                        type: integer

                observedGeneration:
                  type: integer
                  format: int64

                history:
                  type: array
                  items:
                    type: object
                    properties:
                      revision:
                        type: integer
                      version:
                        type: string
                      operation:
                        type: string
                        enum:
                          - Installed
                          - Upgraded
                          - RolledBack
                          - Failed
                      componentChanges:
                        type: array
                        items:
                          type: object
                          properties:
                            component:
                              type: string
                            oldVersion:
                              type: string
                            newVersion:
                              type: string
                      parameterSnapshot:
                        type: object
                        x-kubernetes-preserve-unknown-fields: true
                      operator:
                        type: string
                      timestamp:
                        type: string
                        format: date-time
```

---

## 5. CloudComponent CRD 完整定义

CloudComponent 对应一个 Helm Chart 的部署实例，是 Operator 实际执行 Helm 操作的对象。

```yaml
apiVersion: apiextensions.k8s.io/v1
kind: CustomResourceDefinition
metadata:
  name: cloudcomponents.delivery.hfwas.io
spec:
  group: delivery.hfwas.io
  names:
    kind: CloudComponent
    listKind: CloudComponentList
    plural: cloudcomponents
    singular: cloudcomponent
    shortNames:
      - cc
      - ccmp
  scope: Namespaced
  versions:
    - name: v1
      served: true
      storage: true
      subresources:
        status: {}
      additionalPrinterColumns:
        - name: Display Name
          type: string
          jsonPath: .spec.displayName
        - name: Component Type
          type: string
          jsonPath: .spec.componentType
        - name: Status
          type: string
          jsonPath: .status.phase
        - name: Chart Version
          type: string
          jsonPath: .spec.chartVersion
        - name: Helm Release
          type: string
          jsonPath: .status.helmReleaseName
        - name: Age
          type: date
          jsonPath: .metadata.creationTimestamp
      schema:
        openAPIV3Schema:
          type: object
          required:
            - spec
          properties:
            spec:
              type: object
              required:
                - chartName
                - chartVersion
                - componentType
              properties:
                # 显示名称
                displayName:
                  type: string
                  maxLength: 128

                # 组件类型
                componentType:
                  type: string
                  enum:
                    - Service
                    - Cluster
                    - Project

                # 所属 CloudService（可选，独立部署时可无属主）
                serviceRef:
                  type: string
                  description: "所属 CloudService 名称"

                # Helm Chart 定义
                chartName:
                  type: string
                  description: "Chart 名称（如 redis）"
                chartVersion:
                  type: string
                  pattern: '^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(-[a-zA-Z0-9]+)?$'
                chartRepo:
                  type: string
                  format: uri
                  description: "Chart 仓库 URL"
                chartRepoName:
                  type: string
                  description: "仓库别名（对应 helm repo 名称）"

                # Helm release 名称（默认自动生成：{serviceRef}-{componentName}）
                releaseName:
                  type: string
                  pattern: '^[a-z]([a-z0-9-]*[a-z0-9])?$'
                  maxLength: 53

                # 命名空间覆盖（默认等于 CR 命名空间）
                targetNamespace:
                  type: string
                  description: "Helm release 部署的目标命名空间"
                  pattern: '^[a-z]([a-z0-9-]*[a-z0-9])?$'

                # 完整 Values（最终合并后的值）
                values:
                  type: object
                  x-kubernetes-preserve-unknown-fields: true
                  description: "合并后的完整 values（Operator 负责 merge，用户通常不直接写）"

                # Values 覆盖（用户/上层传入的覆盖）
                valuesOverlay:
                  type: object
                  x-kubernetes-preserve-unknown-fields: true
                  description: "上层传入的参数覆盖，Operator 负责与 defaultValues merge"

                # 部署策略
                deploymentStrategy:
                  type: object
                  properties:
                    updateType:
                      type: string
                      enum:
                        - RollingUpdate
                        - Recreate
                      default: RollingUpdate
                    timeout:
                      type: string
                      default: "600s"
                    rollback:
                      type: object
                      properties:
                        enabled:
                          type: boolean
                          default: true
                        maxRetries:
                          type: integer
                          default: 3
                    retry:
                      type: object
                      properties:
                        maxRetries:
                          type: integer
                          default: 3
                        backoffStrategy:
                          type: string
                          enum:
                            - Linear
                            - Exponential
                          default: Exponential
                    revisionHistoryLimit:
                      type: integer
                      default: 10
                    atomic:
                      type: boolean
                      default: false
                      description: "// TODO: design decision needed — 是否支持 Atomic"

                # 依赖
                dependencies:
                  type: array
                  items:
                    type: object
                    properties:
                      component:
                        type: string
                        description: "被依赖的 CloudComponent 名称"
                      serviceRef:
                        type: string
                        description: "跨 CloudService 引用时指定目标服务"
                      type:
                        type: string
                        enum:
                          - Hard
                          - Soft
                          - Optional
                        default: Hard
                      condition:
                        type: string
                        enum:
                          - Ready
                          - Deployed
                        default: Ready

                # 本组件所属的发布号。由上级 CloudService/App 下发，用于 status.observedReleaseID 对比。
                releaseID:
                  type: string
                  description: "所属发布号"

                # 引用集群里已存在的对象（不归本组件管理，但依赖其存在）
                refResources:
                  type: array
                  items:
                    type: object
                    required:
                      - kind
                      - name
                    properties:
                      group:
                        type: string
                      version:
                        type: string
                      kind:
                        type: string
                      name:
                        type: string
                      namespace:
                        type: string
                        description: "集群级对象留空"
                      clusterScoped:
                        type: boolean
                        default: false

                # 持久化配置。retain 通过给 PVC 打 helm.sh/resource-policy: keep 实现，
                # 卸载时保留数据；reuseFromRelease 用于重装时复用上一次发布留下的 PVC。
                persistentVolumeConfigs:
                  type: array
                  items:
                    type: object
                    properties:
                      volumeName:
                        type: string
                      storageClass:
                        type: string
                      size:
                        type: string
                        description: "如 10Gi"
                      accessModes:
                        type: array
                        items:
                          type: string
                          enum:
                            - ReadWriteOnce
                            - ReadOnlyMany
                            - ReadWriteMany
                      retain:
                        type: boolean
                        default: false
                        description: "卸载时保留 PVC（helm.sh/resource-policy: keep）"
                      useEmptyDir:
                        type: boolean
                        default: false
                        description: "不使用 PVC，改用 emptyDir"
                      reuseFromRelease:
                        type: string
                        description: "从哪一次发布复用已有 PVC（按 release-id 标签匹配）"

                # 健康检查
                healthCheck:
                  type: object
                  properties:
                    prometheus:
                      type: object
                      properties:
                        query:
                          type: string
                        interval:
                          type: string
                          default: "30s"
                        timeout:
                          type: string
                          default: "10s"
                    http:
                      type: object
                      properties:
                        url:
                          type: string
                        interval:
                          type: string
                          default: "30s"
                        timeout:
                          type: string
                          default: "10s"
                        expectedCodes:
                          type: array
                          items:
                            type: integer
                          default: [200]
                    pod:
                      type: object
                      properties:
                        minReadySeconds:
                          type: integer
                          default: 0
                        requiredReplicasRatio:
                          type: number
                          default: 1.0

                # 多集群分发
                clusterAffinity:
                  type: object
                  properties:
                    clusterLabelSelector:
                      type: object
                      x-kubernetes-preserve-unknown-fields: true
                    placement:
                      type: string
                      enum:
                        - ControlPlane
                        - Workload
                        - Both
                      default: ControlPlane

                # // TODO: design decision needed — 是否支持 pause（暂停调和，保留当前状态）
                # paused:
                #   type: boolean
                #   default: false

            status:
              type: object
              properties:
                phase:
                  type: string
                  enum:
                    - Pending
                    - Deploying
                    - Ready
                    - Degraded
                    - Failed
                    - Unknown
                    - Deleting
                    - Suspended
                  default: Pending

                # Helm 相关信息
                helmReleaseName:
                  type: string
                  description: "实际的 Helm release 名称"
                helmStatus:
                  type: string
                  description: "Helm release 状态（deployed, failed, pending-install 等）"
                helmRevision:
                  type: integer
                  description: "Helm release revision 号"

                # 部署信息
                chartVersion:
                  type: string
                  description: "当前已部署的 chart 版本"
                observedChartVersion:
                  type: string
                  description: "可观测到的实际 chart 版本"
                observedReleaseID:
                  type: string
                  description: "实际观察到的发布号（来自 spec.releaseID 或 release-id 标签）"

                # Workload 状态聚合
                workloadStatus:
                  type: object
                  properties:
                    totalWorkloads:
                      type: integer
                    readyWorkloads:
                      type: integer
                    degradedWorkloads:
                      type: integer
                    workloads:
                      type: array
                      items:
                        type: object
                        properties:
                          kind:
                            type: string
                          name:
                            type: string
                          namespace:
                            type: string
                          apiVersion:
                            type: string
                          ready:
                            type: boolean
                          status:
                            type: string
                            description: "Pod 就绪摘要"

                # 每个 Pod 的明细（由 aggregator 填充，用于白屏排障）
                podsDetail:
                  type: array
                  items:
                    type: object
                    properties:
                      name:
                        type: string
                      namespace:
                        type: string
                      ip:
                        type: string
                      hostIP:
                        type: string
                      workloadKind:
                        type: string
                      workloadName:
                        type: string
                      ready:
                        type: boolean
                      phase:
                        type: string
                        description: "Pod phase：Pending / Running / Succeeded / Failed"
                      state:
                        type: string
                        description: "容器 state 摘要，如 Running / CrashLoopBackOff / Completed"
                      restarts:
                        type: integer
                      updatedRevision:
                        type: boolean
                        description: "是否已切到当前修订：StatefulSet 比对 controller-revision-hash，Deployment 比对 pod-template-hash"
                      message:
                        type: string

                # 期望值与实际值的差异，用于回答「为什么没起来」「为什么没切到新版本」
                workloadDiffs:
                  type: array
                  items:
                    type: object
                    properties:
                      kind:
                        type: string
                      name:
                        type: string
                      namespace:
                        type: string
                      path:
                        type: string
                        description: "差异字段路径"
                      expected:
                        type: string
                      current:
                        type: string
                      message:
                        type: string

                # 说明：本方案不存整份渲染后的 manifest（原 v0.1 里拟加的 lastAppliedManifest 字段取消）。
                # 排障信息由 workloads[]（对象清单 + ready）、podsDetail[]、workloadDiffs[] 承担，
                # 避免 status 随 chart 复杂度膨胀、撑大 etcd；需要完整 manifest 时从 Helm release secret 还原。

                # 操作计数
                installCount:
                  type: integer
                  minimum: 0
                upgradeCount:
                  type: integer
                  minimum: 0
                rollbackCount:
                  type: integer
                  minimum: 0

                # 失败信息
                failureMessage:
                  type: string
                failureReason:
                  type: string
                retryCount:
                  type: integer
                  minimum: 0
                lastRetryTime:
                  type: string
                  format: date-time

                conditions:
                  type: array
                  items:
                    type: object
                    required:
                      - type
                      - status
                      - lastTransitionTime
                    properties:
                      type:
                        type: string
                        enum:
                          - Available
                          - Progressing
                          - Degraded
                          - HelmDeployed
                          - Healthy
                          - ResourceReady
                      status:
                        type: string
                        enum:
                          - "True"
                          - "False"
                          - "Unknown"
                      reason:
                        type: string
                      message:
                        type: string
                      lastTransitionTime:
                        type: string
                        format: date-time
                      observedGeneration:
                        type: integer

                observedGeneration:
                  type: integer
                  format: int64

                # ControllerRevision 引用
                lastAppliedRevision:
                  type: string
                  description: "最后应用的 ControllerRevision 名称"
                currentRevision:
                  type: string
                  description: "当前 ControllerRevision 名称"

                # 健康检查结果
                healthCheckResult:
                  type: object
                  properties:
                    healthy:
                      type: boolean
                    lastCheckTime:
                      type: string
                      format: date-time
                    message:
                      type: string

                history:
                  type: array
                  items:
                    type: object
                    properties:
                      revision:
                        type: integer
                      operation:
                        type: string
                        enum:
                          - Install
                          - Upgrade
                          - Rollback
                          - Failed
                      chartVersion:
                        type: string
                      valuesHash:
                        type: string
                        description: "values 的 SHA256 摘要（用于判断是否需要 upgrade）"
                      message:
                        type: string
                      operator:
                        type: string
                      startTime:
                        type: string
                        format: date-time
                      completionTime:
                        type: string
                        format: date-time
```

---

## 6. ProductTask CRD 完整定义

**集群级（Cluster scope）。** 表示某一次发布（`releaseID`）上的一个运维步骤，用来表达**顺序**——例如「init job 先跑完，网关再起」——而不是把顺序写进各个 workload。

设计来源：`docs/research/aap-product-operator 四类 CRD.md` 的 `ProductTask`。吸收的要点是它**可以作用于任意资源、可以跨 namespace，且顺序是一份与被排序对象解耦的独立声明**；我们原有的依赖 DAG 只能表达「组件 ↔ 组件」的依赖。

### 6.1 与依赖 DAG 的分工

| | 依赖 DAG（`CloudComponent.spec.dependencies`） | ProductTask |
|---|---|---|
| 粒度 | 组件 → 组件 | 任意步骤，可作用于任意 K8s 对象 |
| 作用域 | 同一 namespace | 集群级，可跨 namespace |
| 顺序表达 | 硬/软/可选依赖 + Ready/Deployed 条件 | `taskOrder.before[]` / `after[]` |
| 是否产生动作 | 否（只等待） | 可等待，也可只作顺序标记 |
| 典型用途 | redis 就绪后才装 backend | init job 先完成，网关后起 |

两者共存：DAG 管组件间依赖，ProductTask 管一次性步骤的先后顺序。

### 6.2 YAML 定义

```yaml
apiVersion: apiextensions.k8s.io/v1
kind: CustomResourceDefinition
metadata:
  name: producttasks.delivery.hfwas.io
spec:
  group: delivery.hfwas.io
  names:
    kind: ProductTask
    listKind: ProductTaskList
    plural: producttasks
    singular: producttask
    shortNames:
      - pt
      - ptask
  scope: Cluster
  versions:
    - name: v1
      served: true
      storage: true
      subresources:
        status: {}
      additionalPrinterColumns:
        - name: Release
          type: string
          jsonPath: .spec.releaseID
        - name: OpsType
          type: string
          jsonPath: .spec.opsType
        - name: Target
          type: string
          jsonPath: .spec.resource.name
        - name: Phase
          type: string
          jsonPath: .status.phase
        - name: Age
          type: date
          jsonPath: .metadata.creationTimestamp
      schema:
        openAPIV3Schema:
          type: object
          required:
            - spec
          properties:
            spec:
              type: object
              required:
                - releaseID
              properties:
                releaseID:
                  type: string
                  description: "属于哪一次发布，与 App.spec.releaseID 取同一个值"
                opsType:
                  type: string
                  description: "这一步要做的操作。自由字符串，不加 enum（理由见「开放问题与建议」）"
                resource:
                  type: object
                  description: "作用对象"
                  properties:
                    group:
                      type: string
                    version:
                      type: string
                    kind:
                      type: string
                    name:
                      type: string
                    namespace:
                      type: string
                refComponent:
                  type: object
                  description: "关联的 CloudComponent；同 namespace 只给 name，跨 namespace 显式给 namespace"
                  properties:
                    serviceRef:
                      type: string
                    component:
                      type: string
                    namespace:
                      type: string
                taskOrder:
                  type: object
                  description: "按 task 名称声明本步骤排在哪些步骤之前 / 之后"
                  properties:
                    before:
                      type: array
                      items:
                        type: string
                    after:
                      type: array
                      items:
                        type: string
            status:
              type: object
              properties:
                phase:
                  type: string
                  enum:
                    - Pending
                    - Running
                    - Succeeded
                    - Failed
                messages:
                  type: array
                  items:
                    type: object
                    properties:
                      message:
                        type: string
                      lastTransitionTime:
                        type: string
                        format: date-time
                conditions:
                  type: array
                  items:
                    type: object
                    properties:
                      type:
                        type: string
                      status:
                        type: string
                      reason:
                        type: string
                      message:
                        type: string
                      lastTransitionTime:
                        type: string
                        format: date-time
                      observedGeneration:
                        type: integer
                        format: int64
                observedGeneration:
                  type: integer
                  format: int64
```

### 6.3 spec 字段

| 字段 | 含义 |
|------|------|
| `releaseID` | 属于哪一次发布。与 `App.spec.releaseID` 同值 |
| `opsType` | 这一步要做的操作。**自由字符串，不加 enum**——理由见「开放问题与建议」 |
| `resource.group/version/kind/name/namespace` | 作用对象。`kind: Job` 时控制器读该 Job 的完成状态 |
| `refComponent.serviceRef/component/namespace` | 关联的 CloudComponent，控制器据此对组件做顺序放行 |
| `taskOrder.before[]` / `after[]` | 按 task 名称声明先后 |

### 6.4 status 字段

| 字段 | 含义 |
|------|------|
| `phase` | `Pending` / `Running` / `Succeeded` / `Failed` |
| `messages[]` | `message` + `lastTransitionTime`，记录阶段迁移原因 |
| `conditions[]` | 与三层 CRD 共用同一套 condition 规范 |
| `observedGeneration` | 最后处理的 spec 版本 |

### 6.5 顺序如何生效

1. ProductTask 控制器按 `releaseID` 取同一批任务，用 `before` / `after` 解出拓扑层次（复用与依赖 DAG 相同的 Kahn 算法，见「组件依赖排序」）。
2. 一个任务只有在其 `after[]` 全部 `Succeeded` 后才进入 `Running`。
3. 进入 `Running` 后：声明了 `resource` 的，读该对象的完成状态（如 Job 的 `status.succeeded`）；未声明 `resource` 的视为纯顺序标记，直接 `Succeeded`。
4. CloudComponent 调和前，若存在以它为 `refComponent` 且尚未 `Succeeded` 的任务，则 requeue 等待——这就是「init job 先跑完，网关再起」的落地方式。

---

## 7. 参数 Schema 设计

### 7.1 三层作用域

```
参数作用域分层
┌─────────────────────────────────────────────────┐
│  1. 全局参数 (Global)                            │
│     ┌ imageRegistry: "registry.example.com"     │
│     └ defaultStorageClass: "alicloud-nas"       │
│                                                  │
│  App CRD spec.globalParameters                   │
├─────────────────────────────────────────────────┤
│  2. 产品级覆盖 (Service)                         │
│     ┌ resources.limits.memory: "4Gi"             │
│     └ persistence.size: "50Gi"                  │
│                                                  │
│  CloudService CRD spec.parameters                │
├─────────────────────────────────────────────────┤
│  3. 组件默认值 (Component)                       │
│     ┌ image.tag: "7.0.0"                        │
│     └ replicaCount: 3                           │
│                                                  │
│  CloudService CRD spec.components[].defaultValues │
│  + CloudComponent CRD spec.valuesOverlay          │
└─────────────────────────────────────────────────┘
```

### 7.2 参数定义结构

每个参数在 schema 中至少包含：

```yaml
parameters:
  - name: "storageSize"              # 内部键名
    displayName: "存储大小"            # UI 显示
    description: "PVC 创建大小"        # 帮助文本
    path: "persistence.size"          # 在 values 中的点号路径
    type: "string"                    # 参数类型
    defaultValue: "20Gi"              # 默认值
    required: true                    # 是否必填
    validation:
      pattern: '^[0-9]+(Mi|Gi|Ti)$'  # 正则校验
      minimum: 10                     # 数字最小值
      maximum: 1000                   # 数字最大值
    scope: "component"                # 参数作用域
    targetComponents: ["redis"]       # 影响哪些组件
    # // TODO: design decision needed — 是否支持分组（UI 渲染为折叠面板）？
    # group: "storage"
```

---

## 8. 参数 Merge 策略

### 8.1 合并算法

采用 **JSON Merge Patch (RFC 7386)** + 针对特定 path 的 **Simple Override** 组合策略。

理由：
- Strategic Merge Patch 需要 Go struct 中定义 `patchStrategy`，对 Helm values 这种自由格式不友好
- Simple override 对点号路径最直观
- JSON Merge Patch 天然支持部分更新和嵌套合并

### 8.2 合并顺序

```
Step 0: 取 Chart 默认 Values（helm show values）
    │
    ▼
Step 1: 覆盖 组件默认值 (CloudService spec.components[].defaultValues)
    ↓  JSON Merge Patch — d1 = merge(chartValues, componentDefaults)
    │
    ▼
Step 2: 覆盖 产品级参数 (CloudService spec.parameters, 按 targetComponents 匹配)
    ↓  对 d1 中匹配的 path 执行 Simple Override
    │
    ▼
Step 3: 覆盖 服务级参数 (App spec.services[].parameters)
    ↓  对 d2 中匹配的 path 执行 Simple Override
    │
    ▼
Step 4: 覆盖 组件级覆盖 (CloudComponent spec.valuesOverlay)
    ↓  JSON Merge Patch — d3 = merge(d2, valuesOverlay)
    │
    ▼
Step 5: 覆盖 全局参数 (App spec.globalParameters)
    ↓  Simple Override 到特定 path（如 image.registry）
    │
    ▼
Step 6: Webhook 改写
    ↓  Mutating Webhook 改写最终 manifest（镜像仓库、SC、网络注解）
    │
    ▼
最终 values → helm install/upgrade
```

### 8.3 伪代码实现

```python
def merge_values(chart_defaults, component_defaults, product_params,
                 service_params, values_overlay, global_params):

    # Step 1: 组件默认值 — JSON Merge Patch
    values = deep_merge(chart_defaults, component_defaults)

    # Step 2: 产品级覆盖 — Simple Override
    for param in product_params:
        if param.target_components is empty or component_name in param.target_components:
            set_path(values, param.path, param.value)

    # Step 3: 服务级覆盖 — Simple Override
    for key, value in service_params.items():
        set_path(values, key, value)

    # Step 4: 组件级覆盖 — JSON Merge Patch
    values = deep_merge(values, values_overlay)

    # Step 5: 全局参数 — Simple Override
    if global_params.imageRegistry:
        set_path(values, 'image.registry', global_params.imageRegistry)
    if global_params.defaultStorageClass:
        set_path(values, 'global.storageClass', global_params.defaultStorageClass)

    return values

def set_path(obj, path, value):
    """按点号路径设置值，如 'resources.limits.memory' → obj['resources']['limits']['memory'] = value"""
    keys = path.split('.')
    for key in keys[:-1]:
        if key not in obj or not isinstance(obj[key], dict):
            obj[key] = {}
        obj = obj[key]
    obj[keys[-1]] = value

def deep_merge(base, overlay):
    """JSON Merge Patch 风格的深度合并"""
    result = base.copy()
    for key, value in overlay.items():
        if key in result and isinstance(result[key], dict) and isinstance(value, dict):
            result[key] = deep_merge(result[key], value)
        else:
            result[key] = value
    return result
```

---

## 9. Operator 调和循环

### 9.1 调和循环流程图

```
┌────────────────────────────────────────────────────┐
│                    触发条件                          │
│  ┌────────────────┐  ┌───────────────────┐         │
│  │ App CR 变更     │  │ CloudService 变更  │         │
│  └───────┬────────┘  └────────┬──────────┘         │
│  ┌───────▼────────┐  ┌───────▼──────────┐         │
│  │ CloudComponent  │  │ 定时 Reconcile    │         │
│  │ 变更            │  │ (默认 10min)      │         │
│  └───────┬────────┘  └───────────────────┘         │
└──────────┼─────────────────────────────────────────┘
           │
           ▼
┌────────────────────────────────────────────────────┐
│                调和循环 (Reconcile)                  │
│                                                     │
│  1. 读取当前 CloudComponent CR                       │
│  3. 解析依赖 → 拓扑排序                              │
│  4. 检查前置依赖状态                                  │
│     ├─ 未就绪 → 等待，更新 condition                 │
│     └─ 已就绪 → 继续                                 │
│  5. 判断 Helm Action：install / upgrade / skip       │
│  6. 执行 Helm 操作                                    │
│     ├─ helm template → webhook 改写 manifest         │
│     ├─ helm install/upgrade                          │
│     └─ 成功/失败处理                                  │
│  7. LabelMarker 打标                                  │
│  8. 聚合 workload 状态                                │
│  9. 更新状态 → 写回 CR                               │
└────────────────────────────────────────────────────┘
```

### 9.2 集群组件特殊处理

```
集群组件调和逻辑（CloudComponent.spec.componentType = "Cluster"）
    │
    ├─ 该组件尚未部署 → helm install
    ├─ 已部署且 spec.chartVersion > 已部署版本 → helm upgrade
    └─ 已部署且 spec.chartVersion <= 已部署版本 → 跳过（不动）
```

### 9.3 调和子循环：CloudService → CloudComponent

Operator watch CloudService CR，自动管理下属 CloudComponent 生命周期：

```
Watch CloudService
    │
    ▼
对比 CloudService.spec.components vs 现存 CloudComponent 列表
    │
    ├─ 新增组件 → 创建 CloudComponent CR
    ├─ 删除组件 → 删除对应 CloudComponent CR（或 Orphan）
    ├─ 变更组件 → 更新 CloudComponent CR
    └─ 依赖变更 → 重算拓扑排序
```

### 9.4 调和触发

| 触发源 | 类型 | 说明 |
|--------|------|------|
| App CR 变更 | Watch | 主调和入口 |
| CloudService CR 变更 | Watch | 产品定义变更 |
| CloudComponent CR 变更 | Watch | 组件级变更 |
| Helm release 状态变化 | Informer | 监听 Secret (sh.helm.release.v1) |
| 定时 Reconcile | Timer | 间隔 10min，兜底恢复 |
| 外部依赖状态变化 | Watch/Informer | 监听依赖 CC 的 status |

---

## 10. LabelMarker 打标规范

### 10.1 打标策略

Operator 在 `helm template` 渲染出 manifest 之后、执行 `helm install/upgrade` 之前，对 manifest 中的 workload 资源添加以下标签。

### 10.2 标签定义

| 标签 Key | 值 | 用途 |
|----------|-----|------|
| `delivery.hfwas.io/app` | App 名称 | 关联到 App |
| `delivery.hfwas.io/service` | CloudService 名称 | 关联到 CloudService |
| `delivery.hfwas.io/component` | CloudComponent 名称 | 关联到 CloudComponent |
| `delivery.hfwas.io/component-type` | Service/Cluster/Project | 组件类型 |
| `delivery.hfwas.io/managed-by` | cn-app-operator | 标明管控主体 |
| `delivery.hfwas.io/revision` | ControllerRevision 编号 | 追踪版本 |
| `delivery.hfwas.io/release-id` | App.spec.releaseID | 把一次发布串起来；PV 复用与 ProductTask 按此匹配 |
| `delivery.hfwas.io/retain` | true / false | 卸载时保留的 PVC 标记（同时给 PVC 打 `helm.sh/resource-policy: keep`） |

### 10.3 打标的资源类型

Operator 只对以下「有意义的工作负载」打标：

- `Deployment`
- `StatefulSet`
- `DaemonSet`
- `Job` / `CronJob`
- `Service`（为拓扑聚合）
- `Ingress`（为拓扑聚合）

### 10.4 实现方式

webhook 打标是第一道防线，Operator 层面的打标是兜底策略。

---

## 11. 状态聚合策略

### 11.1 聚合层次

```
CloudComponent 级别：
    helm template 解析出 workload 列表
        → 每个 workload 检查 Ready/Available 状态
        → status.workloadStatus 写入摘要
        → status.podsDetail / status.workloadDiffs 采集 Pod 明细与期望/实际差异
        → status.phase 判断：
            - 所有 workload Ready → Ready
            - 部分 Ready → Degraded
            - 所有未 Ready → Deploying/Pending
            - Helm 失败 → Failed

CloudService 级别：
    对所有下属 CloudComponent 的状态聚合
        → 所有 CC Ready → Ready
        → 任一 CC Failed → Failed（其他可继续）
        → 任一 CC Degraded → Degraded
        → 其他 → Deploying/Pending

App 级别：
    对所有下属 CloudService 的状态聚合（同上）
```

### 11.2 聚合算法

```python
def aggregate_phase(children_statuses):
    """
    从子资源状态聚合父资源阶段。
    优先级：Failed > Degraded > Deploying > Ready > Pending
    """
    has_failed = any(s == 'Failed' for s in children_statuses)
    has_degraded = any(s == 'Degraded' for s in children_statuses)
    has_deploying = any(s in ('Deploying', 'Pending') for s in children_statuses)

    if has_failed:
        return 'Failed'
    if has_degraded:
        return 'Degraded'
    if has_deploying:
        return 'Deploying'
    if all(s == 'Ready' for s in children_statuses):
        return 'Ready'
    return 'Unknown'
```

### 11.3 Conditions 标准化

所有四个 CRD 共用同一套 conditions 规范：

| Type | True 含义 | False 含义 | Unknown |
|------|-----------|------------|---------|
| Available | 已就绪且可提供服务 | 不可用 | 状态未知 |
| Progressing | 正在部署/升级/回滚 | 不处于变更中 | 状态未知 |
| Degraded | 功能降级 | 运行正常 | 状态未知 |

### 11.4 Pod 级明细与差异采集（v0.3）

`status.workloadStatus.workloads[]` 只回答「哪个 workload 没 ready」，不回答「为什么」。因此每轮聚合同时采集两项：

| 输出 | 来源 | 回答的问题 |
|------|------|-----------|
| `podsDetail[]` | 按 `delivery.hfwas.io/component` 标签列出 Pod | 哪个 Pod 没起来、在哪个节点、重启几次、容器处于什么 state |
| `workloadDiffs[]` | workload spec 与 Pod / status 实际值对比 | 期望副本 vs 就绪副本、期望镜像 vs 实际运行镜像 |

`podsDetail[].updatedRevision` 用于判断「是否已切到当前修订」：

- **StatefulSet**：比对 Pod 的 `controller-revision-hash` 标签与 `sts.status.currentRevision`。
- **Deployment**：比对 Pod 的 `pod-template-hash` 标签与 Deployment 当前（最新）ReplicaSet 的 hash。

升级后最常见的故障是「滚动卡住 / 副本没切到新版本」，这一项是它的直接判据，白屏不必再人肉 `kubectl describe`。

---

## 12. 失败重试与回滚策略

### 12.1 Helm 超时和 Pending 状态处理

| Helm 状态 | Operator 行为 |
|-----------|--------------|
| deployed | 正常，聚合 workload 状态 |
| failed | 检查 retry 策略 → 重试或标记 Failed |
| pending-install | 等待 timeout → 超时则检查 atomic 标志 → 回滚或标记 Failed |
| pending-upgrade | 等待 timeout → 超时则回滚到上一 revision |
| pending-rollback | 等待完成 → 超时则标记 Failed（Atomic 模式） |
| superseded | 旧 revision，忽略 |
| uninstalled | 异常状态，重建 release |
| unknown | 检查 release 详情 → 未知则标记 Failed |

### 12.2 重试策略

```yaml
# CloudComponent spec.deploymentStrategy.retry
retry:
  maxRetries: 3
  backoffStrategy: Exponential  # Linear / Exponential / Immediate
```

### 12.3 回滚策略

失败后 Operator 的行为取决于 `deploymentStrategy.rollback.failureAction`：

```
Helm upgrade 失败
    │
    ▼
检查 rollback.enabled & spec.chartVersion == observedChartVersion
    │
    ├─ True → helm rollback 到上一 revision
    │     ├─ waitForReady 等待就绪
    │     └─ rollback 失败 → 进入 retryPolicy
    │
    └─ False → 标记 Failed → 等待人工干预
```

### 12.4 熔断保护

- 连续重试达到 `maxRetries` 后暂停调和，等待人工介入
- 人工可通过更新 CR 的 annotation `delivery.hfwas.io/reset-retry` 重置重试计数器
- Webhook 拦截：单个 CloudComponent 的失败次数超过阈值（如 5 次/小时）则拒绝新的变更请求

---


## 13. App 聚合逻辑与 kubectl get app 展示

### 13.1 App 聚合流程图

```
kubectl get app -A (用户入口)
    │
    ▼
App Reconcile
    │
    ├─ 1. 读取 App.spec.services 列表
    │
    ├─ 2. 对每个 service 引用：
    │      ├─ 查找同 namespace 下的 CloudService CR
    │      ├─ 查找该 CloudService 下属的 CloudComponent CR
    │      └─ 读取各自 status
    │
    ├─ 3. 聚合 Summary：
    │      ├─ totalServices = len(services)
    │      ├─ readyServices = count(phase == Ready)
    │      ├─ failedServices = count(phase == Failed)
    │      ├─ totalComponents = sum(all CC count)
    │      └─ readyComponents = sum(all CC ready count)
    │
    ├─ 4. 聚合 Phase（见 11.2 聚合算法）
    │
    └─ 5. 更新 App.status
```

### 13.2 App 如何找到下属资源

App 不创建/管理 CR。它通过标签选择器关联下属 CloudService：

```yaml
# Operator 通过以下方式建立关联
# 1. 显式关联：App.spec.services[].name → CloudService 名称
# 2. 标签关联：CloudService 自动继承标签 delivery.hfwas.io/app: <app-name>
```

### 13.3 kubectl get app -A 展示效果

```text
NAMESPACE   NAME           DISPLAY NAME   STATUS     SERVICES   READY   AGE
devops      bizstack       业务中台       Ready      5          5      3d2h
devops      monitoring     监控套件       Degraded   3          2      7d
prod        gitlab         GitLab CE      Deploying  1          0      10m
```

`STATUS` 列取自 `status.phase`，聚合算法见 11.2。

### 13.4 kubectl describe app 展示示例

```text
Name:         bizstack
Namespace:    devops
Labels:       <none>
Annotations:  <none>
API Version:  delivery.hfwas.io/v1
Kind:         App
Spec:
  Display Name:  业务中台
  Version:       2.1.0
  Global Parameters:
    Default Storage Class:  alicloud-nas
    Image Registry:         registry-vpc.cn-hangzhou.aliyuncs.com
  Services:
    Name: bizstack-core
    Parameters:
      resources.limits.memory: 8Gi
    Name: bizstack-gateway
Status:
  Phase:  Ready
  Aggregated Summary:
    Ready Components:    8
    Ready Services:      4
    Total Components:    8
    Total Services:      4
  Conditions:
    Type:    Available
    Status:  True
    Reason:  AllServicesReady
Events:
  Type    Reason    Age    Message
  ----    ------    ----   -------
  Normal  Progress  5m     Service bizstack-core: upgrading to version 2.1.0
  Normal  Ready     2m     All services are ready
```

---

## 14. Helm 交互

### 14.1 Action 判断策略

Operator 判断 Helm action 的决策树：

```
Reconcile CloudComponent
    │
    ▼
检查 Helm release 是否存在（通过 Secret: sh.helm.release.v1.{releaseName}.{namespace}）
    │
    ├─ 不存在 → Helm install
    │
    └─ 存在 →
        │
        ▼
    比较 spec.values（合并后）与已部署 release 的 values（哈希比较）
        │
        ├─ values or chartVersion 无变化 → skip（跳过）
        │
        └─ 有变化 →
            │
            ▼
        比较组件类型：
            ├─ Cluster type & 已部署版本 >= spec.chartVersion → skip
            │   （集群组件策略：只升不降、不重复装）
            │
            └─ 其他 → Helm upgrade
```

### 14.2 Helm 实现细节

推荐使用 `helm.sh/helm/v3/pkg/action` 包（Helm SDK），而不是 exec helm CLI。原因：
1. SDK 可直接操作内存中的 chart 和 values，不需经过文件系统
2. 可控制日志输出和超时
3. 可获取 release 对象进行详细状态判断

核心动作：
- `action.NewInstall()` → `Run(chart, values)`
- `action.NewUpgrade()` → `Run(releaseName, chart, values)`
- `action.NewRollback()` → `Run(releaseName, revision)`
- `action.NewGet()` → `Run(releaseName)`
- `action.NewTemplate()` → `Run(chart, values, options)`

### 14.3 Helm 超时处理

Helm SDK 使用 `context.Context` 控制超时。Operator 从 `CloudComponent.spec.deploymentStrategy.timeout` 解析超时时间（默认 600s）。

超时后：
1. 检查 atomic 标志：true 则 Helm SDK 自动执行 rollback；false 则标记 Failed
2. 不阻塞其他组件的调和

### 14.4 Pending 状态检测

Helm release 的 pending 状态通过定期检查 Secret 检测：
```
secret: sh.helm.release.v1.{name}.{namespace}
data.release: base64(gzip(protobuf)) → 读取 Info.Status
```

---

## 15. 组件依赖排序

### 15.1 拓扑排序算法

Operator 在调和 CloudService 时，对其下属所有 CloudComponent 进行拓扑排序：

```
输入：所有组件的依赖关系 DAG
    A → B (B 依赖 A)
    A → C
    B → D

拓扑排序结果（DFS + 入度法）：
    Layer 0: A       (无依赖)
    Layer 1: B, C    (依赖 A)
    Layer 2: D       (依赖 B)
```

### 15.2 并行 vs 串行策略

```
同一层（无依赖关系）→ 并行部署
    Layer 0: [A, E]          ← 可同时安装 A 和 E

不同层（有依赖关系）→ 串行部署，层间同步等待
    Layer 1: [B, C]          ← 等待 A 和 E 全部就绪
    Layer 2: [D, F]          ← 等待 B, C 全部就绪
```

### 15.3 跨 CloudService 依赖

```
CloudService A:                    CloudService B:
  - redis (Service)                  - myapp (Service)
    dependencies: []                   dependencies:
                                        - serviceRef: A, component: redis (Hard)

拓扑排序跨越两个 CloudService：
Server A/redis → (Layer 0)
Server B/myapp → (Layer 1, 依赖 A/redis)
```

### 15.4 与 ProductTask 的分工（v0.3）

依赖 DAG 与 ProductTask 是两套并行机制，各自解决不同问题，不要互相替代：

| 问题 | 交给谁 |
|------|--------|
| 「redis 就绪后才装 backend」 | 依赖 DAG（`dependencies` + `condition: Ready`） |
| 「init job 跑完才起网关」 | ProductTask（`taskOrder` + `resource` 指向那个 Job） |
| 依赖集群里**别人管的**已有对象 | `refResources`（只断言存在，不排序） |
| 顺序涉及非本系统创建的资源 | ProductTask（集群级，可指向任意对象） |

实现上两者在 CloudComponent 调和的**同一个入口**收敛：先查依赖 DAG（`checkDependencies`），再查同 `releaseID` 下以本组件为 `refComponent` 的 ProductTask，任一未满足就 requeue。排序算法复用同一套 Kahn 实现（`pkg/deps/topo.go` 与 `pkg/tasks/order.go`）。

---

## 16. Webhook 集成

### 16.1 Mutating Webhook

用途：改写 Helm 渲染后的 Kubernetes 资源 manifest，适配目标集群环境。

常见改写场景：
1. **镜像仓库替换**：在 manifest 中所有 `image:` 字段前加 `spec.imageRegistry` 前缀
2. **StorageClass 改写**：将所有 PVC 的 `storageClassName` 替换为 `spec.defaultStorageClass`
3. **网络注解改写**：根据集群环境添加特定注解（如 ALB Ingress 等）
4. **资源配额改写**：按组件级参数覆盖 `resources` 字段

### 16.2 Validating Webhook

校验点：
1. CloudService parameters 的 validation 规则
2. 组件名唯一性
3. 依赖不存在环（DAG 检测）
5. CloudComponent componentType 与部署策略的兼容性

---

## 17. 多集群策略

### 17.1 ClusterLabelSelector

```yaml
clusterAffinity:
  clusterLabelSelector:
    matchLabels:
      region: cn-hangzhou
      tier: production
  placement: Workload
```

### 17.2 与 OCM 集成

```
多集群下发的流程：
1. Operator 在主集群上创建 CloudComponent CR
2. 检查 clusterAffinity.clusterLabelSelector
3. 匹配 OCM ManagedCluster 的 label
4. 对每个匹配的集群通过 OCM ManifestWork 下发 helm release
5. 从子集群聚合状态回主集群 CloudComponent.status
```

### 17.3 多集群状态聚合

```
// TODO: design decision needed
// 在多集群场景下，单个 CloudComponent 对应多个集群上的多个 Helm release
// status.phase 需要聚合所有集群的状态
```

---

## 18. ControllerRevision 历史管理

### 18.1 用途

- 记录每次 CloudComponent 的变更历史
- 支持审计追踪
- 支持回滚到历史版本（通过引用历史 ControllerRevision）

### 18.2 实现

```yaml
# Operator 在每次成功的 Helm 操作后创建/更新 ControllerRevision
# ControllerRevision 命名约定：{component-name}-{revision-number}

apiVersion: apps/v1
kind: ControllerRevision
metadata:
  name: redis-v1
  namespace: devops
  labels:
    delivery.hfwas.io/component: redis
    delivery.hfwas.io/service: bizstack-core
    delivery.hfwas.io/app: bizstack
    delivery.hfwas.io/managed-by: cn-app-operator
  ownerReferences:
    - apiVersion: delivery.hfwas.io/v1
      kind: CloudComponent
      name: redis
data:
  values: |-
    {}
  chartVersion: "17.3.0"
  chartRef:
    repository: oci://registry.example.com/charts/redis
    version: "17.3.0"
revision: 1
```

### 18.3 历史裁剪

`CloudComponent.spec.deploymentStrategy.revisionHistoryLimit` (默认: 10)
超过限制时删除最旧的 ControllerRevision。

---

## 19. 开放问题与建议

### 19.1 设计决策待定 (TODO Items)

以下字段在设计中标记为 `// TODO: design decision needed`，需要在实现前决策：

| # | 问题 | 建议 | 理由 |
|---|------|------|------|
| 1 | App 是否锁 CloudService 版本？ | 建议支持 `serviceRef.revision` 字段，空表示不锁定 | 大产品需要版本锁定，避免下游服务的变更意外触发升级 |
| 2 | App 是否跨 namespace 引用 Service？ | 建议同一 namespace | 简化权限模型和状态聚合 |
| 3 | 参数是否支持跨参数引用（如 `${global.imageRegistry}/myapp`）？ | 建议在 webhook 层做简单替换，不引入复杂模板引擎 | 避免双重转义问题 |
| 4 | 是否存储完整 lastAppliedManifest？ | **已定论（v0.3）：不存储**。仅存 values hash + 对象清单 | 完整 manifest 非常大，会显著增加 etcd 负载；AAP 亦只存 `resourceStatuses[]` 对象清单 |
| 5 | CloudService 间是否可以相互依赖？ | 建议第一期不支持 | 跨产品的依赖复杂度高 |
| 6 | 是否支持 CloudComponent 的 pause 功能？ | 建议支持 | 允许运维人员暂停组件调和 |
| 7 | atomic flag 是否等价于 --atomic？ | 建议等价 | 与 Helm 语义对齐 |
| 8 | history 最大条目数？ | 建议 50 条（App/CloudService），20 条（CloudComponent） | 避免 history 无限增长 |
| 9 | 多集群场景下状态如何聚合？ | 建议先支持单集群 | 多集群状态聚合设计复杂度高 |
| 10 | 参数分组（UI 折叠面板）？ | 建议在 parameter schema 中增加 `group` 字段 | 提升 UI 可用性 |

### 19.2 后续迭代路线图建议

```
Phase 1: 单集群、Helm-only、手工创建 CloudComponent
    - CRD 定义注册
    - Operator 基础调和循环
    - Helm install/upgrade/rollback
    - 状态聚合（LabelMarker）
    - ControllerRevision 历史

Phase 2: CloudService 编排 + App 聚合
    - CloudService → CloudComponent 自动创建
    - 依赖拓扑排序
    - App 聚合状态
    - 参数 merge

Phase 3: Webhook + 多集群
    - Mutating Webhook
    - Validating Webhook
    - OCM 多集群下发

Phase 4: UI 集成 + 增强功能
    - 白屏参数配置
    - 部署进度可视化
    - 工堪集成
    - 版本管理
```

### 19.3 与现有仓库的集成建议

- **不需要**修改现有 Java/Tekton/delivery 代码
- `container-core` 模块可直接通过 fabric8 客户端操作本次定义的 CRD
- 前端可通过 Kubernetes API 代理直接读取 CRD 状态展示
- `deploy-app.sh` 脚本可保留为开发环境快速部署的工具，生产环境由 `cn-app-operator` 取代

### 19.4 v0.3 对照 AAP 四类 CRD 的吸收结论

来源：`docs/research/aap-product-operator 四类 CRD.md`。逐条取舍如下。

**已吸收**

| 项 | 落地位置 |
|----|----------|
| ProductTask：集群级、带 `before`/`after` 的步骤排序 | 第 6 节，新增第 4 个 CRD |
| 发布实例层：`releaseID` / `planRevision` | `App.spec` + `App.status.observedReleaseID` |
| `podsDetail[]` / `workloadDiffs[]` | `CloudComponent.status`，采集规则见 11.4 |
| `PersistentVolumeConfigs`：`retain` 与跨发布复用 | `CloudComponent.spec` |
| `refResources[]`：依赖集群里已存在的对象 | `CloudComponent.spec` |
| status 只存对象清单、不存整份 manifest | TODO #4 定论，见下 |

**明确不吸收**

| AAP 的做法 | 为什么不跟 |
|-----------|-----------|
| AppInstance 由 operator 直接创建 StatefulSet/Job/Service/PVC | 与本方案「应用定义只支持 Helm Chart」「UI 不执行 Helm」的既定约束冲突；改掉等于重造 chart 的 hook / values / 子 chart 能力 |
| meta-server + FunctionProvider 能力总线 | 那是 AAP runtime 内部的目录与能力发现；本方案的等价物是 delivery-platform 后端与自有 API，无对应关系 |
| siteCode / siteplan 站点模型 | 本方案多集群走 ClusterLabelSelector + OCM |
| `sideCars[]` | 走 Helm 时边车在 chart 内声明，不需要 CRD 字段 |

**enum 策略**

AAP 的 `phase` / `opsType` / `deployPhase` / `workload.kind` 在 CRD 里**都不加 enum**，是自由字符串。本方案有意反其道：

| | 本方案（加 enum） | AAP（不加 enum） |
|---|---|---|
| 新增状态值 | 需改 CRD，存量 CR 可能失配 | 不用改 CRD |
| 白屏取值域 | 稳定可枚举，可直接驱动 UI | 拿到任意字符串，UI 只能兜底 |
| 校验强度 | CRD 层即拦截写错的 phase | 依赖 webhook / 控制器自校验 |

**定论**：控制器自己写入的字段（`status.phase`、`componentType`）继续加 enum；**面向人的、语义开放的字段不加 enum**——`ProductTask.spec.opsType` 按此处理，保持自由字符串，配 `status.messages[]` 承载自由文本。

注意：AAP 另有 `aap-product-webhook`，所以「CRD schema 里没有 enum」**不等于**「整链路没有校验」，不能据此推断其校验更弱。

**lastAppliedManifest 定论**

沿用 TODO #4 并采用 AAP 的实证做法：**不把整份渲染后的 manifest 写进 status**。AAP 的 `HelmChartInstance.status.resourceStatuses[]` 同样只列对象清单（group / version / kind / namespace / name / phase / ready / message）而不存 manifest。本方案由 `workloadStatus.workloads[]` + `podsDetail[]` + `workloadDiffs[]` 承担排障信息；需要完整 manifest 时从 Helm release secret 还原。

---

## 参考来源

| 来源 | 说明 |
|------|------|
| `docs/research/yunyou-operator-helm-package.md` | 三层 CRD、LabelMarker、调和循环的原始研究 |
| `docs/research/operator-job-deployment-system.md` | DeploymentPlan CRD 的阶段状态机和重试策略参考 |
| CNStack 公开资料 | `kubectl get app -A` 用户入口、Helm-only + OAM 预留 |
| ADP 开发者社区 | 工堪、cluster-checker、参数 Schema 集成 |
| `deploy/charts/backend/values.yaml` | 现有 Helm Chart 的 values 分层模式参考 |