import { del, get, put } from '@/shared/api/request'
import type { PageResult } from '@/shared/types/common'
import type { StatefulSetSummary, WorkloadEnv, WorkloadEnvUpdate, WorkloadVolumeUpdate, WorkloadVolumes } from '../types/resource'

export const statefulSetApi = {
  list: (clusterId: string, params: { namespace?: string; keyword?: string; pageNo?: number; pageSize?: number }) =>
    get<PageResult<StatefulSetSummary>>(`/container/clusters/${clusterId}/statefulsets`, params),
  yaml: (clusterId: string, namespace: string, name: string) =>
    get<string>(`/container/clusters/${clusterId}/namespaces/${namespace}/statefulsets/${name}/yaml`),
  updateYaml: (clusterId: string, namespace: string, name: string, yaml: string) =>
    put<void>(`/container/clusters/${clusterId}/namespaces/${namespace}/statefulsets/${name}/yaml`, yaml),
  delete: (clusterId: string, namespace: string, name: string) =>
    del<void>(`/container/clusters/${clusterId}/namespaces/${namespace}/statefulsets/${name}`),
  env: (clusterId: string, namespace: string, name: string) =>
    get<WorkloadEnv>(`/container/clusters/${clusterId}/namespaces/${namespace}/statefulsets/${name}/env`),
  updateEnv: (clusterId: string, namespace: string, name: string, data: WorkloadEnvUpdate) =>
    put<void>(`/container/clusters/${clusterId}/namespaces/${namespace}/statefulsets/${name}/env`, data),
  volumes: (clusterId: string, namespace: string, name: string) =>
    get<WorkloadVolumes>(`/container/clusters/${clusterId}/namespaces/${namespace}/statefulsets/${name}/volumes`),
  updateVolumes: (clusterId: string, namespace: string, name: string, data: WorkloadVolumeUpdate) =>
    put<void>(`/container/clusters/${clusterId}/namespaces/${namespace}/statefulsets/${name}/volumes`, data),
}