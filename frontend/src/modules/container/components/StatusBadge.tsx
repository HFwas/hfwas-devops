import { StatusIcon } from '@/components/console/StatusIcon'

/** 容器状态走全站字典，保留这个名字以免各列表再写一套颜色。 */
export function StatusBadge({ status }: { status?: string | null }) {
  return <StatusIcon status={status} />
}
