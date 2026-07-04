import type { PaginationProps } from 'naive-ui'
import { DEFAULT_PAGE_SIZE, DEFAULT_PAGE_SIZES, type PageQuery } from '@/shared/types/pagination'

export interface UsePaginationOptions {
  pageNo?: number
  pageSize?: number
  pageSizes?: readonly number[]
}

export function usePagination(options: UsePaginationOptions = {}) {
  const pageNo = ref(options.pageNo ?? 1)
  const pageSize = ref(options.pageSize ?? DEFAULT_PAGE_SIZE)
  const total = ref(0)
  const pageSizes = options.pageSizes ?? DEFAULT_PAGE_SIZES

  const query = computed<PageQuery>(() => ({
    pageNo: pageNo.value,
    pageSize: pageSize.value,
  }))

  function setTotal(value: number | string | undefined) {
    const n = Number(value)
    total.value = Number.isFinite(n) && n >= 0 ? n : 0
  }

  function resetPage() {
    pageNo.value = 1
  }

  function onPageChange(page: number) {
    pageNo.value = page
  }

  function onPageSizeChange(size: number) {
    pageSize.value = size
    pageNo.value = 1
  }

  /** After deleting the last row on a page, go back one page. */
  function afterDelete(currentPageCount: number) {
    if (currentPageCount <= 1 && pageNo.value > 1) {
      pageNo.value -= 1
    }
  }

  return {
    pageNo,
    pageSize,
    total,
    pageSizes,
    query,
    setTotal,
    resetPage,
    onPageChange,
    onPageSizeChange,
    afterDelete,
  }
}

export type PaginationState = ReturnType<typeof usePagination>

/**
 * Remote pagination for n-data-table.
 * Handlers are bound via table @update:page / @update:page-size (Naive UI requirement).
 */
export function useDataTablePagination(
  pagination: PaginationState,
  reload: () => void | Promise<void>,
) {
  function handlePageChange(page: number) {
    pagination.onPageChange(page)
    void reload()
  }

  function handlePageSizeChange(pageSize: number) {
    pagination.onPageSizeChange(pageSize)
    void reload()
  }

  const tablePagination = computed((): PaginationProps => ({
    page: pagination.pageNo.value,
    pageSize: pagination.pageSize.value,
    itemCount: pagination.total.value,
    showSizePicker: true,
    pageSizes: [...pagination.pageSizes],
    showQuickJumper: true,
    prefix: ({ itemCount }: { itemCount?: number }) => `共 ${itemCount ?? 0} 条`,
  }))

  return { tablePagination, handlePageChange, handlePageSizeChange }
}
