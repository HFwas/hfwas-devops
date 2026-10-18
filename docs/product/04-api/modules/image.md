# 图片处理接口骨架

> 日期：2026-10-17
> 版本：v0.1
> 状态：骨架
> 数据来源：`backend/image-core/src/main/java/com/hfwas/devops/image/controller/ImageProcessorController.java`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：会话、预览、元数据、转换、批量、历史 |

---

基础路径：`/api/image`。

## 接口清单

| 方法 | 路径 | 用途 | 响应 |
|------|------|------|------|
| GET | `/api/image/health` | 引擎健康检查 | `BaseResult<ImageHealthVO>` |
| GET | `/api/image/history?limit=20` | 最近转换历史 | `BaseResult<List<ImageHistoryVO>>` |
| POST | `/api/image/sessions` | 上传图片创建会话（`multipart/form-data`，参数 `file`） | `BaseResult<ImageSessionVO>` |
| GET | `/api/image/sessions/{id}` | 会话详情 | `BaseResult<ImageSessionVO>` |
| GET | `/api/image/sessions/{id}/metadata` | 图片元数据 | `BaseResult<ImageMetadataVO>` |
| GET | `/api/image/sessions/{id}/preview` | 预览（二进制流） | `Resource` |
| POST | `/api/image/sessions/{id}/convert` | 格式转换 | `BaseResult<ImageConvertVO>` |
| GET | `/api/image/sessions/{id}/jobs/{jobId}` | 转换任务状态 | `BaseResult<ImageConvertVO>` |
| POST | `/api/image/batch-convert` | 批量转换 | `BaseResult<List<ImageConvertVO>>` |
| GET | `/api/image/sessions/{id}/result` | 下载结果（二进制流） | `Resource` |
| DELETE | `/api/image/sessions/{id}` | 删除会话 | `BaseResult<Void>` |

## 转换请求

`POST /api/image/sessions/{id}/convert`：

```json
{
  "targetFormat": "webp",
  "quality": 0.8,
  "maxWidth": 1920,
  "maxHeight": 1080
}
```

> 字段名以 `ImageConvertRequest` 为准，待补。

批量：

```json
{
  "sessionIds": ["s1", "s2"],
  "convert": {
    "targetFormat": "jpeg",
    "quality": 0.8
  }
}
```

## 失败响应（待补示例）

| 场景 | 行为 |
|------|------|
| 会话不存在 | 业务错误 |
| 非图片文件 | 创建会话失败 |
| 不支持的目标格式 | 参数校验失败 |
| 结果已清理 | 下载失败 |

## 待补

1. `ImageSessionVO`、`ImageMetadataVO`、`ImageConvertVO`、`ImageConvertRequest` 完整字段。
2. 失败响应完整示例。
3. Postman collection：`docs/api/image/image-api.postman_collection.json`。
