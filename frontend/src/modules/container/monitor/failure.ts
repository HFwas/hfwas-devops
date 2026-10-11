import { errorMessage } from '@/shared/errors/apiError'

export type MonitorFailureKind = 'misconfig' | 'request'

export interface MonitorFailure {
  kind: MonitorFailureKind
  message: string
}

const MISCONFIG = /未配置|prometheusUrl|Prometheus 地址|labels/i

/** 把监控接口错误分成「没配 Prometheus」和「请求失败」。 */
export function describeMonitorFailure(errors: unknown[]): MonitorFailure | null {
  const present = errors.filter((error) => error != null)
  if (present.length === 0) return null
  const message = errorMessage(present[0], '监控数据加载失败')
  return {
    kind: MISCONFIG.test(message) ? 'misconfig' : 'request',
    message,
  }
}

export function monitorEmptyText(error: unknown): string {
  const failure = describeMonitorFailure([error])
  if (!failure) return '暂无数据'
  return failure.kind === 'misconfig' ? `未配置：${failure.message}` : `请求失败：${failure.message}`
}
