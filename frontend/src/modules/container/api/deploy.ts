import { post } from '@/shared/api/request'
import type { DeployFromImageRequest, DeployResultVO } from '../types/registry'

export const deployApi = {
  deployFromImage: (registryId: string, data: DeployFromImageRequest) =>
    post<DeployResultVO>(`/container/registries/${registryId}/deploy`, data),
}