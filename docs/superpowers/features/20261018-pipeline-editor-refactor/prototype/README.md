# 编辑器重构原型引用

> 日期：2026-10-18
> 版本：v0.2
> 原型版本：`docs/pipeline/prototype` v0.8（已冻结）

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-18 | 建立引用：指向流水线模块原型与本次相关画板 |
| v0.2 | 2026-10-18 | 同步原型版本到 v0.8，补充新建页画板 |

---

## 1. 原型位置

| 文件 | 路径 |
|------|------|
| 可点击原型 | [`docs/pipeline/prototype/index.html`](../../../../pipeline/prototype/index.html) |
| 规格（机器可读） | [`pipeline-management-prototype-spec.json`](../../../../pipeline/pipeline-management-prototype-spec.json) |
| 规格（人读） | [`pipeline-management-prototype-spec.md`](../../../../pipeline/pipeline-management-prototype-spec.md) |
| 画板 | [`docs/pipeline/prototype/shots/`](../../../../pipeline/prototype/shots/) |

## 2. 本次相关画板

| 画板 | 覆盖内容 |
|------|----------|
| `04-editor.png`、`16-editor-invalid.png` | 标题旁名称、移除基本信息、无缩放、克隆检查器仓库配置、最近克隆摘要、任务名称/类型只读、克隆任务无高级设置 |
| `21-editor-new.png` | 新建页同样只读任务名称/类型；克隆仓库配置 |
| `05-editor-add-task.png` | 阶段自动命名后的添加任务面板 |
| `03-detail.png` | 详情六个指标卡（含 Commit） |
| `11-modal-run-params.png`、`12-modal-delete.png`、`13-modal-cancel-run.png`、`17-modal-discard.png`、`19-credential-invalid.png` | 全站弹框统一圆角/底色/遮罩/标题与底部分隔线 |

## 3. 评审

评审结论记录在 [`docs/pipeline/prototype/README.md` §8](../../../../pipeline/prototype/README.md)。冻结前不得进入页面代码实现。
