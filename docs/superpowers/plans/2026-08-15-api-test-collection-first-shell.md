# ApiTest Collection-First Shell Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Recast `ApiTestShell` into a Postman-style collections-only workspace: left collection tree (create collection / create request), right overview + request debug (split panes), top-right environment select/create/edit drawer.

**Architecture:** Keep existing multi-tab `workspace` store and `RequestWorkspace`. Remove module rail. Promote collections to the only sidebar. Add `collectionOverview` tabs and a `createRequestInCollection` helper that orchestrates definition create + collection item add (no REST shape changes). Environment editing moves from side panel / separate page into a drawer opened from the header selector.

**Tech Stack:** Vue 3, TypeScript, Vite, Pinia, Vue Router, Naive UI, Vitest

**Spec:** `docs/superpowers/specs/2026-08-15-api-test-collection-first-shell-design.md`

## Global Constraints

- Greenfield: remove module-rail UX; do not keep dual navigation for “apis vs collections”
- Do **not** change backend REST contracts; only orchestrate existing APIs
- New request under collection = create definition then `addItem`; on `addItem` failure call `apiDefinitionApi.delete(definitionId)` when possible
- Collection item still opens linked `definitionId`; Save writes that definition
- Auth / Scripts / Variables on collection overview: Coming soon only
- Docs tab: edit `description` on the definition (persist on Save), not a docs product
- Theme: follow console light/dark CSS variables
- Project id remains `1` where already hard-coded
- Prefer login shell / Node 20 for frontend tests: `cd frontend && npm test`

---

## File Structure

| Path | Responsibility |
|------|----------------|
| `frontend/src/modules/api-test/shell/types/workspace.ts` | Extend `TabSource` with `collectionOverview`; add `description` to `RequestDraft` |
| `frontend/src/modules/api-test/shell/stores/workspace.ts` | `openOrFocusCollectionOverview(collectionId, title)`; keep request tab helpers |
| `frontend/src/modules/api-test/shell/utils/createRequestInCollection.ts` | Orchestrate definition create + item add + rollback |
| `frontend/src/modules/api-test/shell/utils/createRequestInCollection.test.ts` | Unit tests for create/rollback |
| `frontend/src/modules/api-test/shell/utils/loadDefinitionDraft.ts` | Also map `detail.description` into draft |
| `frontend/src/modules/api-test/shell/components/CollectionsSidebar.vue` | Collections-only tree: create collection, expand, open overview, new request |
| `frontend/src/modules/api-test/shell/components/CollectionsSidebar.test.ts` | Sidebar interaction tests (mocked stores) |
| `frontend/src/modules/api-test/shell/components/CollectionOverviewTab.vue` | Overview + Runs for a collection |
| `frontend/src/modules/api-test/shell/components/CollectionOverviewTab.test.ts` | Overview smoke tests |
| `frontend/src/modules/api-test/shell/components/EnvironmentEditDrawer.vue` | Drawer: name + `VariableList` save via env store |
| `frontend/src/modules/api-test/shell/components/EnvironmentEditDrawer.test.ts` | Drawer open/save tests |
| `frontend/src/modules/api-test/debug/components/EnvironmentSelector.vue` | New env + open drawer (no `window.open`) |
| `frontend/src/modules/api-test/shell/views/ApiTestShell.vue` | No ModuleRail; sidebar = CollectionsSidebar; main = overview \| request; env drawer; `?envEdit=` |
| `frontend/src/modules/api-test/shell/components/RequestWorkspace.vue` | Docs textarea; Save includes `description`; scratch save picks collection |
| `frontend/src/modules/api-test/shell/components/RequestTabBar.vue` | Show overview tabs (title without method badge if needed) |
| Deprecate / stop mounting | `ModuleRail.vue`, `ResourcePanel.vue`, `ApiTreePanel.vue`, `EnvironmentPanel.vue`, old `CollectionPanel.vue` (replace with CollectionsSidebar) |

Reuse: `CollectionTree` patterns, `collectionApi` / store, `apiDefinitionApi`, `EnvironmentFormDialog` patterns / `VariableList`, `resolveVariablesForUpdate`, `CollectionRunDrawer`, `ComingSoonPane`.

---

### Task 1: Workspace tab source + description on draft

**Files:**
- Modify: `frontend/src/modules/api-test/shell/types/workspace.ts`
- Modify: `frontend/src/modules/api-test/shell/stores/workspace.ts`
- Modify: `frontend/src/modules/api-test/shell/stores/workspace.test.ts`
- Modify: `frontend/src/modules/api-test/shell/utils/loadDefinitionDraft.ts`
- Modify: `frontend/src/modules/api-test/shell/utils/loadDefinitionDraft.test.ts` (create if missing)

**Interfaces:**
- Produces:
  - `type TabSource = 'definition' | 'collection' | 'collectionOverview' | 'scratch'`
  - `RequestDraft.description: string`
  - `emptyDraft` defaults `description: ''`
  - `openOrFocusCollectionOverview(collectionId: number, title: string): RequestTab` — `source: 'collectionOverview'`, `refId: collectionId`, `method: ''`, `draft: emptyDraft()`, no `definitionId`

- [ ] **Step 1: Extend failing tests in `workspace.test.ts`**

```ts
it('openOrFocusCollectionOverview dedupes by collection id', () => {
  const store = useWorkspaceStore()
  const a = store.openOrFocusCollectionOverview(9, 'Demo')
  const b = store.openOrFocusCollectionOverview(9, 'Demo')
  expect(a.id).toBe(b.id)
  expect(store.tabs).toHaveLength(1)
  expect(a.source).toBe('collectionOverview')
  expect(a.refId).toBe(9)
})

it('emptyDraft includes description', () => {
  expect(emptyDraft().description).toBe('')
})
```

- [ ] **Step 2: Run tests — expect FAIL**

```bash
cd frontend && npm test -- src/modules/api-test/shell/stores/workspace.test.ts
```

Expected: FAIL (method / field missing)

- [ ] **Step 3: Implement types + store helper + loadDefinitionDraft**

```ts
// types
export type TabSource = 'definition' | 'collection' | 'collectionOverview' | 'scratch'
// RequestDraft + emptyDraft: description: ''

// store
function openOrFocusCollectionOverview(collectionId: number, title: string): RequestTab {
  return openOrFocusTab({
    source: 'collectionOverview',
    refId: collectionId,
    title,
    method: '',
    draft: emptyDraft(),
  })
}
```

```ts
// loadDefinitionDraft.ts
const draft = emptyDraft({
  url: detail.path || '',
  method: detail.method,
  contentType: detail.contentType || 'application/json',
  description: detail.description || '',
})
```

- [ ] **Step 4: Run tests — expect PASS**

```bash
cd frontend && npm test -- src/modules/api-test/shell/stores/workspace.test.ts src/modules/api-test/shell/utils/loadDefinitionDraft.test.ts
```

- [ ] **Step 5: Commit**

```bash
git add frontend/src/modules/api-test/shell/types/workspace.ts \
  frontend/src/modules/api-test/shell/stores/workspace.ts \
  frontend/src/modules/api-test/shell/stores/workspace.test.ts \
  frontend/src/modules/api-test/shell/utils/loadDefinitionDraft.ts \
  frontend/src/modules/api-test/shell/utils/loadDefinitionDraft.test.ts
git commit -m "feat(api-test): add collectionOverview tabs and draft description"
```

---

### Task 2: `createRequestInCollection` helper

**Files:**
- Create: `frontend/src/modules/api-test/shell/utils/createRequestInCollection.ts`
- Create: `frontend/src/modules/api-test/shell/utils/createRequestInCollection.test.ts`

**Interfaces:**
- Consumes: `apiDefinitionApi.create`, `apiDefinitionApi.delete`, `collectionApi.addItem` (or store wrappers)
- Produces:

```ts
export interface CreateRequestInCollectionInput {
  projectId: number
  collectionId: number
  userId: number
  folderId?: number | null
  name?: string
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH' | 'HEAD' | 'OPTIONS'
  path?: string
}

export interface CreateRequestInCollectionResult {
  definitionId: number
  itemId: number
  name: string
  method: string
  path: string
}

export async function createRequestInCollection(
  input: CreateRequestInCollectionInput,
): Promise<CreateRequestInCollectionResult>
```

- [ ] **Step 1: Write failing tests (mock APIs)**

```ts
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createRequestInCollection } from './createRequestInCollection'

vi.mock('@/modules/api-test/define/api/definition', () => ({
  apiDefinitionApi: {
    create: vi.fn(),
    delete: vi.fn(),
  },
}))
vi.mock('@/modules/api-test/collection/api/collection', () => ({
  collectionApi: {
    addItem: vi.fn(),
  },
}))

import { apiDefinitionApi } from '@/modules/api-test/define/api/definition'
import { collectionApi } from '@/modules/api-test/collection/api/collection'

beforeEach(() => vi.clearAllMocks())

it('creates definition then item', async () => {
  vi.mocked(apiDefinitionApi.create).mockResolvedValue({ id: 101, name: 'Untitled Request', method: 'POST', path: '/' } as any)
  vi.mocked(collectionApi.addItem).mockResolvedValue({ id: 55, definitionId: 101 } as any)
  const result = await createRequestInCollection({
    projectId: 1, collectionId: 7, userId: 1,
  })
  expect(apiDefinitionApi.create).toHaveBeenCalledWith(
    expect.objectContaining({ projectId: 1, name: 'Untitled Request', method: 'POST', path: '/' }),
    1,
  )
  expect(collectionApi.addItem).toHaveBeenCalledWith(
    7,
    expect.objectContaining({ definitionId: 101, folderId: null }),
    1,
  )
  expect(result).toEqual({
    definitionId: 101, itemId: 55, name: 'Untitled Request', method: 'POST', path: '/',
  })
})

it('deletes definition if addItem fails', async () => {
  vi.mocked(apiDefinitionApi.create).mockResolvedValue({ id: 101 } as any)
  vi.mocked(collectionApi.addItem).mockRejectedValue(new Error('boom'))
  await expect(createRequestInCollection({
    projectId: 1, collectionId: 7, userId: 1,
  })).rejects.toThrow('boom')
  expect(apiDefinitionApi.delete).toHaveBeenCalledWith(101)
})
```

- [ ] **Step 2: Run — expect FAIL**

```bash
cd frontend && npm test -- src/modules/api-test/shell/utils/createRequestInCollection.test.ts
```

- [ ] **Step 3: Implement helper**

```ts
export async function createRequestInCollection(input: CreateRequestInCollectionInput) {
  const name = input.name ?? 'Untitled Request'
  const method = input.method ?? 'POST'
  const path = input.path ?? '/'
  const def = await apiDefinitionApi.create({
    projectId: input.projectId,
    name,
    method,
    path,
  }, input.userId)
  try {
    const item = await collectionApi.addItem(input.collectionId, {
      folderId: input.folderId ?? null,
      definitionId: def.id,
      name,
    }, input.userId)
    return {
      definitionId: def.id,
      itemId: item.id,
      name,
      method: def.method ?? method,
      path: def.path ?? path,
    }
  } catch (e) {
    try { await apiDefinitionApi.delete(def.id) } catch { /* ignore rollback errors */ }
    throw e
  }
}
```

Adjust return typing to match real `create` / `addItem` VO shapes in the codebase.

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit**

```bash
git add frontend/src/modules/api-test/shell/utils/createRequestInCollection.ts \
  frontend/src/modules/api-test/shell/utils/createRequestInCollection.test.ts
git commit -m "feat(api-test): add createRequestInCollection with rollback"
```

---

### Task 3: CollectionsSidebar (unique left panel)

**Files:**
- Create: `frontend/src/modules/api-test/shell/components/CollectionsSidebar.vue`
- Create: `frontend/src/modules/api-test/shell/components/CollectionsSidebar.test.ts`
- Modify: `frontend/src/modules/api-test/shell/views/ApiTestShell.vue` (wire sidebar only; full shell cleanup in Task 5 if needed)

**Interfaces:**
- Emits: `run: [collectionId: number]`, `history: [collectionId: number]`
- On collection click → `workspace.openOrFocusCollectionOverview(id, name)` + ensure detail loaded
- On item click → existing `loadDefinitionIntoTab` + `openOrFocusTab({ source: 'collection', refId: item.id, definitionId, ... })`
- Toolbar `+` → create collection dialog → `collectionStore.create` → reload page → open overview
- Collection row `+` → `createRequestInCollection` → reload detail → open request tab
- Empty: CTA “创建第一个集合”

UI shape (Postman-like, keep console tokens):

```
COLLECTIONS          [search optional] [+]
▾ Collection A       [+] […]
   · POST xxx
▾ Collection B
```

Prefer expandable list: selecting a collection expands it (loadDetail) and opens overview; do **not** use the old “drill + Back” single-collection view.

- [ ] **Step 1: Write failing mount tests**

```ts
it('renders COLLECTIONS header and create button', async () => {
  // mock collectionStore.loadPage → one collection
  const wrapper = mount(CollectionsSidebar, { global: { plugins: [pinia], stubs: { /* naive */ } } })
  expect(wrapper.get('[data-testid="collections-sidebar"]').exists()).toBe(true)
  expect(wrapper.get('[data-testid="create-collection"]').exists()).toBe(true)
})

it('clicking collection opens overview tab', async () => {
  // click [data-testid="collection-row-1"]
  expect(useWorkspaceStore().activeTab?.source).toBe('collectionOverview')
})
```

- [ ] **Step 2: Run — expect FAIL**

```bash
cd frontend && npm test -- src/modules/api-test/shell/components/CollectionsSidebar.test.ts
```

- [ ] **Step 3: Implement sidebar**

Reuse `CollectionTree` inside an expanded collection section, or build a thin wrapper that maps `currentDetail.folders/items`. Keep `Run` / `History` on collection overflow menu (emit to shell).

Require `userId` from `useAuthStore` for create; warn if missing.

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit**

```bash
git add frontend/src/modules/api-test/shell/components/CollectionsSidebar.vue \
  frontend/src/modules/api-test/shell/components/CollectionsSidebar.test.ts
git commit -m "feat(api-test): add collections-only sidebar"
```

---

### Task 4: CollectionOverviewTab

**Files:**
- Create: `frontend/src/modules/api-test/shell/components/CollectionOverviewTab.vue`
- Create: `frontend/src/modules/api-test/shell/components/CollectionOverviewTab.test.ts`

**Interfaces:**
- Props: `collectionId: number`
- Loads detail via `collectionStore.loadDetail` if needed
- Tabs: `Overview` | `Runs` | Coming soon (`Authorization`, `Scripts`, `Variables`)
- Overview: name, description (read-only or inline edit via `collectionStore.update` if already available), created metadata if present on VO
- Runs: buttons to emit/call same as panel `run` / `history` — use inject/emits: `run`, `history`

- [ ] **Step 1: Failing test — renders overview name**

```ts
it('shows collection name on overview', async () => {
  // mock loadDetail → { id: 1, name: '百度AI平台', description: '...' }
  const wrapper = mount(CollectionOverviewTab, { props: { collectionId: 1 }, ... })
  expect(wrapper.text()).toContain('百度AI平台')
})
```

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: Implement component with `n-tabs`**

- [ ] **Step 4: Run — PASS**

- [ ] **Step 5: Commit**

```bash
git add frontend/src/modules/api-test/shell/components/CollectionOverviewTab.vue \
  frontend/src/modules/api-test/shell/components/CollectionOverviewTab.test.ts
git commit -m "feat(api-test): add collection overview tab"
```

---

### Task 5: Shell layout — remove module rail, wire overview + sidebar

**Files:**
- Modify: `frontend/src/modules/api-test/shell/views/ApiTestShell.vue`
- Modify: `frontend/src/modules/api-test/shell/views/ApiTestShell.test.ts`
- Modify: `frontend/src/modules/api-test/shell/components/RequestTabBar.vue` (optional: hide method chip when `method === ''`)

**Interfaces:**
- Layout:

```
[ CollectionsSidebar | resize ] [ RequestTabBar ]
                                [ CollectionOverviewTab | RequestWorkspace | empty ]
```

- Remove `ModuleRail`, `ResourcePanel`, `applyQueryModule` / `?module=` (ignore or no-op)
- Empty main: `n-empty` “选择集合或新建接口”
- Keep `CollectionRunDrawer` + env selector in header
- Stop calling `onTreeLoaded` from ApiTreePanel; open `?def=` still works after mount (load definition tab as `source: 'definition'` OK for deep links)

- [ ] **Step 1: Update shell tests**

```ts
it('does not render module rail', () => {
  const wrapper = mountShell()
  expect(wrapper.find('[data-testid="module-rail"]').exists()).toBe(false)
  expect(wrapper.find('[data-testid="collections-sidebar"]').exists()).toBe(true)
})

it('shows overview when active tab is collectionOverview', async () => {
  useWorkspaceStore().openOrFocusCollectionOverview(1, 'Demo')
  await nextTick()
  expect(wrapper.find('[data-testid="collection-overview"]').exists()).toBe(true)
})
```

- [ ] **Step 2: Run — FAIL**

```bash
cd frontend && npm test -- src/modules/api-test/shell/views/ApiTestShell.test.ts
```

- [ ] **Step 3: Implement shell template changes**

```vue
<aside class="api-shell__sidebar" :style="{ width: `${sidebarWidth}px` }">
  <CollectionsSidebar @run="onCollectionRun" @history="onCollectionHistory" />
</aside>
<!-- ... -->
<main>
  <RequestTabBar />
  <CollectionOverviewTab
    v-if="activeTab?.source === 'collectionOverview' && activeTab.refId"
    :collection-id="activeTab.refId"
    @run="onCollectionRun"
    @history="onCollectionHistory"
  />
  <RequestWorkspace v-else-if="activeTab && activeTab.source !== 'collectionOverview'" />
  <n-empty v-else description="选择集合或新建接口" />
</main>
```

- [ ] **Step 4: Run shell + related tests — PASS**

```bash
cd frontend && npm test -- src/modules/api-test/shell/
```

- [ ] **Step 5: Commit**

```bash
git add frontend/src/modules/api-test/shell/views/ApiTestShell.vue \
  frontend/src/modules/api-test/shell/views/ApiTestShell.test.ts \
  frontend/src/modules/api-test/shell/components/RequestTabBar.vue
git commit -m "feat(api-test): switch shell to collections-only layout"
```

---

### Task 6: Environment selector + edit drawer

**Files:**
- Create: `frontend/src/modules/api-test/shell/components/EnvironmentEditDrawer.vue`
- Create: `frontend/src/modules/api-test/shell/components/EnvironmentEditDrawer.test.ts`
- Modify: `frontend/src/modules/api-test/debug/components/EnvironmentSelector.vue`
- Modify: `frontend/src/modules/api-test/shell/views/ApiTestShell.vue` (`envEdit` query + drawer state)

**Interfaces:**
- `EnvironmentEditDrawer` props: `show: boolean`, `environmentId: number | null`, `projectId: number`; emits `update:show`, `saved`
- Reuse `VariableList` + `resolveVariablesForUpdate` like `EnvironmentFormDialog`
- Selector emits or callbacks: `create` / `edit(id)` instead of `window.open`
- Shell: `envDrawerShow`, `envDrawerId`; on `?envEdit=123` open drawer after `loadAll`

- [ ] **Step 1: Failing drawer test — save calls store.update**

```ts
it('saves variables via environment store', async () => {
  // open drawer with id 5, click save
  expect(updateMock).toHaveBeenCalled()
})
```

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: Implement drawer + selector**

Selector UX:

- Options from `allList`
- Clearable “No environment”
- Extra footer / button: “新建环境” → small name modal → `environmentStore.create({ name }, projectId, userId)` → select → open drawer
- “编辑变量” when selected → open drawer (replace `window.open`)

- [ ] **Step 4: Run drawer + any selector tests — PASS**

- [ ] **Step 5: Commit**

```bash
git add frontend/src/modules/api-test/shell/components/EnvironmentEditDrawer.vue \
  frontend/src/modules/api-test/shell/components/EnvironmentEditDrawer.test.ts \
  frontend/src/modules/api-test/debug/components/EnvironmentSelector.vue \
  frontend/src/modules/api-test/shell/views/ApiTestShell.vue
git commit -m "feat(api-test): environment create and edit drawer in shell header"
```

---

### Task 7: Docs tab + scratch save to collection

**Files:**
- Modify: `frontend/src/modules/api-test/shell/components/RequestWorkspace.vue`
- Modify: `frontend/src/modules/api-test/shell/components/RequestWorkspace.test.ts`

**Interfaces:**
- Docs pane: `n-input type="textarea"` bound to `activeTab.draft.description` via `patch({ description })`
- `handleSave` / update payload includes `description: tab.draft.description`
- Scratch dialog: replace group tree with collection select (`collectionStore.pageResult.records`); on confirm:

```ts
const created = await apiDefinitionApi.create({ projectId: 1, name, method, path: draft.url || '/', ... }, userId)
await collectionApi.addItem(selectedCollectionId, { definitionId: created.id, name }, userId)
// setTabMeta to collection source + ids; markClean
```

Or call `createRequestInCollection` then `patchDraft` from existing scratch draft onto definition via `update` (preferred if create-then-update is clearer):

1. `createRequestInCollection({ name: scratchName, method, path: draft.url || '/' })`
2. `apiDefinitionApi.update` with full draft params + description
3. Retarget tab meta to `source: 'collection'`, `refId: itemId`, `definitionId`

- [ ] **Step 1: Tests**

```ts
it('renders docs textarea instead of ComingSoon for Docs', () => { ... })
it('includes description when saving definition', async () => { ... })
```

- [ ] **Step 2: FAIL → Step 3: implement → Step 4: PASS**

- [ ] **Step 5: Commit**

```bash
git add frontend/src/modules/api-test/shell/components/RequestWorkspace.vue \
  frontend/src/modules/api-test/shell/components/RequestWorkspace.test.ts
git commit -m "feat(api-test): docs description tab and scratch save into collection"
```

---

### Task 8: Cleanup + redirect polish + full shell test pass

**Files:**
- Modify tests that still expect ModuleRail / ResourcePanel (`ResourcePanel.test.ts`, `CollectionPanel.test.ts`, `ApiTestShell.test.ts`)
- Optionally delete unused mounts only; **do not** mass-delete files unless unused and tests updated — prefer leave dead components unmounted to reduce scope, or delete `ModuleRail` usage only
- Confirm routes still redirect `/api-test/environments` → shell; document `?envEdit=` in a one-line comment near shell watcher
- Empty states already covered in Tasks 3–5

- [ ] **Step 1: Run full api-test shell suite**

```bash
cd frontend && npm test -- src/modules/api-test/shell/
```

Expected: all PASS. Fix any broken tests from removed rail.

- [ ] **Step 2: Manual checklist (document in commit body)**

1. Open `/api-test` — no left module rail; collections sidebar only  
2. Create collection → overview tab  
3. Create request under collection → Send works  
4. Env: create + edit drawer; Send uses selected env  
5. Docs tab edits description and Save persists  

- [ ] **Step 3: Commit**

```bash
git add -u frontend/src/modules/api-test/shell
git commit -m "chore(api-test): finish collection-first shell cleanup and tests"
```

---

## Spec coverage (self-review)

| Spec section | Task |
|--------------|------|
| §3 Remove module rail; collections-only sidebar | 3, 5 |
| §3 Env top-right | 6 |
| §4 Overview + request tabs | 1, 4, 5 |
| §4 Docs description | 1, 7 |
| §5 Create collection / request orchestration | 2, 3 |
| §5 Scratch → collection | 7 |
| §6 Env drawer + envEdit | 6 |
| §7 Empty / errors | 3, 5 (message.error on failures) |
| §9 Vitest | each task |
| No backend contract change | Global + Task 2 |

No TBD placeholders. Types: `collectionOverview`, `CreateRequestInCollectionResult`, `description` on draft — consistent across tasks.
