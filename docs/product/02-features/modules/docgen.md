# 文档生成（Docgen）功能规格

> 日期：2026-10-17
> 版本：v0.1
> 状态：骨架
> 数据来源：`backend/server/src/main/java/com/hfwas/devops/docgen/DocgenController.java`、`docs/docgen/`、`frontend/src/modules/docgen/`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：单文件/批量生成、格式与参数 |

---

## 1. 概述

Docgen 按指定格式与内容生成样例文档，支持单个下载、生成到服务器目录、以及“多格式 × 多大小 × 多数量”的批量生成，主要用于测试文件处理、压测与演示数据准备。

## 2. 角色与场景

| 角色 | 场景 |
|------|------|
| 测试工程师 | 批量生成不同大小/格式文件用于上传与解析测试 |
| 售前/文档人员 | 快速产出演示文档 |
| 研发工程师 | 验证文件解析、存储、预览能力 |

## 3. 功能清单

| 功能项 | 说明 | 页面/路由 | 状态 |
|--------|------|-----------|------|
| 单文件生成 | 指定格式、文件名、数据，直接下载 | `/docgen` | 可用 |
| 生成到目录 | 指定输出目录，文件落盘 | `/docgen` | 可用 |
| 批量生成 | 多格式 × 多大小 × 文件数 | `/docgen` | 可用 |
| 格式支持 | word / excel / ppt / image / md / pdf | — | 可用 |
| 大小梯度 | 100KB、500KB、1MB、2MB、5MB、10MB、15MB、20MB | — | 可用 |
| 命名规则 | `{格式标签}_{大小标签}_{基础名}_{序号}.{ext}` | — | 可用 |

## 4. 核心流程

```text
选择格式与参数
  → 单文件：POST /api/docgen/generate（返回二进制流）
  → 落盘：POST /api/docgen/generate-to-dir（返回目录与文件名）
  → 批量：POST /api/docgen/batch-generate
       → 解析 formats / sizes / fileCount
       → 生成目录（默认 docgen.output-dir）
       → 逐格式逐大小生成 fileCount 个文件
       → 返回生成清单
```

## 5. 业务规则

| 规则 | 说明 |
|------|------|
| 格式枚举 | `word`、`excel`、`ppt`、`image`、`md`、`pdf` |
| 默认输出目录 | `docgen.output-dir`，默认 `../../files` |
| 文件名安全 | `sanitizeBaseName` 清理非法字符 |
| 批量大小 | `sizes` 为空时按 0（不限制大小）处理 |
| 批量格式 | `formats` 为空直接返回失败（400，“请至少选择一个格式”） |
| Excel 参数 | `columnCount`、`rowSize`、`rowCount` |
| PDF 参数 | `pageCount`、`encrypt`、`pdfPassword`、`emptyContent`、`emptyPageCount` |
| 操作审计 | 三个接口均带 `@OperLog(module="docgen")` |

## 6. 异常与边界

| 场景 | 系统行为 |
|------|----------|
| 未选择格式 | `code=400`，“请至少选择一个格式” |
| 输出目录不可写 | 返回错误（具体提示待确认） |
| 文件名非法 | 清理后生成，可能重名覆盖 |
| 生成引擎异常 | 返回 500 / 业务错误 |
| 超大文件 | 受 JVM 内存与磁盘限制，无显式上限 |

## 7. 接口与数据

- 接口：`POST /api/docgen/generate`、`POST /api/docgen/generate-to-dir`、`POST /api/docgen/batch-generate`
- 详见 [04-api/modules/docgen.md](../../04-api/modules/docgen.md)
- 数据表：无独立业务表；生成记录通过操作日志（`sys_oper_log`）审计
- 生成引擎：`DocgenUtil` + Python 脚本（见 `docs/docgen/python-docgen-service.md`）

## 8. 验收标准

| 编号 | 验收项 |
|------|--------|
| AC-DOC-01 | 六种格式均能生成且可被对应软件打开 |
| AC-DOC-02 | 批量生成文件数量 = 格式数 × 大小数 × fileCount |
| AC-DOC-03 | 文件命名符合 `{格式}_{大小}_{基础名}_{序号}.{ext}` |
| AC-DOC-04 | PDF 加密参数生效，可用密码打开 |
| AC-DOC-05 | 生成到目录时目录不存在会自动创建 |

## 9. 待确认

1. `docgen.output-dir` 在生产 Helm 环境中的实际挂载路径。
2. 生成大文件的内存策略与上限。
3. Python 生成服务与 Java `DocgenUtil` 的调用关系（进程内还是 HTTP）。
