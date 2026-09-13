<script setup lang="ts">
import { useMessage } from 'naive-ui'
import { pipelineApi } from '@/modules/pipeline/api/pipeline'
import type { EntityId, RunParamValue } from '@/modules/pipeline/types/pipeline'

const props = defineProps<{
  show: boolean
  pipelineId: EntityId
}>()

const emit = defineEmits<{
  'update:show': [value: boolean]
  run: [params: Record<string, string>]
}>()

const message = useMessage()
const loading = ref(false)
const params = ref<RunParamValue[]>([])
const values = ref<Record<string, string>>({})

async function loadParams() {
  loading.value = true
  try {
    const data = await pipelineApi.getDefaultParams(props.pipelineId)
    params.value = data || []
    // 初始化默认值
    const init: Record<string, string> = {}
    for (const p of params.value) {
      init[p.paramKey] = p.defaultValue || ''
    }
    values.value = init
  } catch (e) {
    message.error('加载参数失败')
  } finally {
    loading.value = false
  }
}

function confirm() {
  for (const p of params.value) {
    if (p.required && !values.value[p.paramKey]?.trim()) {
      message.warning(`请填写「${p.paramLabel}」`)
      return
    }
  }
  emit('update:show', false)
  emit('run', { ...values.value })
}

watch(
  () => props.show,
  (val) => {
    if (val) {
      void loadParams()
    }
  },
)
</script>

<template>
  <n-modal
    :show="show"
    title="运行参数"
    preset="card"
    style="width: 480px"
    @update:show="(v: boolean) => emit('update:show', v)"
  >
    <n-spin :show="loading">
      <template v-if="params.length > 0">
        <p class="run-param-hint">以下变量可在本次运行中选择，选项来自任务市场中对应 Task 的预置环境变量：</p>
        <n-form label-placement="top">
          <n-form-item
            v-for="p in params"
            :key="p.paramKey"
            :label="p.paramLabel"
            :rule="p.required ? { required: true, message: `请填写${p.paramLabel}` } : undefined"
          >
            <!-- input 类型 -->
            <n-input
              v-if="p.paramType === 'input'"
              v-model:value="values[p.paramKey]"
              :placeholder="p.placeholder || `输入${p.paramLabel}`"
            />

            <!-- select 类型 -->
            <n-select
              v-else-if="p.paramType === 'select'"
              v-model:value="values[p.paramKey]"
              :options="(p.options || []).map((o) => ({ label: o, value: o }))"
              :placeholder="`选择${p.paramLabel}`"
            />

            <!-- api_select 类型（后端已预取选项） -->
            <n-select
              v-else-if="p.paramType === 'api_select'"
              v-model:value="values[p.paramKey]"
              :options="(p.options || []).map((o) => ({ label: o, value: o }))"
              :placeholder="p.loading ? '加载中...' : `选择${p.paramLabel}`"
              :disabled="p.loading"
            />
          </n-form-item>
        </n-form>
      </template>
      <template v-else-if="!loading">
        <p class="run-param-empty">该流水线没有可调的运行时参数</p>
      </template>
    </n-spin>

    <template #footer>
      <n-button @click="emit('update:show', false)">取消</n-button>
      <n-button type="primary" :disabled="loading" @click="confirm">确认运行</n-button>
    </template>
  </n-modal>
</template>

<style scoped>
.run-param-hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--wb-muted, #888);
}

.run-param-empty {
  text-align: center;
  color: var(--wb-muted, #888);
  font-size: 13px;
}
</style>