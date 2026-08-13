/** 接口状态枚举 */
export type ApiStatus = 'DRAFT' | 'PUBLISHED' | 'DEPRECATED'

/** 请求方式 */
export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE' | 'HEAD' | 'OPTIONS'

/** 参数类型 */
export type ParamType = 'path' | 'query' | 'header' | 'body'

/** 参数数据类型 */
export type ParamDataType = 'string' | 'integer' | 'number' | 'boolean' | 'array' | 'object' | 'file'

/** 接口参数 */
export interface ApiDefinitionParamVO {
  id: number
  definitionId: number
  paramType: ParamType
  name: string
  dataType: ParamDataType
  required: boolean
  defaultValue: string | null
  description: string
  parentId: number | null
  sortOrder: number
  example: string | null
}

/** 接口响应定义 */
export interface ApiDefinitionResponseVO {
  id: number
  definitionId: number
  statusCode: number
  contentType: string
  description: string
  bodySchema: any
  bodyExample: any
  createTime: string
}

/** 接口定义列表视图 */
export interface ApiDefinitionVO {
  id: number
  projectId: number
  groupId: number | null
  groupName: string
  name: string
  path: string
  method: HttpMethod
  status: ApiStatus
  version: string
  tags: string[]
  description: string
  protocol: string
  createBy: number
  createTime: string
  updateTime: string
}

/** 接口定义详情视图 */
export interface ApiDefinitionDetailVO {
  id: number
  projectId: number
  groupId: number | null
  groupName: string
  name: string
  path: string
  method: HttpMethod
  status: ApiStatus
  version: string
  tags: string[]
  description: string
  protocol: string
  host: string
  contentType: string | null
  params: ApiDefinitionParamVO[]
  responses: ApiDefinitionResponseVO[]
  createBy: number
  createTime: string
  updateTime: string
}

/** 接口参数 DTO */
export interface ApiDefinitionParamDTO {
  id?: number
  paramType: ParamType
  name: string
  dataType?: ParamDataType
  required?: boolean
  defaultValue?: string
  description?: string
  parentId?: number | null
  sortOrder?: number
  example?: string
}

/** 接口响应定义 DTO */
export interface ApiDefinitionResponseDTO {
  id?: number
  statusCode?: number
  contentType?: string
  description?: string
  bodySchema?: any
  bodyExample?: any
}

/** 创建接口定义请求 */
export interface ApiDefinitionCreateDTO {
  projectId: number
  groupId?: number | null
  name: string
  path: string
  method: HttpMethod
  tags?: string[]
  description?: string
  protocol?: string
  host?: string
  contentType?: string
  params?: ApiDefinitionParamDTO[]
  responses?: ApiDefinitionResponseDTO[]
}

/** 更新接口定义请求 */
export interface ApiDefinitionUpdateDTO {
  groupId?: number | null
  name: string
  path: string
  method: HttpMethod
  tags?: string[]
  description?: string
  protocol?: string
  host?: string
  contentType?: string
  params?: ApiDefinitionParamDTO[]
  responses?: ApiDefinitionResponseDTO[]
}

/** 接口定义查询条件 */
export interface ApiDefinitionQueryDTO {
  projectId: number
  groupId?: number
  keyword?: string
  method?: HttpMethod
  status?: ApiStatus
  tags?: string[]
  pageNo?: number
  pageSize?: number
}

/** 接口状态配置（用于前端展示） */
export const API_STATUS_OPTIONS = [
  { label: '草稿', value: 'DRAFT', color: '#909399' },
  { label: '已发布', value: 'PUBLISHED', color: '#67C23A' },
  { label: '已废弃', value: 'DEPRECATED', color: '#F56C6C' },
] as const

/** 请求方式配置（用于前端展示） */
export const HTTP_METHOD_OPTIONS = [
  { label: 'GET', value: 'GET', color: '#67C23A' },
  { label: 'POST', value: 'POST', color: '#409EFF' },
  { label: 'PUT', value: 'PUT', color: '#E6A23C' },
  { label: 'PATCH', value: 'PATCH', color: '#909399' },
  { label: 'DELETE', value: 'DELETE', color: '#F56C6C' },
  { label: 'HEAD', value: 'HEAD', color: '#909399' },
  { label: 'OPTIONS', value: 'OPTIONS', color: '#909399' },
] as const

/** 参数类型配置 */
export const PARAM_TYPE_OPTIONS = [
  { label: '路径参数', value: 'path' },
  { label: 'Query参数', value: 'query' },
  { label: '请求头', value: 'header' },
  { label: '请求体', value: 'body' },
] as const

/** 参数数据类型配置 */
export const PARAM_DATA_TYPE_OPTIONS = [
  { label: '字符串', value: 'string' },
  { label: '整数', value: 'integer' },
  { label: '浮点数', value: 'number' },
  { label: '布尔值', value: 'boolean' },
  { label: '数组', value: 'array' },
  { label: '对象', value: 'object' },
  { label: '文件', value: 'file' },
] as const