<script setup lang="ts">
import { ArrowLeft, Pencil, Play } from '@lucide/vue'
import { useMessage } from 'naive-ui'
import { pipelineApi } from '@/modules/pipeline/api/pipeline'
import { isApiError } from '@/shared/errors/apiError'
import RunParamDialog from '@/modules/pipeline/components/RunParamDialog.vue'
import '@/modules/pipeline/styles/pipeline-theme.css'

const router = useRouter()
const route = useRoute()
const message = useMessage()
const loading = ref(false)
const name = ref('')
const ready = ref(false)
const runParamShow = ref(false)

const pipelineId = computed(() => String(route.params.id ?? ''))

function errorMessage(e: unknown): string {
  if (isApiError(e)) return e.message
  return e instanceof Error ? e.message : '操作失败'
}

function editPath() {
  return `/pipeline/pipelines/${pipelineId.value}/edit`
}

async function latestRunId(known?: string | number | null) {
  if (known != null && known !== '') return known
  const page = await pipelineApi.pageRuns(pipelineId.value, { pageNo: 1, pageSize: 1 })
  return page.records?.[0]?.id ?? null
}

async function load() {
  if (!pipelineId.value) return
  loading.value = true
  ready.value = false
  try {
    const row = await pipelineApi.get(pipelineId.value)
    name.value = row.name
    const runId = await latestRunId(row.lastRunId)
    if (runId != null) {
      await router.replace(`/pipeline/pipelines/${row.id}/runs/${runId}`)
      return
    }
    ready.value = true
  } catch (e) {
    message.error(errorMessage(e))
    ready.value = true
  } finally {
    loading.value = false
  }
}

async function run() {
  try {
    const params = await pipelineApi.getDefaultParams(pipelineId.value)
    if (params && params.length > 0) {
      runParamShow.value = true
      return
    }
    await doStart()
  } catch (e) {
    message.error(errorMessage(e))
  }
}

async function doStart(runtimeParams?: Record<string, string>) {
  try {
    const result = await pipelineApi.start(pipelineId.value, runtimeParams)
    await router.replace(`/pipeline/pipelines/${pipelineId.value}/runs/${result.id}`)
  } catch (e) {
    message.error(errorMessage(e))
  }
}

onMounted(load)
watch(pipelineId, load)
</script>

<template>
  <n-spin :show="loading" class="pl-home-spin">
    <div v-if="ready" class="pl-page">
      <header class="pl-hero">
        <div class="pl-hero-main">
          <n-button text size="small" class="pl-back" @click="router.push('/pipeline/pipelines')">
            <template #icon><ArrowLeft :size="14" /></template>
            流水线
          </n-button>
          <h1 class="pl-hero-title">{{ name || '流水线' }}</h1>
          <p class="pl-hero-desc">尚未执行。运行后将在此查看最新一次构建过程。</p>
        </div>
        <div class="pl-hero-extra">
          <n-button @click="router.push(editPath())">
            <template #icon><Pencil :size="14" /></template>
            编辑
          </n-button>
          <n-button type="primary" @click="run">
            <template #icon><Play :size="14" /></template>
            运行
          </n-button>
        </div>
      </header>
      <n-empty description="暂无执行记录" />
    </div>
  </n-spin>
  <RunParamDialog
    v-model:show="runParamShow"
    :pipeline-id="pipelineId"
    @run="(params: Record<string, string>) => doStart(params)"
  />
</template>

<style scoped>
.pl-home-spin {
  min-height: 100%;
}
</style>
