# Python 文档生成 - 最简单方案

> 日期：2026-08-27
> 说明：一个 Python 脚本搞定所有格式，Java 直接调用

---

## 一、使用方法

### 1. 安装依赖

```bash
cd backend/scripts
pip3 install -r requirements.txt
```

> 如果 pip 安装慢或报 SSL 错误，使用国内镜像：
> ```bash
> pip3 install -i https://pypi.tuna.tsinghua.edu.cn/simple/ --trusted-host pypi.tuna.tsinghua.edu.cn -r requirements.txt
> ```

### 2. 准备数据文件（JSON）

```json
{
  "title": "项目周报",
  "paragraphs": ["完成了文档生成服务开发。"],
  "table": {
    "headers": ["模块", "状态", "完成度"],
    "rows": [
      ["Word", "已完成", "100%"],
      ["Excel", "已完成", "100%"]
    ]
  }
}
```

### 3. 执行脚本

```bash
# 生成 Word
python3 generate_doc.py word data.json output.docx

# 生成 Excel
python3 generate_doc.py excel data.json output.xlsx

# 生成 PPT
python3 generate_doc.py ppt data.json output.pptx

# 生成图片
python3 generate_doc.py image data.json output.png

# 生成 Markdown
python3 generate_doc.py md data.json output.md

# 生成 PDF
python3 generate_doc.py pdf data.json output.pdf
```

---

## 二、文件结构

```
backend/scripts/
├── generate_doc.py       # 主脚本（6种格式，~400行）
├── sample_data.json      # 示例数据文件
├── requirements.txt      # 依赖清单
└── venv/                 # 虚拟环境（可选）
```

### 一个脚本，6 种格式，全部搞定

```python
# 每个格式就是一个函数，清晰明了
GENERATORS = {
    "word":  generate_word,   # 40行
    "excel": generate_excel,  # 35行
    "ppt":   generate_ppt,    # 25行
    "image": generate_image,  # 50行
    "md":    generate_markdown, # 35行
    "pdf":   generate_pdf,    # 40行
}
```

---

## 三、Java 调用方式

### 最简单的调用（直接调用）

```java
// 只需要这一行工具类
public byte[] generateDoc(String format, Map<String, Object> data, String outputName) {
    // 1. 把 data 写入临时 JSON 文件
    File dataFile = File.createTempFile("docgen_", ".json");
    objectMapper.writeValue(dataFile, data);

    // 2. 调用 Python 脚本
    ProcessBuilder pb = new ProcessBuilder(
        "python3", "backend/scripts/generate_doc.py",
        format, dataFile.getAbsolutePath(), outputPath
    );
    Process process = pb.start();
    process.waitFor();

    // 3. 读取生成的文件返回
    return Files.readAllBytes(Path.of(outputPath));
}
```

### 完整工具类（已写好）

`backend/server/src/main/java/com/hfwas/devops/common/docgen/DocgenUtil.java`

```java
@Autowired
private DocgenUtil docgenUtil;

// 生成文档（返回字节数组）
byte[] bytes = docgenUtil.generate("word", data, "report.docx");

// 生成文档（保存到文件）
docgenUtil.generateToFile("excel", data, "/tmp/report.xlsx");

// 链式调用
DocgenUtil.quick("word", "周报.docx")
    .put("title", "周报")
    .put("paragraphs", List.of("内容1", "内容2"))
    .done();
```

---

## 四、各格式数据格式说明

### Word (docx)

| 字段 | 类型 | 说明 |
|------|------|------|
| title | string | 文档标题 |
| paragraphs | string[] | 段落列表 |
| table.headers | string[] | 表头 |
| table.rows | string[][] | 表格数据 |
| image | string | 图片路径（可选） |

### Excel (xlsx)

| 字段 | 类型 | 说明 |
|------|------|------|
| sheet_name | string | 工作表名称 |
| rows | array[] | 数据行，第一行作为表头 |
| header_style | bool | 是否加表头样式 |

### PPT (pptx)

| 字段 | 类型 | 说明 |
|------|------|------|
| title | string | 标题 |
| subtitle | string | 副标题 |
| slides[].title | string | 每页标题 |
| slides[].items | string[] | 每页内容列表 |

### 图片 (image)

| 字段 | 类型 | 说明 |
|------|------|------|
| chart_type | string | line / bar / pie |
| title | string | 图表标题 |
| series | array | 折线图数据 |
| categories | string[] | 柱状图分类 |
| values | number[] | 柱状图数值 |
| labels | string[] | 饼图标签 |
| sizes | number[] | 饼图数值 |

### Markdown (md)

| 字段 | 类型 | 说明 |
|------|------|------|
| title | string | 标题 |
| date | string | 日期 |
| author | string | 作者 |
| sections[].heading | string | 章节标题 |
| sections[].content | string | 章节内容 |
| table | object | 表格（同 Word） |

### PDF

| 字段 | 类型 | 说明 |
|------|------|------|
| title | string | 标题 |
| content | string[] | 段落列表 |
| table | object | 表格（同 Word） |

---

## 五、示例：生成一个完整的项目周报

```java
// Java 代码
Map<String, Object> data = new HashMap<>();
data.put("title", "项目周报 - 2026年第35周");
data.put("date", "2026-08-27");
data.put("author", "张三");
data.put("paragraphs", List.of(
    "本周完成了文档生成服务的开发工作。",
    "支持 Word / Excel / PPT / 图片 / Markdown / PDF 六种格式。"
));
data.put("table", Map.of(
    "headers", List.of("模块", "状态", "负责人", "完成度"),
    "rows", List.of(
        List.of("Word 生成", "已完成", "张三", "100%"),
        List.of("Excel 生成", "已完成", "李四", "100%"),
        List.of("PPT 生成", "进行中", "王五", "60%"),
        List.of("图片生成", "已完成", "赵六", "100%"),
        List.of("PDF 生成", "待开始", "张三", "0%")
    )
));

// 生成 Word 文档
byte[] docx = docgenUtil.generate("word", data, "周报.docx");

// 生成 Excel 报表
byte[] xlsx = docgenUtil.generate("excel", data, "周报.xlsx");

// 生成 Markdown
byte[] md = docgenUtil.generate("md", data, "周报.md");
```

---

## 六、常见问题

### Q: 中文字体显示为方块？

图片生成时 matplotlib 默认字体不支持中文，脚本已自动处理：
- macOS: 自动使用 PingFang / STHeiti
- Windows: 自动使用 微软雅黑 / 黑体
- Linux: 自动使用文泉驿 / Noto Sans CJK

如果仍不显示，手动安装字体：
```bash
# macOS
cp /System/Library/Fonts/PingFang.ttc ~/matplotlib/fonts/

# Linux
apt install fonts-noto-cjk
```

### Q: 需要支持更多格式？

在 `generate_doc.py` 中添加新函数，然后在 `GENERATORS` 字典中注册即可：

```python
def generate_html(data: dict, output_path: str):
    with open(output_path, "w") as f:
        f.write(f"<h1>{data['title']}</h1>")

GENERATORS["html"] = generate_html  # 一行注册
```

### Q: 并发生成文档？

脚本每次独立执行，天然支持并发。Java 端用线程池即可：

```java
@Autowired
private DocgenUtil docgenUtil;

// 并发生成多个文档
CompletableFuture.allOf(
    CompletableFuture.runAsync(() -> docgenUtil.generate("word", data1, "a.docx")),
    CompletableFuture.runAsync(() -> docgenUtil.generate("excel", data2, "b.xlsx")),
    CompletableFuture.runAsync(() -> docgenUtil.generate("md", data3, "c.md"))
).join();
```

---

## 七、总结

```
┌──────────────┐         ┌──────────────────────────────┐
│  Java 后端    │ 调用     │  Python 脚本                  │
│              │────────→│                              │
│  传 data.json │         │  generate_doc.py             │
│  收文件       │←────────│  word / excel / ppt / image  │
└──────────────┘         │  md / pdf                    │
                          └──────────────────────────────┘
```

**全部代码量：**
- Python 脚本：**~400 行**（覆盖 6 种格式）
- Java 工具类：**~120 行**（调用脚本 + 链式 API）
- 依赖文件：**1 行** `pip install`
- 测试数据：**1 个 JSON 文件**

**就这么简单。**