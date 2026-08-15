import { post } from '@/shared/api/request'
import type { ApiDebugExecuteDTO, ApiDebugResultVO } from '@/modules/api-test/debug/types/debug'

export const debugApi = {
  /** 执行调试 */
  execute: (data: ApiDebugExecuteDTO) =>
    post<ApiDebugResultVO>('/apitest/debug/execute', data),
}