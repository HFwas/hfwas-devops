# 文档生成工具技术选型报告

> 日期：2026-08-27
> 背景：需要生成 Word/Excel/PPT/图片/Markdown/PDF 等多种格式文档，调研业界成熟方案

---

## 一、核心结论

**推荐语言：Java**

项目技术栈为 Java/Spring Boot，使用 Java 生态文档生成工具是最佳选择：

| 优势 | 说明 |
|------|------|
| ✅ 零额外运行时 | 无需部署其他语言服务 |
| ✅ Maven 引入 | 一行依赖，Spring Boot 原生集成 |
| ✅ 社区成熟 | 坑少，中文资料丰富 |
| ✅ 一键集成 | REST API 直接返回文件流 |

---

## 二、各格式推荐方案

### 2.1 📄 Word (docx)

| 工具 | 类型 | 推荐指数 | 许可证 | 说明 |
|------|------|:--------:|:------:|------|
| **[poi-tl](https://deepoove.com/poi-tl/)** | 模板引擎 | ⭐⭐⭐⭐⭐ | Apache 2.0 | **最推荐**。基于 Word 模板（docx）填充数据，完美保留样式 |
| **[Apache POI (XWPF)](https://poi.apache.org/)** | 底层 API | ⭐⭐⭐⭐ | Apache 2.0 | 纯代码构建 docx，灵活但代码量大 |
| **[Aspose.Words](https://products.aspose.com/words/java/)** | 商业库 | ⭐⭐⭐⭐⭐ | 商业收费 | 保真度最高，DOCX↔PDF 直接转换 |

**推荐方案**：**poi-tl + Word 模板**，模板由业务人员维护，开发只需定义数据模型。

```xml
<dependency>
    <groupId>com.deepoove</groupId>
    <artifactId>poi-tl</artifactId>
    <version>1.12.2</version>
</dependency>
```

```java
// 模板填充数据
XWPFTemplate template = XWPFTemplate.compile("template.docx").render(
    new HashMap<String, Object>() {{
        put("title", "报告标题");
        put("date", "2026-08-27");
        put("table", dataList);
    }}
);
template.write(new FileOutputStream("output.docx"));
```

---

### 2.2 📊 Excel (xlsx)

| 工具 | 推荐指数 | 许可证 | 说明 |
|------|:--------:|:------:|------|
| **[Alibaba EasyExcel](https://easyexcel.opensource.alibaba.com/)** | ⭐⭐⭐⭐⭐ | Apache 2.0 | **最推荐**。大文件内存极低，API 极其简洁 |
| **[Apache POI (XSSF/SXSSF)](https://poi.apache.org/)** | ⭐⭐⭐⭐ | Apache 2.0 | 功能全面，大文件需 SXSSF 流式写入 |
| **[Aspose.Cells](https://products.aspose.com/cells/java/)** | ⭐⭐⭐⭐⭐ | 商业收费 | 功能最强，公式/图表/透视表 |

> 注意：EasyExcel 官方已进入维护模式，社区 fork 为 **FastExcel**。但 EasyExcel 3.x 稳定版完全可用，已有大量生产案例。

```xml
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>easyexcel</artifactId>
    <version>3.3.4</version>
</dependency>
```

```java
// 一行代码写 Excel
EasyExcel.write("output.xlsx", DataClass.class).sheet("模板").doWrite(dataList);

// 大文件流式写入（分批）
EasyExcel.write(fileName, DataClass.class)
    .registerWriteHandler(new LongestMatchColumnWidthStyleStrategy())
    .sheet("大数据")
    .doWrite(() -> databasePageQuery(pageNum++));
```

---

### 2.3 🎨 PPT (pptx)

| 工具 | 推荐指数 | 许可证 | 说明 |
|------|:--------:|:------:|------|
| **[Apache POI (XSLF)](https://poi.apache.org/)** | ⭐⭐⭐⭐ | Apache 2.0 | 唯一免费选择，支持创建/修改幻灯片、图表、表格 |
| **[Aspose.Slides](https://products.aspose.com/slides/java/)** | ⭐⭐⭐⭐⭐ | 商业收费 | 保真度更高，支持模板、母版、动画 |

**推荐方案**：一般需求用 **Apache POI XSLF** 足够；对 PPT 模板和样式保真度要求高，考虑 Aspose.Slides。

```xml
<!-- Apache POI 全量引入 -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.5.1</version>
</dependency>
```

---

### 2.4 🖼️ 图片生成

| 场景 | 工具 | 推荐指数 | 许可证 |
|------|------|:--------:|:------:|
| 图表/报表图 | **[JFreeChart](https://www.jfree.org/jfreechart/)** | ⭐⭐⭐⭐ | LGPL |
| 流程图/架构图 | **[PlantUML](https://plantuml.com/)** | ⭐⭐⭐⭐⭐ | MIT |
| 富文本/海报 | **[Java Graphics2D](https://docs.oracle.com/javase/8/docs/api/java/awt/Graphics2D.html)** | ⭐⭐⭐⭐ | JDK 内置 |
| ECharts 转图片 | headless browser 渲染 | ⭐⭐⭐⭐ | 自由 |

**推荐方案**：
- 图表类 → **JFreeChart**（纯 Java，无外部依赖）
- 架构图/时序图 → **PlantUML**（文本描述→图片，版本可控）
- 复杂海报/证书 → **Graphics2D + 模板**

---

### 2.5 📝 Markdown 生成

| 工具 | 推荐指数 | 许可证 | 说明 |
|------|:--------:|:------:|------|
| **[commonmark-java](https://github.com/commonmark/commonmark-java)** | ⭐⭐⭐⭐⭐ | BSD 2-Clause | **最推荐**。官方 CommonMark 实现 0.30.0，轻量快速 |
| **[flexmark-java](https://github.com/vsch/flexmark-java)** | ⭐⭐⭐⭐⭐ | BSD 2-Clause | 功能更丰富，30+ 扩展（数学公式、脚注、Emoji） |

```xml
<dependency>
    <groupId>org.commonmark</groupId>
    <artifactId>commonmark</artifactId>
    <version>0.30.0</version>
</dependency>
```

```java
// 解析并渲染 Markdown
Document document = Parser.builder().build().parse("## Hello *World*");
String html = HtmlRenderer.builder().build().render(document);
```

> 直接生成 .md 文件时，最简单的做法就是字符串拼接/模板引擎生成文本，无需解析库。

---

### 2.6 📄 PDF 生成

| 工具 | 推荐指数 | 许可证 | 说明 |
|------|:--------:|:------:|------|
| **[iText](https://itextpdf.com/)** | ⭐⭐⭐⭐⭐ | AGPL / 商业 | 功能最全的 PDF 库 |
| **[Apache PDFBox](https://pdfbox.apache.org/)** | ⭐⭐⭐⭐ | Apache 2.0 | 免费，功能完整 |
| **[Flying Saucer](https://github.com/flyingsaucerproject/flyingsaucer)** | ⭐⭐⭐⭐ | LGPL | HTML+CSS → PDF |
| **[wkhtmltopdf](https://wkhtmltopdf.org/)** | ⭐⭐⭐⭐ | LGPL | 外部进程 + HTML 模板 |

**推荐方案**：
- Word→PDF 转换：Aspose.Words（收费）或 LibreOffice 命令行（免费）
- HTML→PDF：**Flying Saucer** 或 **wkhtmltopdf**
- 数据直接生成：**iText** 或 **PDFBox**

---

### 2.7 其他格式

| 格式 | 推荐工具 | 说明 |
|------|---------|------|
| HTML | **Thymeleaf/Freemarker** | Spring Boot 标配模板引擎 |
| CSV | **OpenCSV / Apache Commons CSV** | 轻量级，适合大数据量导出 |
| XML | **Jackson XML / JAXB** | Java 原生 XML 处理 |
| JSON | **Jackson / Gson** | Spring Boot 默认集成 |
| ZIP 打包 | **java.util.zip** / **Zip4j** | JDK 内置，或 Zip4j 支持加密 |

---

## 三、推荐组合方案

### 方案 A：免费全栈方案（推荐）

| 格式 | 工具 | Maven 坐标 |
|:----:|------|------------|
| Word | poi-tl | `com.deepoove:poi-tl` |
| Excel | EasyExcel | `com.alibaba:easyexcel` |
| PPT | Apache POI XSLF | `org.apache.poi:poi-ooxml` |
| 图片 | JFreeChart | `org.jfree:jfreechart` |
| Markdown | commonmark-java | `org.commonmark:commonmark` |
| PDF | PDFBox + Flying Saucer | `org.apache.pdfbox:pdfbox` |

**总依赖数**：6 个 Maven 依赖，全部免费，Spring Boot 直接集成。

### 方案 B：商业方案（高保真需求）

| 格式 | 工具 |
|:----:|------|
| Word | Aspose.Words |
| Excel | Aspose.Cells |
| PPT | Aspose.Slides |
| PDF | Aspose.PDF |
| 图片/Markdown | 同免费方案 |

**优点**：统一 API、DOCX/PPTX→PDF 直接转、保真度最高
**成本**：单产品 ~$999/年，全家桶 ~$3,997/年（5 开发者）

---

## 四、架构设计建议

```text
┌──────────────────────────────────────────────────┐
│              Spring Boot 文件生成服务               │
├──────────────────────────────────────────────────┤
│                                                    │
│  ┌───────────┐  ┌───────────┐  ┌──────────────┐  │
│  │ poi-tl    │  │EasyExcel  │  │ Apache POI   │  │
│  │ (Word)    │  │(Excel)    │  │ (PPT)        │  │
│  └───────────┘  └───────────┘  └──────────────┘  │
│                                                    │
│  ┌───────────┐  ┌───────────┐  ┌──────────────┐  │
│  │JFreeChart │  │commonmark │  │ Flying Saucer│  │
│  │ (图片)    │  │ (MD)      │  │ (PDF)        │  │
│  └───────────┘  └───────────┘  └──────────────┘  │
│                                                    │
│  ┌───────────────────────────────────────────┐     │
│  │     统一 DocumentService 接口               │     │
│  │     - generate(Format, Data, Template)    │     │
│  │     - 返回 InputStream / File / URL       │     │
│  └───────────────────────────────────────────┘     │
└──────────────────────────────────────────────────┘
```

### 接口设计示例

```java
public interface DocumentService {

    /**
     * 生成文档
     * @param format   文档格式 (WORD, EXCEL, PPT, PDF, MARKDOWN, IMAGE)
     * @param data     填充数据
     * @param template 模板路径（可选，部分格式需要）
     * @return 文件字节流
     */
    byte[] generate(DocumentFormat format, Map<String, Object> data, String template);

    /**
     * 生成并保存到文件
     */
    File generateToFile(DocumentFormat format, Map<String, Object> data,
                        String template, String outputPath);
}
```

---

## 五、集成难度评估

| 格式 | 最佳工具 | 是否免费 | 集成难度 | 代码量估算 |
|:----:|---------|:--------:|:--------:|:----------:|
| Word | poi-tl | ✅ | ⭐ 极简单 | 3-5 行 |
| Excel | EasyExcel | ✅ | ⭐ 极简单 | 3-5 行 |
| PPT | Apache POI | ✅ | ⭐⭐ 简单 | 20-50 行 |
| 图片 | JFreeChart | ✅ | ⭐⭐ 简单 | 10-30 行 |
| Markdown | commonmark-java | ✅ | ⭐ 极简单 | 1-3 行 |
| PDF | Flying Saucer | ✅ | ⭐⭐ 简单 | 10-20 行 |
| 其他 | 各格式专用库 | ✅ | ⭐⭐ 简单 | 5-15 行 |

---

## 六、参考链接

| 工具 | 官网 |
|------|------|
| poi-tl | [https://deepoove.com/poi-tl/](https://deepoove.com/poi-tl/) |
| EasyExcel | [https://easyexcel.opensource.alibaba.com/](https://easyexcel.opensource.alibaba.com/) |
| Apache POI | [https://poi.apache.org/](https://poi.apache.org/) |
| commonmark-java | [https://github.com/commonmark/commonmark-java](https://github.com/commonmark/commonmark-java) |
| flexmark-java | [https://github.com/vsch/flexmark-java](https://github.com/vsch/flexmark-java) |
| JFreeChart | [https://www.jfree.org/jfreechart/](https://www.jfree.org/jfreechart/) |
| PlantUML | [https://plantuml.com/](https://plantuml.com/) |
| Apache PDFBox | [https://pdfbox.apache.org/](https://pdfbox.apache.org/) |
| iText | [https://itextpdf.com/](https://itextpdf.com/) |
| Aspose | [https://www.aspose.com/](https://www.aspose.com/) |
| Flying Saucer | [https://github.com/flyingsaucerproject/flyingsaucer](https://github.com/flyingsaucerproject/flyingsaucer) |