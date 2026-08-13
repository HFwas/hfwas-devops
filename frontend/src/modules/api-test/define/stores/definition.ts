import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ApiDefinitionVO, ApiDefinitionDetailVO, ApiDefinitionQueryDTO } from '@/modules/api-test/define/types/definition'
import { apiDefinitionApi } from '@/modules/api-test/define/api/definition'
import type { PageResult } from '@/shared/types/common'

export const useApiDefinitionStore = defineStore('apiDefinition', () => {
  /** 接口列表分页结果 */
  const pageResult = ref<PageResult<ApiDefinitionVO>>({
    records: [],
    total: 0,
    size: 20,
    current: 1,
    pages: 0,
  })

  /** 当前查询条件 */
  const query = ref<ApiDefinitionQueryDTO>({
    projectId: 0,
    pageNo: 1,
    pageSize: 20,
  })

  /** 当前查看的接口详情 */
  const currentDetail = ref<ApiDefinitionDetailVO | null>(null)

  /** 加载中 */
  const loading = ref(false)

  /** 分页查询 */
  async function loadPage(searchQuery?: Partial<ApiDefinitionQueryDTO>) {
    if (searchQuery) {
      Object.assign(query.value, searchQuery)
    }
    // 重置页码
    if (searchQuery?.keyword !== undefined || searchQuery?.groupId !== undefined ||
        searchQuery?.method !== undefined || searchQuery?.status !== undefined) {
      query.value.pageNo = 1
    }

    loading.value = true
    try {
      pageResult.value = await apiDefinitionApi.page(query.value)
    } finally {
      loading.value = false
    }
  }

  /** 翻页 */
  async function changePage(pageNo: number, pageSize: number) {
    query.value.pageNo = pageNo
    query.value.pageSize = pageSize
    await loadPage()
  }

  /** 获取详情 */
  async function loadDetail(id: number) {
    currentDetail.value = await apiDefinitionApi.detail(id)
    return currentDetail.value
  }

  /** 创建 */
  async function create(data: Parameters<typeof apiDefinitionApi.create>[0], userId: number) {
    const detail = await apiDefinitionApi.create(data, userId)
    await loadPage()
    return detail
  }

  /** 更新 */
  async function update(id: number, data: Parameters<typeof apiDefinitionApi.update>[1], userId: number) {
    const detail = await apiDefinitionApi.update(id, data, userId)
    if (currentDetail.value?.id === id) {
      currentDetail.value = detail
    }
    await loadPage()
    return detail
  }

  /** 删除 */
  async function deleteDefinition(id: number) {
    await apiDefinitionApi.delete(id)
    if (currentDetail.value?.id === id) {
      currentDetail.value = null
    }
    await loadPage()
  }

  /** 发布 */
  async function publish(id: number, userId: number) {
    await apiDefinitionApi.publish(id, userId)
    if (currentDetail.value?.id === id) {
      currentDetail.value.status = 'PUBLISHED'
    }
    await loadPage()
  }

  /** 废弃 */
  async function deprecate(id: number, userId: number) {
    await apiDefinitionApi.deprecate(id, userId)
    if (currentDetail.value?.id === id) {
      currentDetail.value.status = 'DEPRECATED'
    }
    await loadPage()
  }

  /** 恢复草稿 */
  async function revertDraft(id: number, userId: number) {
    await apiDefinitionApi.revertDraft(id, userId)
    if (currentDetail.value?.id === id) {
      currentDetail.value.status = 'DRAFT'
    }
    await loadPage()
  }

  return {
    pageResult,
    query,
    currentDetail,
    loading,
    loadPage,
    changePage,
    loadDetail,
    create,
    update,
    deleteDefinition,
    publish,
    deprecate,
    revertDraft,
  }
})