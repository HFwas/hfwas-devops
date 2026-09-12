import { del, get, post, put } from '@/shared/api/request'
import type { PageResult } from '@/shared/types/common'
import type {
  DependencyComponent,
  DependencyComponentAggregate,
  DependencyScanSubmitPayload,
  BatchDependencyScanSubmitPayload,
  DependencyScanTask,
} from '@/modules/pipeline/types/dependency'

export const dependencyApi = {
  // 依赖组件
  pageComponents: (params: { pageNo?: number; pageSize?: number; search?: string; language?: string }) =>
    get<PageResult<DependencyComponent>>('/dependency/components/page', params),
  aggregatedComponents: (params: { pageNo?: number; pageSize?: number; search?: string; language?: string }) =>
    get<PageResult<DependencyComponentAggregate>>('/dependency/components/aggregated', params),

  // 批量依赖扫描
  submitScan: (data: DependencyScanSubmitPayload) => post<DependencyScanTask>('/dependency-scan/submit', data),
  batchSubmitScan: (data: BatchDependencyScanSubmitPayload) =>
    post<DependencyScanTask[]>('/dependency-scan/batch-submit', data),
  listScanTasks: (params?: { limit?: number }) => get<DependencyScanTask[]>('/dependency-scan/tasks', params),
}