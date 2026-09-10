import { del, get, post, postFormData } from '@/shared/api/request'
import request from '@/shared/api/request'
import type { PageResult } from '@/shared/types/common'
import type { PodDetail, PodSummary } from '../types/resource'

export const podApi = {
  list: (clusterId: string, params: { namespace?: string; keyword?: string; pageNo?: number; pageSize?: number }) =>
    get<PageResult<PodSummary>>(`/container/clusters/${clusterId}/pods`, params),
  get: (clusterId: string, namespace: string, name: string) =>
    get<PodDetail>(`/container/clusters/${clusterId}/namespaces/${namespace}/pods/${name}`),
  yaml: (clusterId: string, namespace: string, name: string) =>
    get<string>(`/container/clusters/${clusterId}/namespaces/${namespace}/pods/${name}/yaml`),
  logs: (clusterId: string, namespace: string, name: string, params?: { container?: string; tailLines?: number }) =>
    get<string>(`/container/clusters/${clusterId}/namespaces/${namespace}/pods/${name}/logs`, params),
  delete: (clusterId: string, namespace: string, name: string) =>
    del<void>(`/container/clusters/${clusterId}/namespaces/${namespace}/pods/${name}`),

  /** Upload a file to the pod container */
  uploadFile: (clusterId: string, namespace: string, name: string,
               container: string, destPath: string, file: File) => {
    const form = new FormData()
    form.append('file', file)
    form.append('container', container)
    form.append('destPath', destPath)
    return postFormData<void>(
      `/container/clusters/${clusterId}/namespaces/${namespace}/pods/${name}/upload`,
      form,
      300_000, // 5 min timeout for large files
    )
  },

  /** Download a file from the pod container */
  downloadFile: async (clusterId: string, namespace: string, name: string,
                       container: string, path: string): Promise<{ blob: Blob; filename: string }> => {
    const res = await request.get(
      `/container/clusters/${clusterId}/namespaces/${namespace}/pods/${name}/download`,
      {
        responseType: 'blob',
        params: { container, path },
      },
    )
    const blob = res.data as Blob

    // Check if the response is actually an error (JSON wrapped in blob)
    if (blob.type?.includes('json')) {
      const text = await blob.text()
      try {
        const err = JSON.parse(text) as { code?: number; msg?: string }
        throw new Error(err.msg || '下载失败')
      } catch (e) {
        if (e instanceof Error) throw e
        throw new Error('下载失败')
      }
    }

    // Extract filename from Content-Disposition header
    const disposition = res.headers['content-disposition'] as string | undefined
    let filename = path.split('/').pop() || 'download'
    if (disposition) {
      const match = disposition.match(/filename\*=UTF-8''([^;]+)/i)
      if (match?.[1]) {
        filename = decodeURIComponent(match[1])
      }
    }

    return { blob, filename }
  },
}