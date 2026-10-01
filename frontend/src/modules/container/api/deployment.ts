import { del, get, put } from '@/shared/api/request'
import type { PageResult } from '@/shared/types/common'
import type { DeploymentDetail, DeploymentSummary, PodSummary, WorkloadEnv, WorkloadEnvUpdate, WorkloadVolumeUpdate, WorkloadVolumes } from '../types/resource'

export const deploymentApi = {
  list: (clusterId: string, params: { namespace?: string; keyword?: string; pageNo?: number; pageSize?: number }) =>
    get<PageResult<DeploymentSummary>>(`/container/clusters/${clusterId}/deployments`, params),
  get: (clusterId: string, namespace: string, name: string) =>
    get<DeploymentDetail>(`/container/clusters/${clusterId}/namespaces/${namespace}/deployments/${name}`),
  yaml: (clusterId: string, namespace: string, name: string) =>
    get<string>(`/container/clusters/${clusterId}/namespaces/${namespace}/deployments/${name}/yaml`),
  updateYaml: (clusterId: string, namespace: string, name: string, yaml: string) =>
    put<void>(`/container/clusters/${clusterId}/namespaces/${namespace}/deployments/${name}/yaml`, yaml),
  scale: (clusterId: string, namespace: string, name: string, replicas: number) =>
    put<void>(`/container/clusters/${clusterId}/namespaces/${namespace}/deployments/${name}/scale`, { replicas }),
  restart: (clusterId: string, namespace: string, name: string) =>
    put<void>(`/container/clusters/${clusterId}/namespaces/${namespace}/deployments/${name}/restart`),
  delete: (clusterId: string, namespace: string, name: string) =>
    del<void>(`/container/clusters/${clusterId}/namespaces/${namespace}/deployments/${name}`),
  pods: (clusterId: string, namespace: string, name: string) =>
    get<PodSummary[]>(`/container/clusters/${clusterId}/namespaces/${namespace}/deployments/${name}/pods`),
  env: (clusterId: string, namespace: string, name: string) =>
    get<WorkloadEnv>(`/container/clusters/${clusterId}/namespaces/${namespace}/deployments/${name}/env`),
  updateEnv: (clusterId: string, namespace: string, name: string, data: WorkloadEnvUpdate) =>
    put<void>(`/container/clusters/${clusterId}/namespaces/${namespace}/deployments/${name}/env`, data),
  volumes: (clusterId: string, namespace: string, name: string) =>
    get<WorkloadVolumes>(`/container/clusters/${clusterId}/namespaces/${namespace}/deployments/${name}/volumes`),
  updateVolumes: (clusterId: string, namespace: string, name: string, data: WorkloadVolumeUpdate) =>
    put<void>(`/container/clusters/${clusterId}/namespaces/${namespace}/deployments/${name}/volumes`, data),
}