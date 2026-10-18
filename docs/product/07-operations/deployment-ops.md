# 部署与运维索引

> 日期：2026-10-17
> 版本：v0.1
> 状态：已完成（索引级；具体步骤以引用文档为准）
> 数据来源：`docker-compose.yml`、`scripts/`、`deploy/charts/`、`docs/devops/`、`docs/container-platform/`、`AGENTS.md`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：本地/集群部署入口、日志、数据、迁移与排障索引 |

---

本文只做入口与关键约定，避免与现有部署文档重复。

## 1. 部署方式

| 方式 | 适用 | 入口 |
|------|------|------|
| docker-compose 一键启动 | 本地开发/演示 | `docker compose up -d`；`scripts/start-dev.sh` |
| 宿主机进程 | 后端/前端调试 | `scripts/start-backend.sh`、`scripts/start-frontend.sh` |
| Helm + Kubernetes | 测试/生产 | `deploy/charts/deploy-app.sh`（Windows 用 `.ps1`） |
| 流水线执行集群 | Tekton 环境 | `scripts/start-pipeline-cluster.sh`、`docs/pipeline/` |

## 2. 本地服务与端口

| 服务 | 端口 | 说明 |
|------|------|------|
| 后端 | 8089 | `/health/check` |
| 前端（直连） | 5173 | Vite dev server |
| 前端（容器） | 80 | Nginx |
| Kong | 8000（代理）、8443（SSL）、8001（Admin） | DB-less |
| Keycloak | 8081 | `admin` 引导账号见 `docker-compose.yml` |
| k3s | 6443（API）、30880、30222 | 本地集群 |
| Harbor | 见 `docs/container-platform/harbor-deployment.md` | NodePort/Ingress 以实际部署为准 |

## 3. 操作系统差异（必须遵守）

### macOS：Docker 内 k3s

1. `docker ps | grep k3s` 定位容器（通常 `devops-k3s`）；
2. `docker exec <容器> kubectl ...`、`docker exec <容器> helm ...`；
3. 禁止使用宿主机 `kubectl` / `helm` / `~/.kube/config`；
4. 导入镜像：`docker exec -i <容器> ctr -n k8s.io images import -`。

### Windows：Docker Desktop

1. `kubectl config current-context` 应为 `docker-desktop`；
2. 使用宿主机 `kubectl` / `helm`；
3. 不要 `docker exec` k3s，也不要 `ctr import`（共用 Docker 镜像）。

## 4. 日志

| 来源 | 位置 |
|------|------|
| 后端文件日志 | `logs/backend/devops.log` |
| 前端 Nginx | `logs/frontend/` |
| Kong | `logs/kong/` |
| Keycloak | `logs/keycloak/` |
| 集群 Pod（应用） | `devops` 命名空间；macOS 进 k3s 容器查，Windows 宿主机查 |
| 流水线运行日志 | 运行详情页 + `pipeline_run_job.log_text` |

## 5. 数据与备份

| 项目 | 说明 |
|------|------|
| 数据库文件 | `data/hfwas-devops.db`（compose 使用 `devops-data` 卷） |
| 上传/生成文件 | `devops-files` 卷（`/app/files`）、`data/uploads` |
| 备份 | 升级前执行 `sqlite3 <db> ".backup '<version>-<timestamp>.db'"`，并用 `PRAGMA integrity_check` 校验；生产建议定期快照卷 |
| 恢复 | 停止写入 → 恢复备份 → 回退镜像 → 再执行迁移检查 |
| 迁移 | embedded：应用启动建表；external：`migrate.py` initContainer。禁止修改已执行 SQL；禁止 rollback 到含旧 05 DROP 的版本 |
| 并发 | Helm `strategy.type: Recreate`；应用与迁移 URL 使用 `busy_timeout=5000` |

## 6. 配置

| 配置 | 位置/变量 |
|------|-----------|
| 端口 | `BACKEND_PORT`、`FRONTEND_PORT` |
| schema 模式 | `DEVOPS_SCHEMA_INIT=embedded|external` |
| JWT | `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI`、`OAUTH2_ISSUER_URI` |
| Docgen 输出 | `DOCGEN_OUTPUT_DIR` |
| 流水线集群 | `PIPELINE_KUBECONFIG`、`PIPELINE_NAMESPACE` |
| Argo | `ARGO_KUBECONFIG` |
| 代理 | `HTTP_PROXY` / `HTTPS_PROXY`（外网访问，见 `AGENTS.md`） |

## 7. 发布与升级

1. 构建后端/前端镜像（`deploy/docker/`）；
2. 推送镜像到 Harbor；
3. `helm upgrade` 后端（先）/前端；
4. 检查 schema initContainer 日志；
5. 验证 `/health/check`、登录、核心链路；
6. 发布记录写入 [release-notes.md](release-notes.md)。

## 8. 常见故障入口

| 现象 | 参考文档 |
|------|----------|
| 本地代理/外网失败 | `docs/devops/clash-verge-rule-mode-troubleshooting.md`、`docs/devops/colima-docker-proxy-troubleshooting.md` |
| Homebrew 升级异常 | `docs/devops/homebrew-upgrade-troubleshooting.md` |
| k3s local-path / 节点亲和 | `docs/container-platform/k3s-local-path-node-affinity-troubleshooting.md` |
| 监控无数据 | `docs/container-platform/monitor-no-data-troubleshooting.md` |
| 后端启动 OCR 初始化 | `docs/backend/20260903-backend-startup-ocr-init-troubleshooting.md` |
| Kong NodePort 链路 | `docs/container-platform/nodeport-30483-traffic-path.md` |

## 9. 运维检查单

- [ ] 数据库文件可写、磁盘充足
- [ ] Keycloak realm/client 配置与 issuer 一致
- [ ] Kong 上游指向正确
- [ ] `devops` 命名空间 Pod 全部 Ready
- [ ] 流水线执行集群 kubeconfig 有效
- [ ] Prometheus 可查询
- [ ] Harbor 可推送/拉取
- [ ] 日志目录可写、轮转正常
- [ ] 备份任务执行成功
