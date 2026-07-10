<script setup lang="ts">
import type { DropdownOption } from 'naive-ui'
import PmQueryBuilder from '@/modules/pm/components/PmQueryBuilder/index.vue'
import PmWorkItemTable from '@/modules/pm/components/PmWorkItemTable/index.vue'
import PmDynamicForm from '@/modules/pm/components/PmDynamicForm/index.vue'
import PmWorkItemImportExportDrawer from '@/modules/pm/components/PmWorkItemImportExportDrawer/index.vue'
import { pmWorkItemApi } from '@/modules/pm/api'
import { useFieldSchemaStore } from '@/modules/pm/stores'
import type { PmWorkItem, QuerySpec } from '@/modules/pm/types'
import { typeLabel, emptyQuerySpec } from '@/modules/pm/types'
import { useProjectIssueTypes } from '@/modules/pm/composables/useIssueTypes'
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
const { types: projectTypes } = useProjectIssueTypes(projectId)
const pageTitle = computed(() => typeLabel(typeCode.value, projectTypes.value) || '事项')
const querySpec = ref<QuerySpec>(emptyQuerySpec(projectId.value, typeCode.value))
const pagination = usePagination()
const items = ref<PmWorkItem[]>([])
const commentCounts = ref<Record<string, number>>({})
const loading = ref(false)
const showCreate = ref(false)
const ioMode = ref<'export' | 'import'>('export')
const showIoDrawer = ref(false)
const checkedRowKeys = ref<string[]>([])
const filterExpanded = ref(false)
const form = ref<Partial<PmWorkItem>>({ projectId: projectId.value, typeCode: typeCode.value, title: '' })

const fieldDefs = computed(() => fieldStore.getSchema(projectId.value, typeCode.value))
const conditionCount = computed(() => querySpec.value.conditions?.length ?? 0)

const moreOptions: DropdownOption[] = [
  { label: '导出', key: 'export' },
  { label: '导入', key: 'import' },
]

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

function onMoreSelect(key: string) {
  if (key === 'export') openExport()
  if (key === 'import') openImport()
}

function toggleFilter() {
  filterExpanded.value = !filterExpanded.value
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
  filterExpanded.value = false
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
  <div class="work-item-list pm-panel">
    <div class="list-toolbar">
      <div class="toolbar-left">
        <span class="list-name">{{ pageTitle }}列表</span>
        <n-button type="primary" size="small" @click="showCreate = true">新增</n-button>
        <n-dropdown :options="moreOptions" @select="onMoreSelect">
          <n-button size="small">更多操作</n-button>
        </n-dropdown>
        <n-text v-if="checkedRowKeys.length" depth="3" class="selected-hint">
          已选 {{ checkedRowKeys.length }} 条
        </n-text>
      </div>
      <div class="toolbar-right">
        <n-badge :value="conditionCount" :max="99" :show-zero="false">
          <n-button
            size="small"
            :type="filterExpanded ? 'primary' : 'default'"
            :ghost="!filterExpanded"
            @click="toggleFilter"
          >
            高级筛选
          </n-button>
        </n-badge>
        <n-button size="small" quaternary @click="search" title="刷新">刷新</n-button>
      </div>
    </div>

    <PmQueryBuilder
      v-model:model-value="querySpec"
      v-model:expanded="filterExpanded"
      :field-defs="fieldDefs"
      @search="onSearch"
    />

    <div class="list-table">
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
    </div>

    <div class="list-footer">
      <AppPagination :pagination="pagination" :on-change="search" />
    </div>

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
  </div>
</template>

<style scoped>
.work-item-list {
  display: flex;
  flex-direction: column;
}

.list-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
  padding: 8px 12px;
  border-bottom: 1px solid var(--pm-border-soft, #eef0f3);
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.list-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--pm-text, #1f2329);
  margin-right: 2px;
}

.selected-hint {
  font-size: 12px;
  margin-left: 2px;
}

.list-table {
  padding: 0;
}

.list-table :deep(.n-data-table-empty) {
  padding: 28px 0 24px;
}

.list-table :deep(.n-empty) {
  --n-icon-size: 40px;
}

.list-footer {
  padding: 6px 12px 8px;
  border-top: 1px solid var(--pm-border-soft, #eef0f3);
}
</style>
