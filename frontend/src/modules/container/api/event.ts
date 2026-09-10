import { get } from '@/shared/api/request'
import type { EventInfo } from '../types/event'

export const eventApi = {
  list: (clusterId: number, namespace: string, uid?: string) =>
    get<EventInfo[]>(`/container/clusters/${clusterId}/namespaces/${namespace}/events`, { uid }),
}