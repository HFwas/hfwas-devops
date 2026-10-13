/** Standard page sizes (Ant Design / Naive UI convention). */
export const DEFAULT_PAGE_SIZES = [10, 20, 50, 100] as const

export const DEFAULT_PAGE_SIZE = 10

export interface PageQuery {
  pageNo: number
  pageSize: number
}

export function parsePageTotal(total: number | string | undefined): number {
  const n = Number(total)
  return Number.isFinite(n) && n >= 0 ? n : 0
}

/** 从分页结果推导总页数：优先用后端 pages，缺失时按 total / pageSize 估算，至少 1 页。 */
export function pageCountOf(
  page: { pages?: number | string; total?: number | string } | undefined,
  pageSize: number,
): number {
  const pages = Number(page?.pages)
  if (Number.isFinite(pages) && pages >= 1) return Math.floor(pages)
  const size = pageSize > 0 ? pageSize : 1
  return Math.max(1, Math.ceil(parsePageTotal(page?.total) / size))
}

export function applyPageResult(
  page: { total?: number | string },
  setTotal: (n: number) => void,
) {
  setTotal(parsePageTotal(page.total))
}
