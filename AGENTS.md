# Agent instructions

HFWAS DevOps：从 0 到 1 的 DevOps 平台。后端 Spring Boot 3 / Java 21，前端 Vue 3。Cursor 额外规则在 `.cursor/rules/`。

## 优先使用本地文档

回答产品、架构、运维、安装、设计类问题前，**先查 `docs/`**，再看代码或通用知识。

1. 按下面目录定位 1～3 篇相关文档并读完再回答。
2. 用 Grep / Glob 在 `docs/` 搜模块名、组件名、操作名。
3. 引用具体路径。本地文档与通用知识冲突时，**以 `docs/` 为准**。
4. 没有对应文档时，写明「本地 docs 未覆盖」，再根据代码或通用知识回答。

不要把整份 `docs/` 读进上下文。

| 主题 | 目录 |
|------|------|
| Kong / 代理 / 本机安装 | `docs/devops/` |
| 流水线 / Tekton / CI | `docs/pipeline/` |
| 容器 / Harbor / 监控 | `docs/container-platform/` |
| PM 设计与 API | `docs/pm/` |
| PM 工作流演进 | `docs/evolution/` |
| 后端架构 / API / 库表 | `docs/backend/` |
| 前端 | `docs/frontend/` |
| 文件解析 / OCR | `docs/file-parser/` |
| 文档生成 | `docs/docgen/` |
| 图片处理 | `docs/image/` |
| API 测试平台 | `docs/api-test/` |
| 测试 / 进度 / 安全 | `docs/general/` |
| 设计稿与实现计划 | `docs/superpowers/` |

## 绿野项目：不做存量兼容

本地/开发库可随时重建，**不要**为已有数据库内容做向后兼容。

- 改表结构、列名、JSON 形状、API 字段时：**直接改** schema / entity / VO / 前端类型。
- **不要**写双写旧列、legacy 字段合成、`if (oldFormat)`、仅为迁旧数据的 runtime migration（除非用户明确要求）。
- 需要清库或重跑 schema / 启动脚本时，优先重建，不要打补丁兼容脏数据。
- 用户明确要求兼容某版本或线上迁移时，写一次性脚本，并在文档标明可删除期限。

## 本地部署（k3s）

本地集群跑在 Docker 里的 k3s。查集群、装 Helm、看 Pod、导入镜像时：

1. **先定位容器**，不要写死名字：
   ```bash
   docker ps | grep k3s
   ```
   本仓库 compose 一般是 `devops-k3s`，以 `docker ps` 实际输出为准。
2. **进入该容器再操作**（`docker exec` / `docker exec -it <容器> sh`）。
3. **禁止**使用宿主机的 `kubectl`、`helm`、`~/.kube/config`、`KUBECONFIG`，以及仓库里导出到宿主机的 kubeconfig（如 `data/pipeline/kubeconfig.yaml`）去打集群。
4. 容器内用 `kubectl` / `helm` / `ctr`。rancher/k3s 里 `/bin/kubectl` 是 k3s 的 symlink，**不要**写 `k3s kubectl`（会变成 `kubectl kubectl`）。

```bash
# 正确
docker exec <k3s容器> kubectl get ns
docker exec <k3s容器> kubectl -n harbor get pods
docker exec -i <k3s容器> ctr -n k8s.io images import -

# 错误：本机 kubectl / 本机 kubeconfig
kubectl get pods
kubectl --kubeconfig ~/.kube/config get pods
```

## 外网与本地代理

访问外网（拉镜像、curl、npm、git、helm repo、安装脚本等）若超时、TLS 失败或被墙，**优先走本机代理** `127.0.0.1:7890`（Clash 等 HTTP 代理），不要先改镜像源或放弃。

```bash
export HTTP_PROXY=http://127.0.0.1:7890
export HTTPS_PROXY=http://127.0.0.1:7890
export ALL_PROXY=http://127.0.0.1:7890
export http_proxy=http://127.0.0.1:7890
export https_proxy=http://127.0.0.1:7890

curl -fsSL -x http://127.0.0.1:7890 https://example.com
```

Docker 容器访问宿主机代理用 `host.docker.internal:7890`，不要把 `127.0.0.1:7890` 写进容器环境（那是容器自己）。本机访问 Harbor / k3s NodePort 等内网地址时不要带代理。

## 前端

Vue SFC 里同一表达式不要混用 `??` 与 `||`，除非加括号（否则 `@vue/compiler-sfc` 编译失败）。
