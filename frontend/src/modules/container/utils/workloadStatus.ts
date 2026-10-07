/** 从 Deployment 详情里的 `replicas=1 updated=1 ready=1` 文本取出一个计数值。 */
export function replicaField(status: string | null | undefined, key: string): number | null {
  if (!status) return null
  const match = status.match(new RegExp(`(?:^|\\s)${key}=(\\d+)(?:\\s|$)`))
  return match ? Number(match[1]) : null
}

/** 用真实副本数字给出概览卡上的状态词，不另造集群数据。 */
export function deploymentAvailability(item: {
  desiredReplicas: number
  readyReplicas: number
  availableReplicas: number
}): string {
  if (item.desiredReplicas === 0) return 'Scaled to zero'
  if (item.availableReplicas >= item.desiredReplicas && item.readyReplicas >= item.desiredReplicas) return 'Available'
  if (item.readyReplicas > 0 || item.availableReplicas > 0) return 'Progressing'
  return 'Pending'
}

export function selectorPills(selector?: string | Record<string, string> | null): string[] {
  if (!selector) return []
  if (typeof selector !== 'string') {
    return Object.entries(selector).map(([key, value]) => `${key}=${value}`)
  }
  const body = selector.trim().replace(/^\{/, '').replace(/\}$/, '')
  if (!body) return []
  return body.split(',').map((part) => part.trim()).filter(Boolean)
}

export function recordPills(record?: Record<string, string> | null): string[] {
  return Object.entries(record ?? {}).map(([key, value]) => (value ? `${key}=${value}` : key))
}
