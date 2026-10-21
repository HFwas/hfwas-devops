# 自定义事项类型原型

> 日期：2026-10-19
> 版本：v1.0
> 状态：**待评审**（未冻结，未进入实现）
> 功能设计：[../design.md](../design.md)
> 原型规格：[prototype-spec.md](./prototype-spec.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v1.0 | 2026-10-19 | 初版：内置 5 类 + 自定义类型列表、四步向导、字段布局、工作流与项目启用范围 |

---

## 1. 怎么打开

零依赖单文件，直接双击：

```text
prototype/index.html
```

右下角「原型导航」可切换全部状态；`capture=1` 时隐藏导航与版本角标。

## 2. 路由

| hash | 页面 |
|------|------|
| `#/types` | 事项类型列表 |
| `#/types/new?step=basic|fields|workflow|preview` | 新建自定义类型 |
| `#/types/edit?code=story&step=...` | 编辑类型 |

## 3. 状态参数

| 参数 | 取值 |
|------|------|
| `state` | `empty` / `loading` / `error` / `permission` / `dense` / `invalid` |
| `tab` | `all` / `builtin` / `custom` / `disabled` |
| `q` | 搜索名称、编码或描述 |
| `status` | `enabled` / `disabled` |
| `off` | 停用类型 code，逗号分隔 |
| `overlay` | `duplicate` / `delete` / `disable` / `scheme` / `template-reset` |
| `code` | 操作对象编码 |
| `blocked` | `1` 删除被阻止 |
| `step` | `basic` / `fields` / `workflow` / `preview` |
| `tpl` / `wft` | 模板 / 工作流模板 |
| `lf` / `cf` / `rf` | 列表 / 创建 / 必填字段 key |
| `icon` / `color` | 图标与颜色 |
| `theme` | `dark` |
| `capture` | `1` 截图模式 |

## 4. 画板清单

全部为 1440×900 @2x。

| # | 文件 | URL |
|---|------|-----|
| 01 | `shots/01-types-default.png` | `?capture=1#/types` |
| 02 | `shots/02-types-empty.png` | `?capture=1#/types?state=empty` |
| 03 | `shots/03-types-loading.png` | `?capture=1#/types?state=loading` |
| 04 | `shots/04-types-error.png` | `?capture=1#/types?state=error` |
| 05 | `shots/05-types-permission.png` | `?capture=1#/types?state=permission` |
| 06 | `shots/06-types-dense.png` | `?capture=1#/types?state=dense` |
| 07 | `shots/07-editor-basic.png` | `?capture=1#/types/new?step=basic` |
| 08 | `shots/08-editor-fields.png` | `?capture=1#/types/new?step=fields` |
| 09 | `shots/09-editor-workflow.png` | `?capture=1#/types/new?step=workflow` |
| 10 | `shots/10-editor-preview.png` | `?capture=1#/types/new?step=preview` |
| 11 | `shots/11-editor-invalid.png` | `?capture=1#/types/new?state=invalid` |
| 12 | `shots/12-overlay-duplicate.png` | `?capture=1#/types?overlay=duplicate&code=story` |
| 13 | `shots/13-overlay-delete-blocked.png` | `?capture=1#/types?overlay=delete&code=story&blocked=1` |
| 14 | `shots/14-overlay-delete-allowed.png` | `?capture=1#/types?overlay=delete&code=research&blocked=0` |
| 15 | `shots/15-overlay-disable.png` | `?capture=1#/types?overlay=disable&code=story` |
| 16 | `shots/16-overlay-scheme.png` | `?capture=1#/types?overlay=scheme` |
| 17 | `shots/17-types-dark.png` | `?capture=1#/types?theme=dark` |
| 18 | `shots/18-overlay-template-reset.png` | `?capture=1#/types/new?step=basic&overlay=template-reset` |

### 4.1 重新截图

```bash
cd docs/superpowers/features/20261019-pm-work-item-types/prototype
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
BASE="file://$PWD/index.html"
"$CHROME" --headless=new --disable-gpu --hide-scrollbars \
  --force-device-scale-factor=2 --window-size=1440,900 --virtual-time-budget=2500 \
  --screenshot=shots/01-types-default.png "$BASE?capture=1#/types"
```

## 5. 已实现的原型交互

- 类型搜索、页签筛选、状态筛选；
- 启用开关即时切换（URL 记录 `off`）；
- 新建四步向导前进 / 后退，步骤可点击直达；
- 模板选择同步图标、颜色、字段与工作流；
- 字段的列表 / 创建 / 必填勾选与顺序调整；
- 工作流模板切换与状态流预览；
- 新建表单与列表行预览；
- 复制类型弹层；
- 删除（可删 / 被阻止两种）、停用 / 启用确认；
- 项目启用范围抽屉的勾选与排序；
- 无权限状态隐藏所有写操作；
- 亮 / 暗主题。

## 6. 评审记录

| 版本 | 日期 | 评审人 | 结论 | 冻结提交 | 备注 |
|------|------|--------|------|----------|------|
| 1.0 | 2026-10-19 | 产品 / 前端 / 测试 | 待评审 | — | 等待查看效果 |

## 7. 已知限制

- 内置 `iteration` 为本次新增，后端种子尚未落地；
- 使用量、字段数、描述、模板来源为样例数据，依赖后端补字段；
- 工作流只做模板选择与预览，画布编辑器复用既有页面；
- 字段定义的新增/编辑仍走「字段配置」页，本页只控制显示位置与顺序；
- 项目启用范围的排序仅在原型内生效，保存后未持久化；
- 「从模板创建」复用复制弹层，实际交互以后端能力为准。
