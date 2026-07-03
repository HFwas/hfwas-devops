<script setup lang="ts">
import { pmMetaApi, pmWorkItemApi } from '@/modules/pm/api'
import type { PmWorkItem, PmWorkItemType } from '@/modules/pm/types'
import { STATUS_OPTIONS, TYPE_META } from '@/modules/pm/types'
import { useMessage } from 'naive-ui'

const route = useRoute()
const message = useMessage()
const projectId = computed(() => Number(route.params.projectId))
const typeCode = ref('task')
const types = ref<PmWorkItemType[]>([])
const board = ref<Record<string, PmWorkItem[]>>({})

const columns = computed(() =>
  STATUS_OPTIONS.map((s) => ({
    key: s.value,
    title: s.label,
    items: board.value[s.value] || [],
  })),
)

async function load() {
  types.value = await pmMetaApi.types()
  board.value = await pmMetaApi.board(projectId.value, typeCode.value)
}

async function moveItem(item: PmWorkItem, status: string) {
  await pmWorkItemApi.transition(item.id!, status)
  message.success('已移动')
  await load()
}

watch(typeCode, load)
onMounted(load)
</script>

<template>
  <n-space vertical size="large">
    <n-select
      v-model:value="typeCode"
      :options="types.map((t) => ({ label: t.name, value: t.code }))"
      style="width: 160px"
    />
    <n-scrollbar x-scrollable>
      <n-space align="start" :size="16" style="min-width: 900px">
        <n-card
          v-for="col in columns"
          :key="col.key"
          :title="col.title"
          size="small"
          style="width: 240px; min-height: 400px"
        >
          <n-space vertical>
            <n-card
              v-for="item in col.items"
              :key="item.id"
              size="small"
              hoverable
            >
              <n-text strong>{{ item.title }}</n-text>
              <n-space size="small" style="margin-top: 8px">
                <n-tag size="small" :bordered="false">{{ TYPE_META[item.typeCode]?.label }}</n-tag>
                <n-dropdown
                  :options="STATUS_OPTIONS.filter((s) => s.value !== col.key).map((s) => ({
                    label: `→ ${s.label}`,
                    key: s.value,
                  }))"
                  @select="(key) => moveItem(item, key as string)"
                >
                  <n-button size="tiny">移动</n-button>
                </n-dropdown>
              </n-space>
            </n-card>
          </n-space>
        </n-card>
      </n-space>
    </n-scrollbar>
  </n-space>
</template>
