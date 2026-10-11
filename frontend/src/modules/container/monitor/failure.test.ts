import { describe, expect, it } from 'vitest'
import { ApiError } from '@/shared/errors/apiError'
import { describeMonitorFailure, monitorEmptyText } from '@/modules/container/monitor/failure'

describe('monitor failure', () => {
  it('separates a missing prometheus address from a request failure', () => {
    expect(describeMonitorFailure([new ApiError(30006, '该集群未配置 Prometheus 地址（labels 中无 prometheusUrl）')])).toEqual({
      kind: 'misconfig',
      message: '该集群未配置 Prometheus 地址（labels 中无 prometheusUrl）',
    })
    expect(describeMonitorFailure([new Error('timeout')])).toEqual({
      kind: 'request',
      message: 'timeout',
    })
    expect(describeMonitorFailure([])).toBeNull()
    expect(monitorEmptyText(new Error('502'))).toBe('请求失败：502')
  })
})
