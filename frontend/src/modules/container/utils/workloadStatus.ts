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

function countStatus(pods: { status: string }[], status: string) {
  return pods.filter((pod) => pod.status.trim().toLowerCase() === status).length
}

/**
 * 选择器列表可以比期望副本多（滚动更新、尚未退出的 Pod）。
 * 只在多出来时说明，不改期望数，也不把未知状态说成终止中。
 */
export function podListNote(desired: number, pods: { status: string }[]): string | null {
  if (!Number.isFinite(desired) || pods.length <= desired) return null
  const marks: string[] = []
  const terminating = countStatus(pods, 'terminating')
  const pending = countStatus(pods, 'pending')
  if (terminating > 0) marks.push(`${terminating} 个终止中`)
  if (pending > 0) marks.push(`${pending} 个 Pending`)
  const parts = [`选择器匹配到 ${pods.length} 个 Pod，期望副本是 ${desired}`]
  if (marks.length > 0) parts.push(`其中 ${marks.join('、')}`)
  parts.push('多出来的 Pod 仍显示在列表中，期望数以上方卡片为准')
  return `${parts.join('。')}。`
}
