<script setup lang="ts">
import { ArrowLeft, RefreshCw } from '@lucide/vue'
import { NButton, NCard, NDescriptions, NDescriptionsItem, NEmpty, NSpace, NSpin, NTabPane, NTabs, useMessage } from 'naive-ui'
import { configMapApi } from '@/modules/container/api/configmap'
import ResourceYamlEditor from '@/modules/container/components/ResourceYamlEditor.vue'
import { isApiError } from '@/shared/errors/apiError'

const props = defineProps<{ clusterId: string; namespace: string; name: string }>()
const router = useRouter()
const message = useMessage()

const configmap = ref<any>(null)
const loading = ref(false)
const yamlContent = ref('')
const yamlLoading = ref(false)
const saving = ref(false)

function errorMessage(e: unknown): string {
  if (isApiError(e)) return e.message
  return e instanceof Error ? e.message : '加载失败'
}

async function loadYaml() {
  yamlLoading.value = true
  try {
    yamlContent.value = await configMapApi.yaml(props.clusterId, props.namespace, props.name)
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    yamlLoading.value = false
  }
}

async function saveYaml(yaml: string) {
  saving.value = true
  try {
    await configMapApi.updateYaml(props.clusterId, props.namespace, props.name, yaml)
    message.success('已保存')
    yamlContent.value = yaml
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadYaml()
})
</script>

<template>
  <div class="cm-detail">
    <header class="cm-header">
      <n-button text @click="router.back()">
        <template #icon><ArrowLeft :size="18" /></template>
      </n-button>
      <h2 class="cm-title">{{ name }}</h2>
      <span class="cm-ns">{{ namespace }}</span>
      <span class="cm-spacer" />
      <n-button size="small" quaternary @click="loadYaml">
        <template #icon><RefreshCw :size="14" /></template>
        刷新
      </n-button>
    </header>

    <n-card :bordered="false" class="cm-card">
      <NTabs type="line" animated>
        <NTabPane name="yaml" tab="YAML">
          <ResourceYamlEditor
            :content="yamlContent"
            :loading="yamlLoading || saving"
            :editable="true"
            max-height="calc(100vh - 260px)"
            @save="saveYaml"
          />
        </NTabPane>
      </NTabs>
    </n-card>
  </div>
</template>

<style scoped>
.cm-detail {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--wb-page-bg, #f5f7fb);
}

.cm-header {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 12px;
  height: 48px;
  padding: 0 16px;
  border-bottom: 1px solid var(--wb-border, #e5e7eb);
  background: var(--wb-card-bg, #fff);
}

.cm-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--wb-ink, #1f2329);
  margin: 0;
}

.cm-ns {
  font-size: 12px;
  color: var(--wb-muted, #646a73);
  background: var(--wb-th, #f0f2f5);
  padding: 2px 8px;
  border-radius: 4px;
}

.cm-spacer {
  flex: 1;
}

.cm-card {
  flex: 1;
  min-height: 0;
  margin: 12px;
  overflow: hidden;
}
</style>