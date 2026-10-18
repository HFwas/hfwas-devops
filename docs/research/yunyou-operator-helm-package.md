# 云游运转机制研究：Operator + Helm + 工堪 + 包格式

> 日期：2026-09-21
> 版本：v0.3
> 状态：研究笔记（未拍板、未实施）
> 现场对照：`Bizstack部署文档.md`（现场资料，未随仓库提供）
> 相关：[operator-job-deployment-system.md](./operator-job-deployment-system.md)、[2026-09-08-delivery-platform-design.md](../superpowers/specs/2026-09-08-delivery-platform-design.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-09-16 | 初版：把云游/ADP/CNStack 的 Operator、Helm、工堪、包格式如何咬合写成可带走的研究笔记；附另一台机器上拆 aap / 安装包的检查清单 |
| v0.2 | 2026-09-21 | 补充 CRD 结构：App/CloudService/CloudComponent 三层、`kubectl get app -A` 用户入口、Helm-only+OAM 预留、LabelMarker 机制细化、新增 CRD 开放问题 |
| v0.3 | 2026-10-18 | 整理：未随仓库提供的 Bizstack 现场文档改为纯文本说明，消除失效链接 |

---

## 1. 这份笔记是什么

把「云游这套东西怎么运转」从对话里收成文档，方便换机器后继续挖。现场另有 `.aap` 和其它安装包时，用第 9 节清单对着拆，不要凭记忆。

**本文不设计本仓库怎么实现。** 对照本仓库已有材料只在第 10 节点一下。

不要把下面两代产品混成一个实现：

| 代际 | 名字 | 执行引擎 | 现场沟通介质 |
|------|------|----------|----------------|
| 约 2019 蚂蚁专有云 | 云游 Local + AKE | 对 AKE/Docker 发「起容器、绑 LB、建库」；基线 vs 解决方案 diff 出发布单/运维单 | 解决方案 JSON |
| 约 2021 后阿里云 ADP / CNStack | 能力中心 + `cn-app-operator` | CRD 调和 + Helm install/upgrade/rollback | Sealer Image / tar+`hopctl` / 商业包 `.aap` |

`Bizstack部署文档.md`（现场资料，未随仓库提供）是 **后一代装到现成 ACK 上**：工堪 → CNStack 底座 → antboot/adns → IAM/OP → 再导 `.aap`。域名 `yunyou.*` 是现场白屏（OP / 能力中心），不是 2019 那套 AKE 中台的源码。产品模型（产品、参数、基线、依赖、点发布）是同一条线。

---

## 2. 一句话和四工位

云游不是「页面里手改 YAML 再 exec helm」。它是声明式交付控制：

**包描述终态 → 工堪判断这套集群能不能接 → Operator 把终态写成集群里的声明 → Helm 只负责把声明渲染成 K8s 对象。**

页面改端口、StorageClass、镜像前缀，改的是包里暴露的参数。落到集群，是 Operator 拿合并后的 values 去 `helm upgrade`。

```
导入产品包
    │
    ▼
工堪 / 前置检查（机器、SC、Ingress、镜像、License、漏填参数）
    │
    ▼
白屏写「实例 / 配置」CR（终态：版本 + 覆盖参数）
    │
    ▼
cn-app-operator 调和
    │  展开云组件 → merge values → webhook 改镜像/SC
    │  helm install | upgrade | rollback
    │  给 workload 打标 → 聚合 Ready 写回 status
    ▼
基线更新；下次再导包是 diff，不是全新安装
```

白屏（能力中心 / 云游 Local / 手册里的 OP）**不执行 Helm**。UI Backend 只封装 API，不管对象生命周期。「点部署」= 写 CR。

---

## 3. 包格式：三层，不要混

运维知识打进包，而不是长期写在实施手册里靠人记。

### 3.1 第一层：组件 = 一份 Helm Chart

最小可安装单元。一个业务能力或一个中间件就是一个 chart。

在线侧用 `zlink push -c <chartDir>` 上传时会：

- 扫描 chart 里的镜像；operator 镜像若没写进编排会提示
- 记下元数据（后面页面表单、镜像预拉靠这份）
- 可选 `--sync` 把镜像同步到平台仓库

公开材料写死：**当前应用定义只支持 Helm Chart**。OAM 是预留扩展，不是现在在跑的执行模型。能力中心也允许只导一个标准 Helm Chart 当「云组件」用；带依赖、License、多组件的完整产品才走 Sealer / `.aap`。

### 3.2 第二层：产品 / 云服务 = 多组件 + 依赖 + 可覆盖参数

Harbor / GitLab / BizStack 在这一层，通常不是「一个巨大 chart」，而是：

- 有哪些组件、依赖/大致顺序
- 哪些是控制面（只装主集群）、哪些是数据面（可下发客用集群）
- **组件默认 values** 和 **产品级覆盖** 分开；现场页面改的是产品配置，Operator 做 merge
- 参数作用域：全局（镜像前缀、默认 SC、域名）vs 组件（某个 PVC 大小）
- 覆盖路径用点号，与 Helm values 相同，例如 `resources.limits.memory`

老云游把这一层叫「产品版本」：镜像、资源（机 / LB / 库 / 盘）、依赖产品、API、启动参数、健康检查。新一代换成云服务 CR，语义没变。

另外区分生命周期：

| 类型 | 谁负责 | 行为 |
|------|--------|------|
| 云服务组件 | 跟着云服务 | 控制面只在主集群；数据面可在主/客集群，健康互不影响 |
| 集群组件 | 集群管理员 | 全集群往往唯一：没装才装；已装则只允许升到声明的更高版本，否则不动 |
| 项目组件 | 项目使用者 | 装在项目 namespace，可和自研软件一起编排 |

「集群组件只升不降、不重复装」是为了避免两个产品各装一遍 Ingress / 存储插件 / CoreDNS。

### 3.3 第三层：交付包 = 能拷到现场的那一坨

这一层才是 U 盘 / OSS 上的文件。壳可以换，内核都是「定义 + chart + 镜像」：

| 壳 | 典型用法 | 里面大致有什么 |
|----|----------|----------------|
| Sealer Image（OCI） | `sealer run` + Clusterfile | 云服务定义、chart、全部 container image；甚至可带上 K8s 和 `cn-app-operator` |
| tar + `hopctl` | 解压后改 `global.yaml`，`./hopctl install -p .` | 节点 IP、SSH、盘符、默认 SC、网络插件等环境覆盖 |
| `.aap`（商业包装） | 导入云游 / 能力中心 | 同一类东西：产品元数据 + chart + 镜像清单 + License 绑定名 |

公开的 `hopctl` 路径里，`global.yaml` 会覆盖例如：

- `cluster.spec.masters.ips` / `nodes.ips`
- `sshPassword`
- `storageDevice` / `yodaDevice`（本地盘分区给 Docker / Open-Local）
- `defaultstorageclass`

其它产品组件的覆盖是「对原配置打补丁」，多级用 `.` 分隔。

包里真正值钱的不是 YAML 全文，是三份清单：

1. **参数 schema**：哪些能改、类型、默认、是否必填
2. **镜像清单**：断网预拉、核对缺镜像
3. **依赖与资源需求**：工堪和发布排序（CPU/内存/盘、要不要 Ingress、PVC 下限）

现场手册里 PVC &lt; 20Gi 报错，就是产品声明了「这块盘至少 20Gi」，执行时校验，不是实施口诀。

### 3.4 在线出包（ADP-online）在干什么

现场包不是实施手搓的。在线平台流程大致是：

1. 组件接入（Helm Chart + 镜像）
2. 产品编排：自研组件 + 服务目录里的中间件 + 默认带一份容器底座（不可移除）
3. 公有云拉起验证环境、故障注入、再锁定版本
4. 针对「线上 ECS」或「线下客户 IaaS」出包
5. 线下出包前往往先工堪，把节点信息导回平台再生成方案

云游 Global（蚂蚁内部）和各局点 Local 之间，老模型的沟通介质是「解决方案」；新模型是 Sealer / aap。Local 之间互不影响。部署完成后可把基线回流 Global，供下次升级当初始态。

---

## 4. 工堪：失败提前到还没点发布

工堪是 Day 0 环境探针，不是装完再看日志。ADP 把交付踩坑收成检查项；Sealer 插件里也有时钟同步、网络/存储预检、OS 参数调优、磁盘智能分区。

### 4.1 机器 / 集群段（cluster-checker）

公开 ADP 用法（版本号以现场包为准）：

1. 下载 `cluster-checker`，放到待检环境任一节点，`chmod +x`
2. 同目录配置 `cluster-info-brief.yaml`：SSH 用户密码、节点 IP 列表（可带非 22 端口）、**待装产品声明的 cpu / memory / storage**
3. `./cluster-checker run`
4. 成功则生成 `cluster-info-detailed.yaml`（节点详情，不含密码），可导回在线平台做批量加节点 / 出方案
5. 失败则停，不进入出包或安装

这和现场手册「前三个 node 根盘 ≥ 300G、IP 要 512、必须开 Ingress、网络插件 terway、不要多余监控 RDS」是同一类：**产品声明需求，工堪对照现状**。

### 4.2 两层含义

| 层 | 拦什么 |
|----|--------|
| 硬工堪 | 机器够不够、K8s 插件在不在、盘符/SC/Ingress、时钟、端口、二层/UDP |
| 软工堪 | 这个包在当前基线上能不能装：参数冲突/漏填、镜像是否已在仓库、License、依赖产品是否 Ready、外部 RDS 是否已建同名库 |

老云游把软工堪叫「前置任务」（参数、镜像中心、VIP 落到哪个 LB、schema 落到哪个库、客户自带 MySQL 先建账号）。新一代一部分变成 Operator 调和前的 admission / License：超授权用量直接拒绝部署或变更。

### 4.3 工堪盖不住的

手册后半「手改 ACK 的 SC 标签、PVC 太小、域名解析、重启 CoreDNS、IAM 用户组授权」说明：

- 检查项没覆盖全，或
- 过了工堪但运行时 DNS/权限仍会炸

理想是：逻辑 SC / 镜像前缀走全局参数 + mutating webhook 改渲染结果，而不是现场 `kubectl label` / `vim yaml`。

---

## 5. Operator：真正的运转核心

组件名：**`cn-app-operator`**。公开设计：基于 OAM 思想，Helm 作渲染引擎；围绕发布包、实例、自运维策略、告警管理建一组 CRD。曾计划随 CNStack 社区开源，公开仓库里未见完整实现，细节要以现场 CRD / 控制器日志为准。

### 5.0 CRD 结构总览

三层 CRD 体系，用户入口为 **App** 级别：

```
kubectl get app -A                     # 查看应用部署状态（用户入口）
    │
    ▼
CloudService  ←─── 产品/服务定义（参数 schema、镜像清单、依赖、license、多集群拓扑）
    │                 多个 CloudComponent 聚合为一个 CloudService
    ▼
CloudComponent  ←── 组件部署单元（对应一个 Helm release）
```

| CRD | 层级 | 职责 |
|-----|------|------|
| **App** | 应用聚合 | 用户面向的顶层资源；`kubectl get app -A` 查看所有应用状态 |
| **CloudService** | 产品/云服务 | 定义产品参数 schema、组件列表与依赖、镜像清单、License、多集群分发策略；支持多组件聚合为一套完整产品 |
| **CloudComponent** | 组件实例 | 对应一个 Helm Chart 的部署实例；内含 values 覆盖、部署策略、健康检查 |

公开材料写死：**当前应用定义只支持 Helm Chart**。OAM 是预留扩展，不是现在在跑的执行模型。

每个 CloudService 内含若干 CloudComponent。Operator 按 CloudService 声明的依赖顺序逐个调和其 CloudComponent，每个 CloudComponent 对应一个 helm release。

参数作用域分层：

1. **全局参数**：镜像仓库前缀、默认 StorageClass、域名 — 跨所有组件生效
2. **产品级覆盖**：CloudService 级别的覆盖参数，merge 到各 CloudComponent 的默认 values 中
3. **组件级**：CloudComponent 自身的 values（即 chart 默认 values.yaml）

Operator 做多级 merge，webhook 在渲染后再改写最终 manifest（见 5.4）。

### 5.1 调和循环（公开描述）

云服务方把定义丢进**主集群**，之后交给 Operator：

1. 根据「云服务 + 云服务配置」自动创建对应「云组件」（也可单独提交一个云组件）
2. 允许单独一份云服务配置覆盖组件部分参数；**默认 ⊕ 覆盖的 merge 由 Operator 做**，定义和配置解耦
3. 集群组件：未部署则部署；已部署仅当声明了更高版本才升级；否则不动
4. 对云组件执行 `helm install` / `upgrade` / `rollback`，**action 由 Operator 判断**，不是人在页面选命令
5. 监听云服务配置、多集群信息等，触发上述自动变更

每次变更用 Kubernetes **ControllerRevision** 记历史（和 Deployment 历史同一套路）。

### 5.2 状态不是 helm 的 exit code

1. 解析 helm manifest，得到属于该产品的 workload 列表
2. **LabelMarker** 打标
3. status 控制器按标签聚合 workload，写成云服务/云组件的真实状态

页面上「产品绿了」是聚合 Ready，不是 `helm install` 返回 0。GitLab 那种 `gitlab-ctl reconfigure` 十几分钟，必须靠 Pod Ready / 健康检查。

### 5.3 License

- 云服务不用自己做 License 生成/校验
- 可用 ServiceMonitor 报用量；Operator 和授权用量对比，超出则拒绝部署/变更
- 云服务也可调 Operator 接口拿授权信息，自己再加逻辑
- 现场手册里带 `adp.aliyuncs.com/license`、`adp.aliyuncs.com/application-name` 的 Secret，就是喂给这一层的（按产品名绑到对应 namespace）

拆包时不要把示例 License 当通用密钥；每套集群要发行方签过的那份。

### 5.4 异构：webhook，不 fork chart

不同 K8s 会有 SC 名、镜像仓库、网络注解差异。ADP 用 **mutating webhook** 改渲染结果。页面上的「镜像前缀」「SC」很多会变成：

- values / 全局参数里写逻辑名
- webhook 按本集群真实仓库、真实 SC 改写

### 5.5 多集群是部署器插件

组件上写 `ClusterLabelSelector`，对上 OCM `ManagedCluster` 的 label，就把 helm release 下发过去。能力中心替实施改 label，人不用碰 OCM。云边协同则把 release 部署到边缘节点。这是扩展部署器，不是另一套产品模型。

### 5.6 和老云游「发布单」怎么和平共处

| | 老云游发布单 | `cn-app-operator` |
|--|----------------|-------------------|
| 触发 | 基线（初态）vs 解决方案（终态）diff 出任务列表 | 水平触发：每次只看当前声明 vs 集群 |
| 任务类型 | 应用部署 → 发布单；扩缩容/变配 → 运维单；测试镜像 → 测试单 | install / upgrade / rollback + 独立的集群组件策略 |
| 人看到的 | 按产品依赖排序的单据；可对产品/应用/步骤/节点 重试、跳过、回滚 | 产品卡片状态；CR status；ControllerRevision |
| 幂等 | 依赖任务机是否写好 | 调和循环按设计应可重复执行 |

现场手册仍有「部署顺序」（底座 → antboot/adns → iam/op → sofa → zk → bizstack），因为**产品依赖**还在：IAM 没 Ready，BizStack 的期望达不到，看起来仍像串行。

点两次部署、Operator 重启，按水平触发应安全。这是和「后端 API 里同步 exec helm」的根本差别：Harbor/GitLab 要很久、要停在某步、要审计，不能卡在一次 HTTP 请求里。

---

## 6. Helm：渲染引擎，不是产品模型

Helm 在这套里很「窄」：

- 不管产品是什么、依赖、License、多集群、页面表单
- 只做：chart + values → manifest，以及 install / upgrade / rollback
- **action 由 Operator 判断**

因此社区 chart（Harbor、GitLab、kube-prometheus-stack）可以几乎原样进包；产品知识（暴露哪些参数、依赖谁、PVC 下限、镜像列表）在云服务定义里，不在 `templates/` 里。

代价：

- chart 必须能被 `helm template` 出来，Operator 才能打标和聚合状态
- 一次调和对应一个 helm release；产品多个组件就是多个 release，依赖由 Operator 排，不把全部编排压在 chart hook 上
- helm 自己的 pending-lock、超时、`--wait` 要收进 Operator 的失败/重试，不能漏到实施去手敲 `helm rollback`

2019 云游没有这一层，是直接对 AKE 发步骤。换成 Helm 是为了让外部产品按社区 chart 进来。

---

## 7. 一次发布如何咬合（已有 ACK，不从零装 K8s）

接近 `Bizstack部署文档.md`（现场资料，未随仓库提供），不是 `sealer run` 连 K8s 一起装：

1. **工堪**：checker 对照包声明的资源；ACK 是否已有 Ingress、默认 SC、节点盘。失败则不出包、不导入。
2. **装底座**：`cnstack-multicluster` 等把管控面装进现成集群（手册：只要 ACK，不要额外虚机）。
3. **antboot / adns**：引导和集群内 DNS，后面产品用域名。
4. **IAM + OP**：账号和白屏；`yunyou.*` 起来之后才能点发布。
5. **导包**：能力中心把 aap/Sealer 解开，云服务定义和 chart 落到主集群。此时还没有业务 Pod。
6. **填参**：页面只渲染 schema 里的字段（SC、镜像前缀、端口、域名、PVC）。全局参数可一次填，后续产品复用。
7. **点部署**：UI 写实例/配置 CR。
8. **Operator**：License 与依赖 → merge values → webhook 改镜像/SC → `helm upgrade --install` → 打标 → 聚合 status。
9. **人看的**：产品卡片从部署中到就绪；失败停在某个组件，允许重试。基线更新成这次终态。

升级和首次安装是同一条路：新包 + 新参数 = 新终态，Operator 判断 upgrade。这就是老说法「基线 vs 解决方案」在 Operator 时代的对应。

公有云可以已有底座上直接云游发布；私有云必须把底座和云游重新装一遍。手册里那段「公有云有 MPAAS 底座、私有云底座要重建、监控可以不装」说的就是边界。

---

## 8. 公开机制 vs 只能拆包才知道

**比较硬的公开机制**

- 组件 = Helm Chart
- 产品 = 多组件 + 覆盖参数 + 依赖
- `cn-app-operator` 调和 install/upgrade/rollback
- ControllerRevision 记历史
- 解析 manifest 打标，再聚合 workload 状态
- Sealer 出包；能力中心是白屏
- License 走 Operator
- 工堪有 cluster-checker 和 Sealer 预检插件
- UI 不实现生命周期，只调 API
- 管控 API 收敛到 Management Gateway；账号侧用过 Keycloak

**没开源、必须以现场文件为准**

- `.aap` 内部目录、压缩格式、清单文件名
- antboot 二进制实际做了什么
- `cluster-info-detailed.yaml` 全字段
- webhook 改哪些 JSON path
- 发布单对象和 CR 如何一一对应
- 参数 schema 的准确 JSON Schema / CRD OpenAPI

**2026-09-21 深研后新增的开放问题**

1. **CloudService / CloudComponent 的完整 JSON Schema**：`spec.parameters`、`spec.dependencies`、`spec.healthCheck` 等字段的确切定义 — 公开仓库和文档均未提供完整 CRD YAML
2. **跨 CloudComponent 依赖排序策略**：组件 A 须先于组件 B 安装时，Operator 如何编排？Helm-only 模型的排序机制不透明
3. **App CRD 与 CloudService/CloudComponent 的关系**：`kubectl get app -A` 中的 App 是封装/聚合层，还是独立的资源抽象？是否存在独立的 App CRD YAML 定义？
4. **参数 merge 具体策略**：产品级覆盖与组件默认值的合并是 simple override、strategic merge patch 还是 JSON merge patch？
5. **基线 diff 与持续调和的表现**：失败时页面是「重试单据」还是等待 Operator 自动恢复？发布单对象是否只是 CR 在 UI 层的投影？

---

## 9. 换机器后怎么继续挖（aap 与其它安装包）

目标：用真实包把第 8 节「只能拆包才知道」补成事实，而不是再猜。

### 9.1 先列清现场有哪些文件

对照手册常见集合（以实际文件名为准）：

- 工堪：`cluster-checker*.tgz` / 二进制
- 底座：`cnstack-multicluster-*.tar` 或 Sealer 镜像、`package.tar`
- 引导：`antboot*.tar.gz`、`adns*.aap`
- 产品：`*.aap`（如 sofa、biz、解决方案 `all.aap`、业务修复包）
- 可能还有 `hopctl`、`zlink`、Clusterfile、license yaml

不要用过期的 OSS 签名 URL 当「官方下载地址」；以磁盘上的包为准。

### 9.2 拆包时建议看的路径（见到再记，不要假设一定存在）

对每个 `.aap` / tar / Sealer：

1. **文件类型**：`file`、是否 zip/tar/OCI。aap 可能是 tar/zip 套了一层私有头。
2. **顶层清单**：`manifest.yaml` / `Chart.yaml` / `metadata.json` / `app.yaml` / `product.yaml` 之类。记下 apiVersion、kind、产品名、版本、依赖字段。
3. **Helm**：`charts/` 或 `*.tgz`。`helm show values`、`helm template` 能否直接跑。
4. **参数 schema**：和 values 分开的 `schema.json`、`config.yaml`、`parameters.yaml`、CRD 片段。区分全局 vs 组件、哪些 `required`。
5. **镜像清单**：`images.txt`、`imagelist`、Sealer layer。是否已含镜像 blob 还是只有引用。
6. **资源需求**：cpu/memory/storage、PVC 最小值、要的 StorageClass 逻辑名、Ingress。
7. **License**：是否引用 `application-name`、校验公钥、和 hopctl 文档里「global.yaml 里的公钥验 License」是否对得上。
8. **Operator / CRD**：包内是否自带 `cn-app-operator` 镜像和 CRD yaml；kind 名字（CloudService / CloudComponent / Application 等以实际为准）。
9. **hook**：SQL、Job、脚本；相对 helm 的先后（对应老云游「一次性任务应用」）。
10. **工堪包**：brief/detailed yaml 样例、检查项列表、产品需求如何声明。

建议每拆一个包在本文变更记录下追加一节「现场记录」，或另开 `docs/research/yunyou-aap-dissection.md`，把**实际路径和字段名**写死。

### 9.3 集群里可以对的对象（若那台机器还能 kubectl）

```text
kubectl api-resources | grep -iE 'adp|cnstack|yunyou|app.alibaba|core.oam'
kubectl get crd | grep -iE 'adp|cnstack|application|cloudservice|cloudcomponent'
kubectl get deploy,sts -A | grep -iE 'operator|yunyou|cn-app|op-'
```

有 CRD 之后：看一份已部署产品的 spec（覆盖参数）和 status（聚合状态）、是否有 ControllerRevision。有 Helm 则 `helm list -A` 和 CR 是否一对一。

### 9.4 继续深挖时优先的两个问题

1. **参数 merge 和 webhook**：逻辑 SC / 镜像前缀如何变成集群真实值（实施到底改 schema 里的哪几个键）。
2. **基线 diff 和持续调和**：失败时页面上是「重试单据」还是「过一会儿 Operator 自己好」；发布单是否只是 CR 的投影。

---

## 10. 和本仓库已有材料的关系（仅索引，不是方案）

| 材料 | 和云游的关系 |
|------|----------------|
| `Bizstack部署文档.md`（现场资料，未随仓库提供） | 现场操作手册：工堪、底座、aap、域名、SC/DNS 坑 |
| [operator-job-deployment-system.md](./operator-job-deployment-system.md) | 本仓库对「CRD + Job + 页面参数」的研究，执行器更偏 Job 而不是长期 Helm Operator |
| [2026-09-08-delivery-platform-design.md](../superpowers/specs/2026-09-08-delivery-platform-design.md) | 已拍板未落地的「应用交付」：导入包、页面改 params、Tekton 跑 helm；产品形态接近能力中心，引擎不是 `cn-app-operator` |

本文不把 Harbor/GitLab/监控套进上述设计。等 aap 拆清楚再另开讨论。

---

## 11. 参考来源

| 来源 | 说明 |
|------|------|
| 阿里云开发者社区《独家交付秘籍之招式拆解（第一回）》 | ADP 在线/本地、Sealer、应用管控 Operator、webhook 抹平异构 K8s、工堪类 Sealer 插件 |
| 阿里云开发者社区《CNStack 2.0云原生技术中台设计思想与架构深度解析》 | 面向 API、一切是资源、`cn-app-operator`、UI Backend 只封装 API、Keycloak、Management Gateway |
| 《CNStack 云服务&云组件：打造丰富的云原生技术中台生态》 | 云服务/云组件类型、Operator 调和步骤、Helm action、打标聚合状态、License、Sealer、能力中心可直接导 Helm |
| 《云原生应用交付服务使用指南》（约 2021） | zlink、产品编排、cluster-checker、`global.yaml`、`hopctl install`、参数点路径覆盖 |
| 《云游操作手册》云游 2.8.0（约 2019） | 解决方案导入、前置任务、基线、发布单/运维单、产品参数页、Global/Local |
| 《AntStack 产品简介 / 运维指南》 | 云游定位：规划、交付、运维；产品发布与 AKE 管控中台 |
| 现场 `Bizstack部署文档.md`（未随仓库提供） | ACK 三方集群、aap 文件名、部署顺序、yunyou 域名、License Secret 标签 |
| [CNStack Community Edition README](https://github.com/alibaba/CNStackCommunityEdition) | 公开的主入口文档：确认 `kubectl get app -A` 为用户入口，Sealer + Clusterfile 为部署方式，Helm-only + OAM 预留 |
| 《SOFAStack CAFE 联邦: 单元化路由与 Kine》(developer.aliyun.com/article/873658) | 联邦层 ApiServer 集成 Kine 适配器，MySQL/OceanBase 替代 etcd；topologyType=cell/cluster 部署单元分发 |
| 《KubeVela 架构解析》(developer.aliyun.com) | OAM 实现参考架构：ComponentDefinition/TraitDefinition/WorkflowStepDefinition 的 CUE 模板化 |

开源对照（不是云游源码，但是同代技术）：[Sealer](https://github.com/sealerio/sealer)、[ACK Distro](https://github.com/AliyunContainerService/ackdistro)、[CNStack Community Edition](https://github.com/alibaba/CNStackCommunityEdition)、OCM、Open-Local。
