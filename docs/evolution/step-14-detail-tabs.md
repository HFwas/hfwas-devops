# Step 14：详情 Tab 可配置

> 状态：已完成  
> 日期：2026-07-10  
> 路线图：[pm-evolution-roadmap.md](../pm-evolution-roadmap.md) Step 14  
> 对应对比文档：[pm-jira-comparison.md](../pm-jira-comparison.md) §5 字段布局  
> 原则：目录代码定义，启用与排序存 `layout_config.detailTabs`

---

## 1. 范围

| 层 | 内容 |
|----|------|
| 目录 | `DetailTabCatalog`：已实现 description / activity / comments / links；预留 files 等 |
| 配置 | `TypeFieldLayoutConfig.detailTabs`（有序） |
| 设置 | 事项类型字段方案页「详情 Tab」启用 / 排序 |
| 详情 | 按配置渲染；描述迁入「详情」Tab |

### 非目标

- 自定义 Tab / SPI  
- 文件、分支、用例真实能力  

---

## 2. API

- `POST /pm/meta/detail-tabs` → 已实现目录  
- `layout/get`、`layout/save` 含 `detailTabs`；空则默认四 Tab  

---

## 3. 验收

1. 可关掉「关联」等 Tab，刷新详情页一致。  
2. 排序生效。  
3. 方案导入导出带 `detailTabs`。  
4. 旧布局无 `detailTabs` 时走默认。  

---

## 4. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-07-10 | 初版并落地 |
