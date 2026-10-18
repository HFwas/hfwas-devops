# 图片处理功能规格

> 日期：2026-10-17
> 版本：v0.1
> 状态：骨架
> 数据来源：`backend/image-core/`、`frontend/src/modules/image/`、`docs/image/`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：图片会话、预览、元数据、格式转换与历史 |

---

## 1. 概述

图片处理模块以“会话”为单位管理一次图片上传与后续操作：预览、元数据读取、格式转换、批量转换、结果下载与历史记录。支持 PNG/JPEG/WebP/BMP/GIF/SVG/TIFF 等格式互转。

## 2. 角色与场景

| 角色 | 场景 |
|------|------|
| 业务用户 | 上传图片、转换格式、查看元数据 |
| 测试工程师 | 批量转换验证格式兼容性 |
| 研发工程师 | 集成图片转换能力 |

## 3. 功能清单

| 功能项 | 说明 | 页面/路由 | 状态 |
|--------|------|-----------|------|
| 创建会话 | 上传图片建立处理会话 | `/image` | 可用 |
| 会话查询 | 查询会话状态 | `/image` | 可用 |
| 预览 | 返回原图/预览图 | `/image` | 可用 |
| 元数据 | 尺寸、格式、EXIF 等 | `/image` | 可用 |
| 格式转换 | 目标格式、质量、尺寸 | `/image` | 可用 |
| 转换任务 | 异步任务状态查询 | `/image` | 可用 |
| 批量转换 | 多会话批量转换 | `/image` | 可用 |
| 结果下载 | 下载转换结果 | `/image` | 可用 |
| 历史记录 | 最近转换记录 | `/image` | 可用 |
| 删除会话 | 清理会话与临时文件 | `/image` | 可用 |

## 4. 核心流程

```text
POST /sessions（上传文件）
  → 创建会话，保存原图与元数据
  → GET /metadata 查看尺寸/格式/EXIF
  → GET /preview 预览
  → POST /sessions/{id}/convert（目标格式/质量/尺寸）
       ├─ 同步返回结果，或
       └─ 返回 jobId，GET /jobs/{jobId} 轮询
  → GET /sessions/{id}/result 下载结果
  → DELETE /sessions/{id} 清理
```

批量：

```text
POST /batch-convert { sessionIds, convert }
  → 逐会话转换
  → 返回每个会话的 ImageConvertVO
```

## 5. 业务规则

| 规则 | 说明 |
|------|------|
| 会话生命周期 | 会话包含上传文件与转换结果；删除会话清理临时文件 |
| 历史记录 | `image_convert_history` 记录用户、租户、源/目标格式、结果大小、宽高、是否去除 GPS、状态 |
| 用户隔离 | 历史按 `user_id` 过滤；表含 `tenant_id` |
| 逻辑删除 | `deleted`（0/1） |
| 预览 | 返回 `Resource` 流，Content-Type 按源图 |
| 批量上限 | 未在代码中显式限制（待确认） |
| EXIF | 转换时可剥离 GPS（`stripped_gps`） |

## 6. 异常与边界

| 场景 | 系统行为 |
|------|----------|
| 会话不存在 | 返回业务错误 |
| 不支持的目标格式 | 参数校验失败 |
| 转换失败 | 历史记录 `status=failed` + `error_message` |
| 上传非图片 | 创建会话失败 |
| 结果文件已清理 | 下载失败 |
| 批量中部分失败 | 逐项返回结果，失败项带错误信息 |

## 7. 接口与数据

- 接口：`/api/image/health`、`/api/image/history`、`/api/image/sessions/*`、`/api/image/batch-convert`
- 详见 [04-api/modules/image.md](../../04-api/modules/image.md)
- 数据表：`image_convert_history`
- 详见 [03-database/tables/image.md](../../03-database/tables/image.md)

## 8. 验收标准

| 编号 | 验收项 |
|------|--------|
| AC-IMG-01 | 支持格式之间互转，结果可正常打开 |
| AC-IMG-02 | 元数据（尺寸/格式/EXIF）读取正确 |
| AC-IMG-03 | 转换质量与尺寸参数生效 |
| AC-IMG-04 | 批量转换返回结果与单次转换一致 |
| AC-IMG-05 | 历史记录按用户隔离且状态正确 |
| AC-IMG-06 | 删除会话后无法再访问结果 |

## 9. 待确认

1. 转换引擎（Java ImageIO / 外部库 / Python）与支持的格式全集。
2. 批量转换的数量与文件大小上限。
3. 临时文件生命周期与磁盘清理策略。
