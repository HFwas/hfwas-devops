/** 环境 */
export interface EnvironmentVO {
  id: number
  projectId: number
  name: string
  description: string
  variableCount: number
  sortOrder: number
  createTime: string
  updateTime: string
}

/** 环境变量项 */
export interface EnvironmentVariableItemVO {
  id: number
  name: string
  value: string
  description: string
  isSecret: boolean
  sortOrder: number
}

/** 环境详情 */
export interface EnvironmentDetailVO {
  id: number
  projectId: number
  name: string
  description: string
  sortOrder: number
  variables: EnvironmentVariableItemVO[]
  createTime: string
  updateTime: string
}

/** 环境变量DTO */
export interface EnvironmentVariableDTO {
  id?: number
  name: string
  value?: string
  description?: string
  isSecret?: boolean
  sortOrder?: number
}

/** 创建环境请求 */
export interface EnvironmentCreateDTO {
  name: string
  description?: string
  sortOrder?: number
  variables?: EnvironmentVariableDTO[]
}

/** 更新环境请求 */
export interface EnvironmentUpdateDTO {
  name?: string
  description?: string
  sortOrder?: number
  variables?: EnvironmentVariableDTO[]
}

/** 环境查询条件 */
export interface EnvironmentQueryDTO {
  projectId: number
  keyword?: string
  pageNo?: number
  pageSize?: number
}