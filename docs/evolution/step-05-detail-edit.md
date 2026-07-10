# Step 05：详情页完整编辑（标题 / 描述）

> 状态：已完成  
> 日期：2026-07-10  
> 路线图：[pm-evolution-roadmap.md](../pm-evolution-roadmap.md) Step 5  
> 对应对比文档：[pm-jira-comparison.md](../pm-jira-comparison.md) §14.1  
> 原则：复用现有 save API 与 Markdown 组件，无后端变更

---

## 1. 动机与范围

### 1.1 动机

详情页标题只读、描述仅预览，与 Jira 核心交互差距大。侧栏已有字段防抖保存，本步把标题与描述收口到主列可编辑。

### 1.2 范围内

| 层 | 内容 |
|----|------|
| 标题 | 页头 `n-input` 可编辑 + 防抖保存；空标题拦截 |
| 描述 | 主列常驻；预览 / 编辑切换；复用 `PmMarkdownEditor` |
| 侧栏 | 排除 `title`（已排除 `description`） |
| 文档 | 本文件 + roadmap + comparison |

### 1.3 非目标

- @mention / 非 Markdown 富文本
- 独立详情 Screen 布局配置
- 链接删除、看板拖拽

---

## 2. 交互

```text
页头标题输入 → scheduleSave(400ms) → pmWorkItemApi.save
描述编辑 / 完成 → scheduleSave → save
侧栏字段变更 → 同上（状态变更仍走 transition）
```

保存成功后轻量刷新 `updateTime`；失败则 `load()` 回滚。

---

## 3. 验收

1. 可改标题并自动保存；空标题有提示且不提交。
2. 描述可预览 / 编辑；空描述可点「添加描述」。
3. 侧栏无标题、描述重复入口。
4. 变更后活动流可刷新看到记录。

---

## 4. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-07-10 | 初版并落地 |
