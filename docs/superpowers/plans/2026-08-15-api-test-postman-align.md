# ApiTest Postman Align Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align the collection-first ApiTest shell with Postman: sidebar `Collections | History`, request breadcrumbs, and tree selection/method polish — without changing backend REST contracts.

**Architecture:** Reuse `debugHistoryApi.page` / `detail` for project-scoped History in the left sidebar. Clicking a History row always opens a new scratch tab filled from the history detail. Breadcrumbs resolve collection/folder path from cached `CollectionDetailVO` using new optional `folderId` on tabs. Send bumps a `historyEpoch` on `debugStore` so the History list refreshes. Remove the response-pane History tab to keep a single entry point.

**Tech Stack:** Vue 3, Pinia, Naive UI, Vitest, existing `debug` / `collection` / `shell` modules

**Spec:** `docs/superpowers/specs/2026-08-15-api-test-postman-align-design.md`

## Global Constraints

- No backend REST contract changes
- History primary entry = sidebar; remove response-area「历史」Tab
- History click = always new scratch tab (no dedupe by history id)
- Breadcrumb middle segments display-only (no click navigation this round)
- Keep console `--wb-*` + existing density / method CSS vars under `.api-test-shell`
- Prefer Node 20: `export PATH="$HOME/.nvm/versions/node/v20.15.1/bin:$PATH"` then `cd frontend && npm test -- --run <paths>`
- Preserve existing `data-testid`s unless the plan explicitly renames/removes one
- Greenfield: no legacy dual-path for old History UI

---

## File Structure

| Path | Responsibility |
|------|----------------|
| `frontend/src/modules/api-test/debug/stores/debug.ts` | Add `historyEpoch` + `bumpHistoryEpoch()` |
| `frontend/src/modules/api-test/shell/types/workspace.ts` | Add optional `folderId` on tab / open input |
| `frontend/src/modules/api-test/shell/stores/workspace.ts` | Persist `folderId` on open / `setTabMeta` |
| `frontend/src/modules/api-test/shell/utils/breadcrumbPath.ts` | Pure helper: collection + folders → string segments |
| `frontend/src/modules/api-test/shell/utils/mapHistoryDetailToResult.ts` | Pure helper: `DebugHistoryDetailVO` → draft patch + `ApiDebugResultVO` |
| `frontend/src/modules/api-test/shell/components/HistorySidebarList.vue` | History list UI + emit select |
| `frontend/src/modules/api-test/shell/components/CollectionsSidebar.vue` | Mode toggle; host History list; pass `selectedId` / `folderId` |
| `frontend/src/modules/api-test/shell/components/RequestWorkspace.vue` | Breadcrumb; remove response History; bump epoch on send |
| `frontend/src/modules/api-test/collection/components/CollectionTree.vue` | selectedKeys, method upper, row polish |
| Matching `*.test.ts` files | Unit coverage per task |

---

### Task 1: `debugStore` historyEpoch

**Files:**
- Modify: `frontend/src/modules/api-test/debug/stores/debug.ts`
- Modify: `frontend/src/modules/api-test/debug/stores/debug.test.ts`

**Interfaces:**
- Produces:
  - `historyEpoch: number` (starts at `0`)
  - `bumpHistoryEpoch(): void` — increments by 1
- Consumes: existing `loadHistory` / `clearHistory` / `execute` unchanged

- [ ] **Step 1: Write failing tests**

```ts
it('historyEpoch starts at 0 and bumpHistoryEpoch increments', () => {
  const store = useDebugStore()
  expect(store.historyEpoch).toBe(0)
  store.bumpHistoryEpoch()
  expect(store.historyEpoch).toBe(1)
  store.bumpHistoryEpoch()
  expect(store.historyEpoch).toBe(2)
})
```

- [ ] **Step 2: Run test — expect FAIL**

```bash
export PATH="$HOME/.nvm/versions/node/v20.15.1/bin:$PATH"
cd frontend && npm test -- --run src/modules/api-test/debug/stores/debug.test.ts
```

Expected: FAIL — `bumpHistoryEpoch` / `historyEpoch` undefined

- [ ] **Step 3: Implement**

In `debug.ts` setup store:

```ts
const historyEpoch = ref(0)
function bumpHistoryEpoch() {
  historyEpoch.value += 1
}
// export historyEpoch, bumpHistoryEpoch in return
```

- [ ] **Step 4: Run tests — expect PASS**

Same command as Step 2. Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add frontend/src/modules/api-test/debug/stores/debug.ts frontend/src/modules/api-test/debug/stores/debug.test.ts
git commit -m "$(cat <<'EOF'
feat(api-test): add historyEpoch for sidebar History refresh

EOF
)"
```

---

### Task 2: Tab `folderId` + breadcrumb path helper

**Files:**
- Modify: `frontend/src/modules/api-test/shell/types/workspace.ts`
- Modify: `frontend/src/modules/api-test/shell/stores/workspace.ts`
- Modify: `frontend/src/modules/api-test/shell/stores/workspace.test.ts`
- Create: `frontend/src/modules/api-test/shell/utils/breadcrumbPath.ts`
- Create: `frontend/src/modules/api-test/shell/utils/breadcrumbPath.test.ts`

**Interfaces:**
- `RequestTab.folderId?: number | null`
- `OpenTabInput.folderId?: number | null`
- `TabMetaPatch` includes `folderId`
- `openOrFocusTab` copies `folderId` onto new tabs
- `setTabMeta` updates `folderId` when provided
- Produces:

```ts
export function buildBreadcrumbSegments(input: {
  source: TabSource
  title: string
  collectionName?: string | null
  folderNames?: string[]
}): string[]
// collection → [collectionName, ...folderNames, title] (skip empty)
// scratch → ['Scratch', title]
// definition → ['Definition', title]
// collectionOverview → []
```

- [ ] **Step 1: Write failing tests**

```ts
// workspace.test.ts
it('openOrFocusTab stores folderId', () => {
  const store = useWorkspaceStore()
  const tab = store.openOrFocusTab({
    source: 'collection',
    refId: 1,
    definitionId: 2,
    collectionId: 3,
    folderId: 9,
    title: 'Login',
    method: 'POST',
    draft: emptyDraft(),
  })
  expect(tab.folderId).toBe(9)
})

// breadcrumbPath.test.ts
it('builds collection path with folders', () => {
  expect(buildBreadcrumbSegments({
    source: 'collection',
    title: 'List',
    collectionName: 'Auth',
    folderNames: ['Apps', 'API'],
  })).toEqual(['Auth', 'Apps', 'API', 'List'])
})

it('scratch and overview', () => {
  expect(buildBreadcrumbSegments({ source: 'scratch', title: 'Untitled' }))
    .toEqual(['Scratch', 'Untitled'])
  expect(buildBreadcrumbSegments({ source: 'collectionOverview', title: 'Auth' }))
    .toEqual([])
})
```

Also add a helper used by breadcrumb UI:

```ts
// same file breadcrumbPath.ts
export function resolveFolderNames(
  folders: CollectionFolderVO[],
  folderId: number | null | undefined,
): string[]
// Walk nested children (and/or parentId links). Return root→leaf names.
// If folderId null/undefined → []
```

Test with a small nested folder tree fixture.

- [ ] **Step 2: Run tests — expect FAIL**

```bash
export PATH="$HOME/.nvm/versions/node/v20.15.1/bin:$PATH"
cd frontend && npm test -- --run src/modules/api-test/shell/stores/workspace.test.ts src/modules/api-test/shell/utils/breadcrumbPath.test.ts
```

- [ ] **Step 3: Implement types, store, helpers**

- Extend `RequestTab` / `OpenTabInput` with `folderId?: number | null`
- In `openOrFocusTab`, set `folderId: input.folderId`
- In `setTabMeta`, `if (partial.folderId !== undefined) tab.folderId = partial.folderId`
- Implement `buildBreadcrumbSegments` + `resolveFolderNames`

For `resolveFolderNames`, prefer DFS find node by id in nested `children`, then build chain by walking parents via a flat map of `id → folder` built from the tree (include nested). Order: root → … → leaf.

- [ ] **Step 4: Run tests — expect PASS**

- [ ] **Step 5: Commit**

```bash
git add frontend/src/modules/api-test/shell/types/workspace.ts \
  frontend/src/modules/api-test/shell/stores/workspace.ts \
  frontend/src/modules/api-test/shell/stores/workspace.test.ts \
  frontend/src/modules/api-test/shell/utils/breadcrumbPath.ts \
  frontend/src/modules/api-test/shell/utils/breadcrumbPath.test.ts
git commit -m "$(cat <<'EOF'
feat(api-test): add folderId on tabs and breadcrumb path helpers

EOF
)"
```

---

### Task 3: History detail → draft/result mapper

**Files:**
- Create: `frontend/src/modules/api-test/shell/utils/mapHistoryDetailToTab.ts`
- Create: `frontend/src/modules/api-test/shell/utils/mapHistoryDetailToTab.test.ts`

**Interfaces:**
- Consumes: `DebugHistoryDetailVO` from `@/modules/api-test/debug/types/debugHistory`
- Produces:

```ts
export function mapHistoryDetailToTab(detail: DebugHistoryDetailVO): {
  title: string
  method: string
  draftPatch: Partial<RequestDraft>
  result: ApiDebugResultVO
}
```

Mapping rules:
- `title` = `detail.name`
- `method` = `detail.requestMethod || 'GET'`
- `draftPatch`: `url`, `method`, `headers` (default `{}`), `queryParams` (default `{}`), `body` (default `''`), `contentType` (default `'application/json'`)
- `result`: mirror fields used today in `RequestWorkspace.onSelectHistory` (historyId, request*, response*, durationMs, status, assertions, extracts)

- [ ] **Step 1: Write failing test** with a full detail fixture; assert key draft + result fields

- [ ] **Step 2: Run — expect FAIL**

```bash
export PATH="$HOME/.nvm/versions/node/v20.15.1/bin:$PATH"
cd frontend && npm test -- --run src/modules/api-test/shell/utils/mapHistoryDetailToTab.test.ts
```

- [ ] **Step 3: Implement mapper**

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit**

```bash
git add frontend/src/modules/api-test/shell/utils/mapHistoryDetailToTab.ts \
  frontend/src/modules/api-test/shell/utils/mapHistoryDetailToTab.test.ts
git commit -m "$(cat <<'EOF'
feat(api-test): map debug history detail into scratch tab payload

EOF
)"
```

---

### Task 4: `HistorySidebarList` component

**Files:**
- Create: `frontend/src/modules/api-test/shell/components/HistorySidebarList.vue`
- Create: `frontend/src/modules/api-test/shell/components/HistorySidebarList.test.ts`

**Interfaces:**
- Props: `records: ApiDebugHistoryVO[]`, `loading?: boolean`, `keyword?: string`
- Emits: `select: [id: number]`
- Filter client-side: name / requestUrl / requestMethod includes keyword (case-insensitive)
- Root `data-testid="history-sidebar-list"`
- Row `data-testid="history-row-{id}"`
- Empty: show Naive `n-empty` with description `发送请求后会出现在这里` when not loading and filtered length 0

Row content: method span with `method-tag method-tag--${METHOD}` (uppercased) + truncated name + status code + createTime (compact).

- [ ] **Step 1: Write failing tests**

```ts
it('renders rows and emits select', async () => {
  const wrapper = mount(HistorySidebarList, {
    props: {
      records: [{
        id: 7,
        definitionId: 1,
        environmentId: null,
        name: 'GET /users',
        requestUrl: '/users',
        requestMethod: 'GET',
        responseStatusCode: 200,
        responseSize: 1,
        durationMs: 10,
        status: 'SUCCESS',
        allAssertionsPassed: true,
        createTime: '2026-08-15 12:00:00',
      }],
      loading: false,
    },
  })
  expect(wrapper.get('[data-testid="history-row-7"]').exists()).toBe(true)
  await wrapper.get('[data-testid="history-row-7"]').trigger('click')
  expect(wrapper.emitted('select')).toEqual([[7]])
})

it('shows empty copy when no records', () => {
  const wrapper = mount(HistorySidebarList, { props: { records: [], loading: false } })
  expect(wrapper.text()).toContain('发送请求后会出现在这里')
})
```

- [ ] **Step 2: Run — expect FAIL**

```bash
export PATH="$HOME/.nvm/versions/node/v20.15.1/bin:$PATH"
cd frontend && npm test -- --run src/modules/api-test/shell/components/HistorySidebarList.test.ts
```

- [ ] **Step 3: Implement component**

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit**

```bash
git add frontend/src/modules/api-test/shell/components/HistorySidebarList.vue \
  frontend/src/modules/api-test/shell/components/HistorySidebarList.test.ts
git commit -m "$(cat <<'EOF'
feat(api-test): add HistorySidebarList for Postman-style history

EOF
)"
```

---

### Task 5: Wire Collections | History in sidebar

**Files:**
- Modify: `frontend/src/modules/api-test/shell/components/CollectionsSidebar.vue`
- Modify: `frontend/src/modules/api-test/shell/components/CollectionsSidebar.test.ts`

**Interfaces:**
- Local `sidebarMode: ref<'collections' | 'history'>('collections')`
- Toggle buttons:
  - `data-testid="sidebar-mode-collections"`
  - `data-testid="sidebar-mode-history"`
- When mode is `history`:
  - Call `debugHistoryApi.page({ projectId: 1, pageNo: 1, pageSize: 50 })` on enter and when `historyEpoch` changes
  - Render `HistorySidebarList` with `records` from page `records`
  - On `@select`: `detail(id)` → `mapHistoryDetailToTab` → `openScratchTab()` → `patchDraft` / `setTabMeta` / `setTabResult`
  - Keep search input; pass keyword into list filter (or reuse `searchKeyword`)
- When mode is `collections`: existing tree UI
- On `onSelectItem`: pass `folderId: item.folderId` into `openOrFocusTab`
- Pass `:selected-id="activeCollectionItemId"` into `CollectionTree` where `activeCollectionItemId` = active tab `source==='collection' ? refId : null`

Mock in tests:

```ts
vi.mock('@/modules/api-test/debug/api/debugHistory', () => ({
  debugHistoryApi: {
    page: (...args: unknown[]) => historyPageMock(...args),
    detail: (...args: unknown[]) => historyDetailMock(...args),
  },
}))
```

- [ ] **Step 1: Write failing tests**

```ts
it('switches to history mode and loads page', async () => {
  historyPageMock.mockResolvedValue({ records: [], total: 0, size: 50, current: 1, pages: 0 })
  const wrapper = mountSidebar()
  await wrapper.get('[data-testid="sidebar-mode-history"]').trigger('click')
  await flushPromises()
  expect(historyPageMock).toHaveBeenCalledWith(expect.objectContaining({ projectId: 1 }))
  expect(wrapper.find('[data-testid="history-sidebar-list"]').exists()).toBe(true)
})

it('clicking history row opens a new scratch tab with mapped draft', async () => {
  historyPageMock.mockResolvedValue({
    records: [{ id: 7, definitionId: null, environmentId: null, name: 'GET /x', requestUrl: '/x', requestMethod: 'GET', responseStatusCode: 200, responseSize: 1, durationMs: 5, status: 'SUCCESS', allAssertionsPassed: true, createTime: 't' }],
    total: 1, size: 50, current: 1, pages: 1,
  })
  historyDetailMock.mockResolvedValue({
    id: 7, projectId: 1, definitionId: null, environmentId: null, name: 'GET /x',
    requestUrl: '/x', requestMethod: 'GET', requestHeaders: { A: '1' }, requestQuery: {},
    requestBody: '', requestContentType: 'application/json',
    responseStatusCode: 200, responseBody: '{}', durationMs: 5, status: 'SUCCESS',
    createBy: 1, createTime: 't',
  })
  const wrapper = mountSidebar()
  await wrapper.get('[data-testid="sidebar-mode-history"]').trigger('click')
  await flushPromises()
  await wrapper.get('[data-testid="history-row-7"]').trigger('click')
  await flushPromises()
  const ws = useWorkspaceStore()
  expect(ws.tabs.some((t) => t.source === 'scratch' && t.draft.url === '/x')).toBe(true)
})
```

Also assert `onSelectItem` path passes `folderId` if existing open-item test can be extended via stub emit.

- [ ] **Step 2: Run — expect FAIL**

```bash
export PATH="$HOME/.nvm/versions/node/v20.15.1/bin:$PATH"
cd frontend && npm test -- --run src/modules/api-test/shell/components/CollectionsSidebar.test.ts
```

- [ ] **Step 3: Implement sidebar wiring**

Watch:

```ts
watch(
  () => [sidebarMode.value, useDebugStore().historyEpoch] as const,
  async ([mode]) => {
    if (mode === 'history') await loadHistoryPage()
  },
)
```

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit**

```bash
git add frontend/src/modules/api-test/shell/components/CollectionsSidebar.vue \
  frontend/src/modules/api-test/shell/components/CollectionsSidebar.test.ts
git commit -m "$(cat <<'EOF'
feat(api-test): add Collections|History sidebar mode and open-from-history

EOF
)"
```

---

### Task 6: RequestWorkspace breadcrumb + remove response History + bump epoch

**Files:**
- Modify: `frontend/src/modules/api-test/shell/components/RequestWorkspace.vue`
- Modify: `frontend/src/modules/api-test/shell/components/RequestWorkspace.test.ts`

**Interfaces:**
- Remove response `n-tab-pane name="history"` and related columns/watch/`onSelectHistory`/`debugHistoryApi` imports used only for that pane
- After successful `handleSend`, call `debugStore.bumpHistoryEpoch()` (always; sidebar watches epoch)
- Breadcrumb row above URL bar:
  - Compute segments via `buildBreadcrumbSegments` + `resolveFolderNames` using `collectionStore` detail / page name and `activeTab.folderId`
  - For `collection` source: look up detail by `collectionId` (`currentDetail` if id matches, else trigger nothing if missing — show collection name from `pageResult` if possible)
  - Render: `seg1 > seg2 > … >` then editable `n-input` for last segment only (`data-testid="request-name"`)
  - If only one segment, still editable title
- Scratch opened from History: show `Scratch` (mapper does not set a History source; title from history name is enough). Optional: if you want `History` label, set tab title prefix only in UI when `result?.historyId` and source scratch — **keep simple: use Scratch**

- [ ] **Step 1: Update / add tests**

```ts
it('bumps historyEpoch after successful send', async () => {
  // existing send setup with definitionId
  const debug = useDebugStore()
  expect(debug.historyEpoch).toBe(0)
  await (wrapper.vm as any).handleSend()
  expect(debug.historyEpoch).toBe(1)
})

it('renders breadcrumb segments for collection tab', async () => {
  const collectionStore = useCollectionStore()
  collectionStore.currentDetail = {
    id: 1, projectId: 1, name: 'Auth', description: '', sortOrder: 0,
    folders: [{
      id: 10, collectionId: 1, parentId: null, name: 'Apps', description: '', sortOrder: 0,
      children: [], items: [],
    }],
    items: [],
  }
  workspace.openOrFocusTab({
    source: 'collection', refId: 88, definitionId: 11, collectionId: 1, folderId: 10,
    title: 'Login', method: 'POST', draft: emptyDraft({ url: '/login', method: 'POST' }),
  })
  const wrapper = mount(RequestWorkspace)
  expect(wrapper.get('[data-testid="request-breadcrumb"]').text()).toContain('Auth')
  expect(wrapper.get('[data-testid="request-breadcrumb"]').text()).toContain('Apps')
  expect(wrapper.find('[data-testid="request-name"]').exists()).toBe(true)
})

it('does not render response history tab', () => {
  workspace.openScratchTab()
  const wrapper = mount(RequestWorkspace)
  expect(wrapper.find('[data-testid="request-history"]').exists()).toBe(false)
})
```

Remove/adjust the older test that switched to response history tab.

- [ ] **Step 2: Run — expect FAIL**

```bash
export PATH="$HOME/.nvm/versions/node/v20.15.1/bin:$PATH"
cd frontend && npm test -- --run src/modules/api-test/shell/components/RequestWorkspace.test.ts
```

- [ ] **Step 3: Implement UI + send bump + delete response History**

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit**

```bash
git add frontend/src/modules/api-test/shell/components/RequestWorkspace.vue \
  frontend/src/modules/api-test/shell/components/RequestWorkspace.test.ts
git commit -m "$(cat <<'EOF'
feat(api-test): breadcrumb request header and sidebar-driven history refresh

EOF
)"
```

---

### Task 7: CollectionTree selection + method polish

**Files:**
- Modify: `frontend/src/modules/api-test/collection/components/CollectionTree.vue`
- Modify: `frontend/src/modules/api-test/collection/components/CollectionTree.test.ts`
- Modify: `frontend/src/modules/api-test/shell/components/CollectionsSidebar.vue` (ensure `:selected-id` bound — if not finished in Task 5)

**Interfaces:**
- Bind `n-tree` `:selected-keys` from `selectedId` → `selectedId != null ? [\`item-${selectedId}\`] : []`
- Method tag text: `(item.method || '').toUpperCase()`; class `method-tag--${upper}`
- CSS: method tag `min-width: 3.2em; text-align: center; font-variant-numeric: tabular-nums;`
- Row: ensure label line-height fits `--api-row-height` via existing density (add `:deep(.n-tree-node-content)` min-height if needed)

- [ ] **Step 1: Write failing tests**

```ts
it('uppercases method and marks selected item', () => {
  const wrapper = mount(CollectionTree, {
    props: {
      folders: [],
      items: [{ id: 5, collectionId: 1, folderId: null, definitionId: 9, name: 'X', description: '', enabled: true, sortOrder: 0, method: 'post', path: '/x' }],
      selectedId: 5,
    },
  })
  expect(wrapper.html()).toContain('method-tag--POST')
  expect(wrapper.text()).toMatch(/POST/)
})
```

(Assert selected keys via component exposed state or DOM class `n-tree-node--selected` if Naive applies it.)

- [ ] **Step 2: Run — expect FAIL**

```bash
export PATH="$HOME/.nvm/versions/node/v20.15.1/bin:$PATH"
cd frontend && npm test -- --run src/modules/api-test/collection/components/CollectionTree.test.ts
```

- [ ] **Step 3: Implement**

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit**

```bash
git add frontend/src/modules/api-test/collection/components/CollectionTree.vue \
  frontend/src/modules/api-test/collection/components/CollectionTree.test.ts \
  frontend/src/modules/api-test/shell/components/CollectionsSidebar.vue
git commit -m "$(cat <<'EOF'
feat(api-test): highlight selected collection item and uppercase method tags

EOF
)"
```

---

### Task 8: Shell smoke + full suite gate

**Files:**
- Modify if needed: `frontend/src/modules/api-test/shell/views/ApiTestShell.test.ts` (stubs stay valid; no required new assertions unless History dialog stubs break)

- [ ] **Step 1: Run focused suites**

```bash
export PATH="$HOME/.nvm/versions/node/v20.15.1/bin:$PATH"
cd frontend && npm test -- --run \
  src/modules/api-test/shell/ \
  src/modules/api-test/debug/stores/debug.test.ts \
  src/modules/api-test/collection/components/CollectionTree.test.ts
```

Expected: all PASS

- [ ] **Step 2: Manual smoke checklist** (document in commit body if anything fixed)

1. Sidebar Collections | History toggle  
2. Send saved request → History list shows new row  
3. Click History → new Tab with URL/response  
4. Breadcrumb shows collection/folder/name; rename + Save updates tree  
5. Active request highlighted in tree  
6. Response pane has no「历史」; collection Run History drawer still works  

- [ ] **Step 3: Commit any leftover fixes** (only if needed)

```bash
git add -A
git commit -m "$(cat <<'EOF'
test(api-test): gate Postman-align shell suites

EOF
)"
```

---

## Spec coverage (self-review)

| Spec requirement | Task |
|------------------|------|
| Sidebar Collections \| History | 4, 5 |
| page API + empty copy | 4, 5 |
| Click → new scratch + draft/result | 3, 5 |
| Send refreshes History via epoch | 1, 6 |
| Remove response History tab | 6 |
| Breadcrumb + folderId | 2, 5, 6 |
| Tree selected + method upper | 7 |
| Full gate | 8 |

No placeholders left. Types: `folderId`, `historyEpoch`, `bumpHistoryEpoch`, `mapHistoryDetailToTab`, `buildBreadcrumbSegments`, `resolveFolderNames` are defined before use.
