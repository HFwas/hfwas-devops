<template>
  <div class="collection-tree">
    <n-tree
      :data="treeData"
      :default-expand-all="false"
      :render-label="renderLabel"
      :render-prefix="renderPrefix"
      :selected-keys="selectedKeys"
      block-line
      expand-trigger="click"
      @update:selected-keys="onSelect"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, h } from 'vue'
import { NTree } from 'naive-ui'
import type { TreeOption } from 'naive-ui'
import type { CollectionFolderVO, CollectionItemVO } from '@/modules/api-test/collection/types/collection'

interface MyTreeOption extends TreeOption {
  isFolder?: boolean
  isItem?: boolean
  raw?: CollectionFolderVO | CollectionItemVO
}

const props = defineProps<{
  folders: CollectionFolderVO[]
  items: CollectionItemVO[]
  selectedId?: number | null
}>()

const emit = defineEmits<{
  'selectItem': [item: CollectionItemVO]
  'selectFolder': [folder: CollectionFolderVO]
}>()

const selectedKeys = computed<Array<string | number>>(() =>
  props.selectedId != null ? [`item-${props.selectedId}`] : [],
)

const treeData = computed<MyTreeOption[]>(() => {
  const result: MyTreeOption[] = []

  for (const folder of props.folders) {
    result.push(buildFolderNode(folder))
  }

  for (const item of props.items) {
    result.push(buildItemNode(item))
  }

  return result
})

function buildFolderNode(folder: CollectionFolderVO): MyTreeOption {
  const children: MyTreeOption[] = []

  if (folder.children) {
    for (const child of folder.children) {
      children.push(buildFolderNode(child))
    }
  }

  if (folder.items) {
    for (const item of folder.items) {
      children.push(buildItemNode(item))
    }
  }

  return {
    key: `folder-${folder.id}`,
    label: folder.name,
    isFolder: true,
    isLeaf: children.length === 0,
    children: children.length > 0 ? children : undefined,
    raw: folder,
  }
}

function buildItemNode(item: CollectionItemVO): MyTreeOption {
  return {
    key: `item-${item.id}`,
    label: item.name || `${item.method} ${item.path}`,
    isItem: true,
    isLeaf: true,
    raw: item,
  }
}

function renderPrefix({ option }: { option: TreeOption }) {
  const myOption = option as MyTreeOption
  if (myOption.isItem && myOption.raw) {
    const item = myOption.raw as CollectionItemVO
    const method = (item.method || '').toUpperCase()
    return h('span', { class: ['method-tag', `method-tag--${method}`] }, method)
  }
  return h('span', { class: 'folder-glyph' }, '▸')
}

function renderLabel({ option }: { option: TreeOption }) {
  const myOption = option as MyTreeOption
  if (myOption.isItem && myOption.raw) {
    const item = myOption.raw as CollectionItemVO
    const disabled = !item.enabled
    return h('span', { class: ['item-label', disabled ? 'item-label--disabled' : null] }, [
      h('span', { class: 'item-name' }, item.name || `${item.method} ${item.path}`),
      disabled ? h('span', { class: 'disabled-badge' }, '已禁用') : null,
    ])
  }
  return h('span', { class: 'folder-label' }, myOption.label)
}

function onSelect(keys: Array<string | number>) {
  if (keys.length === 0) return
  const key = keys[0]
  const option = findOption(treeData.value, key)
  if (!option) return

  if (option.isItem && option.raw) {
    emit('selectItem', option.raw as CollectionItemVO)
  } else if (option.isFolder && option.raw) {
    emit('selectFolder', option.raw as CollectionFolderVO)
  }
}

function findOption(options: MyTreeOption[], key: string | number): MyTreeOption | null {
  for (const opt of options) {
    if (opt.key === key) return opt
    if (opt.children) {
      const found = findOption(opt.children as MyTreeOption[], key)
      if (found) return found
    }
  }
  return null
}
</script>

<style scoped>
.collection-tree {
  min-height: 200px;
}

.method-tag {
  display: inline-block;
  min-width: 3.2em;
  text-align: center;
  font-variant-numeric: tabular-nums;
  font-size: var(--api-font-sm, 10px);
  font-weight: 700;
  padding: 1px 4px;
  border: 1px solid currentColor;
  border-radius: 2px;
  margin-right: 4px;
  line-height: 1.2;
  color: var(--api-method-default, #64748b);
}

.method-tag--GET {
  color: var(--api-method-get, #10b981);
}

.method-tag--POST {
  color: var(--api-method-post, #f59e0b);
}

.method-tag--PUT {
  color: var(--api-method-put, #3b82f6);
}

.method-tag--PATCH {
  color: var(--api-method-patch, #8b5cf6);
}

.method-tag--DELETE {
  color: var(--api-method-delete, #ef4444);
}

.method-tag--HEAD,
.method-tag--OPTIONS,
.method-tag--TRACE {
  color: var(--api-method-default, #64748b);
}

.folder-glyph {
  display: inline-block;
  width: 12px;
  margin-right: 4px;
  font-size: 11px;
  color: var(--wb-text-secondary, #64748b);
  text-align: center;
}

.item-label {
  display: flex;
  align-items: center;
  gap: 4px;
}

.item-label--disabled {
  opacity: 0.55;
}

.item-name {
  font-size: var(--api-font, 13px);
}

.disabled-badge {
  flex-shrink: 0;
  font-size: var(--api-font-sm, 12px);
  color: var(--wb-muted, #999);
  margin-left: 4px;
}

.folder-label {
  font-weight: 500;
  font-size: var(--api-font, 13px);
}

:deep(.n-tree-node-content) {
  min-height: var(--api-row-height, 28px);
}
</style>
