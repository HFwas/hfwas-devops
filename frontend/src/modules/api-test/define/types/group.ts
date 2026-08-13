/** 接口分组类型 */
export interface ApiGroupVO {
  id: number
  projectId: number
  parentId: number | null
  name: string
  sortOrder: number
  description: string
  children: ApiGroupVO[]
  apiCount: number
  createBy: number
  createTime: string
  updateTime: string
}

/** 创建分组请求 */
export interface ApiGroupCreateDTO {
  projectId: number
  parentId?: number | null
  name: string
  sortOrder?: number
  description?: string
}

/** 更新分组请求 */
export interface ApiGroupUpdateDTO {
  name: string
  sortOrder?: number
  description?: string
}