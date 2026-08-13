<template>
  <n-tree
    :data="groupTree"
    :default-expand-all="true"
    :selected-keys="selectedKeys"
    :selectable="true"
    :block-line="true"
    label-field="label"
    key-field="key"
    children-field="children"
    @update:selected-keys="onSelect"
    @contextmenu="handleContextMenu"
  >
  </n-tree>

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
</template>

<script setup lang="ts">
import { computed, ref, h } from 'vue'
import { NButton, useDialog, useMessage } from 'naive-ui'
import { useApiGroupStore } from '@/modules/api-test/define/stores/group'
import type { ApiGroupVO } from '@/modules/api-test/define/types/group'
import { useAuthStore } from '@/modules/user/stores/auth'

const props = defineProps<{
  projectId: number
}>()

const emit = defineEmits<{
  select: [groupId: number | null]
  refresh: []
}>()

const groupStore = useApiGroupStore()
const authStore = useAuthStore()
const dialog = useDialog()
const message = useMessage()

const selectedKeys = computed<number[]>(() =>
  groupStore.selectedGroupId != null ? [groupStore.selectedGroupId] : [],
)

const groupTree = computed(() => buildTreeData(groupStore.groupTree))

/** 右键菜单状态 */
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

function buildTreeData(groups: ApiGroupVO[]): any[] {
  return groups.map((g) => ({
    key: g.id,
    label: g.name,
    parentId: g.parentId,
    apiCount: g.apiCount,
    children: g.children?.length ? buildTreeData(g.children) : undefined,
    isLeaf: !g.children?.length,
  }))
}

function onSelect(keys: number[]) {
  const id = keys.length > 0 ? keys[0] : null
  groupStore.selectGroup(id)
  emit('select', id)
}

function handleContextMenu(e: MouseEvent, node: { key: number }) {
  e.preventDefault()
  contextMenuNode.value = findGroup(groupStore.groupTree, node.key)
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
    handleCreateChild()
  } else if (key === 'edit') {
    handleEdit()
  } else if (key === 'delete') {
    handleDelete()
  }
}

function handleCreateChild() {
  // 通过事件触发父组件中的新建对话框
  emit('refresh')
}

function handleEdit() {
  // 通过事件触发父组件中的编辑对话框
  emit('refresh')
}

function handleDelete() {
  const node = contextMenuNode.value
  if (!node) return
  dialog.warning({
    title: '确认删除',
    content: `确定删除分组「${node.name}」吗？该分组下的所有接口将变为未分组状态。`,
    positiveText: '确定删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await groupStore.deleteGroup(node.id)
        message.success('删除成功')
      } catch (e: any) {
        message.error(e.message || '删除失败')
      }
    },
  })
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
.group-tree-node {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 2px 0;
}

.group-tree-node.is-root {
  font-weight: 500;
}

.group-tree-node__count {
  font-size: 12px;
  color: #999;
  background: #f0f0f0;
  border-radius: 8px;
  padding: 0 6px;
  line-height: 18px;
}
</style>