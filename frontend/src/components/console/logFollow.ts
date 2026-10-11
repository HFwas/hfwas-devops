/** 距底部不超过该像素时仍视为贴底，避免差一两行就暂停跟随。 */
export const LOG_FOLLOW_THRESHOLD_PX = 48

export interface ScrollMetrics {
  scrollTop: number
  scrollHeight: number
  clientHeight: number
}

export type FollowMode = 'follow' | 'paused'

export function distanceFromBottom(metrics: ScrollMetrics): number {
  return metrics.scrollHeight - metrics.clientHeight - metrics.scrollTop
}

/** 内容未溢出，或滚动位置已经靠近底部。 */
export function shouldStickToBottom(metrics: ScrollMetrics, threshold = LOG_FOLLOW_THRESHOLD_PX): boolean {
  if (metrics.scrollHeight <= metrics.clientHeight + 1) return true
  return distanceFromBottom(metrics) <= threshold
}

export function followModeAfterScroll(metrics: ScrollMetrics, threshold = LOG_FOLLOW_THRESHOLD_PX): FollowMode {
  return shouldStickToBottom(metrics, threshold) ? 'follow' : 'paused'
}

/**
 * 跟随模式下、且尚未贴底时，返回应设置的 scrollTop。
 * 已贴底或已暂停时返回 null，避免每次渲染都改滚动位置。
 */
export function scrollTopForFollow(
  mode: FollowMode,
  metrics: ScrollMetrics,
  threshold = LOG_FOLLOW_THRESHOLD_PX,
): number | null {
  if (mode !== 'follow') return null
  if (shouldStickToBottom(metrics, threshold)) return null
  return Math.max(0, metrics.scrollHeight - metrics.clientHeight)
}
