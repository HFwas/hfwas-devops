# Kong 统一网关本地开发接入指南

> 本文档记录了在 hfwas-devops 项目中引入 Kong 作为统一入口网关的方案、配置、启动方式及踩坑记录。

---

## 目录

- [1. 背景与目标](#1-背景与目标)
- [2. 架构概览](#2-架构概览)
- [3. 文件清单](#3-文件清单)
- [4. 快速开始](#4-快速开始)
- [5. 路由配置详解](#5-路由配置详解)
- [6. 踩坑记录](#6-踩坑记录)
  - [6.1 Kong 启动失败：插件名错误](#61-kong-启动失败插件名错误)
  - [6.2 前端 502：Vite 只监听 127.0.0.1](#62-前端-502vite-只监听-127001)
  - [6.3 前端 403：Vite Host 校验](#63-前端-403vite-host-校验)
  - [6.4 前端频繁刷新：HMR WebSocket 断开](#64-前端频繁刷新hmr-websocket-断开)
- [7. 常见操作](#7-常见操作)
- [8. 进阶配置](#8-进阶配置)
- [9. 附录](#9-附录)

---

## 1. 背景与目标

### 为什么需要网关

| 问题 | 说明 |
|------|------|
| 入口分散 | 开发时后端 `:8089`、前端 `:5173`，两个端口 |
| 环境差异 | 开发用 Vite proxy，生产用 nginx，行为不一致 |
| 缺少统一管控 | 鉴权、限流、日志等横切关注点散落在各服务中 |

### 目标

- 统一入口 → 只访问 `localhost:8000`
- 贴近生产 → Kong 即生产级网关，本地结构 ≈ 线上
- 低侵入 → 不改动业务代码，不破坏现有工作流

---

## 2. 架构概览

```
                        ┌──────────────┐
                        │   Kong        │
                        │  :8000 (HTTP) │
                        │  :8443 (HTTPS)│
                        └──┬───────┬───┘
                           │       │
                  ┌────────┘       └────────┐
                  ▼                          ▼
          ┌──────────────┐         ┌──────────────────┐
          │  Backend     │         │  Frontend          │
          │  :8089       │         │  :5173 (dev)       │
          │  /api/*      │         │  / (static + SPA)  │
          └──────────────┘         └──────────────────┘
```

| 端口 | 服务 | 说明 |
|------|------|------|
| `8000` | Kong（HTTP） | **统一入口** |
| `8443` | Kong（HTTPS） | 可选，需配置证书 |
| `8001` | Kong Admin API | 管理接口 |
| `8002` | Kong Manager | 管理 UI（仅 DB 模式可用） |
| `8089` | 后端 Spring Boot | 原始 API 服务 |
| `5173` | 前端 Vite Dev | 原始开发服务器 |

### 路由映射

| Kong 路径 | 上游 | 行为 |
|-----------|------|------|
| `/api` | `host.docker.internal:8089` | `strip_path: true` → `/api/health/check` 转发为 `/health/check` |
| `/` | `host.docker.internal:5173` | 直接透传，SPA 路由由前端处理 |

---

## 3. 文件清单

### 新增文件

| 文件 | 用途 |
|------|------|
| `docker-compose.kong.yml` | Kong 容器定义（DB-less 模式） |
| `kong/kong.yml` | Kong 声明式路由配置 |
| `scripts/start-kong.sh` | 一键启动 Kong（独立使用） |
| `scripts/stop-kong.sh` | 停止 Kong（独立使用） |

### 修改文件

| 文件 | 修改内容 |
|------|---------|
| `frontend/vite.config.ts` | 加 `host: '0.0.0.0'`、`allowedHosts: true`、`hmr.clientPort: 5173` |
| `scripts/common.sh` | 加 Kong 变量及 `start_kong()`、`stop_kong()`、`kong_is_running()` 函数 |
| `scripts/start-dev.sh` | 加 `--kong` 参数，集成 Kong 启动 |
| `scripts/stop-dev.sh` | 集成 Kong 停止 |
| `.gitignore` | 加 `kong/logs/` |

---

## 4. 快速开始

### 前提

- Docker Desktop 已启动
- 项目后端和前端已按原有方式启动

### 步骤

```bash
# 1. 启动后端 + 前端 + Kong（一键完成）
cd /path/to/hfwas-devops
scripts/start-dev.sh --build --kong

# 2. 验证
curl -s http://localhost:8000/api/health/check
# → UP

# 3. 浏览器访问
open http://localhost:8000
```

### 停止

```bash
scripts/stop-dev.sh
# 会自动停止 Kong + 后端 + 前端
```

### 查看日志

```bash
# Kong 日志
docker compose -f docker-compose.kong.yml logs -f kong

# 后端日志
tail -f logs/backend.log
```

---

## 5. 路由配置详解

### 后端路由（`kong.yml`）

```yaml
services:
  - name: backend-service
    url: http://host.docker.internal:8089
    routes:
      - name: backend-api
        paths:
          - /api
        strip_path: true
```

关键点：

- `strip_path: true`：Kong 去掉 `/api` 前缀再转发，后端无需感知网关
- 请求 `/api/health/check` → Kong 转发到后端 `/health/check`
- `host.docker.internal`：Docker Desktop 提供的宿主机 DNS 名

### 前端路由

```yaml
  - name: frontend-service
    url: http://host.docker.internal:5173
    routes:
      - name: frontend-spa
        paths:
          - /
        strip_path: false
```

关键点：

- `strip_path: false`：前端 SPA 需要知道原始路径
- `write_timeout: 30000` / `read_timeout: 30000`：支持 WebSocket 长连接

### 插件

```yaml
plugins:
  - name: cors          # 跨域（开发环境宽松）
  - name: file-log      # 访问日志
  - name: rate-limiting # 速率限制（开发环境较宽松）
  - name: request-size-limiting  # 请求大小限制
```

---

## 6. 踩坑记录

### 6.1 Kong 启动失败：插件名错误

**现象**：

```
init_by_lua error: plugin 'request-size-limiter' not enabled
```

**原因**：`kong.yml` 中写错了插件名：

| ❌ 错误 | ✅ 正确 |
|---------|---------|
| `request-size-limiter` | `request-size-limiting` |

`request-size-limiter` 不是 Kong 内置插件，Kong 启动时解析声明式配置失败。

**修复**：将插件名改为 `request-size-limiting`。

---

### 6.2 前端 502：Vite 只监听 127.0.0.1

**现象**：

```
curl http://localhost:8000/ → 502
Kong 日志: connect() failed (111: Connection refused)
```

**原因**：Vite 默认监听 `127.0.0.1`（localhost），Kong 在 Docker 容器内通过 `host.docker.internal` 访问宿主机时，目标 IP 是 Docker 桥接网卡地址（如 `192.168.5.2`），Vite 没有监听该地址，连接被拒绝。

**修复**：`vite.config.ts` 加 `host: '0.0.0.0'`

```diff
 server: {
+    host: '0.0.0.0',
     port: 5173,
```

---

### 6.3 前端 403：Vite Host 校验

**现象**：

```
curl http://localhost:8000/ → 403 Forbidden
响应体: Blocked request. This host ("host.docker.internal") is not allowed.
```

**原因**：Vite 6 新增了 `allowedHosts` 安全校验，只允许已知 Host 头访问。Kong 转发请求时，`Host` 头被设置为 `host.docker.internal`，Vite 不认识这个 host，拒绝请求。

**修复**：`vite.config.ts` 加 `allowedHosts: true`

```diff
 server: {
     host: '0.0.0.0',
     port: 5173,
+    allowedHosts: true,
```

---

### 6.4 前端频繁刷新：HMR WebSocket 断开

**现象**：浏览器每隔几秒自动刷新页面，无法正常开发。

**原因**：Vite 的 HMR（热更新）依赖 WebSocket 长连接。默认情况下，Vite 注入的 HMR 客户端会连回**页面本身的 host**（即 `localhost:8000`），连接经过 Kong 代理。Kong 的 WebSocket 代理超时或断开时，Vite 的 HMR 客户端 fallback 到全页刷新。

```
浏览器                  Vite HMR 客户端
  │                            │
  │  wss://localhost:8000      │  ← 通过 Kong，连接不稳定
  │       ↓ 超时/断开          │
  │  触发全页 reload (降级)    │
```

**修复**：`vite.config.ts` 加 `hmr.clientPort: 5173`，让 HMR WebSocket 直连 Vite 服务，绕过 Kong。

```diff
 server: {
     host: '0.0.0.0',
     port: 5173,
     allowedHosts: true,
+    hmr: {
+      clientPort: 5173,
+    },
```

```
浏览器                  Vite HMR 客户端
  │                            │
  │  wss://localhost:5173      │  ← 直连 Vite，不走 Kong
  │       ↓ 稳定连接           │
  │  正常 HMR 更新             │
```

---

## 7. 常见操作

### 重启 Kong（修改 `kong.yml` 后）

```bash
docker compose -f docker-compose.kong.yml restart kong
```

### 重载配置（不重启容器）

```bash
docker compose -f docker-compose.kong.yml exec kong kong reload
```

### 停止全部服务

```bash
scripts/stop-dev.sh
# 自动停止: 后端 + 前端 + Kong
```

### 查看路由

```bash
curl -s http://localhost:8001/routes | python3 -m json.tool
```

### 查看服务

```bash
curl -s http://localhost:8001/services | python3 -m json.tool
```

### 查看上游健康状态

```bash
curl -s http://localhost:8001/upstreams/backend-service/health | python3 -m json.tool
```

### 单独重启前端

```bash
lsof -ti:5173 | xargs kill
cd frontend && npm run dev
```

---

## 8. 进阶配置

### 同时使用 Docker 容器（后端/前端也在容器中）

如果后端/前端通过 `docker-compose.yml` 启动，修改 `kong.yml`：

```yaml
services:
  - name: backend-service
    url: http://backend:8089    # 容器名，而非 host.docker.internal
  - name: frontend-service
    url: http://frontend:80      # 前端 nginx 容器
```

并在 `docker-compose.kong.yml` 中加网络配置：

```yaml
networks:
  default:
    name: hfwas-devops_default
    external: true
```

### 启用 HTTPS

Kong 自带 `8443` 端口，在 `kong.yml` 中加证书配置：

```yaml
certificates:
  - cert: /etc/kong/ssl/localhost.crt
    key: /etc/kong/ssl/localhost.key
    snis:
      - localhost
```

### 启用 Key Auth

```yaml
plugins:
  - name: key-auth
    service: backend-service
    config:
      key_names:
        - apikey
```

前端需在请求头中传 `apikey`。

### 切换到 DB 模式（支持 Kong Manager GUI）

DB-less 模式不支持 GUI 管理，如需 Kong Manager（`:8002`），需加 PostgreSQL 容器并切换到 DB 模式：

```yaml
services:
  kong-database:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: kong
      POSTGRES_USER: kong
      POSTGRES_PASSWORD: kong
  kong:
    image: kong:3.9
    environment:
      KONG_DATABASE: postgres
      KONG_PG_HOST: kong-database
```

---

## 9. 附录

### 与现有工作流的关系

```
现有流程: 浏览器 → :5173(Vite proxy) → :8089(后端)
                                  ↓
新流程:   浏览器 → :8000(Kong) ─→ :5173(前端)
                               └→ :8089(后端)
```

- Vite 的 proxy 配置**保留**，直接访问 `:5173` 仍然可用
- Kong 只是增加了一个统一入口，两者互不冲突
- 后端代码**零改动**

### 涉及版本

| 组件 | 版本 |
|------|------|
| Kong | 3.9.3 |
| Vite | 6.x |
| Docker Desktop | 28.4.0 |
| 项目后端 | Spring Boot 3.4 + Java 21 |
| 项目前端 | Vue 3 + TypeScript |