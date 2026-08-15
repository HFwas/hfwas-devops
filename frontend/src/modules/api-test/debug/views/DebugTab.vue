<template>
  <div class="debug-tab">
    <!-- 环境选择 -->
    <div class="debug-tab__toolbar">
      <environment-selector
        :project-id="projectId"
        :environment-id="environmentId"
        @update:environment-id="onEnvironmentChange"
      />
    </div>

    <!-- 请求编辑器 -->
    <n-card title="请求" size="small" class="debug-section">
      <request-editor
        v-model:url="requestUrl"
        v-model:method="requestMethod"
        v-model:headers="requestHeaders"
        v-model:query-params="requestQueryParams"
        v-model:body="requestBody"
        v-model:content-type="requestContentType"
      />
    </n-card>

    <!-- 脚本 -->
    <n-collapse :default-expanded-names="[]" class="debug-section">
      <n-collapse-item title="前置脚本" name="preScript">
        <script-editor v-model="preRequestScript" />
      </n-collapse-item>
      <n-collapse-item title="后置脚本" name="postScript">
        <script-editor v-model="postResponseScript" />
      </n-collapse-item>
    </n-collapse>

    <!-- 断言与变量提取 -->
    <n-collapse :default-expanded-names="[]" class="debug-section">
      <n-collapse-item title="响应断言" name="assertions">
        <assertion-editor v-model:assertions="assertions" />
      </n-collapse-item>
      <n-collapse-item title="变量提取" name="extracts">
        <extract-editor v-model:extracts="extracts" />
      </n-collapse-item>
    </n-collapse>

    <!-- 发送按钮 -->
    <div class="debug-tab__actions">
      <n-button
        type="primary"
        :loading="executing"
        :disabled="!requestUrl"
        size="large"
        @click="handleExecute"
      >
        <template #icon>
          <n-icon><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg></n-icon>
        </template>
        发送请求
      </n-button>
      <n-button @click="handleClear">清空</n-button>
    </div>

    <!-- 响应展示 -->
    <n-card v-if="currentResult" title="响应" size="small" class="debug-section">
      <response-viewer :result="currentResult" />
    </n-card>

    <!-- 调试历史 -->
    <n-card v-if="historyList.length > 0" title="调试历史" size="small" class="debug-section">
      <n-data-table
        :columns="historyColumns"
        :data="historyList"
        :bordered="false"
        size="small"
        :max-height="200"
      />
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useMessage } from 'naive-ui'
import { useDebugStore } from '@/modules/api-test/debug/stores/debug'
import { useEnvironmentStore } from '@/modules/api-test/environment/stores/environment'
import EnvironmentSelector from '@/modules/api-test/debug/components/EnvironmentSelector.vue'
import RequestEditor from '@/modules/api-test/debug/components/RequestEditor.vue'
import ResponseViewer from '@/modules/api-test/debug/components/ResponseViewer.vue'
import ScriptEditor from '@/modules/api-test/debug/components/ScriptEditor.vue'
import AssertionEditor from '@/modules/api-test/debug/components/AssertionEditor.vue'
import ExtractEditor from '@/modules/api-test/debug/components/ExtractEditor.vue'

const props = defineProps<{
  projectId: number
  definitionId: number
}>()

const message = useMessage()
const debugStore = useDebugStore()
const environmentStore = useEnvironmentStore()

const executing = computed(() => debugStore.executing)
const currentResult = computed(() => debugStore.currentResult)
const historyList = computed(() => debugStore.historyList)

// 请求参数
const requestUrl = ref('')
const requestMethod = ref('GET')
const requestHeaders = ref<Record<string, string>>({})
const requestQueryParams = ref<Record<string, string>>({})
const requestBody = ref('')
const requestContentType = ref('application/json')
const environmentId = ref<number | null>(null)

// 脚本
const preRequestScript = ref('')
const postResponseScript = ref('')

// 断言
const assertions = ref<any[]>([])

// 变量提取
const extracts = ref<any[]>([])

// 历史列
const historyColumns = [
  { title: '名称', key: 'name', ellipsis: { tooltip: true } },
  { title: '方法', key: 'requestMethod', width: 80 },
  { title: '状态码', key: 'responseStatusCode', width: 80 },
  { title: '耗时(ms)', key: 'durationMs', width: 90 },
  { title: '状态', key: 'status', width: 80 },
  { title: '时间', key: 'createTime', width: 170 },
]

onMounted(() => {
  if (props.projectId) {
    environmentStore.loadAll(props.projectId)
  }
  debugStore.loadHistory(props.definitionId)
})

function onEnvironmentChange(id: number | null) {
  environmentId.value = id
}

async function handleExecute() {
  if (!requestUrl.value) {
    message.warning('请输入请求URL')
    return
  }

  try {
    await debugStore.execute({
      projectId: props.projectId,
      definitionId: props.definitionId,
      environmentId: environmentId.value ?? undefined,
      url: requestUrl.value,
      method: requestMethod.value,
      headers: requestHeaders.value,
      queryParams: requestQueryParams.value,
      body: requestBody.value || undefined,
      contentType: requestContentType.value || undefined,
      preRequestScript: preRequestScript.value || undefined,
      postResponseScript: postResponseScript.value || undefined,
      assertions: assertions.value.length > 0 ? assertions.value : undefined,
      extracts: extracts.value.length > 0 ? extracts.value : undefined,
    })
    message.success('调试完成')
    // 刷新历史
    debugStore.loadHistory(props.definitionId)
  } catch (e: any) {
    message.error(e.message || '调试失败')
  }
}

function handleClear() {
  debugStore.clearResult()
  requestUrl.value = ''
  requestMethod.value = 'GET'
  requestHeaders.value = {}
  requestQueryParams.value = {}
  requestBody.value = ''
  requestContentType.value = 'application/json'
  preRequestScript.value = ''
  postResponseScript.value = ''
  assertions.value = []
  extracts.value = []
}
</script>

<style scoped>
.debug-tab {
  max-width: 1200px;
  margin: 0 auto;
  padding: 16px 0;
}

.debug-tab__toolbar {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}

.debug-section {
  margin-bottom: 12px;
}

.debug-tab__actions {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
  padding: 12px 0;
}
</style>