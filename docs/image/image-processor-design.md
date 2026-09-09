# 图片处理模块 — 技术设计方案

> 版本：v1.1  
> 日期：2026-09-07  
> 状态：待评审  
> 相关：`docs/file-parser-design.md`（文件解析 / OCR，职责不同，勿合并）  
> 变更说明：v1.0 → v1.1 本地 Docker 作为验收环境，运行时镜像必须预装 ImageMagick、libheif、Perl、ExifTool；不再把原生工具推迟到 P1/P2 才进容器

---

## 1. 背景与目标

### 1.1 需求概述

新增产品级模块「图片处理」，在顶栏产品目录「效率工具」中增加入口。用户上传本地图片后，可以：

- 在前端画布上预览、裁剪、旋转、翻转
- 查看结构化元数据（尺寸、拍摄信息、GPS、方向等）
- 转换格式并下载（可选去除位置信息）

本模块处理的是**像素与元数据**，不是抽文本。OCR、文档解析继续由「文件解析」负责。

### 1.2 与现有产品的边界

| | 文件解析 | 文档生成 | **图片处理（本模块）** |
|---|---|---|---|
| 用户意图 | 这文件里写了什么 | 造一份样例文件 | 看清这张图、改它、换格式带走 |
| 主产物 | 文本 / OCR | 下载的文档 | 处理后的图 + 元数据 |
| 路由 | `/file-parser` | `/docgen` | `/image` |
| 后端模块 | `file-parser` | Python docgen | **`image-core`（新建）** |

OCR 预处理（灰度、最长边 1200px）留在 `file-parser` 的 `OcrPreprocessor`，不与本模块共用一条处理管线。本模块可以在后续用会话产物再交给文件解析（「处理后再识别」），第一期不做这条深链。

### 1.3 支持格式（分期）

能力分期指**产品功能**，不指「Docker 里有没有原生工具」。本地用 `docker compose` 起 backend 做验收时，镜像里 **P0 就必须带齐** ImageMagick、libheif、Perl、ExifTool（见 §8）。

| 阶段 | 输入 | 输出 | 引擎 |
|------|------|------|------|
| P0 | JPEG / PNG / WebP / GIF / BMP / **HEIC** / TIFF | JPEG / PNG / WebP | Docker：Magick + ExifTool；单测：ImageIO + `metadata-extractor` 兜底 |
| P1 | 同上，含 CMYK / 服务端预览图打磨 | 同上 | 默认走 Magick；HEIC 预览图、Orientation 写入像素 |
| P2 | 同上 | + TIFF 导出、批量 | ExifTool `rawTags`、队列 |

浏览器 Canvas 可直接导出 JPEG / PNG / WebP；HEIC、TIFF、CMYK、ICC 必须走服务端（Docker 内 Magick）。

### 1.4 核心指标

| 指标 | 目标 |
|------|------|
| 本地预览出现时间 | 选择文件后立即（`URL.createObjectURL`，不等上传） |
| 元数据返回 | 常规 JPEG/PNG < 1s |
| P0 格式转换 | 10MB 内 JPEG/PNG/WebP < 3s |
| 单文件大小 | ≤ 50MB（可配置，与 file-parser 对齐） |
| 会话 TTL | 30 分钟，到期删除临时文件 |
| HEIC 预览 | 上传后出 JPEG/WebP 预览，画布不空白（Docker 验收必测） |

---

## 2. 技术选型

业界把「改像素」和「读标签」分成两套工具，本模块沿用该分工。

### 2.1 工具角色

| 工具 | 角色 | 本模块用法 |
|------|------|------------|
| 浏览器 Canvas | 即时几何操作 | 预览、裁剪、旋转、翻转；导出轻量 JPEG/PNG/WebP |
| Java ImageIO + TwelveMonkeys | JVM 内转码兜底 | 无 Magick 时的 JPEG/PNG/WebP；CI 单测不启容器 |
| `metadata-extractor`（drewnoakes） | 纯 Java 读 EXIF/IPTC/XMP | Magick/ExifTool 不可用时的浅层元数据 |
| ImageMagick + libheif | 改像素（HEIC/TIFF/WebP/ICC） | **Docker 验收主路径**：解码、转换、预览图 |
| ExifTool（依赖 Perl） | 元数据金标准 | **Docker 验收主路径**：拍摄信息、GPS、厂商字段、strip 校验 |
| Apache Tika | MIME / 浅层 Identify | 上传时同步探测，与 file-parser 已有能力一致 |
| libvips / Sharp | 高并发流式缩略图 | **不采用**。本模块是控制台工具，QPS 低，优先格式覆盖与运维简单 |

**原则：ExifTool 读标签，ImageMagick 改像素；前端只做人眼能点的那一层。**

### 2.2 运行时分层：单测兜底 vs Docker 验收

宿主机 `scripts/start-backend.sh`（`mvn spring-boot:run`）**不保证**已装 Magick/ExifTool。  
**本地验收路径是 `docker compose` 起的 backend 容器**，与 `backend/Dockerfile` 运行时镜像一致。该镜像必须能执行：

| 二进制 | 作用 | 缺了会怎样 |
|--------|------|------------|
| `magick` / `identify` | 转码、HEIC 解码、预览图 | 手机 HEIC、TIFF、CMYK 无法验收 |
| `libheif`（Magick HEIC 委托） | HEIC/HEIF 编解码 | `magick -list format` 无 HEIC |
| `perl` | ExifTool 运行时 | `exiftool` 无法启动 |
| `exiftool` | 读/校验元数据、确认 strip GPS | 只能看到 JVM 浅层 EXIF |

Java 库仍保留：单元测试、无原生工具的开发机可以跑通 JPEG/PNG 会话。Docker 内配置为 Magick + ExifTool **优先**，失败再降级并打日志。

进程模型：`ProcessBuilder` 调 CLI；超时、工作目录限制在会话临时目录；并发信号量默认 2（与 OCR `maxConcurrent` 同类）。

### 2.3 后端依赖

**Maven（始终引入）：**

| 依赖 | 用途 | 许可 |
|------|------|------|
| `com.twelvemonkeys.imageio:imageio-jpeg` 等 | ImageIO 兜底（WebP/TIFF） | BSD |
| `com.drewnoakes:metadata-extractor` | 无 ExifTool 时的 EXIF/IPTC/XMP | Apache 2.0 |
| `org.apache.tika:tika-core`（可选，与 file-parser 对齐） | MIME 探测 | Apache 2.0 |
| Spring Web `MultipartFile` | 上传 | 已有 |

Java 调 Magick 用 `ProcessBuilder`（或薄封装 `im4java`），不把 ImageMagick 链进 JVM。

**Docker 运行时系统包（P0 验收必装，见 §8.2）：** ImageMagick 7 + HEIC/JPEG/TIFF/WebP 模块、libheif、Perl、ExifTool。不装 PDF 模块。

### 2.4 前端依赖

现有 Naive UI 承担布局与表单：

- `n-upload` / `n-upload-dragger` — 选文件
- `n-card` / `n-tabs` / `n-descriptions` / `n-collapse` — 工作台与检查器
- `n-slider` / `n-select` / `n-checkbox` — 转换参数
- `n-alert` / `n-spin` / skeleton — 状态

**新增：** `cropperjs` 或 `vue-advanced-cropper`（裁剪框）。不要从零画选区。

浏览器原生即可：`URL.createObjectURL`、`Canvas`、`HTMLCanvasElement.toBlob`（JPEG/PNG/WebP）。HEIC 预览不靠前端解码，走后端预览图。

### 2.5 双引擎分工

```
本地 File ──► 浏览器 Canvas（即时操作）──► 导出轻量格式
                │
                └── 上传一次 ──► 后端会话
                      ├─ Identify（MIME、宽高）
                      ├─ Metadata（Docker：ExifTool；无 CLI：metadata-extractor）
                      ├─ Transform（Docker：ImageMagick；无 CLI：ImageIO）
                      └─ 下载结果 / 替换画布预览
```

规则：

1. 拖进页面立刻出预览，不要先转圈等上传。
2. 元数据、HEIC、TIFF、CMYK、strip 隐私 → 后台。
3. 仅导出 PNG/JPEG/WebP 时，裁剪/旋转在前端做完，再上传最终像素或几何参数，避免来回传原图。
4. HEIC→JPEG 且要色彩管理 → 整段交给 ImageMagick（Docker 验收路径）。
5. 转换是用户点击触发的服务端动作，不要「拖一下质量滑条就打后端」。

---

## 3. 系统架构

### 3.1 模块划分

新建 Maven 模块 `backend/image-core`，**不要**把接口挂在 `/api/file-parser` 下。文件解析是「上传即解析」；图片会话要存活数分钟、可多次转换。

```
backend/
├── pom.xml                                 # 新增 <module>image-core</module>
├── image-core/
│   └── src/main/java/com/hfwas/devops/image/
│       ├── config/
│       │   └── ImageProcessorConfig.java   # 大小限制、会话 TTL、引擎开关
│       ├── controller/
│       │   └── ImageProcessorController.java
│       ├── service/
│       │   ├── ImageSessionService.java    # 会话生命周期
│       │   ├── ImageStorageService.java    # 临时文件
│       │   ├── identify/
│       │   │   └── ImageIdentifyService.java
│       │   ├── metadata/
│       │   │   ├── ImageMetadataService.java      # 门面
│       │   │   └── MetadataExtractorReader.java   # JVM 兜底
│       │   │   └── ExifToolReader.java            # Docker 主路径
│       │   └── transform/
│       │       ├── ImageTransformService.java     # 门面
│       │       ├── ImageIoTransformer.java        # JVM 兜底
│       │       └── ImageMagickTransformer.java    # Docker 主路径
│       └── dto/
│           ├── ImageSessionVO.java
│           ├── ImageMetadataVO.java
│           └── ImageConvertRequest.java
└── server/                                 # 扫描并暴露 REST
```

绿野项目：会话用临时目录即可，P0 **不建表**。P2 若做历史再加 schema，直接按当时模型建，不做旧格式兼容。

### 3.2 会话模型

```
POST   /api/image/sessions                     上传原图 → sessionId + 探测结果 + 预览 URL
GET    /api/image/sessions/{id}                会话摘要
GET    /api/image/sessions/{id}/preview        预览图（原图或服务端转出的 JPEG/WebP）
GET    /api/image/sessions/{id}/metadata       结构化元数据
POST   /api/image/sessions/{id}/convert        转换（格式 / 质量 / strip / 几何）
GET    /api/image/sessions/{id}/result         下载最近一次转换结果
DELETE /api/image/sessions/{id}                主动结束；TTL 到期同样删除
```

上传后同步做 Identify（MIME、宽高、是否需要服务端解码）。Metadata 可同步返回浅层字段，完整读取允许略慢但仍在同一次响应内（P0 文件不大）。Convert 同步返回；若 P1 出现超大 TIFF，再改为 job + 轮询，第一期不引入。

### 3.3 引擎分层（对前端只暴露一种 API）

| 层 | Docker 验收（默认） | 无 CLI 时兜底 | 职责 |
|----|-------------------|---------------|------|
| Identify | Magick `identify`（HEIC/TIFF） | Tika / ImageIO | MIME、宽高、帧数、是否需服务端预览 |
| Metadata | ExifTool | `metadata-extractor` | 只读，输出统一 VO |
| Transform | ImageMagick | TwelveMonkeys + ImageIO | 写像素、可选 strip |
| Storage | 容器内临时目录 | 同左 | 原图、预览、结果；TTL 清理 |

前端不感知当前用的是 ImageIO 还是 Magick。启动时探测 `magick`、`exiftool` 是否在 PATH，写入健康信息（可挂在现有 `/health/check` 的细节或独立 `/api/image/health`）。

### 3.4 元数据 VO（不要直接甩 ExifTool 扁平键）

```json
{
  "pixel": {
    "width": 4032,
    "height": 3024,
    "colorSpace": "sRGB",
    "hasAlpha": false,
    "frames": 1
  },
  "format": {
    "mime": "image/jpeg",
    "ext": "jpg"
  },
  "capture": {
    "make": "Apple",
    "model": "iPhone 15",
    "takenAt": "2026-09-01T12:00:00",
    "orientation": 6
  },
  "gps": {
    "lat": 31.23,
    "lng": 121.47
  },
  "privacy": {
    "hasGps": true,
    "hasFaceRegions": false
  },
  "rawTags": {}
}
```

- 主界面只用结构化字段；`rawTags` 折叠给进阶用户（P2 才填满）。
- Orientation：预览必须按方向摆正；VO 里保留文件中的原始值，并标明「画面已纠正」。
- GPS 用警告色展示；导出默认勾选「去除位置信息」。

### 3.5 转换请求

```json
{
  "targetFormat": "webp",
  "quality": 85,
  "maxSide": null,
  "stripMetadata": true,
  "applyOrientation": true,
  "geometry": {
    "rotate": 90,
    "flipX": false,
    "crop": { "x": 10, "y": 20, "width": 800, "height": 600 }
  }
}
```

`geometry` 与前端画布坐标系一致（以摆正后的像素为准）。若前端已用 Canvas 导出最终图，可改为二次上传 blob，`geometry` 可空。两种方式 API 都支持，P0 优先「原图会话 + geometry」，避免大图在浏览器再编码一次质量损失。

`maxSide` 默认 `null`（不缩放），**不要**复用 OCR 的 1200px 上限。

### 3.6 安全

- 校验魔数，不只信扩展名 / `Content-Type`。
- 文件大小、像素上限可配置（防 decompression bomb）。
- ImageMagick：**必须**随镜像带上 [policy.xml](https://imagemagick.org/script/security-policy.php)，禁用 PDF、HTTP、MVG 等委托，限制宽高与内存；Alpine 侧不安装 `imagemagick-pdf`。
- 预览/下载 URL 绑定会话 id，不可枚举他人文件（后续若多租户，会话目录带 tenantId）。
- strip 默认推荐开启 GPS；不把原图永久落到用户可见存储。

---

## 4. API 草案

前缀：`/api/image`  
响应外壳与现有 `BaseResult<T>` 一致。

### 4.1 创建会话

```
POST /api/image/sessions
Content-Type: multipart/form-data
file: MultipartFile
```

响应 `ImageSessionVO`：

```json
{
  "sessionId": "01J...",
  "fileName": "IMG_0001.heic",
  "fileSize": 2457600,
  "mimeType": "image/heic",
  "width": 4032,
  "height": 3024,
  "needsServerPreview": true,
  "previewUrl": "/api/image/sessions/01J.../preview",
  "expiresAt": "2026-09-07T15:00:00Z"
}
```

### 4.2 元数据

```
GET /api/image/sessions/{id}/metadata
```

响应见 §3.4。会话不存在或过期返回 404。

### 4.3 转换

```
POST /api/image/sessions/{id}/convert
Content-Type: application/json
```

响应：

```json
{
  "sessionId": "01J...",
  "resultFileName": "IMG_0001.webp",
  "resultSize": 412000,
  "mimeType": "image/webp",
  "width": 800,
  "height": 600,
  "downloadUrl": "/api/image/sessions/01J.../result",
  "strippedGps": true
}
```

下载用 `GET .../result`，`Content-Disposition: attachment`。

---

## 5. 前端设计

### 5.1 产品注册

`frontend/src/shared/console/products.ts` 追加：

```typescript
{
  key: 'image',
  name: '图片处理',
  description: '预览、转换格式并查看图片元数据',
  icon: Image,              // @lucide/vue
  path: '/image',
  group: '效率工具',
}
```

`resolveActiveProductKey` 已按 `/${product.key}` 前缀匹配，无需改算法。

本产品是单路由工作台，**不占用** `CONSOLE_TABS`（与 file-parser、docgen 相同）。不要做成 api-test 那种复杂 Shell。

### 5.2 路由

`frontend/src/router/index.ts` 挂载：

```typescript
import { imageRoutes } from '@/modules/image/router/imageRoutes'
// ...imageRoutes
```

```typescript
// imageRoutes.ts
export const imageRoutes = [
  {
    path: '/image',
    name: 'image',
    component: () => import('@/modules/image/views/ImageWorkbenchView.vue'),
  },
]
```

### 5.3 目录结构

```
frontend/src/modules/image/
├── router/
│   └── imageRoutes.ts
├── views/
│   └── ImageWorkbenchView.vue      # 三栏壳
├── components/
│   ├── ImageDropzone.vue           # 空态拖拽 / 已选缩略图
│   ├── ImageCanvas.vue             # 预览 + cropper
│   ├── ImageToolbar.vue            # 适应窗口、旋转、翻转、裁剪比例、复位
│   ├── MetadataInspector.vue       # 概要 / 元数据
│   └── ConvertPanel.vue            # 目标格式、质量、strip、下载
├── api/
│   └── image.ts
├── types/
│   └── image.ts
└── composables/
    └── useImageSession.ts          # 本地 File、object URL、session、metadata、convert
```

视图不直接堆 `FormData`；会话状态集中在 `useImageSession`。

### 5.4 页面布局（三栏工作台）

file-parser / docgen 是单页表单；图片工具需要同时看见画面和元数据，采用 **单路由 + 三栏**：

```
顶栏产品切换 → 图片处理
┌─────────────┬──────────────────────────┬──────────────────┐
│ 源文件       │ 画布                      │ 检查器            │
│ 拖拽 / 点击  │ 原图 / 处理后 对比         │ 概要 · 元数据     │
│ 文件名 体积  │ 裁剪框、旋转、翻转         │ 转换 · 导出       │
│ MIME        │ 缩放、适应窗口             │                  │
└─────────────┴──────────────────────────┴──────────────────┘
```

#### 左栏：源

- 空态：与 file-parser 同类的 `n-upload-dragger`。  
  `accept`：`.jpg,.jpeg,.png,.webp,.gif,.bmp,.tif,.tiff,.heic,.heif`
- 有文件后：缩略图 + 文件名 + 体积 + MIME；按钮「换一张」（释放 object URL、结束旧会话）。
- 未上传完成时也要用本地 object URL 预览。

#### 中栏：画布

工具条（P0 仅这些）：

- 适应窗口 / 1:1
- 左旋 / 右旋 / 水平翻转
- 裁剪：自由、1:1、4:3、16:9
- 复位
- 转换完成后：**原图 | 处理后** 切换（或左右分屏）

HEIC 等浏览器无法解码时：左下角提示「需服务端解码」，用 `previewUrl` 填画布，禁止对着空白画布操作。

#### 右栏：检查器（`n-tabs`）

**概要**  
宽高、格式、色深、是否透明、方向、是否含 GPS（警告色）。

**元数据**  
分组折叠：拍摄、GPS、版权、其它。提供「复制 JSON」。不要一次铺平全部 raw 标签。

**转换**

- 目标：JPEG / PNG / WebP（P0）；TIFF 放到 P2
- 质量滑条（仅有损格式）
- 最长边（可选，默认不缩放）
- 勾选：去除 GPS/元数据（有 GPS 时默认勾选）、按 Orientation 写入已摆正像素
- 主按钮：**转换并下载**
- 副按钮：转换后替换画布预览

### 5.5 交互状态

不要做成 file-parser 那种整页 `n-spin`。画布在探测/转换期间仍应可看。

| 状态 | 页面表现 |
|------|----------|
| 空 | 左栏拖拽区，中右占位说明 |
| 仅本地预览 | 画布可用；元数据区「上传后读取」 |
| 探测中 | 右栏 skeleton，画布不锁 |
| 已有元数据 | 右栏填满 |
| 转换中 | 导出按钮 loading，画布仍显示原图 |
| 失败 | `n-alert` + 保留原图，可重试 |
| 会话过期 | 提示重新上传，清理本地几何状态 |

### 5.6 交互流程

```
选择 / 拖入文件
  → 立即本地预览（object URL）
  → 后台创建会话（Identify）
  → 拉取 metadata，填充右栏
  → 用户裁剪 / 旋转（仅改本地 geometry）
  → 点击「转换并下载」
  → POST convert（geometry + 格式参数）
  → 浏览器下载 result；可选刷新预览
```

换文件或离开页：`URL.revokeObjectURL`，`DELETE` 会话（失败可忽略，靠 TTL）。

---

## 6. 与 file-parser 的衔接

| 事项 | 约定 |
|------|------|
| OCR 预处理 | 仍只在 `OcrPreprocessor`；本模块不内嵌 RapidOCR |
| 处理后再识别 | P2+ 可用结果文件或临时路径调用 file-parser；第一期不做跳转 |
| 存储 | 各自临时目录；不要复用 file-parser 解析缓存 key |
| 产品入口 | 控制台两个入口，不在解析结果页塞「去转格式」除非后续明确要 |

---

## 7. 分期

### P0 — 工作台 + Docker 原生工具验收（本期落地）

- 控制台产品入口 + 三栏页 + `useImageSession`
- 本地预览 + 裁剪/旋转（Canvas + cropper）
- 后端会话 API；Docker 内 Magick 转码 + ExifTool 元数据；无 CLI 时 JVM 兜底
- `backend/Dockerfile` 运行时安装 ImageMagick（含 HEIC/JPEG/TIFF/WebP）、libheif、Perl、ExifTool，并固化 Magick policy
- 下载；有 GPS 时默认建议 strip
- 无历史表

**成功标准（必须在 `docker compose` 起的 backend 上测）：**

1. 容器内 `magick -version`、`exiftool -ver`、`perl -v` 可用；`magick -list format` 含 HEIC（rw）
2. 拖一张带 GPS 的 iPhone JPEG：立刻预览、能裁、右侧有尺寸与 GPS，转 WebP 并去除位置后可下载
3. 拖一张 HEIC：画布出现服务端预览（非空白），能转到 JPEG/WebP

宿主机 `mvn spring-boot:run` 只要求 JPEG/PNG 路径可用，**不能替代 Docker 验收**。

### P1 — 色彩与预览打磨

- CMYK JPEG、ICC、Orientation 写入已摆正像素
- 预览图质量/尺寸策略
- 子进程超时与并发调优（信号量默认 2）

### P2 — 深度元数据与批量

- ExifTool `rawTags` 与厂商 MakerNotes 完整展示
- 左栏多文件队列
- 历史会话（再加表）
- 超大图 convert 改为异步 job

---

## 8. 部署与配置

### 8.1 配置

`application.yml`（Docker / `prod` 默认打开原生引擎；本机 `dev` 可按探测结果自动开关）：

```yaml
image-processor:
  max-file-size: 50MB
  session-ttl: 30m
  temp-dir: ${java.io.tmpdir}/hfwas-image
  max-pixels: 40000000          # 宽×高上限，防炸弹
  engines:
    magick-enabled: true        # Docker 验收必须为 true
    magick-path: magick
    exiftool-enabled: true      # Docker 验收必须为 true
    exiftool-path: exiftool
    process-timeout: 30s
    max-concurrent: 2
```

上传大小需同时满足 Spring `multipart.max-file-size`。  
`docker-compose.yml` 的 backend 使用 `SPRING_PROFILES_ACTIVE: prod`，与上述默认一致。

### 8.2 本地 Docker 验收（必装四件套）

验收命令：仓库根目录 `docker compose up --build backend`（或全套 compose）。工具装在 **backend 运行时镜像**里，不另起 sidecar。

当前 `backend/Dockerfile` 运行时是 `eclipse-temurin:21-jre-alpine`。Alpine 3.19+ 把 ImageMagick 格式拆成子包，只装 `imagemagick` **没有 HEIC**。运行时阶段应增加：

```dockerfile
# 图片处理：Magick 改像素，ExifTool 读标签（Perl 是 ExifTool 运行时）
# 不装 imagemagick-pdf，降低 Magick 攻击面
RUN apk add --no-cache \
    imagemagick \
    imagemagick-jpeg \
    imagemagick-tiff \
    imagemagick-webp \
    imagemagick-heic \
    libheif \
    perl \
    exiftool \
    && rm -rf /var/cache/apk/*

COPY backend/image-core/src/main/resources/imagemagick/policy.xml \
     /etc/ImageMagick-7/policy.xml
```

| 能力 | Alpine 包 | 镜像内命令 |
|----------------|-----------|------------|
| ImageMagick | `imagemagick` + `imagemagick-{jpeg,tiff,webp,heic}` | `magick`、`identify` |
| libheif | `libheif`（`imagemagick-heic` 会依赖 `libheif.so`，仍显式安装） | 给 Magick HEIC 委托用 |
| Perl | `perl` | `perl`；ExifTool 依赖 |
| ExifTool | `exiftool`（依赖 `perl-image-exiftool`） | `exiftool` |

构建末尾做一次冒烟，失败则镜像不可用：

```dockerfile
RUN magick -version \
    && magick -list format | grep -Ei 'HEIC|HEIF' \
    && exiftool -ver \
    && perl -v >/dev/null
```

`policy.xml` 要点（与官方安全策略对齐）：

- 拒绝 `HTTPS`、`HTTP`、`URL`、`MVG`、`MSL` 等
- 不开放 PDF/PS 读写（也不安装 `imagemagick-pdf`）
- `width` / `height` / `area` / `memory` / `disk` 设上限，与 `max-pixels` 同量级

健康检查：现有 `wget http://localhost:8089/health/check` 保持；另提供 `GET /api/image/health`，返回 `magick` / `exiftool` / `heicDelegate` 是否就绪，compose 手工验收时先看这三项为 true。

### 8.3 宿主机开发（非验收）

`scripts/start-backend.sh` 不强制安装原生工具。若本机要用 HEIC，需自行安装（macOS 可用 Homebrew：`imagemagick`、`libheif`、`exiftool`；ExifTool 自带 Perl）。缺二进制时自动降级 JVM 路径，日志明确 `engines.magick=false`。

---

## 9. 测试要点

| 层级 | 覆盖 |
|------|------|
| 后端单测 | Identify 魔数；JPEG 读 orientation/GPS；convert JPEG→WebP；strip 后 metadata 无 GPS（可用 JVM 兜底，不强制 Magick） |
| 后端单测 | 超大像素拒绝；错误扩展名但实为 JPEG；过期会话 404 |
| Docker 冒烟 | 镜像构建通过 `magick`/`exiftool`/`perl` 版本检查；`magick -list format` 含 HEIC |
| Docker 验收 | HEIC→JPEG；带 GPS 的 JPEG strip 后 ExifTool 无 GPS；Magick policy 拒绝 PDF |
| 前端 | 选文件后未等 API 也能预览；换文件释放 URL；转换中画布不卸载；HEIC 显示服务端预览 |
| 手工 | 含 GPS 的 iPhone JPEG；横向 Orientation=6；透明 PNG→JPEG（底色） |

---

## 10. 开放问题

1. 会话是否要登录用户隔离：控制台已有登录，P0 建议会话 id 不可猜测（ULID）+ 后续绑 `userId`。
2. 前端几何提交：传 crop 矩形 vs 上传 Canvas blob — P0 建议矩形，质量更可控。
3. GIF 动图：P0 只处理第一帧并在概要标明 `frames > 1`，避免误转成静止图还不提示。
4. 是否与 docgen「生成图片」共用模块：否。docgen 是合成样例文件；本模块是处理用户原图。
5. Alpine Magick HEIC：若某版 Temurin Alpine 的 `imagemagick-heic` 不可用或 `magick -list format` 无 HEIC，运行时基础镜像改为 `eclipse-temurin:21-jre`（Debian）并用 `apt` 安装 `imagemagick`、`libheif1`、`perl`、`libimage-exiftool-perl`，前端契约不变。
