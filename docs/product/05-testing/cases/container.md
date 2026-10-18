# 容器平台测试用例

> 日期：2026-10-17
> 版本：v0.1
> 状态：骨架（核心场景）
> 数据来源：`02-features/modules/container.md`、`backend/container-core/`、`frontend/src/modules/container/`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：14 条核心用例 |

---

| 编号 | 场景 | 前置条件 | 步骤 | 预期结果 | 优先级 | 自动化 |
|------|------|----------|------|----------|--------|--------|
| CON-001 | 纳管集群 | 有可用 kubeconfig | 新建集群 → 填信息 → 保存 → 连接测试 | 保存成功；连接测试通过 | P0 | 待补 |
| CON-002 | 无效 kubeconfig | — | 使用错误 kubeconfig 连接测试 | 失败并返回明确错误 | P1 | 待补 |
| CON-003 | 集群列表/统计 | 已纳管集群 | 打开集群列表与详情 | 统计与组件状态正确 | P1 | 待补 |
| CON-004 | 命名空间与 Pod 列表 | 集群可达 | 选择命名空间查看 Pod | 列表与 `kubectl get pods` 一致 | P0 | 待补 |
| CON-005 | Pod 详情/YAML/日志 | 存在 Pod | 打开详情、YAML、日志 | 内容正确；日志可流式查看 | P0 | 待补 |
| CON-006 | Pod 终端 | 存在运行中 Pod | 打开终端 → 执行 `ls` | 命令输出正确；断开后可重连 | P1 | 待补 |
| CON-007 | Pod 文件上传/下载 | 存在运行中 Pod | 上传文件 → 下载该文件 | 内容一致 | P2 | 待补 |
| CON-008 | Deployment 扩缩容 | 存在 Deployment | 修改副本数 | 副本数生效，Pod 数量变化 | P0 | 待补 |
| CON-009 | Deployment 重启 | 存在 Deployment | 触发重启 | 滚动重启，状态恢复 | P1 | 待补 |
| CON-010 | Deployment 环境变量/卷 | 存在 Deployment | 修改 env/volumes → 保存 | 变更生效（Pod 重建或更新） | P1 | 待补 |
| CON-011 | YAML 编辑 | 存在 ConfigMap/Deployment | 修改 YAML 保存 | 变更生效；非法 YAML 被拒绝 | P1 | 待补 |
| CON-012 | Helm 安装/升级/回滚 | Helm 仓库与 chart 可用 | dry-run → 安装 → 升级 → 回滚 | 每步结果与 Helm CLI 一致 | P0 | 待补 |
| CON-013 | 镜像仓库浏览 | Harbor 已配置 | 查看 project/repo/artifact | 数据与 Harbor 一致 | P1 | 待补 |
| CON-014 | 漏洞扫描结果 | 镜像已有扫描报告 | 查看 scan/vulnerabilities | 展示漏洞列表与严重级别 | P1 | 待补 |
| CON-015 | 跨租户隔离 | 两租户各有集群 | 用租户 A 访问租户 B 集群 | 拒绝/不可见 | P0 | 待补 |

## 待补

- 监控指标（节点/Pod/JVM）数值与 Prometheus 查询结果一致性。
- 事件流与 watch WebSocket 的实时性和重连。
- Secret 列表是否脱敏。
- 16 类占位资源上线后的用例。
