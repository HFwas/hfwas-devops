# 第 1 批原型引用

> 日期：2026-10-18
> 版本：v0.2
> 原型版本：`docs/pipeline/prototype` v0.2（已冻结；模块当前基线 v0.11 待评审）

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-18 | 建立引用：指向流水线模块原型 v0.2 与第 1 批新增画板 |
| v0.2 | 2026-10-18 | 引用模块原型契约与真实应用截图；注明 v0.2 已冻结、模块基线 v0.11 待评审 |

---

## 1. 原型位置

流水线原型是模块级长期基线，保留在原路径，不在功能目录复制：

| 文件 | 路径 |
|------|------|
| 可点击原型 | [`docs/pipeline/prototype/index.html`](../../../../pipeline/prototype/index.html) |
| 规格（机器可读） | [`pipeline-management-prototype-spec.json`](../../../../pipeline/pipeline-management-prototype-spec.json) |
| 规格（人读） | [`pipeline-management-prototype-spec.md`](../../../../pipeline/pipeline-management-prototype-spec.md) |
| 画板 | [`docs/pipeline/prototype/shots/`](../../../../pipeline/prototype/shots/) |
| 原型契约 | [`docs/pipeline/prototype/prototype-contract.md`](../../../../pipeline/prototype/prototype-contract.md) |
| 真实应用截图 | [`docs/pipeline/prototype/actual/`](../../../../pipeline/prototype/actual/) |

## 2. 第 1 批相关画板

| 画板 | 覆盖的 UX |
|------|-----------|
| `01-list.png`、`20-list-row-menu.png` | UX-08 整行可点、操作收敛 |
| `08-run-running.png`、`09-run-waiting.png`、`10-run-failed.png` | UX-02 返回、UX-09 触发方式 |
| `18-credentials.png`、`19-credential-invalid.png` | UX-06 表单错误可见 |
| 全部画板 | UX-01 顶栏搜索、UX-19 侧栏分组 |

## 3. 状态与路由

| 用途 | URL |
|------|-----|
| 凭证页 | `index.html?capture=1#/credentials` |
| 新建凭证校验失败 | `index.html?capture=1&state=invalid&overlay=credential-new#/credentials` |
| 列表行内菜单 | `index.html?capture=1&overlay=row-menu#/list` |

## 4. 评审

评审结论记录在 [`docs/pipeline/prototype/README.md` §8](../../../../pipeline/prototype/README.md)。冻结前不得进入页面代码实现。

当前实现差异、分类与处理见 [`prototype-contract.md`](../../../../pipeline/prototype/prototype-contract.md)；本批相关的 D01–D04、D09 仍待确认或处理。
