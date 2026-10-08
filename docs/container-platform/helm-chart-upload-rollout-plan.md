# Helm 包上传 → 仓库 → 安装/升级 实现 Plan

> 日期：2026-10-08
> 版本：v0.4
> 适用仓库：[HFwas/hfwas-devops](https://github.com/HFwas/hfwas-devops)
> 参照：kite Helm Release（页面改 values + Install/Upgrade；**kite 本身不支持本地上传**，本方案在此之上补「上传并推仓」）
> 目标：支持用户上传 Helm chart 包 → 后端推送到 Chart Hub（Helm repo / OCI）→ 前端改 values 部署；后续改参数或换版本可 Upgrade

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-08 | 初版：流程、API、数据模型、前后端任务拆分、验收与风险 |
| v0.2 | 2026-10-08 | 前端交互明确对标 kite Helm 页；上传推仓为自研增强 |
| v0.3 | 2026-10-08 | 锁定：Harbor OCI、禁同版本覆盖、后端调 helm；前后端并行开工 |
| v0.4 | 2026-10-08 | 配置项对齐 `HELM_CHART_OCI_*`；后端镜像安装 helm CLI |

---

## 1. 目标与非目标

### 1.1 目标

| # | 能力 |
|---|------|
| G1 | 上传 `.tgz`（或目录 zip→后端打成 chart）并校验为合法 Helm chart |
| G2 | 后端推送到统一 Chart 仓库（优先 **OCI（Harbor）**，兼容静态 Helm repo） |
| G3 | 前端：选已上传 chart → 编辑 values → Dry Run → Install |
| G4 | 前端：Release 详情改 values / 选新版本 → Dry Run → Upgrade；支持 History Rollback |
| G5 | Release 记录来源（repo、chart、version），升级可追溯 |

### 1.2 非目标（本期不做）

- 完整复刻 kite App Catalog / Artifact Hub 浏览
- 无仓库、把 tgz 直接 `helm install` 进集群且不落制品库（可作调试开关，不作为主路径）
- Chart 在线可视化 values schema 表单生成（可先纯 YAML 编辑，二期再做）
- 多租户配额计费

---

## 2. 端到端流程

### 2.1 首次：上传并部署

```
用户上传 chart.tgz
    → API: POST /helm/charts/upload
    → 校验 Chart.yaml / 解压安全
    → 推仓: helm push / oras (OCI) 或 写入静态 repo + 重建 index
    → 返回 { chartName, version, repositoryId, chartRef }
用户在「应用/ Helm」页选择该 chart+version
    → 拉取默认 values
    → 编辑 custom values
    → （可选）POST …/dry-run
    → POST …/install  → 集群 Helm Release
    → 跳转 Release 详情
```

### 2.2 仅改参数升级（同版本）

```
Release 详情 → Upgrade
    → 预填当前 values
    → 用户改 YAML
    → dry-run → upgrade（chartRef 不变，version 不变）
```

### 2.3 换包升级（新版本）

```
再上传新 tgz（Chart.yaml version 必须升高）
    → 推仓得到新 version
Release 详情 → Upgrade
    → 选择新 version（或默认最新）
    → 合并策略：保留用户上次 custom values，或「用新 chart 默认 values 重置」二选一
    → dry-run → upgrade
```

### 2.4 回滚

```
History → 选 revision → Rollback
（可选）upgrade 时 rollbackOnFailure=true
```

---

## 3. 架构要点

```
┌──────────┐   multipart    ┌─────────────────┐   push    ┌──────────────┐
│  Frontend│ ─────────────► │ backend         │ ────────► │ Harbor/OCI   │
│ Helm UI  │ ◄── chartRef── │ helm-chart svc  │           │ 或 Helm repo │
└────┬─────┘                └────────┬────────┘           └──────────────┘
     │ install/upgrade                │
     │ (chartRef + values)            ▼
     │                      ┌─────────────────┐
     └─────────────────────►│ Helm SDK / CLI  │──► K8s 集群 Release
                            └─────────────────┘
```

**原则：** 集群安装永远引用 **仓库中的 chartRef**，上传只负责「制品入库」。这样审计、复现、多人协作与 kite 模型一致。

---

## 4. 数据模型（建议）

### 4.1 `helm_chart_repository`（仓库登记）

| 字段 | 说明 |
|------|------|
| id | |
| name | 展示名 |
| type | `oci` \| `https` |
| url | 如 `oci://harbor.example/charts` |
| credential_id | 关联已有流水线/镜像凭证或专用 secret |
| tenant_id | 多租户 |
| created_at | |

### 4.2 `helm_chart_artifact`（每次上传）

| 字段 | 说明 |
|------|------|
| id | |
| repository_id | |
| chart_name | 来自 Chart.yaml |
| version | SemVer |
| digest | OCI digest / 文件 sha256 |
| size | |
| chart_ref | 可安装引用，如 `oci://…/name:version` |
| uploaded_by | |
| created_at | |
| **唯一约束** | `(repository_id, chart_name, version)` 默认禁止覆盖 |

### 4.3 `helm_release_record`（平台侧元数据，可选增强）

| 字段 | 说明 |
|------|------|
| cluster_id | |
| namespace | |
| name | |
| chart_artifact_id / chart_ref | 当前来源 |
| values_yaml | 最近一次用户 values（或只存 hash + 对象存储） |
| status | 与集群同步 |
| revision | |

集群真相仍以 Helm storage 为准；库表便于列表过滤与「从哪次上传来的」。

---

## 5. API 草案

前缀示例：`/container/helm` 或独立 `/helm`（与现有 `container-core` 边界对齐时再定）。

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/charts/upload` | `multipart/form-data`: `file`, `repositoryId`；返回 artifact |
| GET | `/charts` | 列表：按 repo/name 筛选 |
| GET | `/charts/{name}/versions` | 版本列表 |
| GET | `/charts/{name}/versions/{version}/values` | 默认 values.yaml |
| GET | `/repositories` | 仓库列表 |
| POST | `/repositories` | 登记仓库（admin） |
| POST | `/releases/{ns}` | Install：`{ name, chartRef\|artifactId, values, createNamespace?, wait? }` |
| POST | `/releases/{ns}/dry-run` | 同上，返回 manifest/diff |
| PUT | `/releases/{ns}/{name}/upgrade` | Upgrade |
| PUT | `/releases/{ns}/{name}/upgrade/dry-run` | |
| PUT | `/releases/{ns}/{name}/rollback` | `{ revision }` |
| GET | `/releases` | 按集群/ns 列表 |
| GET | `/releases/{ns}/{name}` | 详情（values/resources/history） |
| DELETE | `/releases/{ns}/{name}` | Uninstall |

错误码约定：非法 chart、version 冲突、推仓失败、无集群权限、values YAML 解析失败。

---

## 6. 后端任务拆分

### P0 — 上传与推仓（先打通）

1. 配置默认 OCI 仓库（复用现有 Harbor / 凭证体系）
2. `POST /charts/upload`：大小限制、只接受 `.tgz`、解压路径穿越防护、解析 `Chart.yaml`
3. 推送：优先 `helm push` / ORAS 到 OCI；失败信息回传
4. 落库 `helm_chart_artifact`；同 version 返回 409
5. 单测：样例 chart fixture

### P1 — Install / Upgrade / Rollback

1. 选定 Helm 执行方式：Java 调 Helm 库 / 旁路 `helm` CLI（与现有 Tekton/kubectl 凭证模式对齐）
2. kubeconfig 来自已有「集群管理」凭证
3. Install / Upgrade / Dry-run / Rollback / Uninstall
4. Release 列表与详情聚合（可先薄封装 `helm list/get/history`）

### P2 — 仓库管理与权限

1. 多仓库登记、凭证绑定
2. RBAC：上传 / 安装 / 升级 / 删仓 分权
3. 审计日志（谁上传了哪版本、谁升级了哪 Release）

### P3 — 增强（可后置）

1. values JSON Schema → 表单
2. Auto-upgrade（对标 kite）
3. 依赖 chart 子包校验、签名/Cosign

---

## 7. 前端任务拆分（容器产品内菜单）

信息架构提醒：**容器管理** 产品侧栏增加例如「Helm / 应用发布」子菜单，不要平铺到全局。

**UI 对标（用户确认）：直接参考 kite Helm 页面**，学交互与信息架构，不抄品牌。主要对照：

| kite 页面/组件 | 我们对应 |
|----------------|----------|
| App Catalog + chart 详情（README/values/versions） | Chart 浏览 + 已上传制品列表 |
| `helm-install-dialog`（默认 values 只读 + Custom Values + Dry Run + Install） | 安装抽屉/页 |
| `helmrelease-detail`（Overview/Values/Resources/History/Logs/Manifest，Upgrade/Auto/Delete） | Release 详情 |
| Upgrade 对话框（改 values/版本 + dry-run） | Upgrade 抽屉 |
| History Rollback | History Tab |

自研增量（kite 没有）：**上传 tgz → 推仓** 入口与进度；其余装/升流程尽量与 kite 一致。

| 页面 | 内容 |
|------|------|
| Chart 列表 / Catalog | 对标 kite Catalog；额外「上传」 |
| 上传向导 | 选仓库 → 选文件 → 展示 name/version |
| 安装 | 对标 kite Install 对话框 |
| Release 列表 / 详情 / Upgrade | 对标 kite Release 页 |

UI 规范：共享 console 组件 + kite 冷蓝/字体规范；values 用 mono 编辑器。

---

## 8. 配置与运维

| 配置项 | 说明 |
|--------|------|
| `HELM_CHART_OCI_URL` | 默认 OCI 仓库，如 `oci://harbor.example/charts`。空则未配置 |
| `HELM_CHART_OCI_USERNAME` / `HELM_CHART_OCI_PASSWORD` | 机器人账号。口令只来自环境变量 / Helm Secret，默认空，不写入仓库 |
| `HELM_CHART_OCI_INSECURE` | 默认 `false` |
| `HELM_CHART_REPOSITORY_NAME` | 默认仓库显示名，默认 `default` |
| `HELM_CHART_MAX_UPLOAD_MB` | 默认 50，且不大于 servlet multipart 上限 |
| `HELM_BINARY_PATH` | 默认 `helm`。后端运行镜像已 `apk add helm` |
| `HELM_CHART_PUSH_TIMEOUT_SECONDS` | 单次 helm 调用超时，默认 120 |

`deploy/charts/backend/values.yaml` 的 `helmChart.oci` 提供同样的空占位；口令进入 Secret `helm-oci-password`。

本地开发：可用 `docker compose` 里的 Harbor。上传只接受 `.tgz`。

---

## 9. 安全

- 上传解压：**禁止** `../`；限制文件数与总大小
- Chart hooks / 危险模板：安装前 dry-run；生产可加准入策略（二期）
- 凭证不落日志；推仓用短时 token
- values 中可能含密钥：详情按权限脱敏或仅创建 者可见

---

## 10. 验收标准

| # | 验收 |
|---|------|
| A1 | 上传合法 tgz → Harbor/OCI 可见对应 tag → 列表可查 |
| A2 | 同 name+version 再传 → 409，不覆盖 |
| A3 | 选 chart → 改 values → dry-run 出 manifest → install 成功 |
| A4 | 改 values upgrade 成功；History +1 |
| A5 | 上传更高 version → upgrade 到新版本成功 |
| A6 | Rollback 回到上一 revision |
| A7 | 无集群/无仓库权限时错误明确 |

---

## 11. 建议排期

| 阶段 | 时间 | 交付 |
|------|------|------|
| W1 | P0 上传+推仓+列表 API | 可用 Postman/curl 打通 |
| W2 | P1 Install/Upgrade/Rollback API + 最小前端安装/详情 | 主路径可点 |
| W3 | Release 列表详情打磨、凭证/多仓、审计 | 可给内部试用 |
| W4 | Schema 表单 / Auto-upgrade（可选） | 增强 |

落地方式建议：与现前端统一样式一样，**Cloud Agent 按阶段开 PR**（先 backend upload，再 install API，再 UI）。

---

## 12. 已锁定决策（2026-10-08）

| 项 | 决定 |
|----|------|
| 仓库类型 | 仅 **Harbor OCI**（可后续再加 HTTPS repo） |
| 同版本覆盖 | **禁止**（同 name+version → 409） |
| Helm 执行 | 先 **后端进程调 helm**；后续可收紧到集群 Job |
| 菜单归属 | **容器管理** 产品内侧栏 |
| 前端 | **对标 kite** Helm 页；上传推仓为自研 |
| 协作 | 后端与前端 **并行多 PR** |

---

## 13. 执行状态

1. PR-Docs：Plan 入库 `docs/`  
2. PR-Backend-P0：上传 + 推 OCI + 制品列表  
3. PR-Frontend：kite 对标 Helm UI + 上传入口（可先 mock API）  
4. 随后 Backend-P1：Install/Upgrade/Rollback 与前端联调
