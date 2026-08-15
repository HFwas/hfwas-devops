<template>
  <div class="workspace-sidebar">
    <!-- 顶部操作栏 -->
    <div class="workspace-sidebar__header">
      <span class="workspace-sidebar__title">接口列表</span>
      <n-button size="tiny" quaternary @click="$emit('createDefinition')">
        <template #icon>
          <n-icon><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M11 11V5h2v6h6v2h-6v6h-2v-6H5v-2z"/></svg></n-icon>
        </template>
      </n-button>
    </div>

    <!-- 搜索框 -->
    <n-input
      v-model:value="searchKeyword"
      placeholder="搜索接口名称/路径"
      size="tiny"
      clearable
      class="workspace-sidebar__search"
      @update:value="onSearchChange"
    />

    <!-- 接口树 -->
    <div class="workspace-sidebar__tree">
      <n-spin :show="loading">
        <n-tree
          :data="treeData"
          :selected-keys="selectedKeys"
          :default-expand-all="true"
          :render-label="renderLabel"
          :render-prefix="renderPrefix"
          block-line
          selectable
          expand-trigger="click"
          @update:selected-keys="onSelect"
          @contextmenu="handleContextMenu"
        />
      </n-spin>
    </div>

    <!-- 右键菜单 -->
    <n-dropdown
      placement="bottom-start"
      trigger="manual"
      :x="contextMenuX"
      :y="contextMenuY"
      :options="contextMenuOptions"
      :show="showContextMenu"
      :on-clickoutside="closeContextMenu"
      @select="handleContextMenuAction"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, h } from 'vue'
import { NButton, NIcon, useDialog, useMessage } from 'naive-ui'
import type { TreeOption } from 'naive-ui'
import { useApiGroupStore } from '@/modules/api-test/define/stores/group'
import type { ApiGroupVO } from '@/modules/api-test/define/types/group'
import type { ApiDefinitionVO, HttpMethod } from '@/modules/api-test/define/types/definition'
import { HTTP_METHOD_OPTIONS } from '@/modules/api-test/define/types/definition'
import { useAuthStore } from '@/modules/user/stores/auth'

interface MyTreeOption extends TreeOption {
  type: 'group' | 'definition'
  raw?: ApiGroupVO | ApiDefinitionVO
}

const props = defineProps<{
  definitions: ApiDefinitionVO[]
  loading: boolean
  selectedId: number | null
  projectId: number
}>()

const emit = defineEmits<{
  'select': [id: number]
  'createDefinition': []
  'createGroup': [parentId?: number | null]
  'editGroup': [group: ApiGroupVO]
  'deleteGroup': [group: ApiGroupVO]
  'refresh': []
}>()

const groupStore = useApiGroupStore()
const authStore = useAuthStore()
const dialog = useDialog()
const message = useMessage()

const userId = computed(() => Number(authStore.user?.id) || 0)
const searchKeyword = ref('')

// 选中 keys
const selectedKeys = computed<(string | number)[]>(() =>
  props.selectedId ? [props.selectedId] : []
)

// 构建树数据
const treeData = computed<MyTreeOption[]>(() => {
  const result: MyTreeOption[] = []

  // 先加「未分组」节点
  const ungrouped = props.definitions.filter(d => d.groupId == null)
  if (ungrouped.length > 0) {
    result.push({
      key: '__ungrouped',
      label: '未分组',
      type: 'group',
      isLeaf: false,
      children: ungrouped.map(d => buildDefinitionNode(d)),
    })
  }

  // 再加分组树
  const groups = groupStore.groupTree || []
  for (const group of groups) {
    result.push(buildGroupNode(group))
  }

  // 如果有搜索关键词，扁平化展示
  if (searchKeyword.value.trim()) {
    return flattenTree(result, searchKeyword.value.trim().toLowerCase())
  }

  return result
})

function buildGroupNode(group: ApiGroupVO): MyTreeOption {
  const children: MyTreeOption[] = []

  if (group.children?.length) {
    for (const child of group.children) {
      children.push(buildGroupNode(child))
    }
  }

  // 找出属于该分组的 API
  const groupDefs = props.definitions.filter(d => d.groupId === group.id)
  for (const def of groupDefs) {
    children.push(buildDefinitionNode(def))
  }

  return {
    key: `group-${group.id}`,
    label: group.name,
    type: 'group',
    raw: group,
    isLeaf: children.length === 0,
    children: children.length > 0 ? children : undefined,
  }
}

function buildDefinitionNode(def: ApiDefinitionVO): MyTreeOption {
  return {
    key: def.id,
    label: def.name,
    type: 'definition',
    raw: def,
    isLeaf: true,
  }
}

function flattenTree(nodes: MyTreeOption[], keyword: string): MyTreeOption[] {
  const result: MyTreeOption[] = []
  for (const node of nodes) {
    if (node.type === 'definition') {
      const def = node.raw as ApiDefinitionVO
      if (def.name.toLowerCase().includes(keyword) || def.path.toLowerCase().includes(keyword)) {
        result.push(node)
      }
    }
    if (node.children) {
      result.push(...flattenTree(node.children as MyTreeOption[], keyword))
    }
  }
  return result
}

// 方法标签颜色
function getMethodColor(method: string): string {
  const option = HTTP_METHOD_OPTIONS.find(o => o.value === method)
  return option?.color || '#909399'
}

// 渲染前缀（方法标签）
function renderPrefix({ option }: { option: TreeOption }) {
  const myOption = option as MyTreeOption
  if (myOption.type === 'definition' && myOption.raw) {
    const def = myOption.raw as ApiDefinitionVO
    return h('span', {
      class: 'method-tag',
      style: { color: getMethodColor(def.method), borderColor: getMethodColor(def.method) }
    }, def.method)
  }
  if (myOption.type === 'group') {
    return h('span', { class: 'folder-icon' }, '📁')
  }
  return null
}

// 渲染标签（名称 + 路径）
function renderLabel({ option }: { option: TreeOption }) {
  const myOption = option as MyTreeOption
  if (myOption.type === 'definition' && myOption.raw) {
    const def = myOption.raw as ApiDefinitionVO
    return h('div', { class: 'def-label' }, [
      h('span', { class: 'def-name' }, def.name),
      h('span', { class: 'def-path' }, def.path),
    ])
  }
  if (myOption.type === 'group') {
    return h('span', { class: 'group-label' }, myOption.label)
  }
  return h('span', {}, myOption.label)
}

// 选中
function onSelect(keys: Array<string | number>) {
  if (keys.length === 0) {
    emit('select', null as any)
    return
  }
  const key = keys[0]
  // 如果选中的是分组或未分组节点，忽略
  if (typeof key === 'string' && key.startsWith('group-') || key === '__ungrouped') {
    return
  }
  emit('select', Number(key))
}

// 搜索
function onSearchChange() {
  // 自动触发的 computed 会处理
}

// 右键菜单
const showContextMenu = ref(false)
const contextMenuX = ref(0)
const contextMenuY = ref(0)
const contextMenuNode = ref<ApiGroupVO | null>(null)

const contextMenuOptions = computed(() => {
  const base = [
    {
      label: '新建子分组',
      key: 'create-child',
      icon: () => h(NButton, { text: true, depth: 3 }, { default: () => '+' }),
    },
  ]
  if (contextMenuNode.value) {
    base.push(
      { label: '编辑分组', key: 'edit' } as any,
      { label: '删除分组', key: 'delete' } as any,
    )
  }
  return base
})

function handleContextMenu(e: MouseEvent, node: { key: string | number }) {
  const key = node.key
  if (typeof key !== 'string' || !key.startsWith('group-')) return
  const groupId = Number(key.replace('group-', ''))
  contextMenuNode.value = findGroup(groupStore.groupTree, groupId)
  if (!contextMenuNode.value) return
  e.preventDefault()
  contextMenuX.value = e.clientX
  contextMenuY.value = e.clientY
  showContextMenu.value = true
}

function closeContextMenu() {
  showContextMenu.value = false
  contextMenuNode.value = null
}

function handleContextMenuAction(key: string) {
  closeContextMenu()
  if (key === 'create-child') {
    emit('createGroup', contextMenuNode.value?.id)
  } else if (key === 'edit' && contextMenuNode.value) {
    emit('editGroup', contextMenuNode.value)
  } else if (key === 'delete' && contextMenuNode.value) {
    emit('deleteGroup', contextMenuNode.value)
  }
}

function findGroup(tree: ApiGroupVO[], id: number): ApiGroupVO | null {
  for (const node of tree) {
    if (node.id === id) return node
    if (node.children?.length) {
      const found = findGroup(node.children, id)
      if (found) return found
    }
  }
  return null
}
</script>

<style scoped>
.workspace-sidebar {
  width: 280px;
  min-width: 280px;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-right: 1px solid #e5e7eb;
  overflow: hidden;
}

.workspace-sidebar__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 12px 8px;
}

.workspace-sidebar__title {
  font-size: 14px;
  font-weight: 600;
}

.workspace-sidebar__search {
  padding: 0 12px 8px;
}

.workspace-sidebar__tree {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px 8px;
}

.method-tag {
  font-size: 10px;
  font-weight: 700;
  padding: 1px 4px;
  border: 1px solid;
  border-radius: 3px;
  margin-right: 4px;
  line-height: 1.4;
  flex-shrink: 0;
}

.folder-icon {
  font-size: 14px;
  margin-right: 4px;
}

.def-label {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
  overflow: hidden;
}

.def-name {
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.def-path {
  font-size: 11px;
  color: #999;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.group-label {
  font-weight: 500;
  font-size: 13px;
}
</style>