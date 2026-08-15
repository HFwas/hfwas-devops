# ApiTest Shell Compact UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply a compact density + visual hierarchy pass to the collection-first `ApiTestShell` using CSS tokens and component styles, without changing information architecture or APIs.

**Architecture:** Define density and method-color tokens on `.api-test-shell`. Propagate tighter padding/row heights through sidebar, tree, tab bar, request workspace, and response summary. Prefer CSS/`class` updates; only touch tree render functions where method/disabled/folder markers need new classes.

**Tech Stack:** Vue 3, Naive UI, existing `--wb-*` theme tokens, Vitest

**Spec:** `docs/superpowers/specs/2026-08-15-api-test-shell-compact-ui-design.md`

## Global Constraints

- No IA changes (no env-into-tab-row, no new modules)
- No backend REST changes
- Keep console `--wb-*` + existing `--api-test-accent*`; add density tokens only under `.api-test-shell`
- Density: compact (`--api-density-pad-y` ~4–6px, `--api-row-height` ~28px, fonts 12/13px)
- Do not introduce a new JSON editor
- Remove folder emoji; method tags colored by verb; disabled item compact badge
- Theme: light/dark both acceptable contrast
- Prefer Node 20 for tests: `cd frontend && npm test -- src/modules/api-test/shell/`
- Do not break existing `data-testid`s

---

## File Structure

| Path | Responsibility |
|------|----------------|
| `frontend/src/modules/api-test/shell/views/ApiTestShell.vue` | Density + method color CSS variables on `.api-test-shell` |
| `frontend/src/modules/api-test/shell/styles/density.css` (optional) | Shared token block if shell `<style>` gets too long — prefer keep in shell unless >~40 lines of tokens |
| `frontend/src/modules/api-test/shell/components/CollectionsSidebar.vue` | Toolbar / row compact styles |
| `frontend/src/modules/api-test/collection/components/CollectionTree.vue` | Method classes, disabled badge, folder glyph |
| `frontend/src/modules/api-test/shell/components/RequestTabBar.vue` | Compact tabs + active accent |
| `frontend/src/modules/api-test/shell/components/RequestWorkspace.vue` | URL bar / panes / resizer padding |
| `frontend/src/modules/api-test/define/components/ApiWorkspaceResponse.vue` | Summary bar + body font density |
| `frontend/src/modules/api-test/shell/components/CollectionOverviewTab.vue` | Light padding align |
| `frontend/src/modules/api-test/shared/components/KeyValueEditor.vue` | Optional tiny top-margin tighten only if scoped safely |

---

### Task 1: Shell density tokens

**Files:**
- Modify: `frontend/src/modules/api-test/shell/views/ApiTestShell.vue` (`<style scoped>` on `.api-test-shell`)
- Modify: `frontend/src/modules/api-test/shell/views/ApiTestShell.test.ts` (assert CSS variables present via computed style or attribute/class smoke)

**Interfaces:**
- Produces CSS variables on `.api-test-shell`:
  - `--api-density-pad-y: 5px`
  - `--api-density-pad-x: 10px`
  - `--api-row-height: 28px`
  - `--api-font-sm: 12px`
  - `--api-font: 13px`
  - `--api-method-get: #10b981`
  - `--api-method-post: #f59e0b`
  - `--api-method-put: #3b82f6`
  - `--api-method-patch: #8b5cf6`
  - `--api-method-delete: #ef4444`
  - `--api-method-default: #64748b`
  - Dark: slightly brighter equivalents under `html.dark .api-test-shell`

- [ ] **Step 1: Add failing smoke test**

```ts
it('exposes compact density CSS variables on shell root', () => {
  const wrapper = mountShell() // existing helper
  const el = wrapper.get('.api-test-shell').element as HTMLElement
  // jsdom/happy-dom may not compute CSS vars from scoped CSS reliably;
  // assert the style block defines them by checking document or data attribute.
  // Prefer: add data-density="compact" on root in template, assert attribute.
  expect(wrapper.get('.api-test-shell').attributes('data-density')).toBe('compact')
})
```

If happy-dom cannot read CSS variables, use `data-density="compact"` on the root div as the testable contract; still define the CSS variables in the same task.

- [ ] **Step 2: Run test — expect FAIL**

```bash
cd frontend && npm test -- src/modules/api-test/shell/views/ApiTestShell.test.ts
```

- [ ] **Step 3: Implement tokens + `data-density="compact"`**

On root:

```vue
<div class="api-test-shell" data-density="compact">
```

In scoped style, extend `.api-test-shell { ... }` with the variables listed above. Keep existing `--api-test-accent*`. Tighten `__header` padding to use `var(--api-density-pad-y) var(--api-density-pad-x)`.

- [ ] **Step 4: Run — PASS**

- [ ] **Step 5: Commit**

```bash
git add frontend/src/modules/api-test/shell/views/ApiTestShell.vue \
  frontend/src/modules/api-test/shell/views/ApiTestShell.test.ts
git commit -m "style(api-test): add compact density tokens on ApiTestShell"
```

---

### Task 2: Sidebar + CollectionTree method/disabled polish

**Files:**
- Modify: `frontend/src/modules/api-test/shell/components/CollectionsSidebar.vue`
- Modify: `frontend/src/modules/api-test/collection/components/CollectionTree.vue`
- Modify or create: `frontend/src/modules/api-test/collection/components/CollectionTree.test.ts` (if none exists, create minimal mount test)

**Interfaces:**
- Produces class names on method tags: `method-tag method-tag--GET` (uppercased method)
- Disabled: `disabled-badge` text `已禁用` (short); item row/label opacity ~0.55
- Folder prefix: class `folder-glyph` with character `▸` or lucide-free text `📁` **removed** — use `📁` → `▸`/`▼`/`📁` replace with `📁` removed and use `📁` → simple `·` or Unicode folder `🗂️` NO — use `📁` → `▸` for collapsed feel or `🗂` avoided — **use `📁` → `▣` or Naive icon-free `span.folder-glyph` content `📁` replaced by `📁` → `#` — Spec: remove emoji. Use ASCII/Unicode: `▸` for folder.**

- [ ] **Step 1: Failing test — method class + no emoji folder**

```ts
it('renders method-tag--POST for POST items and no folder emoji', () => {
  const wrapper = mount(CollectionTree, {
    props: {
      folders: [{ id: 1, collectionId: 1, parentId: null, name: 'F', description: '', sortOrder: 0, children: [], items: [] }],
      items: [{ id: 2, collectionId: 1, folderId: null, definitionId: 9, name: 'Untitled', description: '', enabled: false, sortOrder: 0, method: 'POST', path: '/' }],
    },
  })
  expect(wrapper.html()).toContain('method-tag--POST')
  expect(wrapper.html()).toContain('已禁用')
  expect(wrapper.html()).not.toContain('📁')
})
```

- [ ] **Step 2: Run — FAIL**

```bash
cd frontend && npm test -- src/modules/api-test/collection/components/CollectionTree.test.ts
```

- [ ] **Step 3: Update `renderPrefix` / `renderLabel` + CSS**

```ts
// method class
h('span', { class: ['method-tag', `method-tag--${item.method}`] }, item.method)
// folder
h('span', { class: 'folder-glyph' }, '▸')
// disabled
h('span', { class: 'disabled-badge' }, '已禁用')
```

CSS (use CSS vars with fallbacks so tree works outside shell too):

```css
.method-tag--GET { color: var(--api-method-get, #10b981); border-color: currentColor; }
.method-tag--POST { color: var(--api-method-post, #f59e0b); border-color: currentColor; }
/* PUT PATCH DELETE DEFAULT similarly */
.item-label.is-disabled, .method-tag + .item-name /* prefer class on label */
.item-label--disabled { opacity: 0.55; }
.disabled-badge {
  font-size: var(--api-font-sm, 12px);
  color: var(--wb-muted, #999);
  margin-left: 4px;
}
```

Tighten `CollectionsSidebar` toolbar/row padding to density vars; Run/Hist stay `size="tiny"` quaternary (already tiny — reduce label prominence via CSS opacity on `__row-actions`).

- [ ] **Step 4: Run tree + sidebar tests — PASS**

```bash
cd frontend && npm test -- src/modules/api-test/collection/components/CollectionTree.test.ts \
  src/modules/api-test/shell/components/CollectionsSidebar.test.ts
```

- [ ] **Step 5: Commit**

```bash
git add frontend/src/modules/api-test/shell/components/CollectionsSidebar.vue \
  frontend/src/modules/api-test/collection/components/CollectionTree.vue \
  frontend/src/modules/api-test/collection/components/CollectionTree.test.ts
git commit -m "style(api-test): compact sidebar and colored method tags"
```

---

### Task 3: RequestTabBar + RequestWorkspace density

**Files:**
- Modify: `frontend/src/modules/api-test/shell/components/RequestTabBar.vue`
- Modify: `frontend/src/modules/api-test/shell/components/RequestWorkspace.vue`
- Modify: `frontend/src/modules/api-test/shell/components/RequestTabBar.test.ts` / `RequestWorkspace.test.ts` only if selectors break

**Interfaces:**
- URL bar padding: `var(--api-density-pad-y) var(--api-density-pad-x)` (approx `6px 10px`)
- Send button: `size="small"` if not already
- Tabs container horizontal padding reduced to density-x
- Active tab underline uses `var(--api-test-accent)` where Naive allows via deep selector sparingly:

```css
:deep(.n-tabs-bar) {
  background-color: var(--api-test-accent, #4098fc);
}
```

Only if it does not break other tabs visually — limit to `.request-workspace__tabs` and `.request-tab-bar`.

- [ ] **Step 1: Smoke assert URL bar class uses compact padding (optional)**

Prefer visual CSS change without brittle computed-style tests. Run existing workspace/tab tests as regression:

```bash
cd frontend && npm test -- src/modules/api-test/shell/components/RequestWorkspace.test.ts \
  src/modules/api-test/shell/components/RequestTabBar.test.ts
```

Expected: PASS before and after if testids unchanged. If you add a `data-compact-url` attribute for contract, add one assertion.

- [ ] **Step 2: Implement CSS/template size tweaks**

`RequestWorkspace.vue`:

```css
.request-workspace__url-bar {
  gap: 6px;
  padding: var(--api-density-pad-y, 6px) var(--api-density-pad-x, 10px) 0;
}
.request-workspace__tabs {
  padding: 0 var(--api-density-pad-x, 10px);
}
.request-workspace__response-tabs {
  padding: 0 var(--api-density-pad-x, 10px);
}
```

Set Send `size="small"`. Shrink alert margin to density padding.

`RequestTabBar.vue`: reduce tab button padding/min-height toward `--api-row-height`; muted inactive labels.

- [ ] **Step 3: Re-run tests — PASS**

- [ ] **Step 4: Commit**

```bash
git add frontend/src/modules/api-test/shell/components/RequestTabBar.vue \
  frontend/src/modules/api-test/shell/components/RequestWorkspace.vue
git commit -m "style(api-test): compact request tab bar and URL workspace"
```

---

### Task 4: Response summary + overview + suite green

**Files:**
- Modify: `frontend/src/modules/api-test/define/components/ApiWorkspaceResponse.vue`
- Modify: `frontend/src/modules/api-test/shell/components/CollectionOverviewTab.vue`
- Optionally: `frontend/src/modules/api-test/shared/components/KeyValueEditor.vue` (`margin-top: 4px` → `2px` on add button only)

**Interfaces:**
- Summary bar: smaller gap/padding; body textarea font-size `var(--api-font-sm, 12px)` via class not inline if easy
- Overview: padding uses density vars

- [ ] **Step 1: Implement response/overview CSS**

```css
.workspace-response__summary {
  gap: 8px;
  padding: var(--api-density-pad-y, 6px) 0;
}
.workspace-response__body-input {
  font-family: monospace;
  font-size: var(--api-font-sm, 12px);
}
```

Replace inline `style="font-family: monospace; font-size: 13px;"` with class.

- [ ] **Step 2: Run full shell suite**

```bash
cd frontend && npm test -- src/modules/api-test/shell/
```

Expected: all PASS. Fix any class/testid fallout.

- [ ] **Step 3: Manual checklist (note in commit body)**

1. Sidebar denser; POST amber / GET green distinguishable  
2. Disabled shows compact 已禁用  
3. URL bar tighter; Send still obvious  
4. Response meta bar tighter; dark mode still readable  

- [ ] **Step 4: Commit**

```bash
git add frontend/src/modules/api-test/define/components/ApiWorkspaceResponse.vue \
  frontend/src/modules/api-test/shell/components/CollectionOverviewTab.vue \
  frontend/src/modules/api-test/shared/components/KeyValueEditor.vue
git commit -m "style(api-test): compact response summary and overview padding"
```

---

## Spec coverage (self-review)

| Spec § | Task |
|--------|------|
| Density tokens | 1 |
| Sidebar + method/disabled/folder | 2 |
| Tab bar + URL workspace | 3 |
| Response + overview | 4 |
| No IA / no JSON editor / no env move | Global |
| Shell tests green | 4 |

No TBD placeholders. Method class naming `method-tag--POST` consistent across Task 2.
