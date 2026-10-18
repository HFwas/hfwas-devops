# 文件解析（File Parser）功能规格

> 日期：2026-10-17
> 版本：v0.1
> 状态：骨架
> 数据来源：`backend/file-parser/`、`docs/file-parser/`、`frontend/src/modules/file-parser/`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：上传解析、OCR、压缩与格式支持 |

---

## 1. 概述

文件解析模块接收上传文件，做格式识别、文本/内容提取、图片 OCR 与图片压缩，返回结构化解析结果。适合作为文档处理、内容抽取与文件预处理的公共服务。

## 2. 角色与场景

| 角色 | 场景 |
|------|------|
| 测试工程师 | 验证不同格式文件的解析结果 |
| 研发工程师 | 集成文件内容提取能力 |
| 业务用户 | 上传文档/图片提取文字 |

## 3. 功能清单

| 功能项 | 说明 | 页面/路由 | 状态 |
|--------|------|-----------|------|
| 上传并解析 | `multipart/form-data` 上传单个文件 | `/file-parser` | 可用 |
| 格式识别 | 基于 MIME Type 识别，兼容 WPS/信创格式 | 同上 | 可用 |
| 文本提取 | DOCX/PPTX/XLSX/PDF/TXT 文本与结构 | 同上 | 可用 |
| 图片 OCR | 图片文字识别 | 同上 | 可用 |
| 图片压缩 | 质量、最大尺寸、最小压缩比 | 同上 | 可用 |
| 健康检查 | 服务状态 | `GET /api/file-parser/health` | 可用 |

## 4. 核心流程

```text
上传文件
  → 校验非空（空文件返回 400）
  → MIME/扩展名识别格式
  → 按格式选择解析器
       ├─ 文档：DOCX/PPTX/XLSX/PDF/TXT 提取文本与结构
       └─ 图片：OCR + 可选压缩
  → 返回 FileParseResultVO
       ├─ success
       ├─ 文本/结构内容
       ├─ OCR 结果
       └─ 错误信息
```

## 5. 业务规则

| 规则 | 说明 |
|------|------|
| 单文件 | 每次请求仅处理一个文件 |
| 空文件 | 直接失败，返回 400 “上传文件为空” |
| 解析失败 | 返回 `BaseResult.failed(500, errorMessage)` |
| OCR 引擎 | Tesseract / PP-OCR（见 `docs/file-parser/` 与 OCR 相关实现） |
| 压缩参数 | 质量、最大尺寸、最小压缩比由 `FileParseRequest.options` 传入 |
| 内存策略 | 大文件采用流式/分块处理，见 `docs/file-parser/file-parser-memory-optimization.md` |
| 无持久化 | 当前无解析历史表 |

## 6. 异常与边界

| 场景 | 系统行为 |
|------|----------|
| 文件为空 | 400，上传文件为空 |
| 不支持的格式 | 解析失败，返回错误信息 |
| 文件损坏/加密 | 解析失败，返回错误信息 |
| OCR 无文字 | 返回空文本或提示 |
| 超大文件 | 受上传大小限制与内存策略约束 |
| 依赖缺失 | 健康检查失败或解析报错 |

## 7. 接口与数据

- 接口：`POST /api/file-parser/upload`、`GET /api/file-parser/health`
- 详见 [04-api/modules/file-parser.md](../../04-api/modules/file-parser.md)
- 数据表：无
- 设计文档：[docs/file-parser/file-parser-design.md](../../../file-parser/file-parser-design.md)

## 8. 验收标准

| 编号 | 验收项 |
|------|--------|
| AC-FP-01 | DOCX/PPTX/XLSX/PDF/TXT 能提取文本 |
| AC-FP-02 | 图片 OCR 能识别中英文（以实际模型能力为准） |
| AC-FP-03 | 压缩参数生效且结果可预览 |
| AC-FP-04 | 空文件与不支持格式返回明确错误 |
| AC-FP-05 | 大文件不会导致 OOM（按内存优化文档验证） |

## 9. 待确认

1. OCR 引擎的最终形态（Tesseract 与 PP-OCR 的启用条件）。
2. 上传大小上限（Spring multipart 配置）。
3. 是否计划保存解析历史或结果文件。
