# ApiTestShell Frontend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace split api-test pages with a Postman/Apifox-style `ApiTestShell` (left module rail + resource tree + multi-request tabs + request/response workspace).

**Architecture:** New `shell/` module owns layout and `workspaceStore` (tabs, active module, layout sizes). Reuse existing `define` / `collection` / `environment` / `debug` stores and editors. Results are stored per tab (not only on global `debugStore.currentResult`). No backend contract changes.

**Tech Stack:** Vue 3, TypeScript, Vite, Pinia, Vue Router, Naive UI, Vitest (new, for store unit tests)

**Spec:** `docs/superpowers/specs/2026-08-14-api-test-shell-frontend-design.md`

## Global Constraints

- Greenfield: no legacy dual layout compatibility; old routes redirect into the shell
- Do **not** change backend REST contracts this phase
- Scripts / assertions / extracts: editable in UI and sent on **Execute**; **Save** persists definition `path/method/params/contentType` only (no dedicated persist APIs yet)
- Collection Item opens linked `definitionId`; Save writes that definition (no item-level request override)
- Auth / Docs / Settings / Visualize / Specs / Mocks: Coming-soon placeholders only
- Theme: follow console light/dark via existing CSS variables / Naive theme — no hard-coded Postman dark-only skin
- Project id remains `1` if still hard-coded elsewhere; do not invent a new project picker

---

## File Structure

| Path | Responsibility |
|------|----------------|
| `frontend/src/modules/api-test/shell/types/workspace.ts` | `ShellModule`, `TabSource`, `RequestDraft`, `RequestTab` |
| `frontend/src/modules/api-test/shell/stores/workspace.ts` | Tab open/activate/close, dirty, layout sizes, active module |
| `frontend/src/modules/api-test/shell/stores/workspace.test.ts` | Vitest unit tests for tab logic |
| `frontend/src/modules/api-test/shell/views/ApiTestShell.vue` | Shell layout composition |
| `frontend/src/modules/api-test/shell/components/ModuleRail.vue` | Left icon rail |
| `frontend/src/modules/api-test/shell/components/ResourcePanel.vue` | Switches API / Collection / Env / Placeholder |
| `frontend/src/modules/api-test/shell/components/ApiTreePanel.vue` | Wraps/adapts group+definition tree → open tab |
| `frontend/src/modules/api-test/shell/components/CollectionPanel.vue` | Collection list → folder tree → open item / run |
| `frontend/src/modules/api-test/shell/components/EnvironmentPanel.vue` | Env list + variable editor |
| `frontend/src/modules/api-test/shell/components/PlaceholderPanel.vue` | Docs/Specs/Mocks empty state |
| `frontend/src/modules/api-test/shell/components/RequestTabBar.vue` | Multi-tab chrome |
| `frontend/src/modules/api-test/shell/components/RequestWorkspace.vue` | URL bar + request tabs + response for active tab |
| `frontend/src/modules/api-test/shell/components/ComingSoonPane.vue` | Shared placeholder pane |
| `frontend/src/modules/api-test/shell/components/CollectionRunDrawer.vue` | In-shell run result / history |
| `frontend/src/modules/api-test/define/router/apiTestRoutes.ts` | Shell entry + redirects |
| `frontend/src/shared/console/tabs.ts` | Clear api-test secondary tabs |
| `frontend/src/shared/console/products.ts` | Product path → `/api-test` |
| `frontend/package.json` / `vite.config.ts` | Vitest scripts/config |

Reuse (do not duplicate): `KeyValueEditor`, `ScriptEditor`, `AssertionEditor`, `ExtractEditor`, `ApiWorkspaceSidebar` patterns, `CollectionTree`, `VariableList`, `EnvironmentSelector`, `debugApi.execute`.

---

### Task 1: Workspace types + store + Vitest

**Files:**
- Create: `frontend/src/modules/api-test/shell/types/workspace.ts`
- Create: `frontend/src/modules/api-test/shell/stores/workspace.ts`
- Create: `frontend/src/modules/api-test/shell/stores/workspace.test.ts`
- Modify: `frontend/package.json`
- Modify: `frontend/vite.config.ts` (or add `frontend/vitest.config.ts` if cleaner)

**Interfaces:**
- Produces:
  - `type ShellModule = 'apis' | 'collections' | 'environments' | 'docs' | 'specs' | 'mocks'`
  - `type TabSource = 'definition' | 'collection' | 'scratch'`
  - `interface RequestDraft { url: string; method: string; headers: Record<string, string>; queryParams: Record<string, string>; body: string; contentType: string; preRequestScript: string; postResponseScript: string; assertions: ApiDebugAssertionDTO[]; extracts: ApiDebugExtractDTO[] }`
  - `interface RequestTab { id: string; source: TabSource; refId?: number; definitionId?: number; title: string; method: string; dirty: boolean; draft: RequestDraft; result: ApiDebugResultVO | null; loadError?: string }`
  - `useWorkspaceStore()` with: `activeModule`, `tabs`, `activeTabId`, `sidebarWidth`, `responseHeight`, `activeTab` (computed), `setModule`, `openOrFocusTab`, `openScratchTab`, `closeTab`, `setActiveTab`, `patchDraft`, `markClean`, `setTabResult`, `setLayout`

- [ ] **Step 1: Add Vitest dependencies and scripts**

In `frontend/`:

```bash
npm install -D vitest @vue/test-utils happy-dom
```

Add to `package.json` scripts:

```json
"test": "vitest run",
"test:watch": "vitest"
```

In `vite.config.ts`, ensure:

```ts
/// <reference types="vitest/config" />
export default defineConfig({
  // ...existing
  test: {
    environment: 'happy-dom',
    include: ['src/**/*.test.ts'],
  },
})
```

- [ ] **Step 2: Write failing store tests**

```ts
// frontend/src/modules/api-test/shell/stores/workspace.test.ts
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { useWorkspaceStore } from './workspace'
import { emptyDraft } from '../types/workspace'

describe('useWorkspaceStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('openOrFocusTab reuses same source+refId', () => {
    const store = useWorkspaceStore()
    const a = store.openOrFocusTab({
      source: 'definition',
      refId: 10,
      definitionId: 10,
      title: 'Login',
      method: 'POST',
      draft: emptyDraft({ url: '/login', method: 'POST' }),
    })
    const b = store.openOrFocusTab({
      source: 'definition',
      refId: 10,
      definitionId: 10,
      title: 'Login',
      method: 'POST',
      draft: emptyDraft({ url: '/login', method: 'POST' }),
    })
    expect(a.id).toBe(b.id)
    expect(store.tabs).toHaveLength(1)
    expect(store.activeTabId).toBe(a.id)
  })

  it('openScratchTab adds blank dirty=false tab', () => {
    const store = useWorkspaceStore()
    const tab = store.openScratchTab()
    expect(tab.source).toBe('scratch')
    expect(tab.dirty).toBe(false)
    expect(store.tabs).toHaveLength(1)
  })

  it('patchDraft marks dirty', () => {
    const store = useWorkspaceStore()
    const tab = store.openScratchTab()
    store.patchDraft(tab.id, { url: 'https://example.com' })
    expect(store.tabs[0].dirty).toBe(true)
    expect(store.tabs[0].draft.url).toBe('https://example.com')
  })

  it('closeTab removes tab and activates neighbor', () => {
    const store = useWorkspaceStore()
    const t1 = store.openScratchTab()
    const t2 = store.openScratchTab()
    store.setActiveTab(t2.id)
    store.closeTab(t1.id)
    expect(store.tabs.map((t) => t.id)).toEqual([t2.id])
    expect(store.activeTabId).toBe(t2.id)
  })

  it('setTabResult isolates per tab', () => {
    const store = useWorkspaceStore()
    const t1 = store.openScratchTab()
    const t2 = store.openScratchTab()
    store.setTabResult(t1.id, { durationMs: 12, status: 'SUCCESS', requestUrl: '/', requestMethod: 'GET' } as any)
    expect(store.tabs.find((t) => t.id === t1.id)?.result?.durationMs).toBe(12)
    expect(store.tabs.find((t) => t.id === t2.id)?.result).toBeNull()
  })
})
```

- [ ] **Step 3: Run tests — expect FAIL**

```bash
cd frontend && npm test -- src/modules/api-test/shell/stores/workspace.test.ts
```

Expected: FAIL (module/store missing)

- [ ] **Step 4: Implement types + store**

`types/workspace.ts` — export `emptyDraft(partial?)`, types above.

`stores/workspace.ts` — Pinia setup store implementing methods from Interfaces. Generate `id` with `crypto.randomUUID()` (fallback `tab-${Date.now()}-${Math.random()}`).

- [ ] **Step 5: Run tests — expect PASS**

```bash
cd frontend && npm test -- src/modules/api-test/shell/stores/workspace.test.ts
```

- [ ] **Step 6: Commit**

```bash
git add frontend/package.json frontend/package-lock.json frontend/vite.config.ts \
  frontend/src/modules/api-test/shell/types/workspace.ts \
  frontend/src/modules/api-test/shell/stores/workspace.ts \
  frontend/src/modules/api-test/shell/stores/workspace.test.ts
git commit -m "$(cat <<'EOF'
feat(api-test): add workspace store and vitest for shell tabs

EOF
)"
```

---

### Task 2: Shell skeleton — ModuleRail + layout chrome

**Files:**
- Create: `frontend/src/modules/api-test/shell/components/ModuleRail.vue`
- Create: `frontend/src/modules/api-test/shell/components/PlaceholderPanel.vue`
- Create: `frontend/src/modules/api-test/shell/components/ComingSoonPane.vue`
- Create: `frontend/src/modules/api-test/shell/components/ResourcePanel.vue`
- Create: `frontend/src/modules/api-test/shell/views/ApiTestShell.vue`
- Modify: `frontend/src/modules/api-test/define/router/apiTestRoutes.ts`
- Modify: `frontend/src/shared/console/products.ts`
- Modify: `frontend/src/shared/console/tabs.ts`

**Interfaces:**
- Consumes: `useWorkspaceStore().activeModule`, `setModule`
- Produces: Route `/api-test` → `ApiTestShell`; product path `/api-test`; `CONSOLE_TABS` empty array (or no api-test entries) so `SecondaryTabs` hides

- [ ] **Step 1: Clear secondary tabs and point product entry**

`tabs.ts` — set `CONSOLE_TABS` to `[]` (PM no longer used this list for api-test; if PM still needs tabs, **keep only non-api-test entries** — currently the file only has api-test tabs, so empty is correct).

`products.ts` — change api-test `path` to `'/api-test'`.

- [ ] **Step 2: Implement ModuleRail**

```vue
<!-- ModuleRail.vue: vertical 48px rail; buttons for apis/collections/environments/docs/specs/mocks -->
<!-- activeModule highlighted; docs/specs/mocks still call setModule (show placeholder) -->
```

Use `@lucide/vue` icons already in repo (`FileJson`, `FolderTree`, `Globe`, `BookOpen`, etc.).

- [ ] **Step 3: ComingSoonPane + PlaceholderPanel + ResourcePanel**

`ResourcePanel` switches on `activeModule`:
- `apis` → `<n-empty description="接口树将在下一任务接入" />`
- `collections` → `<n-empty description="集合面板将在后续任务接入" />`
- `environments` → `<n-empty description="环境面板将在后续任务接入" />`
- else → `PlaceholderPanel` with title Docs / Specs / Mocks and subtitle「即将支持」

- [ ] **Step 4: ApiTestShell layout**

Flex row: `ModuleRail` | resizable sidebar (`sidebarWidth`) | main column with empty main (“从左侧打开接口”) and stub response strip.

Use CSS vars: `var(--wb-card-bg)`, `var(--wb-border)`, `var(--wb-muted)` like `AppShell`.

- [ ] **Step 5: Routes**

```ts
export const apiTestRoutes: RouteRecordRaw[] = [
  { path: '/api-test', name: 'api-test-shell', component: () => import('@/modules/api-test/shell/views/ApiTestShell.vue') },
  { path: '/api-test/definitions', redirect: (to) => ({ path: '/api-test', query: { ...to.query, module: 'apis' } }) },
  { path: '/api-test/definitions/:id', redirect: (to) => ({ path: '/api-test', query: { ...to.query, module: 'apis', def: String(to.params.id) } }) },
  { path: '/api-test/environments', redirect: () => ({ path: '/api-test', query: { module: 'environments' } }) },
  { path: '/api-test/collections', redirect: () => ({ path: '/api-test', query: { module: 'collections' } }) },
  { path: '/api-test/collections/:id', redirect: (to) => ({ path: '/api-test', query: { module: 'collections', collectionId: String(to.params.id) } }) },
  { path: '/api-test/collections/:id/runs', redirect: (to) => ({ path: '/api-test', query: { module: 'collections', collectionId: String(to.params.id), runs: '1' } }) },
]
```

In `ApiTestShell` `onMounted` / `watch(route.query)`: apply `module`, defer `def`/`collectionId` until panels exist (store query for Task 4/7).

- [ ] **Step 6: Manual smoke**

```bash
cd frontend && npm run dev
```

Open `/api-test` — rail switches modules; no secondary tabs; product switcher lands on shell.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/modules/api-test/shell frontend/src/modules/api-test/define/router/apiTestRoutes.ts \
  frontend/src/shared/console/tabs.ts frontend/src/shared/console/products.ts
git commit -m "$(cat <<'EOF'
feat(api-test): add ApiTestShell skeleton and route redirects

EOF
)"
```

---

### Task 3: ApiTreePanel — open definition tabs

**Files:**
- Create: `frontend/src/modules/api-test/shell/components/ApiTreePanel.vue`
- Modify: `frontend/src/modules/api-test/shell/components/ResourcePanel.vue`
- Modify: `frontend/src/modules/api-test/shell/views/ApiTestShell.vue`
- Optionally adapt: `frontend/src/modules/api-test/define/components/ApiWorkspaceSidebar.vue` (prefer thin wrap over rewrite)

**Interfaces:**
- Consumes: `useApiDefinitionStore`, `useApiGroupStore`, `openOrFocusTab`
- Produces: emit/select opens tab with draft loaded from `apiDefinitionApi.detail`

- [ ] **Step 1: Implement load helper**

```ts
// inside ApiTreePanel or shell/utils/loadDefinitionDraft.ts
export async function loadDefinitionIntoTab(definitionId: number) {
  const detail = await apiDefinitionApi.detail(definitionId)
  const draft = emptyDraft({
    url: detail.path || '',
    method: detail.method,
    contentType: detail.contentType || 'application/json',
  })
  for (const p of detail.params || []) {
    if (p.paramType === 'query') draft.queryParams[p.name] = p.defaultValue || ''
    if (p.paramType === 'header') draft.headers[p.name] = p.defaultValue || ''
    if (p.paramType === 'body' && p.defaultValue) draft.body = p.defaultValue
  }
  return { detail, draft }
}
```

- [ ] **Step 2: Wire tree select**

On definition click:

```ts
const { detail, draft } = await loadDefinitionIntoTab(id)
workspace.openOrFocusTab({
  source: 'definition',
  refId: id,
  definitionId: id,
  title: detail.name,
  method: detail.method,
  draft,
})
```

Reuse create/edit/delete group dialogs from existing workspace (copy handlers from `ApiDefinitionWorkspace.vue` or extract shared composable `useApiGroupDialogs`).

- [ ] **Step 3: Deep link `?def=`**

In shell: if `route.query.def`, open that definition once after tree data loaded.

- [ ] **Step 4: Manual smoke** — select two APIs → two tabs (or reuse); create group works.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/modules/api-test/shell
git commit -m "$(cat <<'EOF'
feat(api-test): wire API tree panel to open request tabs

EOF
)"
```

---

### Task 4: RequestTabBar + RequestWorkspace (Send / Save)

**Files:**
- Create: `frontend/src/modules/api-test/shell/components/RequestTabBar.vue`
- Create: `frontend/src/modules/api-test/shell/components/RequestWorkspace.vue`
- Modify: `frontend/src/modules/api-test/shell/views/ApiTestShell.vue`
- Reuse: `KeyValueEditor`, `ScriptEditor`, `AssertionEditor`, `ExtractEditor`, `ApiWorkspaceResponse` or `ResponseViewer`

**Interfaces:**
- Consumes: `activeTab`, `patchDraft`, `setTabResult`, `markClean`, `closeTab`, `useDebugStore.execute`, `useEnvironmentStore.selectedEnvironmentId`, `apiDefinitionApi.update/create`
- Produces: Full request editor UI for active tab

- [ ] **Step 1: RequestTabBar**

Show method color + title + dirty `●` + close. `+` → `openScratchTab()`. On close dirty → `useDialog().warning` confirm then `closeTab`.

- [ ] **Step 2: RequestWorkspace URL bar + tabs**

n-tabs: Params | Auth (ComingSoonPane) | Headers | Body | Scripts | Tests | Docs (ComingSoon) | Settings (ComingSoon).

Tests tab: `AssertionEditor` + `ExtractEditor` bound to `draft.assertions` / `draft.extracts` via `patchDraft`.

- [ ] **Step 3: Send**

```ts
async function handleSend() {
  const tab = workspace.activeTab
  if (!tab?.draft.url) { message.warning('请输入请求 URL'); return }
  try {
    const result = await debugStore.execute({
      projectId: 1,
      definitionId: tab.definitionId,
      environmentId: envStore.selectedEnvironmentId ?? undefined,
      url: tab.draft.url,
      method: tab.draft.method,
      headers: tab.draft.headers,
      queryParams: tab.draft.queryParams,
      body: tab.draft.body || undefined,
      contentType: tab.draft.contentType,
      preRequestScript: tab.draft.preRequestScript || undefined,
      postResponseScript: tab.draft.postResponseScript || undefined,
      assertions: tab.draft.assertions,
      extracts: tab.draft.extracts,
    })
    workspace.setTabResult(tab.id, result)
    message.success('调试完成')
  } catch (e: any) {
    message.error(e.message || '请求失败')
  }
}
```

Response pane: bind `tab.result` (not only `debugStore.currentResult`). Include Visualize ComingSoon.

- [ ] **Step 4: Save**

- If `scratch`: dialog — name + optional group → `apiDefinitionApi.create` → upgrade tab to `source=definition` with new id, `markClean`
- If `definition` or `collection`: `apiDefinitionApi.update(definitionId, { name, path, method, params from draft, contentType }, userId)` then `markClean`
- Do not call non-existent script persist APIs

- [ ] **Step 5: Manual smoke** — Send with env var in URL; Save definition; dirty close confirms.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/modules/api-test/shell
git commit -m "$(cat <<'EOF'
feat(api-test): multi-tab request workspace with send and save

EOF
)"
```

---

### Task 5: Environment panel + header selector

**Files:**
- Create: `frontend/src/modules/api-test/shell/components/EnvironmentPanel.vue`
- Modify: `frontend/src/modules/api-test/shell/views/ApiTestShell.vue`
- Reuse: `VariableList`, `EnvironmentFormDialog`, `useEnvironmentStore`

**Interfaces:**
- Consumes: `loadAll`, `loadDetail`, `selectEnvironment`, `update`
- Produces: Sidebar env list synced with shell header selector

- [ ] **Step 1: EnvironmentPanel UI**

Left list of `allList`; click → `selectEnvironment` + `loadDetail`; show `VariableList` for variables; save via `environmentStore.update`.

- [ ] **Step 2: Shell header env selector**

Reuse `EnvironmentSelector` above tab bar (or in shell top of main column). Selecting updates same `selectedEnvironmentId`.

- [ ] **Step 3: `?module=environments`** activates env module on mount.

- [ ] **Step 4: Manual smoke** — switch env, Send uses variables.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/modules/api-test/shell
git commit -m "$(cat <<'EOF'
feat(api-test): embed environment panel in ApiTestShell

EOF
)"
```

---

### Task 6: Collection panel + in-shell Run drawer

**Files:**
- Create: `frontend/src/modules/api-test/shell/components/CollectionPanel.vue`
- Create: `frontend/src/modules/api-test/shell/components/CollectionRunDrawer.vue`
- Modify: `ResourcePanel.vue`, `ApiTestShell.vue`
- Reuse: `collectionApi`, `CollectionTree`, `CollectionRunResult`

**Interfaces:**
- Consumes: `openOrFocusTab`, `loadDefinitionIntoTab`, `collectionApi.run` / `runHistory` / `detail`
- Produces: Open collection item as `source:'collection'` tab with `refId=item.id`, `definitionId=item.definitionId`

- [ ] **Step 1: CollectionPanel**

States: list collections → drill into one → show folder tree + items. Toolbar: Run, History.

On item click:

```ts
const { detail, draft } = await loadDefinitionIntoTab(item.definitionId)
workspace.openOrFocusTab({
  source: 'collection',
  refId: item.id,
  definitionId: item.definitionId,
  title: item.name || detail.name,
  method: item.method || detail.method,
  draft,
})
```

- [ ] **Step 2: CollectionRunDrawer**

`n-drawer`: run → show `CollectionRunResult`; history list selectable. No route navigation.

Honor `?collectionId=` and `?runs=1` from redirects.

- [ ] **Step 3: Manual smoke** — open item from collection; Run shows drawer; tabs from definition and collection coexist.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/modules/api-test/shell
git commit -m "$(cat <<'EOF'
feat(api-test): collection panel and in-shell run drawer

EOF
)"
```

---

### Task 7: Layout polish, cleanup, verification

**Files:**
- Modify: `ApiTestShell.vue` (drag resize sidebar + response height; persist to `sessionStorage` keys `api-test.sidebarWidth`, `api-test.responseHeight`)
- Modify: shell components styles for dark/light
- Leave obsolete views in place but unused (`ApiDefinitionWorkspace.vue`, `CollectionDetail.vue`, etc.) — do not delete unless zero imports; remove dead imports if any remain

**Interfaces:**
- Consumes: `setLayout` on workspace store

- [ ] **Step 1: Draggable splitters**

Pointer events on vertical divider (sidebar) and horizontal divider (response): update `sidebarWidth` / `responseHeight` with min clamps (sidebar 200–480, response 120–60vh).

- [ ] **Step 2: Persist layout sizes on change; restore on shell mount.

- [ ] **Step 3: Typecheck**

```bash
cd frontend && npm run build
```

Expected: `vue-tsc` + vite build succeed.

- [ ] **Step 4: Re-run unit tests**

```bash
cd frontend && npm test
```

- [ ] **Step 5: Manual checklist (spec success criteria)**

1. Browse API tree, open multiple tabs, Send, Save  
2. Switch environment, variable substitution on Send  
3. Open collection item; Run collection in drawer  
4. Auth/Docs/Visualize/Specs/Mocks show Coming soon  
5. Toggle light/dark — shell readable  
6. Old URLs redirect into shell  

- [ ] **Step 6: Update spec status line to `状态：已确认并实现中/已实现` if desired

- [ ] **Step 7: Commit**

```bash
git add frontend/src/modules/api-test/shell docs/superpowers/specs/2026-08-14-api-test-shell-frontend-design.md
git commit -m "$(cat <<'EOF'
feat(api-test): polish ApiTestShell layout and verify build

EOF
)"
```

---

## Spec coverage checklist

| Spec requirement | Task |
|------------------|------|
| Unified shell + left rail | 2 |
| Modules APIs/Collections/Environments + Docs/Specs/Mocks placeholders | 2, 3, 5, 6 |
| Multi-request tabs | 1, 4 |
| Full request tab chrome; real vs placeholder | 4 |
| Env sync header + sidebar | 5 |
| Collection run in-shell | 6 |
| Remove secondary tabs; redirects | 2 |
| Theme follow console | 2, 7 |
| Per-tab results | 1, 4 |
| workspaceStore tests | 1 |
| No backend contract change | Global + 4 Save rules |
| Resizable panes | 7 |

## Known deferred (explicit)

- Persisting scripts/assertions/extracts on definition Save (no REST yet)
- Auth/Docs/Specs/Mocks/Visualize real features
- Item-level request overrides in collections
