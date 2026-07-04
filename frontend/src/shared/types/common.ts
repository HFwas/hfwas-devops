export interface BaseResult<T> {
  code: number
  msg: string | null
  data: T
}

export interface PageResult<T> {
  records: T[]
  total: number | string
  size: number | string
  current: number | string
  pages: number | string
}

export type { PageQuery } from '@/shared/types/pagination'
export { DEFAULT_PAGE_SIZE, DEFAULT_PAGE_SIZES } from '@/shared/types/pagination'
