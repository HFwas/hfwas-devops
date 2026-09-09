# 后端启动失败与 PP-OCRv6 初始化耗时排查报告

> 报告时间：2026-09-03
> 环境：macOS / Java 21.0.7 / Spring Boot 3.4.5 / PaddleOCR 3.7.0 / bash 5.3.3
> 相关日志：`logs/backend.log`、`logs/devops.log`、`logs/devops-error.log`

## 概述

`./scripts/start-dev.sh --build --install --force` 启动时出现两类问题，彼此叠加：

1. **启动脚本误杀后端**：健康检查等满 120 秒后，`die` 拼错误信息时踩中 bash 5.3 全角括号变量解析 bug，EXIT trap 把刚起来的进程杀掉。
2. **PP-OCRv6 worker 初始化 43139ms**：几乎全部耗在 `from paddleocr import PaddleOCR` 的冷导入，不是模型下载或 ONNX 推理。

修复脚本超时与变量展开后，同机热启动 OCR 初始化稳定在 **约 2.1 秒**，Spring Boot 约 **5.9 秒** 就绪，`GET /health/check` 返回 200。

## 现象

终端：

```
[09:25:09] 后台启动后端 ...
/Users/hfwas/IdeaProjects/hfwas-devops/scripts/common.sh: line 67: url: unbound variable
[09:27:12] 停止开发服务 ...
[09:27:12] 停止 后端 (pid 28410) ...
```

`logs/devops.log` 同一时刻：

```
09:27:15.113  Started DevopsApplication in 46.704 seconds
09:27:15.114  Commencing graceful shutdown
09:27:15.130  Closing PP-OCRv6 Python worker
```

后端其实已经 `Started`，1ms 后进入 graceful shutdown。OCR 日志：

```
09:26:30.968  Initializing PP-OCRv6 Python worker...
09:27:13.664  Creating model: PP-OCRv6_medium_det
09:27:14.107  initialized in 43139ms
```

`Creating model` 之前约 42.7 秒没有任何 Python 日志。

## 问题一：启动脚本超时并误杀进程

### 根因

`wait_for_backend` 默认等 120 秒，轮询 `http://localhost:8089/health/check`。

09:25:09 拉起 `start-backend.sh --build` 后的实际耗时：

| 阶段 | 时间 | 大约耗时 |
|------|------|----------|
| `mvn install` | 09:25:10 – 09:25:25 | 15s |
| `pip install` 文档生成 + OCR | 09:25:25 – 09:26:23 | 58s |
| Spring + OCR 冷启动 | 09:26:28 – 09:27:15 | 47s |
| **合计** | | **约 126s** |

健康检查只在应用 `Started` 之后才通。120 秒上限落在 09:27:09 左右，应用 09:27:15 才就绪，脚本先超时。

超时后执行：

```bash
die "$name 启动超时（$url），请查看 $RUN_DIR/backend.log"
```

bash 5.3 会把 `$url）`（全角右括号 `U+FF09`）解析成变量名 `url）`，在 `set -u` 下报 `url: unbound variable`。已复现：

```bash
bash -c 'set -u; url="http://x"; echo "bare $url），"'
# bash: line 1: url）: unbound variable
```

`start-dev.sh` 的 `trap cleanup EXIT` 随后调用 `stop-dev.sh`，杀掉 pid 28410。这就是日志里 `Started` 紧接着 graceful shutdown 的原因。

### 修复

- `scripts/common.sh`：超时默认值 120 → 240；`$url` / `$port` 改为 `${url}` / `${port}`。
- `scripts/start-dev.sh`：`wait_for_backend` 等待 240 秒。
- `scripts/start-backend.sh`、`scripts/start-frontend.sh`：帮助文案里同样的 `$BACKEND_PORT）` 一并改成 `${BACKEND_PORT}`。

## 问题二：PP-OCRv6 初始化 43139ms

### 分段结论

热启动复测（同一 venv，pip 已完成）：

| 阶段 | 冷启动（09:26，pip 刚结束） | 热启动（本机复测） |
|------|---------------------------|-------------------|
| `from paddleocr import PaddleOCR` | ~42.7s（无日志） | ~1.4s |
| `PaddleOCR()` 创建 det/rec | ~0.4s | ~0.4s |
| **合计** | **43139ms** | **~1.8s** |

ONNX 会话创建只有约 400ms。43 秒不是模型文件缺失，也不是 HuggingFace 下载：本地 det/rec 目录完整，`HF_HUB_OFFLINE=1`、`PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True` 已设置。`~/.paddlex/official_models` 为空，没有新下载。

### 为什么 import 会慢

`from paddleocr import PaddleOCR` 会执行 `paddlex/__init__.py`，进而：

- 导入全部 inference pipeline（VL、表格、语音、3D、视频等），不是只加载 OCR。
- `official_models.py` 顶层导入 `modelscope`、`huggingface_hub`、`aistudio_sdk`。
- 若干模块在依赖存在时 `import matplotlib`。空 `MPLCONFIGDIR` 下重建字体缓存测得 **12.7s**。

09:26 那次紧挨着 `pip install paddleocr`（09:26:17–09:26:22 写入 modelscope 2854 + paddlex 975 个 `.py`），09:26:30 第一次 import，冷页缓存 + 全量 PaddleX 导入，叠到 40s+ 与日志吻合。

PaddleX 对模型源的连通性探测超时只有 1s/源，且已被 `PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK` 跳过，不是 43 秒的主因。

### 修复

- `ocr_worker.py`：打出 `import paddleocr: Xms` / `PaddleOCR() ctor: Xms`；固定 `MPLBACKEND=Agg`、`PADDLE_PDX_EAGER_INIT=0`。
- `OcrService`：向 worker 进程传入同样的离线 / Agg 环境变量。
- `scripts/common.sh`：`pip install` 之后预热一次 `from paddleocr import PaddleOCR`，把冷导入留在脚本阶段，避免堵 Spring `@PostConstruct`。

## 验证（09:38 重启）

`logs/backend.log`：

```
09:38:27.750  Initializing PP-OCRv6 Python worker
09:38:29.457  import paddleocr: 1679ms
09:38:29.913  PaddleOCR() ctor: 456ms
09:38:29.934  initialized in 2184ms
09:38:30.935  Started DevopsApplication in 5.877 seconds
09:38:31.610  GET /health/check
09:38:31.635  Request end: GET /health/check
```

终端：`[09:38:31] 后端 已就绪 (HTTP 200)`。

| 启动 | OCR 初始化 | 应用结果 |
|------|------------|----------|
| 09:26（pip 后首次） | 43139ms | 脚本超时，进程被杀 |
| 09:30 | 2146ms | 5.7s 就绪 |
| 09:38 | 2184ms（import 1679 + ctor 456） | 5.9s 就绪，健康检查通过 |

## 三次启动仍可见的告警（与本次故障无关）

- `logback-core` 1.5.18 与 `logback-classic` 1.5.16 版本不一致。
- 缺少 `io.netty:netty-resolver-dns-native-macos`（Redisson 回退系统 DNS）。
- Redisson `DNSMonitor` 提示 PRO 才能用全部解析地址。可后续单独处理，不影响本次启动。

## 结论

1. **启动失败**是脚本 120s 超时 + bash 5.3 `$url）` 未绑定变量，EXIT trap 杀掉已就绪进程，不是 Spring / 数据库 / Redis 挂掉。
2. **OCR 43 秒**是 `paddleocr` 冷导入整棵 PaddleX，不是 ONNX 加载。热启动约 2.1 秒，其中约 1.7 秒是 import 固定开销。
3. 当前开发启动路径已恢复：后端约 6 秒就绪，健康检查 200。

若要再压 import 的 1.7 秒，需要绕开 PaddleX 全量导入，改为只加载 PP-OCRv6 的 ONNX Runtime 路径。

## 涉及文件

| 文件 | 变更 |
|------|------|
| `scripts/common.sh` | 等待 240s；`${url}` / `${port}`；pip 后预热 import |
| `scripts/start-dev.sh` | `wait_for_backend` 240s |
| `scripts/start-backend.sh` | 帮助文案 `${BACKEND_PORT}` |
| `scripts/start-frontend.sh` | 帮助文案 `${FRONTEND_PORT}` / `${BACKEND_PORT}` |
| `backend/file-parser/src/main/resources/ocr/ocr_worker.py` | 分段计时；`MPLBACKEND=Agg` 等环境变量 |
| `backend/file-parser/.../ocr/OcrService.java` | 向 worker 传入离线 / Agg 环境变量 |
