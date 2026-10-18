# 文档生成（Docgen）接口骨架

> 日期：2026-10-17
> 版本：v0.1
> 状态：骨架
> 数据来源：`backend/server/src/main/java/com/hfwas/devops/docgen/DocgenController.java`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：3 个生成接口与请求字段 |

---

基础路径：`/api/docgen`。要求已认证；操作写入操作日志。

## POST /api/docgen/generate

生成单个文件并直接下载（二进制流，非 `BaseResult`）。

请求体：

```json
{
  "format": "word",
  "filename": "report.docx",
  "data": {}
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `format` | string | 是 | `word` / `excel` / `ppt` / `image` / `md` / `pdf` |
| `filename` | string | 是 | 输出文件名，含扩展名 |
| `data` | object | 否 | 填充数据 |

响应：`200` + 对应 `Content-Type`，`Content-Disposition: attachment; filename*=UTF-8''...`。

## POST /api/docgen/generate-to-dir

生成文件到服务器目录。

```json
{
  "format": "pdf",
  "filename": "report.pdf",
  "directory": "/tmp/docgen",
  "data": {}
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `format` | string | 是 | 同上 |
| `filename` | string | 是 | 文件名 |
| `directory` | string | 否 | 目标目录；空则用 `docgen.output-dir` |
| `data` | object | 否 | 填充数据 |

响应：

```json
{
  "code": 0,
  "msg": "成功",
  "data": {
    "success": true,
    "directory": "/tmp/docgen",
    "filename": "report.pdf",
    "message": "文件已生成到: /tmp/docgen/report.pdf"
  },
  "requestId": "..."
}
```

## POST /api/docgen/batch-generate

批量生成。

```json
{
  "formats": ["word", "excel", "pdf"],
  "filename": "文档",
  "sizes": [102400, 1048576],
  "fileCount": 2,
  "directory": "/tmp/docgen",
  "columnCount": 10,
  "rowSize": 100,
  "rowCount": 1000,
  "pageCount": 10,
  "encrypt": false,
  "pdfPassword": null,
  "emptyContent": false,
  "emptyPageCount": false
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `formats` | string[] | 是 | 至少一个格式；为空返回 `code=400` |
| `filename` | string | 是 | 基础文件名 |
| `sizes` | number[] | 否 | 字节大小；为空按 `[0]` 处理 |
| `fileCount` | int | 否 | 每个组合生成数量，默认 1 |
| `directory` | string | 否 | 输出目录 |
| `columnCount` | int | 否 | Excel 列数 |
| `rowSize` | int | 否 | 每行字符数 |
| `rowCount` | int | 否 | 行数覆盖 |
| `pageCount` | int | 否 | PDF 页数 |
| `encrypt` | boolean | 否 | PDF 是否加密 |
| `pdfPassword` | string | 否 | PDF 打开密码 |
| `emptyContent` | boolean | 否 | PDF 空内容 |
| `emptyPageCount` | boolean | 否 | 不指定页数 |

## 失败响应（待补示例）

| 场景 | 当前行为 |
|------|----------|
| 未选择格式 | `code=400`，`msg="请至少选择一个格式"` |
| 输出目录不可写 | 待确认 |
| 生成引擎异常 | 待确认 |

## 待补

1. 各格式的 `data` 结构说明。
2. 失败响应完整示例。
3. Postman collection：`docs/api/docgen/docgen-api.postman_collection.json`。
