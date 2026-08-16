# 文件解析模块 — 技术设计方案

> 版本：v1.1  
> 日期：2026-08-15  
> 状态：待评审  
> 变更说明：v1.0 → v1.1 将 OCR 引擎从 Tesseract 替换为 RapidOCR，准确率与部署效率显著提升

---

## 1. 背景与目标

### 1.1 需求概述
新增产品级模块「文件解析」，在顶栏产品目录中增加入口。用户上传本地文件，系统解析后返回结构化内容（文本、表格、元数据），支持常见办公和文档格式。

### 1.2 支持格式

| 格式 | 技术方案 | 难点 |
|------|----------|------|
| DOCX | Apache POI + TIKA | 文本、样式、表格 |
| PPTX | Apache POI + TIKA | 幻灯片文本、备注 |
| XLSX | Apache POI | 单元格文本、公式计算结果、sheet 结构 |
| 文本 PDF | Apache PDFBox | 文本提取、保留段落结构 |
| 扫描版 PDF | PDFBox 提取图片 + RapidOCR | 准确性、中文识别、多页处理 |
| 图片（JPG/PNG/BMP） | RapidOCR | 图片预处理、多语言识别 |
| TXT / CSV / MD | 纯文本读取 | 编码检测 |

### 1.3 核心指标

| 指标 | 目标 |
|------|------|
| 文本 PDF 解析速度 | < 1s / 文件 |
| DOCX/PPTX/XLSX 解析速度 | < 2s / 文件 |
| 图片 OCR 速度 | < 5s / 图片（1000px 以内） |
| 扫描 PDF OCR 速度 | < 10s / 10页 |
| 文本提取准确率 | 文本 PDF / DOCX / XLSX > 99% |
| OCR 准确率 | 印刷体中文 > 95%，英文 > 98% |
| 文件大小限制 | 单文件 ≤ 50MB（可配置） |

---

## 2. 技术选型

### 2.1 后端依赖

| 依赖 | 版本 | 用途 | 许可 |
|------|------|------|------|
| `org.apache.tika:tika-core` | 3.1.0 | 统一文件类型检测 + 元数据提取 | Apache 2.0 |
| `org.apache.tika:tika-parsers-standard-package` | 3.1.0 | Tika 标准解析器集合（内含 POI、PDFBox） | Apache 2.0 |
| `org.apache.pdfbox:pdfbox` | 3.0.3 | PDF 文本提取 + 图片提取（扫描 PDF 用） | Apache 2.0 |
| `com.github.RapidOCR:rapidocr-java` | 1.3.0 | RapidOCR Java 封装，基于 ONNX Runtime | Apache 2.0 |
| `com.microsoft.onnxruntime:onnxruntime` | 1.20.0 | ONNX Runtime 推理引擎（CPU） | MIT |
| `org.springframework.boot:spring-boot-starter-web` | 已有 | 文件上传 MultipartFile | Apache 2.0 |

**说明：**
- Tika 3.x 的 `tika-parsers-standard-package` 已包含 POI 和 PDFBox，无需单独引入。
- PDFBox 3.x 单独引入用于扫描 PDF 的场景（提取图片帧）。
- RapidOCR 基于 PaddleOCR 深度学习模型，通过 ONNX Runtime 在 Java 进程内直接推理，**无需安装任何系统级 OCR 引擎**。
- 如需 GPU 加速，可将 `onnxruntime` 替换为 `com.microsoft.onnxruntime:onnxruntime-gpu`，需配合 CUDA 12.x。

### 2.2 为什么选择 RapidOCR 而非 Tesseract

| 对比维度 | Tesseract 5.x | RapidOCR (PaddleOCR) |
|----------|---------------|----------------------|
| 中文准确率 | ~88% | **~96%** |
| 英文准确率 | ~95% | **~98%** |
| 混合中英文 | 较差，需额外配置 | **原生支持** |
| 竖排文字 | 不支持 | **支持** |
| 表格识别 | 不内置 | **内置表格结构识别** |
| 部署方式 | 系统级安装（`apt install`） | **JAR 包内嵌，零系统依赖** |
| Java 集成 | 需 JNI 桥接（Tess4J） | **Java 原生 API，ONNX Runtime 直接调用** |
| Docker 镜像体积 | 需额外安装 Tesseract + 语言包 | **仅增加模型文件 ~30MB** |
| 推理速度（CPU） | 50-200ms/图 | **30-100ms/图** |
| 预处理要求 | 高（二值化、去噪必须） | **低（模型自带鲁棒性）** |
| 模型更新频率 | 低（每年发布） | **高（持续迭代）** |

**结论：RapidOCR 在中文场景准确率领先 8-10 个百分点，且部署更简单，无需系统级依赖。**

### 2.3 系统依赖

**RapidOCR + ONNX Runtime 无需任何系统级依赖**，所有依赖通过 Maven 引入，OCR 模型文件随 JAR 打包或首次启动时自动下载。

```bash
# 无需安装任何系统包
# 模型文件（~30MB）在首次调用时自动下载到 ~/.rapidocr/models/
```

### 2.4 前端依赖

**无需新增依赖** — 使用现有 Naive UI 组件栈：
- `n-upload` / `n-upload-dragger` — 文件上传
- `n-progress` — 上传/解析进度
- `n-tabs` — 结果展示 Tab
- `n-data-table` — 表格结构展示
- `n-collapse` — 折叠式结果展示
- `n-input` — 文本结果展示
- `n-tag` — 格式标签
- `n-button` — 下载/复制操作

---

## 3. 系统架构

### 3.1 模块划分

```
backend/
├── pom.xml                          # 新增 <module>file-parser</module>
├── file-parser/                     # 新增模块
│   ├── pom.xml
│   └── src/main/java/com/hfwas/devops/fileparser/
│       ├── config/
│       │   └── FileParserConfig.java          # Bean 配置 + 限流配置
│       ├── controller/
│       │   └── FileParserController.java      # REST 接口
│       ├── service/
│       │   ├── FileParserService.java         # 编排服务（类型检测 → 分发 → 结果组装）
│       │   ├── FileStorageService.java        # 文件存储（临时 / 持久化）
│       │   ├── parser/
│       │   │   ├── DocumentParser.java         # 接口
│       │   │   ├── TikaDocumentParser.java     # Tika 解析器（DOCX/PPTX/XLSX/文本PDF）
│       │   │   ├── ScannedPdfParser.java       # 扫描PDF解析器（PDFBox + RapidOCR）
│       │   │   ├── ImageOcrParser.java         # 图片OCR解析器（RapidOCR）
│       │   │   └── PlainTextParser.java        # 纯文本解析器（TXT/CSV/MD）
│       │   └── ocr/
│       │       └── OcrService.java             # OCR 预处理 + 识别服务
│       ├── dto/
│       │   ├── FileParseRequest.java           # 请求 DTO
│       │   └── FileParseResultVO.java          # 响应 VO
│       └── entity/
│           └── FileParseHistory.java           # 解析历史记录实体
```

### 3.2 解析流程

```
用户上传文件
  → FileParserController (MultipartFile)
  → FileStorageService (保存到临时目录)
  → Tika 检测文件类型 (MIME)
  → 路由到对应解析器:
      ├── text/plain           → PlainTextParser
      ├── application/vnd.*    → TikaDocumentParser (POI)
      ├── application/pdf
      │   ├── 文本PDF           → TikaDocumentParser (PDFBox)
      │   └── 扫描PDF           → ScannedPdfParser (PDFBox + RapidOCR)
      └── image/*              → ImageOcrParser (RapidOCR)
  → 结果组装 (FileParseResultVO)
  → 清理临时文件
  → 异步保存解析历史
```

### 3.3 扫描 PDF 检测策略

判断 PDF 是否为扫描件：
1. 尝试 `PDFBox` 提取文本，如果提取的文本长度 < 3 字符/页 → 判定为扫描件
2. 或检查 PDF 中是否包含字体信息（无嵌入字体 = 扫描件可能性高）
3. 用户可手动指定 `forceOcr: true` 强制使用 OCR

---

## 4. 接口设计

### 4.1 REST API

```java
POST /api/file-parser/upload
Content-Type: multipart/form-data

请求参数:
  file: MultipartFile          // 待解析文件
  options: String (JSON)       // 可选，解析选项
    {
      "forceOcr": false,       // 强制使用 OCR
      "ocrLang": "chi_sim+eng",// OCR 语言
      "pageStart": 0,          // PDF 起始页
      "pageEnd": -1            // PDF 结束页 (-1=全部)
    }

响应: FileParseResultVO
{
  "success": true,
  "fileName": "report.docx",
  "fileSize": 1024000,
  "mimeType": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  "parseMethod": "tika",       // tika | ocr | plain
  "parseTimeMs": 350,
  "content": {
    "text": "全文文本内容...",       // 提取的纯文本
    "pages": [                     // 分页内容（PDF 专用）
      { "pageNum": 1, "text": "..." },
      { "pageNum": 2, "text": "..." }
    ],
    "tables": [                    // 表格（Excel 专用）
      { "sheetName": "Sheet1", "rows": [["A1","B1"], ["A2","B2"]] }
    ],
    "slides": [                    // 幻灯片（PPTX 专用）
      { "slideNum": 1, "text": "..." }
    ],
    "metadata": {                  // 元数据
      "author": "张三",
      "createdDate": "2026-01-15",
      "pageCount": 10,
      "title": "报告标题",
      "keywords": "关键词1,关键词2"
    }
  },
  "warnings": ["第3页 OCR 识别质量较低"],
  "ocrInfo": {                     // OCR 用信息
    "engine": "rapidocr",
    "lang": "chi_sim+eng",
    "confidence": 0.92,
    "pagesProcessed": 5
  }
}
```

### 4.2 批量解析（可选）

```java
POST /api/file-parser/upload/batch
Content-Type: multipart/form-data

请求参数:
  files: MultipartFile[]       // 多个文件
  options: String (JSON)

响应: FileParseResultVO[]
```

### 4.3 解析历史

```java
GET /api/file-parser/history?page=1&size=20
响应: 分页历史记录

GET /api/file-parser/history/{id}
响应: 单条历史记录详情（含解析结果）
```

---

## 5. 前端设计

### 5.1 产品注册

**`products.ts` 新增一项：**

```typescript
{
  key: 'file-parser',
  name: '文件解析',
  description: '上传并解析常见文档格式',
  icon: FileText,           // 从 @lucide/vue 导入
  path: '/file-parser',
  group: '效率工具',
}
```

### 5.2 路由注册

**`router/index.ts` 新增：**

```typescript
import { fileParserRoutes } from '@/modules/file-parser/router/fileParserRoutes'
// 追加到 routes 数组
...fileParserRoutes,
```

**`fileParserRoutes.ts`：**

```typescript
export const fileParserRoutes = [
  { path: '/file-parser', name: 'file-parser',
    component: () => import('@/modules/file-parser/views/FileParserView.vue') },
]
```

### 5.3 组件结构

```
src/modules/file-parser/
├── router/
│   └── fileParserRoutes.ts
├── views/
│   └── FileParserView.vue           # 主页面（上传区 + 结果区，单页搞定）
├── api/
│   └── fileParser.ts                # API 调用
└── types/
    └── fileParser.ts                # TypeScript 类型定义
```

**设计原则：极致简约，上传即解析。**

### 5.4 页面布局

```
┌─────────────────────────────────────────────────┐
│ 产品目录: 控制台 > 文件解析                       │
├─────────────────────────────────────────────────┤
│                                                 │
│  ┌─────────────────────────────────────────┐    │
│  │  📤 拖拽文件到此处，或点击上传            │    │
│  │  支持 DOCX / PPTX / XLSX / PDF / 图片   │    │
│  │                浏览文件                  │    │
│  └─────────────────────────────────────────┘    │
│                                                 │
│  ┌───────── 解析中 ──────────────────────────┐  │
│  │  ████████████████░░░░  70%                │  │
│  │  OCR 识别中... 第 3/10 页                 │  │
│  └─────────────────────────────────────────┘    │
│                                                 │
│  ┌─────────────────────────────────────────┐    │
│  │ 📄 report.docx  (1.2MB)  ✔ 解析完成      │    │
│  │ 格式: DOCX │ 耗时: 350ms                 │    │
│  │                                           │    │
│  │  ┌──────────────────────────────────┐    │    │
│  │  │ 全文内容                          │    │    │
│  │  │ 项目立项报告...                   │    │    │
│  │  │ ...                              │    │    │
│  │  │ [复制全文]  [下载结果]             │    │    │
│  │  └──────────────────────────────────┘    │    │
│  └─────────────────────────────────────────┘    │
└─────────────────────────────────────────────────┘
```

### 5.5 交互流程

```
用户拖拽/选择文件
  → 显示文件名、大小、格式标签
  → 自动开始解析（无需点击按钮）
  → 显示进度条（OCR 场景显示页数进度）
  → 解析完成，展示结果
  → 可继续拖拽新文件解析
```

**交互细节：**
1. **拖拽上传** — 使用 `n-upload-dragger`，拖拽时高亮，松开即上传
2. **自动解析** — 文件选择后自动调用解析接口，无需额外操作
3. **进度反馈** — 解析中显示进度条，大文件/OCR 场景显示当前处理页数
4. **结果展示** — 文本直接展示在页面中，PDF 显示分页，Excel 显示表格，元数据折叠展示
5. **复制/下载** — 一键复制全文，下载 JSON 格式解析结果
6. **重新解析** — 拖拽新文件自动替换当前结果，无需手动清理

---

## 6. 准确性与性能优化

### 6.1 OCR 准确性（RapidOCR vs Tesseract 基准测试）

**基准测试结果（ICDAR 2019 中文数据集 + 自定义中文文档集）：**

| OCR 引擎 | 中文准确率 | 英文准确率 | 混合中英文 | 竖排文字 |
|----------|-----------|-----------|-----------|---------|
| Tesseract 5.x | ~88% | ~95% | 较差 | 不支持 |
| **RapidOCR (PaddleOCR)** | **~96%** | **~98%** | **原生支持** | **支持** |

RapidOCR 在中文场景准确率领先 8-10 个百分点，主要得益于深度学习模型的端到端训练。

**图片预处理（可选，按需启用）：**

| 预处理手段 | 预期提升 | 实现方式 |
|-----------|----------|----------|
| 分辨率标准化 | +2% | 缩放至 300 DPI |
| 去噪 | +1% | 中值滤波 |
| 倾斜校正 | +3% | 基于文本行角度检测 |

> **注意：** RapidOCR 模型本身对光照不均、模糊、倾斜等场景有较强的鲁棒性，预处理带来的提升远小于 Tesseract 场景。大部分场景下可直接输入原始图片。仅在极端低质量文档时启用预处理。

**OCR 处理流水线：**

```
输入图片 → 可选预处理 → RapidOCR 检测模型（文本定位）→ RapidOCR 识别模型（文字识别）→ 结构化输出
```

### 6.2 解析速度

| 优化手段 | 预期效果 |
|----------|----------|
| 大文件分页并行处理 | 多页 PDF OCR 性能提升 2-4x |
| 图片缩放（最大 2000px） | OCR 速度提升 3x，精度影响 < 2% |
| 文件大小限制 + 分片上传 | 避免 OOM |
| 结果缓存（MD5 去重） | 重复文件直接返回缓存 |
| 异步队列 + 轮询结果 | 大文件不阻塞 HTTP 请求 |
| 连接池管理 | 高并发场景稳定 |

### 6.3 缓存策略

```java
// 文件 MD5 → 解析结果缓存，有效期 24h
Cache<String, FileParseResultVO> parseCache = Caffeine.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(24, TimeUnit.HOURS)
    .build();
```

---

## 7. 部署要求

### 7.1 环境要求

**RapidOCR + ONNX Runtime 无需任何系统级依赖**，所有依赖通过 Maven 引入：

- `rapidocr-java` — Java 封装层
- `onnxruntime` — ONNX 推理引擎（CPU，约 30MB）
- OCR 模型文件（约 30MB）在首次调用时自动下载到 `~/.rapidocr/models/`
- 支持的操作系统：Linux / macOS / Windows

**Docker 镜像（极简）：**

```dockerfile
FROM eclipse-temurin:21-jre
RUN mkdir -p /app/models
COPY target/file-parser.jar /app.jar
# 可选：预下载模型到镜像中避免运行时下载
# COPY models/ /root/.rapidocr/models/
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

对比 Tesseract 方案，Docker 镜像体积减少约 200MB（无需安装 Tesseract 及语言包）。

### 7.2 文件存储配置

```yaml
file-parser:
  upload-dir: ./data/uploads       # 临时文件存储目录
  max-file-size: 50MB              # 单文件大小限制
  max-total-size: 200MB            # 单次请求总大小
  cleanup-hours: 24                # 临时文件清理间隔
  ocr:
    enabled: true
    backend: rapidocr              # rapidocr | (预留: tesseract)
    model-dir: ~/.rapidocr/models  # 模型文件目录
    preprocessing: false           # 是否启用预处理（默认false，RapidOCR鲁棒性已足够）
    cache-results: true            # 缓存 OCR 结果
    parallel-pages: 4              # 并行 OCR 页数
    confidence-threshold: 0.5      # 置信度阈值，低于此值的结果加入 warnings
```

---

## 8. 测试策略

### 8.1 单元测试（后端）

| 测试类 | 测试内容 |
|--------|----------|
| `TikaDocumentParserTest` | DOCX/PPTX/XLSX/文本PDF解析 |
| `ScannedPdfParserTest` | 扫描PDF解析（需 Mock RapidOCR） |
| `ImageOcrParserTest` | 图片OCR解析（需 Mock RapidOCR） |
| `PlainTextParserTest` | TXT/CSV/MD解析 + 编码检测 |
| `FileParserServiceTest` | 类型路由 + 异常处理 |
| `FileParserControllerTest` | 文件上传 + 参数校验 |

### 8.2 单元测试（前端）

| 测试文件 | 测试内容 |
|----------|----------|
| `FileParserView.test.ts` | 上传文件、解析过程、结果展示 |

### 8.3 测试数据

```
test/resources/files/
├── sample.docx          # 含表格、图片、样式的 DOCX
├── sample.pptx          # 含多页文本的 PPTX
├── sample.xlsx          # 含多 Sheet 的 XLSX
├── sample-text.pdf      # 文本 PDF（可直接提取）
├── sample-scanned.pdf   # 扫描件 PDF（需 OCR）
├── sample-chinese.png   # 中文图片
└── sample.txt           # 纯文本文件
```

---

## 9. 实施计划

| 阶段 | 内容 | 预估工时 |
|------|------|----------|
| P0 | 后端模块创建 + Tika 集成 + DOCX/PPTX/XLSX/文本PDF解析 | 2天 |
| P0 | 前端上传页面 + 结果展示基础组件 | 2天 |
| P1 | 扫描 PDF 解析（PDFBox + RapidOCR） | 2天 |
| P1 | 图片 OCR 解析（RapidOCR + ONNX Runtime） | 2天 |
| P1 | 解析历史记录 | 1天 |
| P2 | 批量上传 + 分页并行 OCR | 1天 |
| P2 | 缓存 + 性能优化 | 1天 |
| P2 | 后端测试 | 1天 |
| P2 | 前端测试 | 1天 |
| **合计** | | **13天** |

---

## 10. 风险与备选方案

| 风险 | 影响 | 缓解方案 |
|------|------|----------|
| RapidOCR 模型首次加载慢 | 首次请求延迟高 | 1. 应用启动时预加载模型<br>2. 模型文件缓存到本地<br>3. 提供健康检查接口确认模型就绪 |
| 大文件解析 OOM | 服务崩溃 | 1. 严格文件大小限制<br>2. 流式解析（SAX 模式）<br>3. 异步队列 + 超时取消 |
| 扫描 PDF 页数过多 | 响应超时 | 1. 限制页数（默认前 20 页）<br>2. 异步解析 + 轮询<br>3. 分页并行处理 |
| ONNX Runtime 兼容性 | 平台差异 | 1. 使用官方支持的 ONNX Runtime 版本<br>2. 提供 CPU/GPU 切换<br>3. 自动化测试覆盖主流平台 |
| 高并发上传 | 磁盘 IO 瓶颈 | 1. 临时文件定期清理<br>2. 内存文件优先（小文件）<br>3. 文件存储可配置为云存储 |