<script setup lang="ts">
import PmQueryBuilder from '@/modules/pm/components/PmQueryBuilder/index.vue'
import PmWorkItemTable from '@/modules/pm/components/PmWorkItemTable/index.vue'
import PmDynamicForm from '@/modules/pm/components/PmDynamicForm/index.vue'
import { pmWorkItemApi } from '@/modules/pm/api'
import { useFieldSchemaStore } from '@/modules/pm/stores'
import type { PmWorkItem, QuerySpec } from '@/modules/pm/types'
import { TYPE_META, emptyQuerySpec } from '@/modules/pm/types'
import { routeId, asId } from '@/modules/pm/utils/id'
import { useMessage } from 'naive-ui'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const fieldStore = useFieldSchemaStore()

const projectId = computed(() => routeId(route.params.projectId))
const typeCode = computed(() => String(route.params.typeCode))
const pageTitle = computed(() => TYPE_META[typeCode.value]?.label ?? '事项')
const querySpec = ref<QuerySpec>(emptyQuerySpec(projectId.value, typeCode.value))
const items = ref<PmWorkItem[]>([])
const commentCounts = ref<Record<string, number>>({})
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
    const ids = items.value.map((item) => item.id).filter((id) => id != null)
    commentCounts.value = ids.length ? await pmWorkItemApi.countCommentsBatch(ids) : {}
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

async function removeItem(item: PmWorkItem) {
  if (!item.id) return
  try {
    await pmWorkItemApi.delete(item.id)
    message.success('已删除')
    await search()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '删除失败')
  }
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
      :comment-counts="commentCounts"
      @refresh="search"
      @row-click="openItem"
      @open="openItem"
      @delete="removeItem"
    />
    <n-modal v-model:show="showCreate" preset="card" :title="`新建${pageTitle}`" style="width: 640px">
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
  </n-space>
</template>
