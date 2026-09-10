# Pod 控制台上传/下载功能 — 设计方案

> 日期：2026-09-10  
> 状态：待实施  
> 版本：v0.1  
> 关联：[container-platform-design.md](./container-platform-design.md)、[container-platform-implementation-plan.md](./container-platform-implementation-plan.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-09-10 | 初版：上传/下载 REST 端点 + PodShellTerminal 增强 |

---

## 目录

1. [背景与目标](#1-背景与目标)
2. [现有架构](#2-现有架构)
3. [设计方案](#3-设计方案)
4. [接口定义](#4-接口定义)
5. [前端交互](#5-前端交互)
6. [边界情况与错误处理](#6-边界情况与错误处理)
7. [验证方案](#7-验证方案)

---

## 1. 背景与目标

### 1.1 需求

当前 Pod 详情页已实现 WebSocket 交互式控制台（`PodShellTerminal`）和日志实时查看（`PodLogStream`），支持用户通过 xterm.js 终端在 Pod 容器内执行命令。然而缺乏文件传输能力——用户在排查问题时经常需要向容器上传脚本/工具或下载日志/诊断文件，现有实现只能通过 `kubectl cp` 在本地命令行完成，体验割裂。

### 1.2 目标

在 Pod 控制台侧增加「上传文件」和「下载文件」功能，让用户直接在浏览器中完成容器文件传输，无需离开平台使用 kubectl。

### 1.3 约束

- 上传文件大小限制为 **100 MB**（Spring Boot `spring.servlet.multipart.max-file-size`）
- 传输走 REST 接口，不占用 WebSocket 终端通道
- 兼容 alpine 等不含 `tar` 命令的极简容器镜像

## 2. 现有架构

### 2.1 前端相关文件

| 文件 | 职责 |
|------|------|
| `frontend/src/modules/container/components/PodShellTerminal.vue` | xterm WebSocket 终端组件 |
| `frontend/src/modules/container/views/PodDetailView.vue` | Pod 详情页，包含「控制台」tab |
| `frontend/src/modules/container/api/pod.ts` | Pod REST API（list/get/yaml/logs/delete） |
| `frontend/src/shared/api/request.ts` | 通用 HTTP 工具（`postFormData` 上传、`getBlob` 下载） |

### 2.2 后端相关文件

| 文件 | 职责 |
|------|------|
| `backend/container-core/src/main/java/.../ws/PodShellWebSocketHandler.java` | WebSocket Shell 处理 |
| `backend/container-core/src/main/java/.../ws/ContainerWsConfig.java` | WebSocket 路由注册 |
| `backend/container-core/src/main/java/.../ws/ContainerWsAuthInterceptor.java` | JWT WebSocket 认证 |
| `backend/container-core/src/main/java/.../controller/PodController.java` | Pod CRUD REST 控制器 |
| `backend/container-core/src/main/java/.../service/ResourceService.java` | 底层 K8s 资源操作（Fabric8 7.8.0） |
| `backend/container-core/src/main/java/.../service/SecurityHelper.java` | 当前租户/用户上下文 |

### 2.3 核心调用模式

```
SecurityHelper.currentTenantId()
 → ClusterService.getById(clusterId, tenantId)
 → ClusterKubernetesClientFactory.getClient(cluster)
 → KubernetesClient (Fabric8 7.8.0)
```

### 2.4 WebSocket 终端连接流程

前端通过 `ws://host/api/ws/container/shell/{clusterId}/{namespace}/{podName}?container=xxx` 建立 WebSocket 连接，经过 JWT 鉴权后建立 Fabric8 ExecWatch，实现 TTY 交互。

## 3. 设计方案

### 3.1 技术选型

文件传输走 **REST 接口**（非 WebSocket），原因：
- 上传/下载是一次性请求/响应模式，不适合 WebSocket 双工流
- REST 天然支持 multipart 上传和文件流下载
- 可复用现有 `postFormData()` 和 `getBlob()` 前端工具

K8s 侧使用 **Fabric8 kubernetes-client 7.8.0** 的 exec 机制实现文件传输，底层与 `kubectl cp` 使用相同的 tar 流协议。

### 3.2 后端架构

新增两个文件：

| 文件 | 类型 | 职责 |
|------|------|------|
| `PodFileService.java` | 服务层 | 上传/下载的 K8s exec 操作逻辑 |
| `PodFileController.java` | 控制器 | REST 端点暴露 |

#### 上传流程

```
用户选择文件 → POST multipart/form-data → PodFileController
 → PodFileService.uploadFile()
 → 将文件打包为 tar 流（commons-compress TarArchiveOutputStream）
 → 主方案：exec "tar xf - -C {destDir}" 通过 stdin 发往容器
   → 成功 → 返回
 → 回退方案：exec "mkdir -p {dir} && cat > {path}"（alpine 等无 tar 镜像）
   → 成功 → 返回
 → 抛出异常
```

#### 下载流程

```
用户输入容器内路径 → GET /download → PodFileController
 → PodFileService.downloadFile()
 → 主方案：Fabric8 file(path).copy() API（返回 tar 流）
   → TarArchiveInputStream 解出文件内容
   → 成功 → 流式返回
 → 回退方案：exec "cat {path}" 通过 stdout 读取
   → PipedInputStream 流式返回
```

### 3.3 前端架构

在 `PodShellTerminal.vue` 中新增两个交互操作：

```
终端已连接时显示：
 [上传文件] [下载文件]   断开  重连  container-name
```

- **上传**：文件选择器 → 目标路径对话框 → POST 上传 → 完成通知
- **下载**：文件路径输入对话框 → 触发下载 Blob → 浏览器保存

### 3.4 安全

- 复用现有 JWT 鉴权（由 Spring Security 和 AuthInterceptor 处理）
- 上传/下载接口受 `SecurityHelper.currentTenantId()` 租户隔离保护
- 跨域已在 WebSocket/REST 配置中全局处理

## 4. 接口定义

### 4.1 上传文件

```
POST /api/container/clusters/{clusterId}/namespaces/{namespace}/pods/{name}/upload
Content-Type: multipart/form-data

Request:
  file:      File       (必填) 待上传文件
  container: String    (必填) 目标容器名
  destPath:  String    (可选, 默认 /tmp) 目标目录

Response 200:
  { "code": 0, "msg": "success", "data": null }

Response 400:
  { "code": 400, "msg": "目标路径不存在或容器不存在", "data": null }
```

### 4.2 下载文件

```
GET /api/container/clusters/{clusterId}/namespaces/{namespace}/pods/{name}/download
  ?container=xxx&path=/tmp/myfile.log

Response 200:
  Content-Type: application/octet-stream
  Content-Disposition: attachment; filename*=UTF-8''myfile.log
  Body: <文件二进制内容>

Response 400:
  { "code": 400, "msg": "文件不存在或路径不合法" }
```

## 5. 前端交互

### 5.1 组件变更

`PodShellTerminal.vue` 新增：

| 新增项 | 说明 |
|--------|------|
| `uploading` / `downloading` ref | 加载状态 |
| 上传对话框（NModal） | 文件选择 + 目标路径输入 |
| 下载对话框（NModal） | 容器内文件路径输入 |
| `handleUpload()` | 校验 → API 调用 → 成功提示 |
| `handleDownload()` | 校验 → API 调用 → 浏览器触发下载 |
| 工具栏按钮 | 终端已连接时展示 |

### 5.2 上传交互

1. 用户点击「上传文件」按钮
2. 弹出上传对话框：文件选择 `<input type="file">` + 目标路径输入（默认 `/tmp/`，带输入校验）
3. 用户确认后调用 `podApi.upload()`
4. 上传期间按钮显示 loading 状态
5. 完成：`message.success('文件已上传到 /tmp/filename')`
6. 失败：`message.error('上传失败: 原因')`

### 5.3 下载交互

1. 用户点击「下载文件」按钮
2. 弹出下载对话框：容器内文件路径输入（带路径格式校验）
3. 用户确认后调用 `podApi.download()`
4. 下载期间按钮显示 loading 状态
5. 完成：生成 `<a>` 标签触发浏览器下载 + `message.success('文件已下载')`
6. 失败：`message.error('下载失败: 原因')`

### 5.4 空/边界状态

| 状态 | 表现 |
|------|------|
| 终端未连接 | 上传/下载按钮置灰，hover 提示「请先连接容器」 |
| 上传中 | 按钮 loading，防止重复提交 |
| 下载中 | 按钮 loading |
| 容器选择为空 | 上传/下载按钮置灰 |
| 大文件上传 | 按钮显示「上传中 (xx%)」— 首期使用 Spinning，二期可加进度条 |

## 6. 边界情况与错误处理

| 场景 | 处理方式 |
|------|----------|
| 容器内目标目录不存在 | 返回 400「目标路径不存在」 |
| 文件 > 100 MB | Spring Boot `max-file-size` 拒绝，前端提示 |
| 容器内无 `tar` 命令 | 使用 `cat` + base64 编码回退方案 |
| 容器内无 `cat` 命令 | 使用 Fabric8 `file().copy()` 直接读取 |
| 下载路径是目录而非文件 | 返回 400「路径为目录，请指定文件路径」 |
| 文件不存在 | 返回 404「文件不存在」 |
| 网络中断 | 前端显示失败提示，支持重试 |
| 权限不足 | 后端 403，前端显示错误提示 |
| 中文文件名 | `URLEncoder.encode` + `filename*=UTF-8''` |

## 7. 验证方案

### 7.1 上传验证

1. 进入 Pod 控制台，连接一个容器
2. 点击「上传文件」，选择本地文件（如 `test.sh`），目标路径 `/tmp/`
3. 上传成功后，在终端执行 `ls -la /tmp/test.sh` 确认文件存在
4. 执行 `cat /tmp/test.sh` 确认内容完整

### 7.2 下载验证

1. 在终端执行 `echo "hello world" > /tmp/download-test.txt`
2. 点击「下载文件」，输入路径 `/tmp/download-test.txt`
3. 确认浏览器下载了名为 `download-test.txt` 的文件
4. 打开文件确认内容为 `hello world`

### 7.3 错误场景验证

| 场景 | 预期 |
|------|------|
| 下载不存在的路径 | 错误提示「文件不存在」 |
| 上传到不存在的目录 | 错误提示「目标路径不存在」 |
| 终端未连接时点上传/下载 | 按钮置灰，不可操作 |
| 中文文件名上传后下载 | 文件名正确，内容完整 |

### 7.4 大文件验证

1. 准备 50 MB 测试文件
2. 上传到容器 `/tmp/`
3. 下载回本地
4. `sha256sum` 对比原始文件与下载文件的一致性