import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useDebugStore } from './debug'
import type { ApiDebugExecuteDTO, ApiDebugResultVO, ApiDebugHistoryVO } from '@/modules/api-test/debug/types/debug'

vi.mock('@/modules/api-test/debug/api/debug', () => ({
  debugApi: {
    execute: vi.fn(),
  },
}))

vi.mock('@/modules/api-test/debug/api/debugHistory', () => ({
  debugHistoryApi: {
    listByDefinition: vi.fn(),
  },
}))

import { debugApi } from '@/modules/api-test/debug/api/debug'
import { debugHistoryApi } from '@/modules/api-test/debug/api/debugHistory'

const mockResult: ApiDebugResultVO = {
  requestUrl: '/api/users',
  requestMethod: 'GET',
  responseStatusCode: 200,
  responseBody: '{"id":1,"name":"test"}',
  durationMs: 156,
  status: 'SUCCESS',
  assertionResults: [{ name: '状态码', source: 'RESPONSE_STATUS', compareType: 'EQUALS', expression: null, expected: '200', actual: '200', passed: true }],
  allAssertionsPassed: true,
}

const mockHistory: ApiDebugHistoryVO[] = [
  { id: 1, definitionId: 10, environmentId: 1, name: 'GET /api/users', requestUrl: '/api/users', requestMethod: 'GET', responseStatusCode: 200, responseSize: 50, durationMs: 156, status: 'SUCCESS', allAssertionsPassed: true, createTime: '2026-08-15T10:00:00' },
  { id: 2, definitionId: 10, environmentId: 1, name: 'POST /api/users', requestUrl: '/api/users', requestMethod: 'POST', responseStatusCode: 201, responseSize: 30, durationMs: 200, status: 'SUCCESS', allAssertionsPassed: true, createTime: '2026-08-15T10:05:00' },
]

describe('useDebugStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('initial state is correct', () => {
    const store = useDebugStore()
    expect(store.currentResult).toBeNull()
    expect(store.executing).toBe(false)
    expect(store.historyList).toEqual([])
  })

  it('historyEpoch starts at 0 and bumpHistoryEpoch increments', () => {
    const store = useDebugStore()
    expect(store.historyEpoch).toBe(0)
    store.bumpHistoryEpoch()
    expect(store.historyEpoch).toBe(1)
    store.bumpHistoryEpoch()
    expect(store.historyEpoch).toBe(2)
  })

  it('execute sets result on success', async () => {
    vi.mocked(debugApi.execute).mockResolvedValue(mockResult)
    const store = useDebugStore()
    const dto: ApiDebugExecuteDTO = { url: '/api/users', method: 'GET' }
    const result = await store.execute(dto)
    expect(result).toStrictEqual(mockResult)
    expect(store.currentResult).toStrictEqual(mockResult)
    expect(store.executing).toBe(false)
  })

  it('execute sets executing flag', async () => {
    let resolvePromise: (value: ApiDebugResultVO) => void
    const promise = new Promise<ApiDebugResultVO>((resolve) => {
      resolvePromise = resolve
    })
    vi.mocked(debugApi.execute).mockReturnValue(promise as any)

    const store = useDebugStore()
    const dto: ApiDebugExecuteDTO = { url: '/api/users', method: 'GET' }
    const executePromise = store.execute(dto)

    expect(store.executing).toBe(true)
    expect(store.currentResult).toBeNull()

    resolvePromise!(mockResult)
    await executePromise
    expect(store.executing).toBe(false)
  })

  it('execute clears previous result before running', async () => {
    vi.mocked(debugApi.execute).mockResolvedValue(mockResult)
    const store = useDebugStore()
    store.currentResult = mockResult
    const dto: ApiDebugExecuteDTO = { url: '/api/other', method: 'POST' }
    await store.execute(dto)
    expect(store.currentResult).toStrictEqual(mockResult)
  })

  it('execute sets executing to false on error', async () => {
    vi.mocked(debugApi.execute).mockRejectedValue(new Error('Network error'))
    const store = useDebugStore()
    const dto: ApiDebugExecuteDTO = { url: '/api/error', method: 'GET' }
    await expect(store.execute(dto)).rejects.toThrow('Network error')
    expect(store.executing).toBe(false)
    expect(store.currentResult).toBeNull()
  })

  it('loadHistory populates history list', async () => {
    vi.mocked(debugHistoryApi.listByDefinition).mockResolvedValue(mockHistory)
    const store = useDebugStore()
    await store.loadHistory(10)
    expect(store.historyList).toEqual(mockHistory)
    expect(store.historyList).toHaveLength(2)
  })

  it('loadHistory with custom limit', async () => {
    vi.mocked(debugHistoryApi.listByDefinition).mockResolvedValue(mockHistory)
    const store = useDebugStore()
    await store.loadHistory(10, 50)
    expect(debugHistoryApi.listByDefinition).toHaveBeenCalledWith(10, 50)
  })

  it('clearResult resets current result', () => {
    const store = useDebugStore()
    store.currentResult = mockResult
    store.clearResult()
    expect(store.currentResult).toBeNull()
  })

  it('multiple executions update result each time', async () => {
    const result1: ApiDebugResultVO = { ...mockResult, durationMs: 100, responseBody: '{"first":true}' }
    const result2: ApiDebugResultVO = { ...mockResult, durationMs: 200, responseBody: '{"second":true}' }
    vi.mocked(debugApi.execute).mockResolvedValueOnce(result1).mockResolvedValueOnce(result2)

    const store = useDebugStore()
    const dto: ApiDebugExecuteDTO = { url: '/api/users', method: 'GET' }

    await store.execute(dto)
    expect(store.currentResult?.durationMs).toBe(100)

    await store.execute(dto)
    expect(store.currentResult?.durationMs).toBe(200)
  })

  it('loadHistory with no definitionId returns empty', async () => {
    vi.mocked(debugHistoryApi.listByDefinition).mockResolvedValue([])
    const store = useDebugStore()
    await store.loadHistory(999)
    expect(store.historyList).toEqual([])
  })
})