<script setup lang="ts">
import { pmMetaApi } from '@/modules/pm/api'
import type { PmWorkItemType } from '@/modules/pm/types'
import { TYPE_META } from '@/modules/pm/types'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => Number(route.params.projectId))
const types = ref<PmWorkItemType[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    types.value = await pmMetaApi.types()
  } finally {
    loading.value = false
  }
}

function openType(typeCode: string) {
  router.push(`/pm/projects/${projectId.value}/settings/types/${typeCode}`)
}

onMounted(load)
</script>

<template>
  <n-space vertical size="large">
    <n-page-header
      title="事项配置"
      subtitle="管理各事项类型的字段展示方案，配置列表、搜索与新建表单的字段启用状态"
    />
    <n-spin :show="loading">
      <n-grid :cols="2" :x-gap="16" :y-gap="16">
        <n-gi v-for="t in types" :key="t.code">
          <n-card hoverable @click="openType(t.code)">
            <n-space align="center" justify="space-between">
              <n-space align="center">
                <n-tag :bordered="false" :color="{ color: TYPE_META[t.code]?.color, textColor: '#fff' }">
                  {{ TYPE_META[t.code]?.label ?? t.name }}
                </n-tag>
                <n-text depth="3">{{ t.code }}</n-text>
              </n-space>
              <n-space>
                <n-button text type="primary" @click.stop="router.push(`/pm/projects/${projectId}/settings/workflow/${t.code}`)">
                  状态流转
                </n-button>
                <n-button text type="primary" @click.stop="openType(t.code)">字段布局 →</n-button>
              </n-space>
            </n-space>
            <n-text depth="3" style="display: block; margin-top: 12px">
              配置该事项的字段展示方案与状态流转规则
            </n-text>
          </n-card>
        </n-gi>
      </n-grid>
    </n-spin>
  </n-space>
</template>
