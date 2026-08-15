# 接口测试壳层紧凑视觉精修设计

> 日期：2026-08-15  
> 状态：已确认（待实现）  
> 范围：前端样式与密度精修；**不改信息架构与后端契约**  
> 前置：`2026-08-15-api-test-collection-first-shell-design.md`

---

## 1. 背景与目标

集合优先 `ApiTestShell` 功能已可用，但整体偏「控制台默认间距」：侧栏行高偏大、方法色单一、URL/参数区留白偏多、禁用态不够醒目。

**目标：** 在现有结构上做 **紧凑布局精修**，提升一屏信息量与扫读层次，仍跟随控制台浅/深主题。

**非目标：** 环境栏并入 Tab 行；Postman 深色侧栏；换字体栈；重写 JSON 可视化；新功能。

---

## 2. 已确认决策

| 项 | 选择 |
|----|------|
| 范围 | **A**：布局精修，不动 IA |
| 密度 | **1**：紧凑 |
| 实现路径 | **方案 1**：CSS 令牌 + 组件样式精修（少量 class / 树渲染 class） |

---

## 3. 密度令牌

挂在 `.api-test-shell`（及必要时子根），深色模式只调整方法色亮度，间距令牌共用：

| 令牌 | 用途 | 建议值 |
|------|------|--------|
| `--api-density-pad-y` | 行 / 工具条垂直 padding | `4px`–`6px` |
| `--api-density-pad-x` | 水平 padding | `8px`–`10px` |
| `--api-row-height` | 侧栏 / 树行目标高度 | `~28px` |
| `--api-font-sm` | 辅助文字 | `12px` |
| `--api-font` | 正文 | `13px` |
| `--api-method-get` 等 | HTTP 方法色 | 固定色板；`html.dark` 略提亮 |

继续使用全局 `--wb-border` / `--wb-chip-bg` / `--wb-muted` / `--wb-card-bg` / `--api-test-accent*`。

---

## 4. 分区改动

### 4.1 侧栏（`CollectionsSidebar` + `CollectionTree`）

- 工具条更矮；`COLLECTIONS` 小号字重 + 搜索同排收紧
- 集合行：hover / 选中用 chip / accent-soft
- 方法标签：按 method 着色（非一律蓝）
- 禁用项：降低透明度 + 短徽标「已禁用」（或等价紧凑标记）
- 文件夹前缀：去掉 emoji，改简洁图标/字符
- Run / Hist：保持可用，视觉降权（tiny / quaternary）

### 4.2 请求 Tab 与工作区（`RequestTabBar` + `RequestWorkspace`）

- Tab 行高降低；激活态底边用 accent
- URL 栏 padding 从约 `12px 16px` 收到约 `6px 10px`；发送按钮保持主色、略缩小
- Params 等编辑区减少顶部空白，使「添加」更贴近表格内容
- 请求/响应分割条 hover 用 accent

### 4.3 响应区

- 状态码 / 耗时 / 大小 / 断言条更紧凑
- Body 字号约 `12–13px`，减少多余 padding
- **不**引入新 JSON 编辑器组件

### 4.4 壳层杂项

- 环境 header、侧栏 resizer 与现有 accent hover 对齐密度
- `CollectionOverviewTab` 内边距与壳层密度一致（轻量）

---

## 5. 文件影响（预期）

| 文件 | 改动类型 |
|------|----------|
| `shell/views/ApiTestShell.vue` | 定义密度令牌 |
| `shell/components/CollectionsSidebar.vue` | 工具条 / 行样式 |
| `collection/components/CollectionTree.vue` | 方法色、禁用徽标、去 emoji |
| `shell/components/RequestTabBar.vue` | Tab 密度与激活态 |
| `shell/components/RequestWorkspace.vue` | URL 栏 / tabs / 分割条 padding |
| 响应相关子组件（若有独立样式） | 状态条紧凑 |
| `CollectionOverviewTab.vue` | 轻量 padding |

测试：以现有 Vitest 不回归为主；不强制新增视觉快照。若 class / testid 不变，多数测试无需改。

---

## 6. 成功标准

1. 同样视口下侧栏与请求区可见行数明显增加（主观可感知紧凑）。
2. GET/POST/… 方法色可区分；禁用项一眼可辨。
3. 浅色 / 深色主题下对比度可接受，无硬编码破坏 `--wb-*`。
4. `npm test -- src/modules/api-test/shell/` 保持通过。
