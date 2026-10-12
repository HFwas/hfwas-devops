import { UsageBar } from '@/components/console/UsageBar'
import { quantityRatio } from '@/modules/container/utils/quantity'

/**
 * 容器资源列已有的「请求 / 上限」文本。
 * 两边都能解析时，用上限当分母画 Requests 条（蓝，超阈值转黄/红）。
 * 行内没有节点容量，不另画一条总会顶满的 Limits 条。
 */
export function RequestLimitCell({ request, limit }: { request?: string | null; limit?: string | null }) {
  const left = request?.trim() || '—'
  const right = limit?.trim() || '—'
  const caption = `${left} / ${right}`
  const ratio = quantityRatio(request, limit)
  if (ratio == null) return <span className="tabular-nums">{caption}</span>
  return <UsageBar kind="requests" ratio={ratio} caption={caption} />
}
