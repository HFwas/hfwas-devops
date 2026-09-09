# 前端 JS Bundle 体积分析与优化方案

## 问题

`http://localhost:8000/assets/index-C6GhgPiI.js` — **1.58 MB**（未压缩）

`index.html` 中 `<script>` 仅引用了这一个 JS 文件，为 Vite 构建的主入口 chunk，包含应用首次加载时必须解析的全部代码。

---

## 根因分析

### 1. `app.use(naive)` 全量导入 naive-ui（最大 contributor）

**位置**：`main.ts`

```ts
import naive from 'naive-ui'
app.use(naive)
```

naive-ui 的 `preset.mjs` 会遍历全部 **99+ 个组件** 并全局注册：

```js
import * as components from "./components.mjs"  // 99+ 组件 barrel export
const naive = create({
  components: Object.keys(components).map(key => components[key])
})
```

尽管 Vite/Rollup 有 tree-shaking，但 `Object.keys(components).map(...)` 的动态引用 + barrel export（`export *`）的组合，使得 Rollup **无法安全地剔除未使用的组件**。

实际 `components.d.ts` 中只注册了 **69 个组件**，但 bundle 包含了全部 99+ 个组件及其代码。

naive-ui 还带来以下重量级传递依赖：

| 依赖 | disk 大小 | 说明 |
|---|---|---|
| `date-fns` | 26 MB | 日期处理全库 |
| `lodash` | 4.9 MB | 非 tree-shakable 版本 |
| `lodash-es` | 2.6 MB | ES 版本（但 barrel import 难以裁剪） |
| `date-fns-tz` | ~1 MB | 时区处理 |

### 2. naive-ui locales barrel export → 全量 63 个 locale 文件被引入

```ts
// main.ts → import { zhCN } from 'naive-ui' 
// naive-ui/index.mjs → export * from "./locales/index.mjs"
// 该文件 re-exports arDZ, azAZ, csCZ, daDK, ... zhCN, 共 63 个 locale
```

应用只需 `zhCN` 一个 locale，但 barrel export（`export *`）导致 Rollup 难以判断哪些 locale 未被使用。

### 3. 没有 `manualChunks` 配置 → vendor 全部在主 chunk

`vite.config.ts` 中缺少 `build.rollupOptions.output.manualChunks`。Vite 默认将所有代码打包进入口 chunk：

- 路由组件使用了 `() => import(...)` 懒加载 ✅（做得对）
- 但第三方库（vue, vue-router, pinia, keycloak-js, naive-ui, lodash-es, date-fns）全部在主 chunk

### 4. keycloak-js 被同步导入

`main.ts` 中 `import { initKeycloak } from '@/shared/keycloak'` → keycloak-js（~120KB）进入主 chunk。

---

## Bundle 大小分布估算

| 类别 | 估算大小 | 说明 |
|---|---|---|
| naive-ui 主体（组件 + utils） | ~600-700 KB | tree-shake 不掉的 barrel 残留 |
| date-fns + lodash-es | ~200-250 KB | naive-ui 传递依赖 |
| vue + vue-router + pinia | ~150 KB | 框架本身 |
| keycloak-js | ~60 KB | OIDC Client |
| 应用本身（AppShell, router, stores） | ~100-150 KB | 业务代码 |
| naive-ui locales（all 63） | ~150-200 KB | barrel 引入 |
| 其他（css-render, vooks, seemly 等） | ~100-200 KB | naive-ui 内部库 |

---

## 优化方案（按收益排序）

### 方案 1（最高收益 ✅）：去除 `app.use(naive)`，改为按需导入

**现状**：`app.use(naive)` 注册全部 99+ 组件，barrel export 阻止 tree-shaking。

**方案**：放弃全量注册，手动注册仅用到的组件。

```ts
// main.ts — 移除 app.use(naive)，替换为按需导入
import {
  NButton, NLayout, NLayoutHeader, NLayoutContent,
  NInput, NSelect, NForm, NFormItem, NDataTable, NModal,
  NDrawer, NDrawerContent, NMessageProvider, NDialogProvider,
  NConfigProvider, NTag, NSpace, NCard, NSpin, NText,
  NEmpty, NIcon, NTabPane, NTabs, NTree, NMenu,
  NPagination, NPopover, NAvatar,
  NDescriptions, NDescriptionsItem, NEllipsis,
  NDivider, NCheckbox, NCheckboxGroup,
  NRadio, NRadioGroup, NRadioButton,
  NDatePicker, NColorPicker, NInputNumber,
  NSwitch, NSlider, NProgress, NSkeleton,
  NCollapse, NCollapseItem,
  NTimeline, NTimelineItem, NPageHeader, NPopconfirm,
  NScrollbar, NThing, NResult, NList, NListItem,
  NButtonGroup, NGi, NGrid, NAlert, NBadge
} from 'naive-ui'

const components = [
  NButton, NLayout, NLayoutHeader, NLayoutContent,
  NInput, NSelect, NForm, NFormItem, NDataTable, NModal,
  NDrawer, NDrawerContent, NMessageProvider, NDialogProvider,
  NConfigProvider, NTag, NSpace, NCard, NSpin, NText,
  NEmpty, NIcon, NTabPane, NTabs, NTree, NMenu,
  NPagination, NPopover, NAvatar,
  NDescriptions, NDescriptionsItem, NEllipsis,
  NDivider, NCheckbox, NCheckboxGroup,
  NRadio, NRadioGroup, NRadioButton,
  NDatePicker, NColorPicker, NInputNumber,
  NSwitch, NSlider, NProgress, NSkeleton,
  NCollapse, NCollapseItem,
  NTimeline, NTimelineItem, NPageHeader, NPopconfirm,
  NScrollbar, NThing, NResult, NList, NListItem,
  NButtonGroup, NGi, NGrid, NAlert, NBadge
]
components.forEach(c => app.use(c))
```

> **注意**：`app.use(naive)` 和 `NaiveUiResolver` 目前是重复注册 —— 全量注册 + 自动按需解析，后者变得没有意义。移除 `app.use(naive)` 后，`unplugin-vue-components` 的 `NaiveUiResolver` 仍可在模板中自动解析 `<n-button>` 等标签。

**预期收益**：主 chunk 减少 **40-50%**（约 700-800 KB）

---

### 方案 2（高收益 ✅）：`manualChunks` 拆分 vendor

在 `vite.config.ts` 中追加 `build.rollupOptions.output.manualChunks`：

```ts
// vite.config.ts
export default defineConfig({
  // ... 已有配置
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          'naive-ui': ['naive-ui'],
          'vendor-base': ['vue', 'vue-router', 'pinia'],
          'shared-utils': ['lodash-es', 'date-fns'],
        },
      },
    },
  },
})
```

**预期收益**：主 chunk 拆成 3-4 个独立 chunk（每块 200-400 KB）：
- 并行下载
- 浏览器缓存独立性（更新业务代码不影响 vendor 缓存）
- 利用 HTTP/2 多路复用

---

### 方案 3（中收益）：独立导入 naive-ui locale 替代 barrel

```ts
// shared/naive-locale.ts
// 改为从具体路径导入，而不是从 barrel
import { zhCN } from 'naive-ui/es/locales/common/zhCN.mjs'
import { dateZhCN } from 'naive-ui/es/date-locales/zhCN.mjs'
```

或验证 Vite 是否能 tree-shake 掉其他 62 个 locale（如果是则不需要此修改；如否再执行此方案）。

---

### 方案 4（中收益）：异步加载 keycloak-js

将 keycloak-js 拆到独立 chunk，减少主 chunk 大小：

```ts
// main.ts
async function bootstrap() {
  const { initKeycloak } = await import('@/shared/keycloak')
  await initKeycloak()
  const app = createApp(App)
  app.use(createPinia())
  app.use(router)
  const { default: naive } = await import('naive-ui')
  app.use(naive)
  app.mount('#app')
}
void bootstrap()
```

**注意**：这会使 Vue 应用挂载晚于常规同步方式，需权衡首屏渲染延迟 vs 脚本加载体积。

---

### 方案 5（低收益但零成本）：启用 gzip/brotli 压缩

Web 服务器配置压缩，减少传输体积：

```nginx
# Nginx
gzip on;
gzip_types application/javascript text/css application/json;
gzip_min_length 256;
gzip_comp_level 6;

# 或 brotli（需要 ngx_brotli 模块）
# brotli on;
# brotli_types application/javascript text/css application/json;
# brotli_comp_level 6;
```

| 压缩 | 1.58MB 原文件 | 压缩后 |
|---|---|---|
| gzip (level 6) | 1.58 MB | ~450-500 KB |
| brotli (level 6) | 1.58 MB | ~350-400 KB |

---

### 方案 6（长期）：CSR → SSR 迁移

如首屏加载性能敏感，可考虑使用 Nuxt 3 实现服务端渲染，提供完整 HTML 而非先加载 1.5MB JS 再渲染。

---

## 推荐实施优先级

| 优先级 | 方案 | 工作量 | 收益（主 chunk 减小） |
|---|---|---|---|
| **P0** | 方案 1 + 2：按需导入 naive-ui + manualChunks | ~2h | ~70% → ~450 KB |
| **P1** | 方案 5：nginx gzip 压缩 | ~10min | 传输体积从 1.58 MB → ~450 KB |
| **P2** | 方案 3：locale 独立导入 | ~10min | ~100-150 KB |
| **P2** | 方案 4：keycloak-js 异步 | ~30min | ~60 KB |
| **P3** | 方案 6：SSR 迁移 | ~数天 | 首屏体验质变 |

## 结论

**立即执行 P0**：按需导入 naive-ui + 添加 `manualChunks`，预计 2 小时可将主 JS 从 **1.58 MB 降至 ~450 KB（gzip 后约 150 KB）**。后续加上 nginx 压缩，传输体积可降至 **~150 KB**。