# 产品原型索引

> 日期：2026-10-18
> 版本：v0.2
> 状态：部分完成
> 数据来源：`docs/pipeline/prototype/`、`docs/pipeline/pipeline-management-ui-prototype.md`、`docs/pipeline/pipeline-management-prototype-spec.md`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：现有原型入口、截图清单与待建清单 |
| v0.2 | 2026-10-18 | 原型规范改为引用 `docs/frontend/prototype-first-standard.md`，明确新增页面必须先冻结原型 |

---

## 1. 现有原型

### 1.1 流水线管理（高保真）

| 项目 | 内容 |
|------|------|
| 入口 | [docs/pipeline/prototype/index.html](../../pipeline/prototype/index.html) |
| 说明 | [docs/pipeline/prototype/README.md](../../pipeline/prototype/README.md) |
| 规格 | [pipeline-management-prototype-spec.md](../../pipeline/pipeline-management-prototype-spec.md)（同时有 `.json`） |
| UI 说明 | [pipeline-management-ui-prototype.md](../../pipeline/pipeline-management-ui-prototype.md) |
| 交互 | 单文件可点击原型，hash 路由，7 个页面 |
| 截图 | `docs/pipeline/prototype/shots/01~17*.png`（1440×900 @2x） |
| 流程图 | `docs/pipeline/prototype/shots/15-flow-map.svg` |

查看方式：直接用浏览器打开 `index.html`，无需启动服务。

页面与截图：

| 页面 | 截图 |
|------|------|
| 流水线列表 | `shots/01-*.png` |
| 流水线编辑器 | `shots/02~08-*.png` |
| 运行详情/日志 | `shots/09~12-*.png` |
| 任务市场/参数 | `shots/13~14-*.png` |
| 流程总览 | `shots/15-flow-map.svg` |
| 其他状态 | `shots/16~17-*.png` |

> 具体文件名以 `shots/` 目录为准。

## 2. 其他模块原型状态

| 模块 | 原型 | 状态 |
|------|------|------|
| 项目管理（PM） | 无 HTML/Figma 原型 | 待建 |
| 用户 / 租户 | 无 | 待建 |
| 容器平台 | 无（仅有设计文档与页面实现） | 待建 |
| API 测试平台 | 无（仅有设计文档与页面实现） | 待建 |
| Docgen | 无 | 待建 |
| 文件解析 | 无 | 待建 |
| 图片处理 | 无 | 待建 |
| 工作台 / 产品切换器 | 无 | 待建 |

## 3. 原型规范

新增或改版页面统一遵守 [前端原型先行规范](../../frontend/prototype-first-standard.md)：

1. 先写 `prototype-spec.json` / `prototype-spec.md`，再生成单文件可点击 `index.html`；
2. 关键页面与状态输出 1440×900 @2x 截图到 `shots/`；
3. 同步提供 `README.md`（页面清单、路由、状态参数、画板清单、评审与冻结记录）；
4. 原型与 [feature-catalog.md](../02-features/feature-catalog.md) 的功能项一一对应；
5. 原型中的路由与 `frontend/src/router.tsx` 保持一致，避免“原型有、实现无”；
6. 原型评审冻结后才能进入测试用例与编码，冻结后修改必须升版并重新评审。

## 4. 待确认

1. 是否需要把原型统一迁移到 `docs/product/06-prototype/<module>/`（当前保留原位置并引用）。
2. 原型是否作为发布产物的一部分交付给客户。
3. 设计工具（Figma/MasterGo）的源文件位置与维护人。
