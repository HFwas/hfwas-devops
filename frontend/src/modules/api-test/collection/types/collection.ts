/** 集合 */
export interface CollectionVO {
  id: number
  projectId: number
  name: string
  description: string
  sortOrder: number
  folderCount: number
  itemCount: number
  createTime: string
  updateTime: string
}

/** 集合文件夹（树形结构） */
export interface CollectionFolderVO {
  id: number
  collectionId: number
  parentId: number | null
  name: string
  description: string
  sortOrder: number
  children: CollectionFolderVO[]
  items: CollectionItemVO[]
}

/** 集合项 */
export interface CollectionItemVO {
  id: number
  collectionId: number
  folderId: number | null
  definitionId: number
  name: string
  description: string
  enabled: boolean
  sortOrder: number
  method: string
  path: string
}

/** 集合详情 */
export interface CollectionDetailVO {
  id: number
  projectId: number
  name: string
  description: string
  sortOrder: number
  folders: CollectionFolderVO[]
  items: CollectionItemVO[]
}

/** 创建集合请求 */
export interface CollectionCreateDTO {
  name: string
  description?: string
  sortOrder?: number
}

/** 更新集合请求 */
export interface CollectionUpdateDTO {
  name?: string
  description?: string
  sortOrder?: number
}

/** 创建文件夹请求 */
export interface CollectionFolderCreateDTO {
  parentId?: number | null
  name: string
  description?: string
  sortOrder?: number
}

/** 更新文件夹请求 */
export interface CollectionFolderUpdateDTO {
  name?: string
  description?: string
  sortOrder?: number
}

/** 添加集合项请求 */
export interface CollectionItemAddDTO {
  folderId?: number | null
  definitionId: number
  name?: string
  description?: string
  enabled?: boolean
}

/** 批量添加集合项请求 */
export interface CollectionItemBatchDTO {
  folderId?: number | null
  definitionIds: number[]
}

/** 集合运行记录 */
export interface CollectionRunVO {
  id: number
  collectionId: number
  projectId: number
  environmentId: number | null
  name: string
  status: string
  totalCount: number
  passedCount: number
  failedCount: number
  errorCount: number
  durationMs: number
  triggerMode: string
  createTime: string
}

/** 集合运行项结果 */
export interface CollectionRunItemVO {
  id: number
  runId: number
  collectionItemId: number | null
  definitionId: number | null
  name: string
  requestUrl: string
  requestMethod: string
  requestHeaders: string
  requestBody: string
  responseStatusCode: number | null
  responseHeaders: string
  responseBody: string
  responseSize: number | null
  durationMs: number
  status: string
  errorMessage: string
  assertionResults: string
  allAssertionsPassed: boolean
  extractedVariables: string
  sortOrder: number
}

/** 集合运行详情 */
export interface CollectionRunDetailVO {
  id: number
  collectionId: number
  projectId: number
  environmentId: number | null
  name: string
  status: string
  totalCount: number
  passedCount: number
  failedCount: number
  errorCount: number
  durationMs: number
  triggerMode: string
  createTime: string
  items: CollectionRunItemVO[]
}