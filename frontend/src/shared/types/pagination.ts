/** Standard page sizes (Ant Design / Naive UI convention). */
export const DEFAULT_PAGE_SIZES = [10, 20, 50, 100] as const

export const DEFAULT_PAGE_SIZE = 20

export interface PageQuery {
  pageNo: number
  pageSize: number
}

export function parsePageTotal(total: number | string | undefined): number {
  const n = Number(total)
  return Number.isFinite(n) && n >= 0 ? n : 0
}

export function applyPageResult(
  page: { total?: number | string },
  setTotal: (n: number) => void,
) {
  setTotal(parsePageTotal(page.total))
}
