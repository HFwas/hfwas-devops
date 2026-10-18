# 图片处理数据字典

> 日期：2026-10-17
> 版本：v0.1
> 状态：已完成（表级）
> 数据来源：`backend/server/src/main/resources/db/image-schema.sql`、`backend/image-core/`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：image_convert_history 字段与索引 |

---

图片处理只有 1 张持久化表；会话与图片内容存放在运行时/临时存储中。

## image_convert_history

图片格式转换历史。

| 字段 | 说明 |
|------|------|
| `id` | 主键（雪花） |
| `session_id` | 处理会话 ID |
| `user_id` | 操作用户 |
| `tenant_id` | 租户 |
| `file_name` | 源文件名 |
| `source_mime` | 源 MIME 类型 |
| `target_format` | 目标格式 |
| `result_file_name` | 结果文件名 |
| `result_size` | 结果大小（字节） |
| `width` / `height` | 结果宽高 |
| `stripped_gps` | 是否剥离 GPS 信息 |
| `status` | 转换状态 |
| `error_message` | 失败原因 |
| `deleted` / `create_by` / `create_time` | 逻辑删除与审计 |

索引：`idx_image_history_user_time`（用户 + 时间）。

## 公共约定

- 主键 `INTEGER PRIMARY KEY`，雪花 ID。
- 逻辑删除 `deleted`（0/1）。
- 历史按 `user_id` 过滤，租户字段用于隔离统计。

## 写入方

`ImageSessionService`：创建会话、执行转换、批量转换时写入。

## 注意

1. 转换结果文件不在数据库中，删除会话后历史记录仍保留（是否保留待确认）。
2. `status` 的完整枚举待从 `ImageSessionService` 补齐。
