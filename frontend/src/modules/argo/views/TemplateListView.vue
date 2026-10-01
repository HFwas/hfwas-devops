<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NCard, NEmpty, NInput, NPagination, NSpace, NSpin, useMessage } from 'naive-ui'
import { Plus, RefreshCw, Search, Eye } from '@lucide/vue'
import type { WorkflowTemplate } from '@/modules/argo/types/workflow'
import { pageTemplates } from '@/modules/argo/api/workflowApi'
import '@/modules/pipeline/styles/pipeline-theme.css'

const router = useRouter()
const message = useMessage()
const loading = ref(false)
const keyword = ref('')
const templates = ref<WorkflowTemplate[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = 20

async function fetch() {
  loading.value = true
  try {
    const res = await pageTemplates({ pageNo: pageNo.value, pageSize, keyword: keyword.value || undefined })
    templates.value = res.records
    total.value = res.total
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

function goDetail(name: string) {
  router.push(`/argo/workflows/${name}`)
}

onMounted(fetch)
</script>

<template>
  <div class="pl-toolbar">
    <NInput
      v-model:value="keyword"
      placeholder="搜索模板..."
      clearable
      style="width: 280px"
      @keyup.enter="fetch"
    >
      <template #prefix><Search :size="14" /></template>
    </NInput>
    <NSpace style="margin-left: auto">
      <NButton quaternary @click="fetch">
        <template #icon><RefreshCw :size="14" /></template>
        刷新
      </NButton>
      <NButton type="primary">
        <template #icon><Plus :size="14" /></template>
        新建模板
      </NButton>
    </NSpace>
  </div>

  <NSpin :show="loading">
    <div v-if="templates.length === 0" class="pl-empty">
      <NEmpty description="暂无模板" />
    </div>

    <div v-else class="pl-grid">
      <div v-for="t in templates" :key="t.name" class="pl-tile" @click="goDetail(t.name)">
        <div class="pl-tile-top">
          <div class="pl-tile-icon" style="background: #f5f3ff; color: #7c3aed">
            <Eye :size="16" />
          </div>
        </div>
        <div class="pl-tile-body">
          <div class="pl-tile-name">{{ t.name }}</div>
          <div class="pl-tile-meta">
            <span>入口: {{ t.entrypoint }}</span>
            <span>{{ t.templates?.length ?? 0 }} 个步骤</span>
          </div>
        </div>
      </div>
    </div>

    <div v-if="total > pageSize" class="pl-footer">
      <NPagination v-model:page="pageNo" :page-size="pageSize" :item-count="total" @update:page="fetch" />
    </div>
  </NSpin>
</template>

<style scoped>
.pl-empty {
  display: flex;
  justify-content: center;
  padding: 60px 0;
}
</style>