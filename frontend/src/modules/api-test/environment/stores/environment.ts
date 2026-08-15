import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { EnvironmentVO, EnvironmentDetailVO, EnvironmentQueryDTO } from '@/modules/api-test/environment/types/environment'
import { environmentApi } from '@/modules/api-test/environment/api/environment'
import type { PageResult } from '@/shared/types/common'

export const useEnvironmentStore = defineStore('apiEnvironment', () => {
  /** 环境列表分页结果 */
  const pageResult = ref<PageResult<EnvironmentVO>>({
    records: [],
    total: 0,
    size: 20,
    current: 1,
    pages: 0,
  })

  /** 所有环境列表（不分页） */
  const allList = ref<EnvironmentVO[]>([])

  /** 当前环境详情 */
  const currentDetail = ref<EnvironmentDetailVO | null>(null)

  /** 当前选中的环境ID */
  const selectedEnvironmentId = ref<number | null>(null)

  /** 加载中 */
  const loading = ref(false)

  /** 分页查询 */
  async function loadPage(query: EnvironmentQueryDTO) {
    loading.value = true
    try {
      pageResult.value = await environmentApi.page(query)
    } finally {
      loading.value = false
    }
  }

  /** 加载所有环境列表 */
  async function loadAll(projectId: number) {
    allList.value = await environmentApi.listAll(projectId)
  }

  /** 获取详情 */
  async function loadDetail(id: number) {
    currentDetail.value = await environmentApi.detail(id)
    return currentDetail.value
  }

  /** 创建 */
  async function create(data: Parameters<typeof environmentApi.create>[0], projectId: number, userId: number) {
    const detail = await environmentApi.create(data, projectId, userId)
    return detail
  }

  /** 更新 */
  async function update(id: number, data: Parameters<typeof environmentApi.update>[1], userId: number) {
    const detail = await environmentApi.update(id, data, userId)
    if (currentDetail.value?.id === id) {
      currentDetail.value = detail
    }
    return detail
  }

  /** 删除 */
  async function deleteEnvironment(id: number) {
    await environmentApi.delete(id)
    if (currentDetail.value?.id === id) {
      currentDetail.value = null
    }
  }

  /** 选中环境 */
  function selectEnvironment(id: number | null) {
    selectedEnvironmentId.value = id
  }

  return {
    pageResult,
    allList,
    currentDetail,
    selectedEnvironmentId,
    loading,
    loadPage,
    loadAll,
    loadDetail,
    create,
    update,
    deleteEnvironment,
    selectEnvironment,
  }
})