# Step 15：事项类型功能（Type Features）

> 状态：已完成  
> 日期：2026-07-11  
> 路线图：[pm-evolution-roadmap.md](../pm-evolution-roadmap.md) Step 15  
> 对应对比文档：[pm-jira-comparison.md](../pm-jira-comparison.md) §5 / §11  
> 原则：目录代码定义，启用与配置存 `layout_config.features`；与 detailTabs 同构

---

## 1. Jira 对照

**Jira 没有与本能力同构的原生机制。**

| 诉求 | Jira | HFWAS |
|------|------|-------|
| 按事项类型开关列表能力（如导入导出） | 基本没有；功能多在项目 / 站点 / App 级 | ✅ `features.work_item_io.enabled` |
| 按类型存默认导入/导出字段 | 原生没有；导出多为当场选列 | ✅ `exportFieldKeys` / `importFieldKeys` |
| 统一「类型设置里的插件目录」 | Marketplace / Forge 自建配置，非类型一等公民 | ✅ `FeatureCatalog` + 设置页「功能」Tab |

侧栏「功能模块」对应 Jira **Components**，不是本能力，勿混用。

---

## 2. 范围

| 层 | 内容 |
|----|------|
| 目录 | `FeatureCatalog`：首期 `work_item_io`（导入导出，`list_actions`） |
| 配置 | `TypeFieldLayoutConfig.features.work_item_io` |
| 设置 | 事项类型页「功能」Tab：开关 + 默认字段多选 |
| 列表 | `enabled` 控制更多操作；抽屉默认勾选配置字段 |

### 非目标

- 第三方插件市场 / Forge 动态加载  
- 把 Components 改造成插件  
- 每个功能独立微服务  

---

## 3. 数据形状

```json
{
  "features": {
    "work_item_io": {
      "enabled": true,
      "exportFieldKeys": ["title", "status", "priority"],
      "importFieldKeys": ["title", "status"]
    }
  }
}
```

**默认（绿野）：** `features` / `work_item_io` 缺失时 `enabled=true`，避免列表突然失去导入导出；字段 key 为空时前端回退现有 `showInList` / 内置默认。

---

## 4. API

- `POST /pm/meta/features` → 已实现功能目录  
- `layout/get`、`layout/save` 含 `features`  
- 事项类型方案导入导出 JSON 的 `fieldScheme.layout` 带上 `features`  

---

## 5. 验收

1. 关闭导入导出后，该类型列表无「更多操作」导入/导出入口。  
2. 配置默认导出/导入字段后，打开抽屉默认勾选一致；清空后回退旧逻辑。  
3. 方案导入导出带 `features`。  
4. 无 `features` 的布局读出后服务端回填默认开启。  

---

## 6. 修订记录

| 日期 | 说明 |
|------|------|
| 2026-07-11 | 初版并落地 |
