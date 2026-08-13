import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { ApiGroupVO } from '@/modules/api-test/define/types/group'
import { apiGroupApi } from '@/modules/api-test/define/api/group'

export const useApiGroupStore = defineStore('apiGroup', () => {
  /** 分组树 */
  const groupTree = ref<ApiGroupVO[]>([])

  /** 当前选中的分组 ID */
  const selectedGroupId = ref<number | null>(null)

  /** 当前选中的分组 */
  const selectedGroup = computed(() => {
    if (!selectedGroupId.value) return null
    return findGroupById(groupTree.value, selectedGroupId.value)
  })

  /** 加载分组树 */
  async function loadTree(projectId: number) {
    groupTree.value = await apiGroupApi.tree(projectId)
  }

  /** 选中分组 */
  function selectGroup(id: number | null) {
    selectedGroupId.value = id
  }

  /** 创建分组 */
  async function create(data: Parameters<typeof apiGroupApi.create>[0], userId: number) {
    const vo = await apiGroupApi.create(data, userId)
    // 刷新树
    await loadTree(data.projectId)
    return vo
  }

  /** 更新分组 */
  async function update(id: number, data: Parameters<typeof apiGroupApi.update>[1], userId: number) {
    const vo = await apiGroupApi.update(id, data, userId)
    // 刷新树
    if (groupTree.value.length > 0) {
      const projectId = findProjectId(groupTree.value)
      if (projectId) await loadTree(projectId)
    }
    return vo
  }

  /** 删除分组 */
  async function deleteGroup(id: number) {
    await apiGroupApi.delete(id)
    // 刷新树
    if (groupTree.value.length > 0) {
      const projectId = findProjectId(groupTree.value)
      if (projectId) await loadTree(projectId)
    }
    if (selectedGroupId.value === id) {
      selectedGroupId.value = null
    }
  }

  /** 获取分组详情 */
  async function getDetail(id: number) {
    return await apiGroupApi.detail(id)
  }

  return {
    groupTree,
    selectedGroupId,
    selectedGroup,
    loadTree,
    selectGroup,
    create,
    update,
    deleteGroup,
    getDetail,
  }
})

/** 在树中递归查找分组 */
function findGroupById(tree: ApiGroupVO[], id: number): ApiGroupVO | null {
  for (const node of tree) {
    if (node.id === id) return node
    if (node.children?.length) {
      const found = findGroupById(node.children, id)
      if (found) return found
    }
  }
  return null
}

/** 从树中获取 projectId（根节点继承） */
function findProjectId(tree: ApiGroupVO[]): number | null {
  if (tree.length === 0) return null
  return tree[0].projectId
}