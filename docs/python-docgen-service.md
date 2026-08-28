# Python 文档生成服务方案

> 日期：2026-08-27
> 说明：基于 Python 实现文档生成服务，通过 HTTP API 与现有 Java 后端集成

---

## 一、Python 库一览

| 格式 | 库 | pip 安装 | 代码量 |
|:----:|----|----------|:------:|
| **Word** (docx) | `python-docx` | `pip install python-docx` | 2-3 行 |
| **Excel** (xlsx) | `openpyxl` | `pip install openpyxl` | 2-3 行 |
| **PPT** (pptx) | `python-pptx` | `pip install python-pptx` | 3-5 行 |
| **图片** | `Pillow` + `matplotlib` | `pip install Pillow matplotlib` | 2-3 行 |
| **Markdown** | 字符串拼接 / `mistune` | `pip install mistune` | 1 行 |
| **PDF** | `reportlab` / `weasyprint` | `pip install reportlab` | 3-5 行 |
| **HTML** | `jinja2` | `pip install jinja2` | 2-3 行 |

---

## 二、代码示例：Python 到底有多简单

### Word (python-docx)

```python
from docx import Document

doc = Document()
doc.add_heading('项目报告', 0)
doc.add_paragraph('这是一份自动生成的文档。')
doc.add_table(rows=3, cols=3)
doc.save('output.docx')
```

### Excel (openpyxl)

```python
from openpyxl import Workbook

wb = Workbook()
ws = wb.active
ws.title = "数据报表"
ws.append(["姓名", "年龄", "部门", "薪资"])
ws.append(["张三", 28, "技术部", 15000])
ws.append(["李四", 32, "市场部", 18000])
wb.save('output.xlsx')
```

### PPT (python-pptx)

```python
from pptx import Presentation
from pptx.util import Inches

prs = Presentation()
slide = prs.slides.add_slide(prs.slide_layouts[1])
slide.shapes.title.text = "项目汇报"
slide.placeholders[1].text = "2026年度总结"
prs.save('output.pptx')
```

### 图片 (matplotlib + Pillow)

```python
import matplotlib.pyplot as plt
from PIL import Image

# 生成图表
plt.figure(figsize=(8, 4))
plt.plot(["1月", "2月", "3月"], [100, 150, 130])
plt.title("月度数据趋势")
plt.savefig('chart.png', dpi=150)

# 或用 Pillow 生成图片
img = Image.new('RGB', (800, 400), color='white')
img.save('output.png')
```

### PDF (reportlab)

```python
from reportlab.pdfgen import canvas

c = canvas.Canvas("output.pdf")
c.drawString(100, 750, "Hello, World!")
c.save()
```

### Markdown

```python
# 最简单的做法：直接字符串拼接
md = f"""# {title}

## 基本信息
- 日期：{date}
- 作者：{author}
- 部门：{department}

## 数据汇总
{generate_table(data)}
"""
with open('output.md', 'w', encoding='utf-8') as f:
    f.write(md)
```

---

## 三、项目架构：与 Java 后端集成

### 推荐方案：Python 独立微服务

```text
┌─────────────────────────────────────────────────────────┐
│                    Java 后端 (Spring Boot)                │
│  ┌───────────────┐    ┌──────────────────────────────┐  │
│  │  API 层        │    │  DocumentService (Feign 调用) │  │
│  │  /api/doc/gen  │───→│  POST /api/docgen/generate   │  │
│  └───────────────┘    └──────────┬───────────────────┘  │
│                                  │  HTTP                │
└──────────────────────────────────┼──────────────────────┘
                                   │
┌──────────────────────────────────┼──────────────────────┐
│           Python 文档生成服务      │                      │
│  ┌───────────────┐               │                     │
│  │  FastAPI API  │◄──────────────┘                     │
│  │  /api/docgen  │                                     │
│  ├───────────────┤                                     │
│  │               │                                     │
│  │  ┌─────────┐  │  ┌─────────┐  ┌─────────────────┐  │
│  │  │docx     │  │  │xlsx     │  │pptx             │  │
│  │  │(python- │  │  │(openpyxl│  │(python-pptx)    │  │
│  │  │ docx)   │  │  │        │  │                 │  │
│  │  └─────────┘  │  └─────────┘  └─────────────────┘  │
│  │  ┌─────────┐  │  ┌─────────┐  ┌─────────────────┐  │
│  │  │图片     │  │  │PDF     │  │Markdown         │  │
│  │  │(Pillow/ │  │  │(report-│  │(字符串拼接)     │  │
│  │  │matplotlib│  │  │ lab)   │  │                 │  │
│  │  └─────────┘  │  └─────────┘  └─────────────────┘  │
│  └───────────────┘                                     │
└─────────────────────────────────────────────────────────┘
```

### 项目目录结构

```text
backend/
  server/                          # Java 主服务（已有）
  api-test-core/                   # 已有模块
  file-parser/                     # 已有模块
  pm-core/                         # 已有模块
  ...
  docgen-service/                  # 【新增】Python 文档生成服务
    ├── requirements.txt           # 依赖清单
    ├── app.py                     # FastAPI 入口
    ├── config.py                  # 配置文件
    ├── routers/
    │   ├── __init__.py
    │   └── generate.py            # 生成接口路由
    ├── services/
    │   ├── __init__.py
    │   ├── word_service.py        # Word 生成
    │   ├── excel_service.py       # Excel 生成
    │   ├── ppt_service.py         # PPT 生成
    │   ├── image_service.py       # 图片生成
    │   ├── markdown_service.py    # Markdown 生成
    │   └── pdf_service.py         # PDF 生成
    ├── templates/                 # 模板文件（Word/PPT 模板等）
    └── output/                    # 生成的文件输出目录
```

---

## 四、FastAPI 服务完整代码

### app.py - 入口

```python
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from routers import generate

app = FastAPI(title="文档生成服务", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(generate.router, prefix="/api/docgen", tags=["文档生成"])
```

### routers/generate.py - 统一接口

```python
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field
from typing import Optional, Any
from services.word_service import generate_word
from services.excel_service import generate_excel
from services.ppt_service import generate_ppt
from services.image_service import generate_image
from services.markdown_service import generate_markdown
from services.pdf_service import generate_pdf
from fastapi.responses import FileResponse

router = APIRouter()


class GenerateRequest(BaseModel):
    """统一生成请求"""
    format: str = Field(..., description="文档格式: word/excel/ppt/image/md/pdf")
    template: Optional[str] = Field(None, description="模板文件名（可选）")
    data: dict = Field(default_factory=dict, description="填充数据")
    filename: Optional[str] = Field(None, description="输出文件名")


SERVICE_MAP = {
    "word": generate_word,
    "excel": generate_excel,
    "ppt": generate_ppt,
    "image": generate_image,
    "md": generate_markdown,
    "pdf": generate_pdf,
}

FORMAT_EXT = {
    "word": ".docx",
    "excel": ".xlsx",
    "ppt": ".pptx",
    "image": ".png",
    "md": ".md",
    "pdf": ".pdf",
}


@router.post("/generate")
async def generate_document(req: GenerateRequest):
    if req.format not in SERVICE_MAP:
        raise HTTPException(400, f"不支持的格式: {req.format}，支持: {list(SERVICE_MAP.keys())}")

    ext = FORMAT_EXT[req.format]
    filename = req.filename or f"output_{req.format}{ext}"
    output_path = f"output/{filename}"

    SERVICE_MAP[req.format](output_path, req.data)

    return FileResponse(
        output_path,
        filename=filename,
        media_type="application/octet-stream"
    )
```

### services/word_service.py - Word 生成

```python
from docx import Document
from docx.shared import Inches, Pt
from docx.enum.text import WD_ALIGN_PARAGRAPH
import os


def generate_word(output_path: str, data: dict):
    doc = Document()

    # 标题
    doc.add_heading(data.get("title", "文档"), level=0)

    # 段落
    for paragraph in data.get("paragraphs", []):
        p = doc.add_paragraph(paragraph.get("text", ""))
        if paragraph.get("bold"):
            p.runs[0].bold = True
        if paragraph.get("align"):
            p.alignment = WD_ALIGN_PARAGRAPH.CENTER

    # 表格
    table_data = data.get("table")
    if table_data:
        headers = table_data.get("headers", [])
        rows = table_data.get("rows", [])
        table = doc.add_table(rows=1 + len(rows), cols=len(headers))
        table.style = 'Light Grid Accent 1'
        for i, h in enumerate(headers):
            table.rows[0].cells[i].text = h
        for r_idx, row in enumerate(rows):
            for c_idx, cell in enumerate(row):
                table.rows[r_idx + 1].cells[c_idx].text = str(cell)

    # 图片
    img_path = data.get("image")
    if img_path and os.path.exists(img_path):
        doc.add_picture(img_path, width=Inches(5.5))

    os.makedirs(os.path.dirname(output_path) or ".", exist_ok=True)
    doc.save(output_path)
```

### services/excel_service.py - Excel 生成

```python
from openpyxl import Workbook
from openpyxl.styles import Font, Alignment, PatternFill
from openpyxl.utils import get_column_letter
import os


def generate_excel(output_path: str, data: dict):
    wb = Workbook()
    ws = wb.active
    ws.title = data.get("sheet_name", "Sheet1")

    # 写入数据
    for row_idx, row in enumerate(data.get("rows", []), start=1):
        for col_idx, value in enumerate(row, start=1):
            cell = ws.cell(row=row_idx, column=col_idx, value=value)

    # 表头样式（第一行）
    header_font = Font(bold=True, color="FFFFFF")
    header_fill = PatternFill(start_color="4472C4", end_color="4472C4", fill_type="solid")
    for cell in ws[1]:
        cell.font = header_font
        cell.fill = header_fill
        cell.alignment = Alignment(horizontal="center")

    # 自动列宽
    for col in ws.columns:
        max_length = max(len(str(cell.value or "")) for cell in col)
        ws.column_dimensions[get_column_letter(col[0].column)].width = max_length + 2

    os.makedirs(os.path.dirname(output_path) or ".", exist_ok=True)
    wb.save(output_path)
```

### services/ppt_service.py - PPT 生成

```python
from pptx import Presentation
from pptx.util import Inches, Pt
from pptx.enum.text import PP_ALIGN
import os


def generate_ppt(output_path: str, data: dict):
    prs = Presentation()

    # 标题页
    slide = prs.slides.add_slide(prs.slide_layouts[0])
    slide.shapes.title.text = data.get("title", "演示文稿")
    slide.placeholders[1].text = data.get("subtitle", "")

    # 内容页
    for page in data.get("slides", []):
        slide = prs.slides.add_slide(prs.slide_layouts[1])
        slide.shapes.title.text = page.get("title", "")
        content = slide.placeholders[1]
        content.text = "\n".join(page.get("items", []))

    os.makedirs(os.path.dirname(output_path) or ".", exist_ok=True)
    prs.save(output_path)
```

### services/image_service.py - 图片生成

```python
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from PIL import Image
import os


def generate_image(output_path: str, data: dict):
    chart_type = data.get("chart_type", "line")

    if chart_type == "line":
        fig, ax = plt.subplots(figsize=(10, 6))
        for series in data.get("series", []):
            ax.plot(series.get("x", []), series.get("y", []),
                    label=series.get("name", ""), marker="o")
        ax.set_title(data.get("title", "图表"))
        ax.legend()
        ax.grid(True, alpha=0.3)
        fig.tight_layout()
        fig.savefig(output_path, dpi=150)
        plt.close()

    elif chart_type == "bar":
        fig, ax = plt.subplots(figsize=(10, 6))
        categories = data.get("categories", [])
        values = data.get("values", [])
        ax.bar(categories, values, color="steelblue")
        ax.set_title(data.get("title", "柱状图"))
        fig.tight_layout()
        fig.savefig(output_path, dpi=150)
        plt.close()

    elif chart_type == "pie":
        fig, ax = plt.subplots(figsize=(8, 8))
        labels = data.get("labels", [])
        sizes = data.get("sizes", [])
        ax.pie(sizes, labels=labels, autopct="%1.1f%%", startangle=90)
        ax.axis("equal")
        fig.savefig(output_path, dpi=150)
        plt.close()

    else:
        # 纯图片（Pillow）
        width = data.get("width", 800)
        height = data.get("height", 400)
        color = data.get("color", "white")
        img = Image.new("RGB", (width, height), color=color)
        img.save(output_path)

    os.makedirs(os.path.dirname(output_path) or ".", exist_ok=True)
```

### services/markdown_service.py - Markdown 生成

```python
import os


def generate_markdown(output_path: str, data: dict):
    """生成 Markdown 文件，直接拼接字符串"""
    md = f"""# {data.get("title", "文档")}

> 生成时间：{data.get("date", "")}
> 作者：{data.get("author", "")}

---

## 目录

{chr(10).join(f"- {s}" for s in data.get("sections", []))}

---

"""
    for section in data.get("sections", []):
        md += f"""## {section}

{data.get(f"content_{section}", "")}

"""

    # 表格
    table = data.get("table")
    if table:
        headers = table.get("headers", [])
        rows = table.get("rows", [])
        md += "| " + " | ".join(headers) + " |\n"
        md += "| " + " | ".join("---" for _ in headers) + " |\n"
        for row in rows:
            md += "| " + " | ".join(str(c) for c in row) + " |\n"

    os.makedirs(os.path.dirname(output_path) or ".", exist_ok=True)
    with open(output_path, "w", encoding="utf-8") as f:
        f.write(md)
```

### services/pdf_service.py - PDF 生成

```python
from reportlab.pdfgen import canvas
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
import os


def generate_pdf(output_path: str, data: dict):
    c = canvas.Canvas(output_path, pagesize=A4)
    width, height = A4

    # 标题
    c.setFont("Helvetica-Bold", 24)
    c.drawString(50 * mm, height - 30 * mm, data.get("title", "文档"))

    # 正文
    c.setFont("Helvetica", 12)
    y = height - 50 * mm
    for line in data.get("content", []):
        c.drawString(30 * mm, y, line)
        y -= 8 * mm

    # 表格
    table = data.get("table")
    if table:
        y -= 20 * mm
        headers = table.get("headers", [])
        rows = table.get("rows", [])
        c.setFont("Helvetica-Bold", 10)
        x = 30 * mm
        for h in headers:
            c.drawString(x, y, h)
            x += 40 * mm
        y -= 8 * mm
        c.setFont("Helvetica", 10)
        for row in rows:
            x = 30 * mm
            for cell in row:
                c.drawString(x, y, str(cell))
                x += 40 * mm
            y -= 8 * mm

    c.save()
    os.makedirs(os.path.dirname(output_path) or ".", exist_ok=True)
```

---

## 五、Requirements 依赖文件

```text
# requirements.txt
fastapi>=0.110.0
uvicorn>=0.29.0
python-docx>=1.1.2
openpyxl>=3.1.2
python-pptx>=1.0.0
Pillow>=10.0.0
matplotlib>=3.8.0
reportlab>=4.1.0
jinja2>=3.1.0
mistune>=3.0.0
```

---

## 六、Java 端调用示例

### Feign 客户端

```java
@FeignClient(name = "docgen-service", url = "${docgen.service.url:http://localhost:8000}")
public interface DocgenClient {

    @PostMapping(value = "/api/docgen/generate",
                 consumes = MediaType.APPLICATION_JSON_VALUE,
                 produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    ResponseEntity<byte[]> generateDocument(@RequestBody DocgenRequest request);
}

@Data
public class DocgenRequest {
    private String format;       // word / excel / ppt / image / md / pdf
    private String template;     // 模板名（可选）
    private Map<String, Object> data;  // 填充数据
    private String filename;     // 输出文件名（可选）
}
```

### Controller 层

```java
@RestController
@RequestMapping("/api/doc")
public class DocumentController {

    @Autowired
    private DocgenClient docgenClient;

    @PostMapping("/generate")
    public ResponseEntity<byte[]> generate(@RequestBody DocgenRequest request) {
        ResponseEntity<byte[]> response = docgenClient.generateDocument(request);
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=" + getFilename(request.getFormat()))
            .body(response.getBody());
    }
}
```

---

## 七、启动方式

### 本地开发

```bash
# 安装依赖
cd backend/docgen-service
pip install -r requirements.txt

# 启动服务
uvicorn app:app --reload --port 8000
```

### Docker 部署

```dockerfile
FROM python:3.12-slim

WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

EXPOSE 8000
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
```

### docker-compose 集成

```yaml
version: "3.8"
services:
  java-backend:
    build: ./backend/server
    ports:
      - "8089:8089"
    depends_on:
      - docgen-service

  docgen-service:
    build: ./backend/docgen-service
    ports:
      - "8000:8000"
    volumes:
      - ./backend/docgen-service/output:/app/output
    restart: unless-stopped
```

---

## 八、总结

| 对比项 | Java 方案 | Python 方案 |
|:------:|:---------:|:-----------:|
| **代码简洁度** | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ |
| **开发效率** | 中等 | 极高 |
| **性能** | 高（流式处理） | 中（小文件无差别） |
| **部署复杂度** | 单一 JAR | 多一个服务 |
| **维护成本** | 统一技术栈 | 多语言维护 |
| **适用场景** | 大文件、高并发 | 快速迭代、文档量适中 |

**一句话**：Python 开发效率高，代码量不到 Java 的一半，作为独立微服务与 Java 后端通过 HTTP 集成，两全其美。