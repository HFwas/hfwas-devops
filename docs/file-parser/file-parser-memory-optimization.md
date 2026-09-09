# 文件解析模块 — 内存优化报告

> 版本：v1.0  
> 日期：2026-08-19  
> 关联文档：[文件解析模块技术设计方案](file-parser-design.md)

---

## 1. 排查范围

后端 Java 代码全部文件：

| 文件 | 行数 | 职责 |
|------|------|------|
| `PlainTextParser.java` | 216 | 纯文本解析（TXT/CSV/MD） |
| `ImageOcrParser.java` | 103 | 图片 OCR 解析 |
| `ScannedPdfParser.java` | 134 | 扫描版 PDF 解析 |
| `TikaDocumentParser.java` | 109 | Tika 文档解析（DOCX/PPTX/XLSX/PDF） |
| `FileParserService.java` | 150 | 解析编排服务 |
| `FileStorageService.java` | 127 | 临时文件存储 |
| `OcrService.java` | 137 | OCR 识别服务 |
| `FileParserConfig.java` | 47 | 配置类 |
| `FileParserController.java` | 60 | REST 控制器 |
| `FileParseResultVO.java` | 97 | 结果 VO |

---

## 2. 发现的问题

### 🔴 高危：2 个

#### 2.1 TikaDocumentParser — BodyContentHandler(-1) 无上限文本提取

**文件**: `TikaDocumentParser.java:57`

```java
// 修改前：无限制，超大文档可撑爆堆内存
BodyContentHandler handler = new BodyContentHandler(-1);

// 修改后：使用配置的上限（默认 10MB）
BodyContentHandler handler = new BodyContentHandler(config.getTika().getMaxTextLength());
```

**风险**:
- 50MB 的 XLSX 文档解析后，Tika 提取的文本可能也达到 50MB+
- 堆内存中同时存在文件流、Tika 解析树、提取出的超大 String，总占用超 150MB

**修复**:
- 注入 `FileParserConfig`，使用 `tika.max-text-length` 配置项
- 超出时捕获 `SAXException`，返回友好提示而非崩溃

---

#### 2.2 OcrService — ONNX Runtime 堆外内存无限制

**文件**: `OcrService.java:37`

```java
// 修改前：无并发控制，多线程并发导致 native memory 膨胀
engine = InferenceEngine.getInstance(Model.ONNX_PPOCR_V4);

// 修改后：Semaphore 限制并发数
Semaphore ocrPermits = new Semaphore(config.getOcr().getMaxConcurrent(), true);
tryAcquire(60, TimeUnit.SECONDS) → engine.runOcr() → release()
```

**风险**:
- ONNX Runtime 推理在**堆外内存（Native Memory）**中执行，JVM 无法感知
- 无并发控制时，10 个并发推理可能分配 2-5GB native memory
- 进程被 OOM Killer 杀掉，但 Java 堆看起来正常

**修复**:
- 注入 `FileParserConfig`，使用 `ocr.max-concurrent` 配置项（默认 2）
- `Semaphore` 公平模式，等待超时 60 秒
- `recognize()` 方法复用 `recognizeWithConfidence()` 逻辑

---

### 🟡 中危：4 个

#### 2.3 PlainTextParser — 文件内容重复加载

**文件**: `PlainTextParser.java:108 + 53`

```java
// 修改前：detectCharset() 读取整个文件到 byte[]
byte[] bytes = FileUtil.readBytes(file);   // 50MB → 50MB
// 然后 parse() 又读取一次到 String
String text = Files.readString(file.toPath(), ...); // 50MB → ~100MB (char[])

// 修改后：detectCharset() 只读取头部采样
byte[] bytes = readFileHead(file, sampleSize); // 只读 4KB
```

**风险**:
- 50MB 文件：`byte[]` 50MB + `String` 100MB + CSV 解析 50-100MB = **峰值 250MB+**
- 修改后仅需 4KB 采样 + 1 次完整读取

**修复**:
- 新增 `readFileHead()` 方法，使用 `RandomAccessFile` 只读文件头部
- 采样字节数通过 `parser.charset-detection-sample-size` 配置（默认 4096）

---

#### 2.4 ScannedPdfParser — 页面渲染无尺寸上限

**文件**: `ScannedPdfParser.java:62`

```java
// 修改前：300 DPI 渲染，超大页面直接撑爆
BufferedImage pageImage = renderer.renderImageWithDPI(i, OCR_DPI, ImageType.RGB);

// 修改后：渲染后自动缩放
BufferedImage pageImage = renderer.renderImageWithDPI(i, OCR_DPI, ImageType.RGB);
pageImage = scaleIfNeeded(pageImage, maxDimension); // 缩放到 maxDimension
```

**风险**:
- A4 页面 300 DPI ≈ 26MB 的 `BufferedImage`
- 工程图纸 2000×3000px 300 DPI 可达 100MB+
- 短暂内存尖峰在多并发下触发 Full GC

**修复**:
- 新增 `scaleIfNeeded()` 方法，等比例缩放到 `maxImageDimension`（默认 2048px）
- `MAX_PAGES` 硬编码 → `scanned-pdf.max-pages` 配置项

---

#### 2.5 同步阻塞解析占用 Tomcat 线程

**文件**: `FileParserController.java:47` + `FileParserService.java:59`

```java
// 所有解析在 Tomcat 线程中同步执行
FileParseResultVO result = fileParserService.parse(file, ...);
```

**风险**:
- 20 个用户同时上传大文件，Tomcat 线程池（默认 200 个）耗尽
- 后续请求排队或超时，资源持续堆积

**缓解**:
- OCR 操作的 Semaphore 排队机制防止并发堆积
- 建议后续支持 `@Async` 异步解析

---

#### 2.6 FileStorageService — 临时文件清理周期过长

**文件**: `FileStorageService.java:90-91`

```java
// 修改前：24 小时清理一次
@Scheduled(fixedRateString = "${file-parser.cleanup-hours:24}")

// 修改后：1 小时清理一次
@Scheduled(fixedRateString = "${file-parser.cleanup-hours:1}")
```

**风险**:
- 请求在 `save()` 和 `delete()` 之间异常中断，临时文件残留最多 24h
- 大量并发失败时磁盘被填满

**修复**: 默认清理周期 24h → 1h

---

### 🟢 低危：2 个

#### 2.7 Spring multipart 配置与应用层限制不一致

**文件**: `application-dev.yml:12-13` vs `FileParserConfig.java:19`

```yaml
# 修改前：Spring 接受 200MB，应用层限制 50MB
max-file-size: 200MB   # Spring
maxFileSize = 50MB     # 应用层

# 修改后：统一为 50MB
max-file-size: 50MB
```

**修复**: 统一配置值，避免浪费磁盘 I/O

---

#### 2.8 CSV 解析产生第三份内存拷贝

**文件**: `PlainTextParser.java:181-192`

```java
List<List<String>> rows = new java.util.ArrayList<>();
for (String line : lines) {
    rows.add(java.util.Arrays.asList(trimmed.split(",")));
}
```

**状态**: 当前为低风险，建议后续对大 CSV 添加行数限制或流式解析

---

## 3. 修改文件清单

### 3.1 新增配置项

**配置类**: `FileParserConfig.java`

| 配置分组 | 字段 | 类型 | 默认值 | 说明 |
|---------|------|------|--------|------|
| `parser` | `charsetDetectionSampleSize` | `int` | 4096 | 编码检测采样字节数 |
| `ocr` | `maxConcurrent` | `int` | 2 | 最大并发 OCR 推理数 |
| `tika` | `maxTextLength` | `int` | 10MB | Tika 提取文本长度上限 |
| `scannedPdf` | `maxPages` | `int` | 50 | PDF 最大处理页数 |
| `scannedPdf` | `maxImageDimension` | `int` | 2048 | 页面渲染最大像素尺寸 |

### 3.2 修改的生产代码

| 文件 | 修改行数 | 关键变更 |
|------|---------|---------|
| `FileParserConfig.java` | +97 | 新增 3 个配置分组 + 5 个新字段 |
| `TikaDocumentParser.java` | +15 | 注入 config，`-1` → 配置值，超限友好提示 |
| `PlainTextParser.java` | +15 | 注入 config，`FileUtil.readBytes` → `readFileHead` |
| `ScannedPdfParser.java` | +35 | 注入 config，`MAX_PAGES` → 配置值，`scaleIfNeeded()` |
| `OcrService.java` | +30 | 注入 config，`Semaphore` 并发控制 |
| `FileStorageService.java` | +1 | 注释更新 |
| `application-dev.yml` | +24 | 统一限制，新增完整配置段 |

### 3.3 修改的测试代码

| 文件 | 修改行数 | 关键变更 |
|------|---------|---------|
| `PlainTextParserTest.java` | +10 | 新增 `FileParserConfig` mock |
| `TikaDocumentParserTest.java` | +10 | 新增 `FileParserConfig` mock |
| `ScannedPdfParserTest.java` | +10 | 新增 `FileParserConfig` mock |
| `OcrServiceTest.java` | +10 | 新增 `FileParserConfig` mock |

---

## 4. 配置示例

在 `application-dev.yml` 中完整配置：

```yaml
file-parser:
  upload-dir: ./data/uploads
  max-file-size: 52428800
  max-total-size: 209715200
  cleanup-hours: 1
  parser:
    charset-detection-sample-size: 4096
  ocr:
    enabled: true
    lang: chi_sim+eng
    preprocessing: true
    cache-results: true
    parallel-pages: 4
    max-concurrent: 2
  tika:
    max-text-length: 10485760
  scanned-pdf:
    max-pages: 50
    max-image-dimension: 2048
```

---

## 5. 后续建议

| 建议 | 优先级 | 说明 |
|------|--------|------|
| 添加 `-XX:NativeMemoryTracking=summary` | 高 | 监控 ONNX Runtime 堆外内存使用 |
| 添加 `-XX:MaxDirectMemorySize` | 高 | 限制堆外内存上限 |
| 大 CSV 流式解析 | 中 | 超过行数阈值时改用流式，避免全部加载 |
| 异步解析支持 | 中 | `@Async` + 独立线程池，解耦 Tomcat 线程 |
| 启动时清理临时目录 | 低 | 应用启动时清理残留的临时文件 |