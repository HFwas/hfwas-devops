import { del, get, put } from '@/shared/api/request'
import type { PageResult } from '@/shared/types/common'
import type { DeploymentDetail, DeploymentSummary } from '../types/resource'

export const deploymentApi = {
  list: (clusterId: number, params: { namespace?: string; keyword?: string; pageNo?: number; pageSize?: number }) =>
    get<PageResult<DeploymentSummary>>(`/container/clusters/${clusterId}/deployments`, params),
  get: (clusterId: number, namespace: string, name: string) =>
    get<DeploymentDetail>(`/container/clusters/${clusterId}/namespaces/${namespace}/deployments/${name}`),
  yaml: (clusterId: number, namespace: string, name: string) =>
    get<string>(`/container/clusters/${clusterId}/namespaces/${namespace}/deployments/${name}/yaml`),
  scale: (clusterId: number, namespace: string, name: string, replicas: number) =>
    put<void>(`/container/clusters/${clusterId}/namespaces/${namespace}/deployments/${name}/scale`, { replicas }),
  restart: (clusterId: number, namespace: string, name: string) =>
    put<void>(`/container/clusters/${clusterId}/namespaces/${namespace}/deployments/${name}/restart`),
  delete: (clusterId: number, namespace: string, name: string) =>
    del<void>(`/container/clusters/${clusterId}/namespaces/${namespace}/deployments/${name}`),
}