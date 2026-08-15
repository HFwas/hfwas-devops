import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { ApiDebugResultVO, ApiDebugExecuteDTO, ApiDebugHistoryVO } from '@/modules/api-test/debug/types/debug'
import { debugApi } from '@/modules/api-test/debug/api/debug'
import { debugHistoryApi } from '@/modules/api-test/debug/api/debugHistory'

export const useDebugStore = defineStore('apiDebug', () => {
  /** 当前调试结果 */
  const currentResult = ref<ApiDebugResultVO | null>(null)

  /** 调试执行中 */
  const executing = ref(false)

  /** 调试历史列表 */
  const historyList = ref<ApiDebugHistoryVO[]>([])

  /** 历史刷新 epoch（Send 后 bump，供 History 侧栏 watch） */
  const historyEpoch = ref(0)
  function bumpHistoryEpoch() {
    historyEpoch.value += 1
  }

  /** 执行调试 */
  async function execute(data: ApiDebugExecuteDTO) {
    executing.value = true
    currentResult.value = null
    try {
      const result = await debugApi.execute(data)
      currentResult.value = result
      return result
    } finally {
      executing.value = false
    }
  }

  /** 加载某接口的调试历史 */
  async function loadHistory(definitionId: number, limit = 20) {
    historyList.value = await debugHistoryApi.listByDefinition(definitionId, limit)
  }

  /** 清空历史列表（如未关联 definition） */
  function clearHistory() {
    historyList.value = []
  }

  /** 清空结果 */
  function clearResult() {
    currentResult.value = null
  }

  return {
    currentResult,
    executing,
    historyList,
    historyEpoch,
    execute,
    loadHistory,
    clearHistory,
    clearResult,
    bumpHistoryEpoch,
  }
})
