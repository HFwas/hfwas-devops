import { defineStore } from 'pinia'
import { ref } from 'vue'
import type {
  CollectionVO,
  CollectionDetailVO,
  CollectionCreateDTO,
  CollectionUpdateDTO,
  CollectionFolderVO,
  CollectionFolderCreateDTO,
  CollectionFolderUpdateDTO,
  CollectionItemVO,
  CollectionItemAddDTO,
  CollectionItemBatchDTO,
  CollectionRunVO,
  CollectionRunDetailVO,
} from '@/modules/api-test/collection/types/collection'
import { collectionApi } from '@/modules/api-test/collection/api/collection'
import type { PageResult } from '@/shared/types/common'

export const useCollectionStore = defineStore('apiCollection', () => {
  /** 集合列表分页结果 */
  const pageResult = ref<PageResult<CollectionVO>>({
    records: [],
    total: 0,
    size: 20,
    current: 1,
    pages: 0,
  })

  /** 当前集合详情 */
  const currentDetail = ref<CollectionDetailVO | null>(null)

  /** 已加载过的集合详情，按 id 缓存，供跨集合 tab 面包屑等读取 */
  const detailsById = ref<Record<number, CollectionDetailVO>>({})

  /** 文件夹树 */
  const folderTree = ref<CollectionFolderVO[]>([])

  /** 运行历史 */
  const runHistory = ref<PageResult<CollectionRunVO>>({
    records: [],
    total: 0,
    size: 20,
    current: 1,
    pages: 0,
  })

  /** 当前运行详情 */
  const currentRunDetail = ref<CollectionRunDetailVO | null>(null)

  /** 加载中 */
  const loading = ref(false)

  /** 执行中 */
  const executing = ref(false)

  /** 分页查询集合列表 */
  async function loadPage(params: { projectId: number; keyword?: string; pageNo?: number; pageSize?: number }) {
    loading.value = true
    try {
      pageResult.value = await collectionApi.page(params)
    } finally {
      loading.value = false
    }
  }

  /** 获取集合详情 */
  async function loadDetail(id: number) {
    currentDetail.value = await collectionApi.detail(id)
    detailsById.value[id] = currentDetail.value
    return currentDetail.value
  }

  /** 创建集合 */
  async function create(data: CollectionCreateDTO, projectId: number, userId: number) {
    return await collectionApi.create(data, projectId, userId)
  }

  /** 更新集合 */
  async function update(id: number, data: CollectionUpdateDTO, userId: number) {
    const vo = await collectionApi.update(id, data, userId)
    if (currentDetail.value?.id === id) {
      currentDetail.value.name = vo.name
      currentDetail.value.description = vo.description
    }
    return vo
  }

  /** 删除集合 */
  async function deleteCollection(id: number) {
    await collectionApi.delete(id)
  }

  /** 加载文件夹树 */
  async function loadFolderTree(collectionId: number) {
    folderTree.value = await collectionApi.getFolderTree(collectionId)
  }

  /** 创建文件夹 */
  async function createFolder(collectionId: number, data: CollectionFolderCreateDTO, userId: number) {
    return await collectionApi.createFolder(collectionId, data, userId)
  }

  /** 更新文件夹 */
  async function updateFolder(collectionId: number, folderId: number, data: CollectionFolderUpdateDTO, userId: number) {
    return await collectionApi.updateFolder(collectionId, folderId, data, userId)
  }

  /** 删除文件夹 */
  async function deleteFolder(collectionId: number, folderId: number) {
    await collectionApi.deleteFolder(collectionId, folderId)
  }

  /** 添加集合项 */
  async function addItem(collectionId: number, data: CollectionItemAddDTO, userId: number) {
    return await collectionApi.addItem(collectionId, data, userId)
  }

  /** 更新集合项 */
  async function updateItem(collectionId: number, itemId: number, data: CollectionItemAddDTO) {
    return await collectionApi.updateItem(collectionId, itemId, data)
  }

  /** 删除集合项 */
  async function deleteItem(collectionId: number, itemId: number) {
    await collectionApi.deleteItem(collectionId, itemId)
  }

  /** 重排序 */
  async function reorderItems(collectionId: number, itemIds: number[]) {
    await collectionApi.reorderItems(collectionId, itemIds)
  }

  /** 批量添加 */
  async function batchAddItems(collectionId: number, data: CollectionItemBatchDTO, userId: number) {
    await collectionApi.batchAddItems(collectionId, data, userId)
  }

  /** 执行集合 */
  async function runCollection(collectionId: number, environmentId?: number, userId?: number) {
    executing.value = true
    try {
      return await collectionApi.run(collectionId, environmentId, userId)
    } finally {
      executing.value = false
    }
  }

  /** 加载运行历史 */
  async function loadRunHistory(collectionId: number, params?: { pageNo?: number; pageSize?: number }) {
    runHistory.value = await collectionApi.runHistory(collectionId, params)
  }

  /** 获取运行详情 */
  async function loadRunDetail(runId: number) {
    currentRunDetail.value = await collectionApi.runDetail(runId)
    return currentRunDetail.value
  }

  /** 删除运行记录 */
  async function deleteRun(runId: number) {
    await collectionApi.deleteRun(runId)
  }

  return {
    pageResult,
    currentDetail,
    detailsById,
    folderTree,
    runHistory,
    currentRunDetail,
    loading,
    executing,
    loadPage,
    loadDetail,
    create,
    update,
    deleteCollection,
    loadFolderTree,
    createFolder,
    updateFolder,
    deleteFolder,
    addItem,
    updateItem,
    deleteItem,
    reorderItems,
    batchAddItems,
    runCollection,
    loadRunHistory,
    loadRunDetail,
    deleteRun,
  }
})