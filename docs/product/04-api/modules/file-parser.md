# 文件解析（File Parser）接口骨架

> 日期：2026-10-17
> 版本：v0.1
> 状态：骨架
> 数据来源：`backend/file-parser/src/main/java/com/hfwas/devops/fileparser/controller/FileParserController.java`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：上传解析与健康检查 |

---

基础路径：`/api/file-parser`。

## POST /api/file-parser/upload

上传并解析单个文件。

- Content-Type：`multipart/form-data`
- 参数：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `file` | file | 是 | 待解析文件；为空返回 400 |
| `options` | object | 否 | 解析选项（压缩质量、最大尺寸、最小压缩比等） |

响应：`BaseResult<FileParseResultVO>`

```json
{
  "code": 0,
  "msg": "成功",
  "data": {
    "success": true,
    "fileName": "demo.docx",
    "contentType": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "text": "...",
    "errorMessage": null
  },
  "requestId": "..."
}
```

> `FileParseResultVO` 的完整字段以代码为准，待补。

## GET /api/file-parser/health

健康检查，返回纯文本 `OK`（非 `BaseResult`）。

## 失败响应

| 场景 | 行为 |
|------|------|
| 文件为空 | `code=400`，`msg="上传文件为空"` |
| 解析失败 | `code=500`，`msg=<错误信息>` |

## 待补

1. `FileParseRequest.options` 与 `FileParseResultVO` 的完整字段。
2. 支持格式清单与大小限制。
3. 失败响应完整示例。
4. Postman collection：`docs/api/file-parser/file-parser-api.postman_collection.json`。
