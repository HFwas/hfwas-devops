import { get, post } from '@/shared/api/request'
import request from '@/shared/api/request'
import type { BaseResult, PageResult } from '@/shared/types/common'
import type {
  CollectionVO,
  CollectionDetailVO,
  CollectionCreateDTO,
  CollectionUpdateDTO,
  CollectionFolderVO,
  CollectionFolderCreateDTO,
  CollectionFolderUpdateDTO,
  CollectionItemVO,
  CollectionItemAddDTO,
  CollectionItemBatchDTO,
  CollectionRunVO,
  CollectionRunDetailVO,
} from '@/modules/api-test/collection/types/collection'

export const collectionApi = {
  // ===== 集合CRUD =====
  page: (params: { projectId: number; keyword?: string; pageNo?: number; pageSize?: number }) =>
    get<PageResult<CollectionVO>>('/apitest/collections/page', params),

  detail: (id: number) =>
    get<CollectionDetailVO>(`/apitest/collections/${id}`),

  create: (data: CollectionCreateDTO, projectId: number, userId: number) =>
    post<CollectionVO>(`/apitest/collections?projectId=${projectId}&userId=${userId}`, data),

  update: (id: number, data: CollectionUpdateDTO, userId: number) =>
    request.put<BaseResult<CollectionVO>>(`/apitest/collections/${id}?userId=${userId}`, data)
      .then(res => res.data.data),

  delete: (id: number) =>
    request.delete<BaseResult<void>>(`/apitest/collections/${id}`).then(res => res.data.data),

  // ===== 文件夹CRUD =====
  createFolder: (collectionId: number, data: CollectionFolderCreateDTO, userId: number) =>
    post<CollectionFolderVO>(`/apitest/collections/${collectionId}/folders?userId=${userId}`, data),

  updateFolder: (collectionId: number, folderId: number, data: CollectionFolderUpdateDTO, userId: number) =>
    request.put<BaseResult<CollectionFolderVO>>(`/apitest/collections/${collectionId}/folders/${folderId}?userId=${userId}`, data)
      .then(res => res.data.data),

  deleteFolder: (collectionId: number, folderId: number) =>
    request.delete<BaseResult<void>>(`/apitest/collections/${collectionId}/folders/${folderId}`).then(res => res.data.data),

  getFolderTree: (collectionId: number) =>
    get<CollectionFolderVO[]>(`/apitest/collections/${collectionId}/folders/tree`),

  // ===== 集合项管理 =====
  addItem: (collectionId: number, data: CollectionItemAddDTO, userId: number) =>
    post<CollectionItemVO>(`/apitest/collections/${collectionId}/items?userId=${userId}`, data),

  updateItem: (collectionId: number, itemId: number, data: CollectionItemAddDTO) =>
    request.put<BaseResult<CollectionItemVO>>(`/apitest/collections/${collectionId}/items/${itemId}`, data)
      .then(res => res.data.data),

  deleteItem: (collectionId: number, itemId: number) =>
    request.delete<BaseResult<void>>(`/apitest/collections/${collectionId}/items/${itemId}`).then(res => res.data.data),

  reorderItems: (collectionId: number, itemIds: number[]) =>
    request.put<BaseResult<void>>(`/apitest/collections/${collectionId}/items/reorder`, itemIds)
      .then(res => res.data.data),

  batchAddItems: (collectionId: number, data: CollectionItemBatchDTO, userId: number) =>
    post<void>(`/apitest/collections/${collectionId}/items/batch?userId=${userId}`, data),

  // ===== 集合执行 =====
  run: (collectionId: number, environmentId?: number, userId?: number) =>
    post<CollectionRunVO>(`/apitest/collections/${collectionId}/run?environmentId=${environmentId ?? ''}&userId=${userId ?? ''}`),

  runHistory: (collectionId: number, params?: { pageNo?: number; pageSize?: number }) =>
    get<PageResult<CollectionRunVO>>(`/apitest/collections/${collectionId}/runs`, params),

  runDetail: (runId: number) =>
    get<CollectionRunDetailVO>(`/apitest/collections/runs/${runId}`),

  deleteRun: (runId: number) =>
    request.delete<BaseResult<void>>(`/apitest/collections/runs/${runId}`).then(res => res.data.data),
}