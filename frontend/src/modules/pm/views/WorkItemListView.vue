<script setup lang="ts">
import PmQueryBuilder from '@/modules/pm/components/PmQueryBuilder/index.vue'
import PmWorkItemTable from '@/modules/pm/components/PmWorkItemTable/index.vue'
import PmDynamicForm from '@/modules/pm/components/PmDynamicForm/index.vue'
import PmWorkItemImportExportDrawer from '@/modules/pm/components/PmWorkItemImportExportDrawer/index.vue'
import { pmWorkItemApi } from '@/modules/pm/api'
import { useFieldSchemaStore } from '@/modules/pm/stores'
import type { PmWorkItem, QuerySpec } from '@/modules/pm/types'
import { TYPE_META, emptyQuerySpec } from '@/modules/pm/types'
import { routeId, asId } from '@/modules/pm/utils/id'
import { useMessage } from 'naive-ui'
import AppPagination from '@/shared/components/AppPagination.vue'
import { usePagination } from '@/shared/composables/usePagination'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const fieldStore = useFieldSchemaStore()

const projectId = computed(() => routeId(route.params.projectId))
const typeCode = computed(() => String(route.params.typeCode))
const pageTitle = computed(() => TYPE_META[typeCode.value]?.label ?? '事项')
const querySpec = ref<QuerySpec>(emptyQuerySpec(projectId.value, typeCode.value))
const pagination = usePagination()
const items = ref<PmWorkItem[]>([])
const commentCounts = ref<Record<string, number>>({})
const loading = ref(false)
const showCreate = ref(false)
const ioMode = ref<'export' | 'import'>('export')
const showIoDrawer = ref(false)
const checkedRowKeys = ref<string[]>([])
const form = ref<Partial<PmWorkItem>>({ projectId: projectId.value, typeCode: typeCode.value, title: '' })

const fieldDefs = computed(() => fieldStore.getSchema(projectId.value, typeCode.value))

async function loadSchema() {
  await fieldStore.loadSchema(projectId.value, typeCode.value)
}

async function search() {
  loading.value = true
  try {
    querySpec.value.projectId = projectId.value
    querySpec.value.typeCode = typeCode.value
    querySpec.value.pageNo = pagination.pageNo.value
    querySpec.value.pageSize = pagination.pageSize.value
    const page = await pmWorkItemApi.page(querySpec.value)
    items.value = page.records
    pagination.setTotal(page.total)
    const ids = items.value.map((item) => item.id).filter((id) => id != null)
    commentCounts.value = ids.length ? await pmWorkItemApi.countCommentsBatch(ids) : {}
  } finally {
    loading.value = false
  }
}

function onSearch() {
  pagination.resetPage()
  void search()
}

async function createItem() {
  form.value.projectId = projectId.value
  form.value.typeCode = typeCode.value
  const id = await pmWorkItemApi.save(form.value as PmWorkItem)
  message.success('事项已创建')
  showCreate.value = false
  form.value = { projectId: projectId.value, typeCode: typeCode.value, title: '' }
  openItem({ id } as PmWorkItem)
}

function openItem(item: PmWorkItem) {
  if (item.id == null) return
  router.push({
    path: `/pm/projects/${projectId.value}/items/${asId(item.id)}`,
    query: { tab: 'comments', type: typeCode.value },
  })
}

function openExport() {
  if (!fieldDefs.value.length) {
    message.warning('字段配置加载中，请稍后再试')
    return
  }
  ioMode.value = 'export'
  showIoDrawer.value = true
}

function openImport() {
  if (!fieldDefs.value.length) {
    message.warning('字段配置加载中，请稍后再试')
    return
  }
  ioMode.value = 'import'
  showIoDrawer.value = true
}

async function removeItem(item: PmWorkItem) {
  if (!item.id) return
  try {
    await pmWorkItemApi.delete(item.id)
    message.success('已删除')
    pagination.afterDelete(items.value.length)
    await search()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '删除失败')
  }
}

watch(typeCode, async () => {
  querySpec.value = emptyQuerySpec(projectId.value, typeCode.value)
  form.value = { projectId: projectId.value, typeCode: typeCode.value, title: '' }
  checkedRowKeys.value = []
  pagination.resetPage()
  await loadSchema()
  await search()
})

onMounted(async () => {
  await loadSchema()
  await search()
})
</script>

<template>
  <n-space vertical size="large">
    <n-page-header :title="pageTitle" subtitle="筛选、新建与导入导出事项">
      <template #extra>
        <n-space>
          <n-text v-if="checkedRowKeys.length" depth="3">已选 {{ checkedRowKeys.length }} 条</n-text>
          <n-button @click="openExport">导出</n-button>
          <n-button @click="openImport">导入</n-button>
          <n-button type="primary" @click="showCreate = true">新建{{ pageTitle }}</n-button>
        </n-space>
      </template>
    </n-page-header>
    <PmQueryBuilder v-model:model-value="querySpec" :field-defs="fieldDefs" @search="onSearch" />
    <n-card size="small" :bordered="true">
      <PmWorkItemTable
        v-model:checked-row-keys="checkedRowKeys"
        selectable
        :field-defs="fieldDefs"
        :query-spec="querySpec"
        :data="items"
        :loading="loading"
        :comment-counts="commentCounts"
        @refresh="search"
        @row-click="openItem"
        @open="openItem"
        @delete="removeItem"
      />
      <div style="margin-top: 12px">
        <AppPagination :pagination="pagination" :on-change="search" />
      </div>
    </n-card>
    <n-modal v-model:show="showCreate" preset="card" :title="`新建${pageTitle}`" style="width: 560px">
      <n-spin :show="!fieldDefs.length">
        <PmDynamicForm
          v-if="fieldDefs.length"
          v-model:model-value="form"
          :field-defs="fieldDefs"
          mode="create"
          @submit="createItem"
        />
        <n-empty v-else description="正在加载字段配置..." />
      </n-spin>
    </n-modal>

    <PmWorkItemImportExportDrawer
      v-model:show="showIoDrawer"
      :mode="ioMode"
      :project-id="projectId"
      :type-code="typeCode"
      :type-label="pageTitle"
      :field-defs="fieldDefs"
      :query-spec="querySpec"
      :selected-ids="checkedRowKeys"
      @done="search"
    />
  </n-space>
</template>
