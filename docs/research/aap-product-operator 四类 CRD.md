# aap-product-operator 四类 CRD

> 日期：2026-09-22
> 版本：v0.3
> 状态：研究笔记（未拍板）
> 来源：`E:\kunlun\bizstack-deploy\adns1.22.0.aap` 内 `charts/aap-runtime-1.0.0.tgz`（父 chart 加三张子 chart 的 templates / values / crds）
> API：`antstack.alipay.com/v1alpha1`，kubebuilder `controller-gen` v0.9.2
> 相关：`yunyou-classic-deploy-flow.md`、`yunyou-2.0-aap-dissection.md`（现场资料，未随仓库提供）

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-09-22 | 初版：按 chart 内 CRD schema 整理 ProductInstance、AppInstance、ProductTask、HelmChartInstance 的字段与协作关系 |
| v0.2 | 2026-09-22 | 补 `aap-runtime` 1.0.0 的目录、values 和各子 chart 渲染出的 YAML |
| v0.3 | 2026-10-18 | 整理：修正文首元信息块格式；未随仓库提供的关联文档改为纯文本说明 |


---

## 1. 结论
这四类对象是 `aap-product-operator`（chart 名 `aap-prodoperator`）安装进集群的业务 CR。一次产品发布用 `releaseID` 串起来：

1. **ProductInstance** 写下这次要发布的产品和已经展开的应用列表。
2. 每个应用落成一份 **AppInstance**。控制器按 `workload` 创建 StatefulSet 或 Job、Service、PVC，再把副本和 Pod 状态写回。
3. 需要排队的动作写成集群级 **ProductTask**（`opsType` + `before` / `after`），用来表达「init job 先跑、网关后起」这类顺序。
4. **HelmChartInstance** 不走 AAP 的 Application yaml。它只描述一张 Helm chart 和参数，status 里记录 chart 渲染出来的 Kubernetes 对象。BIZGATEWAY、COMPONENTMARKET 走前三类。`aap-runtime` 自己不是一张 HelmChartInstance：安装它的时候，Helm 直接渲染下面三张子 chart 的 Deployment。

控制器二进制不在本机包里，phase 的枚举值 schema 里也没有写死。下面的对象分工来自 CRD 字段、互相引用，以及 operator ClusterRole。白屏写入 meta-server、再由 operator 调和的控制面分工见 `yunyou-classic-deploy-flow.md`（现场资料，未随仓库提供）。

```latex
siteplan / 产品清单
        |
        v
ProductInstance          releaseID、productCode、topology、applications[]
        |
        +---> AppInstance（每个应用一份）
        |         |
        |         +---> StatefulSet / Job、Service、PVC、Pod
        |         |
        |         +-- status.replicas / podsDetail 回写
        |
        +---> ProductTask（按 before/after 排队的运维步骤）
                    |
                    +-- 指向某个 AppInstance 或某个资源

HelmChartInstance  --->  helm install/upgrade  --->  status.resourceStatuses
```

chart 里还有一份 `FunctionProvider`（`kind: product`），Service 指向 operator 的 `20221`。这是把「产品」这类能力注册给 function-manager 的入口，不是第四类业务 CR。

---

## 2. 四类对象对照
| CRD | Kind | 范围 | 谁填写期望 | 控制器回写什么 |
| --- | --- | --- | --- | --- |
| `productinstances.antstack.alipay.com` | ProductInstance | Namespaced | 产品编码、版本、拓扑、这次发布的应用列表 | 每个应用的副本和阶段，汇总成产品 `phase` |
| `appinstances.antstack.alipay.com` | AppInstance | Namespaced | 单个应用的镜像、命令、探针、规格、卷 | Pod、副本、发布进度、工作负载差异 |
| `producttasks.antstack.alipay.com` | ProductTask | **Cluster** | 一次 release 上的一个运维步骤及先后顺序 | `phase` 和消息 |
| `helmchartinstances.antstack.alipay.com` | HelmChartInstance | Namespaced | chart 名、仓库、版本、参数 | chart 渲染出的各对象是否 ready |


operator 的 ClusterRole 对这四类及其 `/status` 拥有全部动词，另外可以读写 Pod、Service、StatefulSet、DaemonSet、Job、PVC、PV、ConfigMap、Secret、ServiceAccount、Role。负载均衡对象 `loadbalancerinstances` 也在同一份 Role 里，但 CRD 定义在 `core-function-manager`，不在这四个文件中。

---

## 3. ProductInstance
表示「这个产品的这一次发布」。`kubectl get` 的附加列是 Code、Version、Phase、Detail。

### spec
必填：`productCode`、`productVersion`。

| 字段 | 含义 |
| --- | --- |
| `productCode` / `productVersion` | 对应 `product-*.yaml` 的 `meta.code` / `meta.version`，例如 `BIZGATEWAY` / `1.92.0` |
| `productTopologyCode` | 选中的拓扑号，例如 `DEPLOY_TOPO_202108311724250946` |
| `planRevision` | 现场规划修订。siteplan 里产品实例的 `revision` |
| `releaseID` | 这一次发布的关联号，下面的 AppInstance 和 ProductTask 用同一个值 |
| `applications[]` | 已经展开的应用列表，字段和 application yaml、siteplan 对齐 |


`applications[]` 里每一项：

| 字段 | 含义 |
| --- | --- |
| `applicationCode` / `applicationVersion` | 应用编码和版本，例如 `bizgateway` / `1.92.0` |
| `applicationInstance` | 实例名，注释写的是 `utils.Format(appCode)` |
| `name` | 应用名 |
| `image` | 容器镜像 |
| `hostNetwork` | 是否使用主机网络 |
| `lifecycle` | liveness / readiness / startup / postStart / preStop，探针可以是 exec、http、tcp |
| `parameters[]` | `key`、`default`、`type`。对应 application 的参数（库地址、账号、开关） |
| `resourceSpec` | 选中的规格名（「标准生产-单机房」）以及 cpu、memory、gpu、副本数、卷 |
| `persistentVolumeConfigs[]` | 存储类、是否保留、是否 emptyDir、卷名，以及从哪一次发布复用 PV |
| `topologyConstraints` | 应用间 affinity / antiAffinity，以及 nodeAffinity |
| `opsSpec` | `maxUnavailable`、`offlinePods`、`updatedReplicas` |
| `releaseStrategy` / `releaseID` / `releaseTimestamp` | 这一应用在本次发布中的策略和时间 |
| `productCode` / `productInstance` / `siteCode` | 指回产品和站点 |


### status
| 字段 | 含义 |
| --- | --- |
| `phase` / `detail` | 产品这一次发布的总阶段和说明 |
| `conditions[]` | 标准 condition：`type`、`status`、`reason`、`message`、`lastTransitionTime` |
| `appInstanceStatus[]` | 从各 AppInstance 收上来的摘要：应用编码和版本、`name`、`deployPhase`、`replicas`、`availableReplicas`、`updatedReplicas`、`podsStatus`、`progress`、期望的 `releaseID` 和观察到的 `observedReleaseID` |


产品状态不单独维护一套副本数，而是汇总下属 AppInstance。

---

## 4. AppInstance
表示产品里的一个应用进程。`kubectl get` 的附加列是 HeadLessService、ReleaseID、Replicas、AvailableReplicas、UpdatedReplicas、PodsStatus、Progress。

### spec
| 字段 | 含义 |
| --- | --- |
| `applicationCode` / `applicationVersion` | 应用身份 |
| `applicationInstance` | 实例名 |
| `productCode` / `productInstance` | 指回 ProductInstance |
| `releaseID` / `releaseTimestamp` | 必须和这次产品发布一致 |
| `siteCode` | 站点 |
| `requireAppConfigFile` | 是否要求应用配置文件 |
| `refResources[]` | 这个应用依赖的已有对象：`group`、`version`、`resource`、`name`，以及是不是集群级 |
| `workload` | 真正要落地的工作负载，见下表 |
| `sideCars[]` | 边车。结构和 workload 同类：镜像、命令、探针、卷。`configFiles` 标了 Deprecated |
| `volumes[]` | 卷定义，含 PVC（`storageClass`、`retain`、`useEmptyDir`）和 Secret |


`workload` 的主要字段：

| 字段 | 含义 |
| --- | --- |
| `kind` | 工作负载种类。application yaml 里见过 `standard`（常驻）和 `job`（初始化任务） |
| `images` | 按架构的镜像表，对应 application 的 `images.amd64` / `arm64` |
| `command` | 容器命令。chart 模板里的 operator 进程本身是 `/bin/manager`，业务容器的命令来自这里 |
| `lifecycle` | 探针和钩子 |
| `resourceSpecs` | cpu、memory、gpu、超卖系数、`replicas`、卷容量 |
| `parameters[]` | 渲染后的键值 |
| `opsSpec` / `releaseStrategy` | 滚动和发布策略 |
| `topologyConstraints` | 副本互斥、节点亲和 |
| `securityContext` | 安全上下文，schema 里是字符串 |
| `configFiles` | 已废弃，注释要求改走 volumes |


`kind: standard` 对应长期 Pod（控制器 Role 里有 StatefulSet）。`kind: job` 对应只跑一次的初始化，例如 BIZGATEWAY 的 `bizstackinit`、`iamaccoreinit`。

### status
| 字段 | 含义 |
| --- | --- |
| `replicas` / `availableReplicas` / `updatedReplicas` | 期望副本、就绪副本、已更新副本。这三项是 status 的必填 |
| `updatedRevision` | 当前已更新的修订 |
| `deployPhase` / `progress` / `podsStatus` | 部署阶段、进度和 Pod 摘要。这三列会再汇总进 ProductInstance |
| `releaseID` | 观察到的发布号 |
| `podsDetail[]` | 每个 Pod 的名字、IP、hostIP、是否就绪、`state`、condition、是否已切到新修订 |
| `workloadDiffs[]` | `path` 上期望值 `expected` 和当前值 `current` 的差异 |
| `appVolumes` | 卷挂载路径，以及对应 PV 的容量、StorageClass、阶段 |
| `service` / `serviceAccount` / `roleBinding` / `clusterRoleBinding` | 控制器创建出来的附属对象名字 |


---

## 5. ProductTask
集群级。表示某一次 `releaseID` 上的一个运维步骤，用来规定应用之间的先后，而不是把顺序写进 Deployment。

CRD 的 `description` 写成了 FunctionProvider。那是生成器抄错的注释，`kind` 是 `ProductTask`。

`kubectl get` 的附加列是 ReleaseID、OpsType、ResourceKind、ResourceName、ResourceNamespace、Phase。

### spec
| 字段 | 含义 |
| --- | --- |
| `releaseID` | 属于哪一次产品发布 |
| `opsType` | 这一步要做的操作 |
| `resource.kind` / `name` / `namespace` / `uid` | 作用对象 |
| `refAppInstance.name` / `namespace` | 关联的 AppInstance。注释原文是「关联的 appInstance cr」 |
| `taskOrder.before[]` / `taskOrder.after[]` | 步骤名字。本步骤要排在哪些步骤之前、之后 |


### status
| 字段 | 含义 |
| --- | --- |
| `phase` | 这一步的阶段 |
| `messages[]` | `message` 加 `lastTransitionTime` |


BIZGATEWAY 的拓扑把 `bizgateway` 标成依赖 `iamaccoreinit` 和 `bizstackinit`。这种「两个 job 先完成，网关再起」适合落成带 `taskOrder` 的 ProductTask，而不是三个互相无关的 Deployment。

---

## 6. HelmChartInstance
命名空间级。给不是 AAP Application 模型的组件用。spec 只有四项：

| 字段 | 含义 |
| --- | --- |
| `chartName` | chart 名 |
| `chartRepo` | chart 仓库 |
| `chartVersion` | chart 版本 |
| `parameters` | 字符串键值，传给 Helm values |


status 必填 `phase`，另外有：

| 字段 | 含义 |
| --- | --- |
| `detail` | 说明 |
| `conditions[]` | 标准 condition |
| `resourceStatuses[]` | chart 渲染出的每个对象：`group`、`version`、`kind`、`namespace`、`name`、`phase`、`ready`、`message` |


`adns1.22.0.aap` 里的 `aap-runtime` 1.0.0 自己就依赖三张子 chart：`aap-prodoperator`、`meta-server`、`core-function-manager`。云游 2.0.3 的元数据把 `aap-runtime` 记成 `helm_charts/aap-runtime-2.0.3.tgz`。那份 tgz 不在本机目录里，只有镜像地址和 values。业务产品包 `all.aap` 的 `charts/` 是空的，所以 IAM、BIZGATEWAY 不会变成 HelmChartInstance。

---

## 7. 和清单文件怎么对上
以 BIZGATEWAY 1.92.0 为例。清单在 `manifests/aap/`，镜像在 `ymz_biz.aap` 的 OCI blob 里。

| 清单 | 进哪一类 CR |
| --- | --- |
| `product-BIZGATEWAY-1.92.0.yaml` | ProductInstance 的 `productCode` / `productVersion`，外加产品参数 `db_type` |
| `topology-BIZGATEWAY-1.92.0-DEPLOY_TOPO_....yaml` | `productTopologyCode`；三个应用以及 IAM、OP 依赖 |
| `application-BIZGATEWAY-bizgateway-1.92.0.yaml` | 一条 AppInstance，`workload.kind` 为常驻，镜像 `bizgateway`，库角色 `bizgatewaydb`，2 副本 |
| `application-BIZGATEWAY-bizstackinit-1.54.1.yaml` | 一条 AppInstance，`kind: job`，镜像 `bizstackinit`，无端口无库 |
| `application-BIZGATEWAY-iamaccoreinit-1.54.1.yaml` | 一条 AppInstance，`kind: job`，镜像 `accoreinit` |
| `siteplan.yaml` 里名为 `bizgateway` 的实例 | `planRevision`、选中的规格、渲染后的参数、`db_type=prod` |


COMPONENTMARKET 是同一套结构的最小例子：一个 ProductInstance，下面只有一条 `componentmarket` 1.10.0 的 AppInstance，拓扑号 `DEPLOY_TOPO_202207201810232349`。

---

## 8. 这份 schema 没有写明的事
+ `phase`、`opsType`、`deployPhase`、`workload.kind` 都是自由字符串，CRD 里没有 enum。`standard` 和 `job` 来自现场 application yaml 的 `meta.kind`，不是 CRD 校验。
+ 控制器源码不在包里。谁先写 ProductInstance（meta-server 同步进 apiserver，还是 operator 自己创建）不能从 CRD 单独证明。能证明的是：operator 被授权调和这四类对象，并据此创建 Pod、StatefulSet、Job、Service 和 PVC。
+ ProductTask 的 OpenAPI `description` 误写成 FunctionProvider，字段本身是任务模型。
+ 云游 2.0.3 values 里的 operator 镜像是 `reg.docker.alibaba-inc.com/antcnstack/aap-product-operator:20250331112251_cbbacb33`。本文 schema 来自 adns 包里的 chart 1.0.0，镜像地址是 `acs-reg.alipay.com/acloud/prodoperator:master_20231107135332_6b97c48b`。两套 chart 版本不同，CRD 组名和 Kind 一致；2.0.3 chart 的 CRD 是否改过字段，本机没有 tgz 可以核对。

---

## 9. aap-runtime chart 的 YAML
包在 `adns1.22.0.aap` 的 `charts/aap-runtime-1.0.0.tgz`。父 chart 自己没有 `templates/`，只负责把三张子 chart 装在一起。Chart 说明是：按 AAP 规范做产品全生命周期管理，覆盖负载均衡、数据库、持久化存储和域名。

```latex
aap-runtime/                         chart 1.0.0，无 templates
  Chart.yaml                         依赖 meta-server、core-function-manager、aap-prodoperator
  values.yaml                        父级覆盖：global、三张子 chart 的镜像和端口
  values-test.yaml
  README.md
  charts/meta-server/                chart 0.1.0，appVersion 1.16.0
  charts/aap-prodoperator/           chart 1.0.0
  charts/core-function-manager/      chart 0.1.0，appVersion 1.16.0
```

父 `values.yaml` 的三个键就是三张子 chart 的名字。`global` 里是环境：`k8sProvider` 默认 `BareMetal`（还可以是 ACK、ACAS）、可用区、`globalZoneDomain`、ADNS 的 VIP 和端口、云账号和 SLB/ECS 地址。子 chart 的镜像、库地址和副本数也在父 values 里覆盖了一遍。

### 9.1 meta-server：产品目录
渲染出一个 Deployment，里面最多三个容器：

| 容器 | 镜像（1.0.0 默认） | 作用 |
| --- | --- | --- |
| `manager` | `acs-reg.alipay.com/acloud/aapmetaserver` | 进程 `/aapmetaserver`，开 leader election。环境变量 `RDB_URL` / `RDB_USER` / `RDB_PASSWORD` / `RDB_PORT` 连库。REST 端口 **28088**，健康检查 8081，指标经本机 8080 |
| `yunyou-render` | `acs-reg.alipay.com/acloud/yunyourender` | `yunyourender.enable` 为 true 时才有。环境变量 `meta_server_endpoint` 默认 `http://127.0.0.1:28088`，`iam_service_url` 默认 `aciamcore.<globalZoneDomain>` |
| `kube-rbac-proxy` | `acs-reg.alipay.com/acloud/kube-rbac-proxy:v0.13.0` | 8443 转到本机 8080 的指标 |


旁边的对象：

| YAML | Kind | 内容 |
| --- | --- | --- |
| `metaserver-manager-config.yaml` | ConfigMap | `config.yaml`：`enableOperator: true`，存储后端 `kind: rdb`，库名 `aapmetaserver`。另有一份 controller-runtime 配置，webhook 端口 9443 |
| `metaserver-metaserver-rest-service.yaml` | Service | ClusterIP，28088，名字会被 operator 配成 `aap-runtime-meta-server-metaserver-rest` |
| `metaserver-nodeport-service.yaml` | Service | NodePort，28088 → 节点端口 **31211** |
| `metaserver-metrics-service.yaml` | Service | 指标口 8443 |
| `metaserver-manager-*.yaml` 等 | ServiceAccount、Role、ClusterRole、Binding | 含 leader election 和 metrics reader |


父 values 把库指到 `mysql-yunyoudb.yunyou.svc:3306`，用户 `root`，`defaultStorageClass` 为 `yoda-lvm-default`。子 chart 自己的 values 默认是 `127.0.0.1` 和用户 `apyunqing`。真正安装时用父 values。口令写在 values 里，这里不抄。

### 9.2 aap-prodoperator：工作负载控制器
| YAML | Kind | 内容 |
| --- | --- | --- |
| `crds/antstack.alipay.com_productinstances.yaml` 等四份 | CustomResourceDefinition | 本文第 3–6 节 |
| `aap-product-operator.yaml` | Deployment | 容器 `operator`，命令 `/bin/manager`。环境变量 `REPOD_ADDRESS` 指向 meta-server 的 `地址:28088`。1 副本，1 核 / 2Gi |
| `aap-product-webhook.yaml` | Deployment | webhook，1 副本，500m / 1Gi |
| `operator-service.yaml` | Service | ClusterIP **20221** |
| `webhook-service.yaml` | Service | ClusterIP **443** |
| `function-provider.yaml` | FunctionProvider | 名字 `product`，`spec.kind: product`，URL 指向上面的 20221 |
| `operator-serviceaccount.yaml`、`operator-clusterrole.yaml`、`operator-rolebinding.yaml` | RBAC | 调和四类 CR，以及 Pod、StatefulSet、Job、Service、PVC |
| `webhook-cert.yaml`、`webhook-issuer.aml` | Certificate、Issuer | webhook 证书。issuer 文件名后缀是 `.aml`，不是 `.yaml` |
| `webhook-*.yaml` | ServiceAccount、ClusterRole、Binding | webhook 自己的权限 |


`k8sProvider` 不是 `baremetal` 时，operator 命令追加 `--image-repo=`。是裸金属时追加 `--workload-annotations=networking.alibaba.com/network-type=Underlay`。

### 9.3 core-function-manager：库、负载均衡、域名、磁盘
一个 Deployment 里并排多个进程，都连同一个 meta-server 地址。

| 容器 | 端口 | 作用 |
| --- | --- | --- |
| `manager` | webhook 9443 | 主控制器。环境变量 `DEFAULT_JOB_IMAGE` 用 `migrate-job` 镜像跑库变更 |
| `lb-provider` | **20222** | 负载均衡。参数有 `k8s-provider`、云账号、`slb-host`、`ecs-host` |
| `domain-provider` | **20223** | 域名。`--dns-master=http://$(ADNS_MASTER_IP):$(ADNS_MASTER_PORT)`，区和 `globalZoneDomain` 来自 global |


另外两个 Deployment 按开关出现：

| YAML | 何时渲染 | 进程 |
| --- | --- | --- |
| `apps_v1_deployment_hestia-manager.yaml` | `global.adnsEnabled` | `/hestia/bin/manager`，镜像 `hestiamanager`，就绪检查 `9802/health`。读 ADNS 主节点 IP 和端口 |
| `apps_v1_deployment_hestia-webhook.yaml` | 同上 | hestia 的 webhook |
| `apps_v1_deployment_anytunnel-controller.yaml` | `anytunnelController.enabled`（默认 true） | 云网络隧道，带云账号和 `k8s-provider` |


注册给调用方的 FunctionProvider：

| 名字 | 地址 | path |
| --- | --- | --- |
| `dbmigration` | function-manager Service 的 **20221** | `db` |
| `lbservice` | 同一 Service 的 **20222** | `service` |
| `domain-service` | 同一 Service 的 **20223** | `domain`。只有 `adnsEnabled` 时才渲染 |


`product` 那条在 aap-prodoperator 里，不在这张表。四条合在一起，就是运行时对外的四类能力：产品工作负载、库变更、负载均衡、域名。

这个子 chart 自己的 CRD（不是前面那四类）：

| Kind | 组 |
| --- | --- |
| DatabasePlan | `antstack.alipay.com` |
| LoadBalancerInstance | `antstack.alipay.com` |
| Domain | `antstack.alipay.com` |
| ResourceInstance | `antstack.alipay.com` |
| FunctionProvider | `antstack.alipay.com` |
| Zone、DNSRecord、DNSJob、DNSEndpoint | `dns.plus.antstack.io` |


配套还有 ConfigMap（`ADNS_MASTER_IP`、`ADNS_MASTER_PORT`、`common.yaml` 里的 meta-server 地址）、function Service、指标 Service、cert-manager 的 Issuer 和 Certificate，以及 manager / hestia / migration-job 的 RBAC。

### 9.4 装进去之后谁听谁
```latex
白屏 / 导入 aap
        |
        v
meta-server :28088          库 aapmetaserver，旁边同 Pod 的 yunyourender
        |
        +-- aap-product-operator :20221     FunctionProvider product
        |         调和 ProductInstance / AppInstance / ProductTask / HelmChartInstance
        |
        +-- core-function-manager
                  :20221  dbmigration
                  :20222  lbservice          -> LoadBalancerInstance
                  :20223  domain-service     -> Domain、Zone、DNS*
                  hestia                      -> 盘和 ADNS
                  migrate-job 镜像            -> DatabasePlan 上的 SQL
```

默认副本都是 1。meta-server 的 NodePort 是 31211。operator 和 function-manager 的 Service 都叫 20221，但是两个不同的 Service。

