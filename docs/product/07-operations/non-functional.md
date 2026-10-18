# 非功能需求与现状

> 日期：2026-10-17
> 版本：v0.1
> 状态：骨架（仓库无可验证的 SLO/容量基线，缺失项标“待采集”）
> 数据来源：`application.yml`、`SecurityConfig.java`、`docker-compose.yml`、`deploy/charts/`、模块设计文档

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：性能、可用性、安全、可观测性、兼容性现状与待采集指标 |

---

## 1. 性能

| 指标 | 现状 | 目标 | 备注 |
|------|------|------|------|
| 后端接口 P95 延迟 | 待采集 | 待定 | 需明确并发与数据量 |
| 前端首屏加载 | 待采集 | 待定 | 见 `docs/frontend/frontend-bundle-size-analysis.md` |
| 流水线运行启动延迟 | 待采集 | 待定 | 依赖 Tekton 调度 |
| Pod 日志流延迟 | 待采集 | 待定 | WebSocket |
| 大文件解析/生成 | 待采集 | 待定 | Docgen 20MB、File Parser 大文件 |
| SQLite 并发写入 | 未启用 WAL | 待评估 | 可能出现 `database is locked` |

> 建议先建立基线：单实例、固定数据集（1 万事项、100 条流水线、1000 张图片），采集 P50/P95/P99。

## 2. 可用性

| 项目 | 现状 |
|------|------|
| 部署形态 | 单实例 Spring Boot（compose/Helm） |
| 数据库 | 单文件 SQLite，无主从 |
| 故障恢复 | 依赖容器重启与卷持久化 |
| 健康检查 | `GET /health/check` |
| 备份 | 无内置定时备份（待建设） |
| SLA | 未定义 |

## 3. 安全

| 项目 | 现状 |
|------|------|
| 认证 | Keycloak JWT；无状态 |
| 授权 | 平台 `admin`/`user` + 租户隔离；模块内细粒度 RBAC 未实现 |
| 传输 | 生产应由 Kong/Ingress 提供 TLS |
| 敏感数据 | 凭证 `secret_enc` 加密；kubeconfig、镜像仓库密码、调试历史报文未确认加密/脱敏 |
| CSRF | 已禁用（`csrf.ignoringRequestMatchers("/**")`），依赖 Bearer Token |
| CORS | 仅允许 localhost/127.0.0.1 通配（生产需调整） |
| 审计 | 登录日志、操作日志；部分模块 `@OperLog` 覆盖 |
| 依赖漏洞 | Dependency-Track / Semgrep / Sonar 相关脚本与配置 |
| 前端漏洞 | 见 `docs/frontend/20260902-frontend-vulnerability-fix-report.md` |
| 安全修复记录 | 见 `docs/general/security-fixes.md` |

## 4. 可观测性

| 能力 | 现状 |
|------|------|
| 请求追踪 | `X-Request-Id` + MDC + `BaseResult.requestId` |
| 日志 | 文件日志 `logs/`；集群日志 `kubectl logs` |
| 指标 | 容器平台集成 Prometheus；平台自身指标未接入 |
| 链路追踪 | 未集成（无 OpenTelemetry/Jaeger 代码引用） |
| 告警 | 未发现平台级告警配置 |
| 审计 | 登录/操作日志 |

## 5. 兼容性

| 项目 | 现状 |
|------|------|
| 浏览器 | 现代浏览器（React 19 + Vite 6） |
| 数据库 | 仅 SQLite；未支持 MySQL/PostgreSQL |
| K8s | k3s 1.31、Docker Desktop Kubernetes |
| Java | 21 |
| Node | 以 `frontend/package.json` engines 为准（待补） |
| 操作系统 | macOS / Windows 部署方式不同 |

## 6. 可维护性

| 项目 | 现状 |
|------|------|
| 后端测试 | api-test 较完整；其他模块覆盖待补 |
| 前端测试 | Vitest 就近测试（pipeline/container 等） |
| 文档 | 本产品文档体系 + 模块设计文档 |
| 代码规范 | 前端见 `docs/frontend/ai-collaboration-frontend.md` |
| 数据库迁移 | embedded/external 双模式，存在漂移风险 |

## 7. 待采集清单

| 编号 | 指标 | 采集方式 | 负责人 | 截止 |
|------|------|----------|--------|------|
| NFR-01 | 核心接口 P95 延迟 | 压测（JMeter/k6） | 待定 | 待定 |
| NFR-02 | 1 万事项下项目/事项列表响应 | 压测 | 待定 | 待定 |
| NFR-03 | 100 并发流水线触发的调度表现 | 压测 | 待定 | 待定 |
| NFR-04 | SQLite 并发写入错误率 | 压测 + WAL 评估 | 待定 | 待定 |
| NFR-05 | 大文件（20MB）生成/解析耗时与内存 | 基准测试 | 待定 | 待定 |
| NFR-06 | 前端首屏 LCP/包体积 | Lighthouse + 构建分析 | 待定 | 待定 |
| NFR-07 | 备份恢复演练 | 手工演练 | 待定 | 待定 |
| NFR-08 | 安全扫描基线 | Semgrep/Dependency-Track | 待定 | 待定 |
