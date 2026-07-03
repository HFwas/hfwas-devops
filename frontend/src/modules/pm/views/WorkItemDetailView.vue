<script setup lang="ts">
import PmDynamicForm from '@/modules/pm/components/PmDynamicForm/index.vue'
import { pmWorkItemApi } from '@/modules/pm/api'
import { useFieldSchemaStore } from '@/modules/pm/stores'
import type { PmWorkItem } from '@/modules/pm/types'
import { STATUS_OPTIONS } from '@/modules/pm/types'
import { useMessage } from 'naive-ui'

interface WorkItemLink {
  id: number
  sourceId: number
  targetId: number
  linkType: string
}

const route = useRoute()
const message = useMessage()
const fieldStore = useFieldSchemaStore()

const projectId = computed(() => Number(route.params.projectId))
const itemId = computed(() => Number(route.params.itemId))
const item = ref<PmWorkItem | null>(null)
const links = ref<WorkItemLink[]>([])
const loading = ref(true)
const toStatus = ref('')
const linkTargetId = ref<number | null>(null)
const linkType = ref('relates_to')

const fieldDefs = computed(() =>
  item.value ? fieldStore.getSchema(projectId.value, item.value.typeCode) : [],
)

async function load() {
  loading.value = true
  try {
    item.value = await pmWorkItemApi.getById(itemId.value)
    await fieldStore.loadSchema(projectId.value, item.value.typeCode)
    links.value = await pmWorkItemApi.listLinks(itemId.value)
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!item.value) return
  await pmWorkItemApi.save(item.value)
  message.success('已保存')
  await load()
}

async function transition() {
  if (!toStatus.value) return
  await pmWorkItemApi.transition(itemId.value, toStatus.value)
  message.success('状态已更新')
  toStatus.value = ''
  await load()
}

async function addLink() {
  if (!linkTargetId.value) return
  await pmWorkItemApi.addLink(itemId.value, linkTargetId.value, linkType.value)
  message.success('关联已添加')
  linkTargetId.value = null
  await load()
}

onMounted(load)
</script>

<template>
  <n-spin :show="loading">
    <n-card v-if="item" title="事项详情">
      <PmDynamicForm v-if="fieldDefs.length" v-model:model-value="item" :field-defs="fieldDefs" @submit="save" />
      <n-divider />
      <n-space>
        <n-select v-model:value="toStatus" :options="STATUS_OPTIONS" placeholder="流转到" style="width: 160px" />
        <n-button @click="transition">状态流转</n-button>
      </n-space>
      <n-divider />
      <n-card title="事项关联" size="small">
        <n-list bordered>
          <n-list-item v-for="link in links" :key="link.id">
            {{ link.linkType }} → #{{ link.sourceId === itemId ? link.targetId : link.sourceId }}
          </n-list-item>
        </n-list>
        <n-space style="margin-top: 12px">
          <n-input-number v-model:value="linkTargetId" placeholder="目标事项 ID" />
          <n-select
            v-model:value="linkType"
            :options="[
              { label: '关联', value: 'relates_to' },
              { label: '阻塞', value: 'blocks' },
              { label: '重复', value: 'duplicates' },
            ]"
            style="width: 120px"
          />
          <n-button @click="addLink">添加关联</n-button>
        </n-space>
      </n-card>
    </n-card>
  </n-spin>
</template>
