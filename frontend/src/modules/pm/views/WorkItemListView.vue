<script setup lang="ts">
import PmQueryBuilder from '@/modules/pm/components/PmQueryBuilder/index.vue'
import PmWorkItemTable from '@/modules/pm/components/PmWorkItemTable/index.vue'
import PmDynamicForm from '@/modules/pm/components/PmDynamicForm/index.vue'
import { pmWorkItemApi } from '@/modules/pm/api'
import { useFieldSchemaStore } from '@/modules/pm/stores'
import type { PmWorkItem, QuerySpec } from '@/modules/pm/types'
import { TYPE_META, emptyQuerySpec } from '@/modules/pm/types'
import { useMessage } from 'naive-ui'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const fieldStore = useFieldSchemaStore()

const projectId = computed(() => Number(route.params.projectId))
const typeCode = computed(() => String(route.params.typeCode))
const pageTitle = computed(() => TYPE_META[typeCode.value]?.label ?? '事项')
const querySpec = ref<QuerySpec>(emptyQuerySpec(projectId.value, typeCode.value))
const items = ref<PmWorkItem[]>([])
const loading = ref(false)
const showCreate = ref(false)
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
    const page = await pmWorkItemApi.page(querySpec.value)
    items.value = page.records
  } finally {
    loading.value = false
  }
}

async function createItem() {
  form.value.projectId = projectId.value
  form.value.typeCode = typeCode.value
  const id = await pmWorkItemApi.save(form.value as PmWorkItem)
  message.success('事项已创建')
  showCreate.value = false
  router.push(`/pm/projects/${projectId.value}/items/${id}`)
}

watch(typeCode, async () => {
  querySpec.value = emptyQuerySpec(projectId.value, typeCode.value)
  form.value = { projectId: projectId.value, typeCode: typeCode.value, title: '' }
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
    <n-page-header :title="pageTitle" />
    <n-space>
      <n-button type="primary" @click="showCreate = true">新建{{ pageTitle }}</n-button>
      <n-button @click="search">查询</n-button>
    </n-space>
    <PmQueryBuilder v-model:model-value="querySpec" :field-defs="fieldDefs" />
    <PmWorkItemTable
      :field-defs="fieldDefs"
      :query-spec="querySpec"
      :data="items"
      :loading="loading"
      @refresh="search"
      @row-click="(row) => router.push(`/pm/projects/${projectId}/items/${row.id}`)"
    />
    <n-modal v-model:show="showCreate" preset="card" :title="`新建${pageTitle}`" style="width: 640px">
      <PmDynamicForm v-model:model-value="form" :field-defs="fieldDefs" mode="create" @submit="createItem" />
    </n-modal>
  </n-space>
</template>
