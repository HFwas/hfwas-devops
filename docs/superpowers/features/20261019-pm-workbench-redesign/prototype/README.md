# PM 工作台原型

> 日期：2026-10-19
> 版本：v1.0
> 状态：**待评审**（未冻结，未进入实现）
> 功能设计：[../design.md](../design.md)
> 原型规格：[prototype-spec.md](./prototype-spec.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v1.0 | 2026-10-19 | 初版：工作项列表、项目列表、筛选/展示配置/批量/预览/新建及其状态矩阵 |

---

## 1. 怎么打开

零依赖单文件，直接双击或用浏览器打开：

```text
prototype/index.html
```

原型导航面板固定在右下角，可一键切换所有状态；`capture=1` 时隐藏导航与版本角标。

> 参数既可放在 hash 内（`#/list?state=empty`），也可放在页面查询串（`?capture=1#/list`）。推荐截图使用后者。

## 2. 路由

| hash | 页面 |
|------|------|
| `#/list` | 工作项列表（P01–P07） |
| `#/projects` | 项目列表（P08） |

工作项详情本版不改造，仅提供右侧快速预览（O04）。

## 3. 状态参数

| 参数 | 取值 | 说明 |
|------|------|------|
| `state` | `empty` / `loading` / `error` / `permission` / `dense` / `long` / `selected` / `invalid` | 页面状态 |
| `overlay` | `filter` / `columns` / `delete` / `detail` / `create` / `saveview` | 弹层 |
| `q` | 任意文本 | 搜索关键词 |
| `view` | `all` / `mine` / `week` / `defect` | 视图页签 |
| `c_status` / `c_priority` / `c_assignee` / `c_type` | 逗号分隔值 | 筛选条件 |
| `sort` | `field:ASC` / `field:DESC` | 排序 |
| `cols` | 列 id 逗号列表 | 列顺序与显示 |
| `density` | `standard` / `compact` | 行密度 |
| `page` / `size` | 数字 | 分页 |
| `sel` | 行 id 逗号列表 | 当前页选中 |
| `theme` | `dark` | 暗色主题 |
| `capture` | `1` | 截图模式，隐藏原型 UI、关闭动画 |

## 4. 画板清单

全部为 1440×900 @2x。

| # | 文件 | URL |
|---|------|-----|
| 01 | `shots/01-list-default.png` | `?capture=1#/list` |
| 02 | `shots/02-list-empty.png` | `?capture=1#/list?state=empty` |
| 03 | `shots/03-list-loading.png` | `?capture=1#/list?state=loading` |
| 04 | `shots/04-list-error.png` | `?capture=1#/list?state=error` |
| 05 | `shots/05-list-permission.png` | `?capture=1#/list?state=permission` |
| 06 | `shots/06-list-dense.png` | `?capture=1#/list?state=dense` |
| 07 | `shots/07-list-long.png` | `?capture=1#/list?state=long` |
| 08 | `shots/08-list-filtered.png` | `?capture=1#/list?c_status=in_progress&c_priority=high&dirty=1` |
| 09 | `shots/09-list-selected.png` | `?capture=1#/list?state=selected` |
| 10 | `shots/10-overlay-filter.png` | `?capture=1#/list?overlay=filter` |
| 11 | `shots/11-overlay-columns.png` | `?capture=1#/list?overlay=columns` |
| 12 | `shots/12-overlay-detail.png` | `?capture=1#/list?overlay=detail&id=1` |
| 13 | `shots/13-overlay-delete.png` | `?capture=1#/list?state=selected&overlay=delete` |
| 14 | `shots/14-overlay-create.png` | `?capture=1#/list?overlay=create` |
| 15 | `shots/15-overlay-create-invalid.png` | `?capture=1#/list?overlay=create&state=invalid` |
| 16 | `shots/16-projects.png` | `?capture=1#/projects` |
| 17 | `shots/17-list-dark.png` | `?capture=1#/list?theme=dark` |

### 4.1 重新截图

```bash
cd docs/superpowers/features/20261019-pm-workbench-redesign/prototype
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
BASE="file://$PWD/index.html"
"$CHROME" --headless=new --disable-gpu --hide-scrollbars \
  --force-device-scale-factor=2 --window-size=1440,900 --virtual-time-budget=2500 \
  --screenshot=shots/01-list-default.png "$BASE?capture=1#/list"
```

## 5. 已实现的原型交互

- 搜索防抖过滤，清除后恢复，URL 同步；
- 视图页签切换（全部 / 我的待办 / 本周到期 / 缺陷攻坚）；
- 筛选面板应用后生成条件 chip，可逐条移除或清空；
- 表头排序三态（升序 → 降序 → 默认）；
- 列显示、顺序调整、恢复默认、密度切换；
- 当前页全选 / 单选、批量栏、批量删除二次确认；
- 分页页码、每页条数、跳页；
- 行更多菜单（预览、复制编号、复制链接、删除）；
- 快速预览抽屉；
- 新建工作项表单校验（标题必填、截止时间不可早于今天）；
- 项目列表搜索与空态；
- 亮 / 暗主题。

## 6. 评审记录

| 版本 | 日期 | 评审人 | 结论 | 冻结提交 | 备注 |
|------|------|--------|------|----------|------|
| 1.0 | 2026-10-19 | 产品 / 前端 / 测试 | 待评审 | — | 等待用户查看效果 |

冻结后修改必须升版并重新评审。

## 7. 已知限制

- 数据为固定样例，分页翻页使用轮转数据，不代表真实后端；
- 筛选面板首版只演示两个条件，未实现嵌套分组；
- 分组展示出现在规格中，本版原型未落地；
- 跨页选择不做；
- 保存视图仅演示弹层，未持久化；
- 详情页本版不改造，仅提供快速预览抽屉。
