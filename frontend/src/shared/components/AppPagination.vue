<script setup lang="ts">
import type { PaginationState } from '@/shared/composables/usePagination'

const props = defineProps<{
  pagination: PaginationState
  onChange: () => void | Promise<void>
}>()

function handlePageChange(page: number) {
  props.pagination.onPageChange(page)
  void props.onChange()
}

function handlePageSizeChange(size: number) {
  props.pagination.onPageSizeChange(size)
  void props.onChange()
}
</script>

<template>
  <n-space justify="space-between" align="center" style="width: 100%">
    <n-text depth="3">共 {{ pagination.total.value }} 条</n-text>
    <n-pagination
      v-if="pagination.total.value > 0"
      :page="pagination.pageNo.value"
      :page-size="pagination.pageSize.value"
      :item-count="pagination.total.value"
      :page-sizes="[...pagination.pageSizes]"
      show-size-picker
      show-quick-jumper
      @update:page="handlePageChange"
      @update:page-size="handlePageSizeChange"
    />
  </n-space>
</template>
