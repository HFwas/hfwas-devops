<template>
  <n-modal
    :show="show"
    title="导入 cURL"
    preset="card"
    style="width: 700px; max-height: 85vh;"
    :mask-closable="false"
    @update:show="$emit('update:show', $event)"
  >
    <div class="curl-import">
      <p class="curl-import__hint">
        粘贴一条或多条 cURL（支持 Chrome「Copy all listed as cURL」），解析后导入为草稿 Tab。
      </p>
      <n-input
        v-model:value="curlText"
        type="textarea"
        placeholder="curl -X POST https://api.example.com/users -H &quot;Authorization: Bearer token123&quot; -H &quot;Content-Type: application/json&quot; -d '{&quot;name&quot;:&quot;test&quot;}'"
        :rows="8"
        style="font-family: monospace; font-size: 13px;"
        :disabled="parsing"
      />

      <div class="curl-import__actions">
        <n-button
          type="primary"
          :loading="parsing"
          :disabled="!curlText.trim()"
          @click="handleParse"
        >
          解析
        </n-button>
      </div>

      <n-alert
        v-if="parsedResults.length > 1"
        type="info"
        :title="`已解析 ${parsedResults.length} 条请求`"
        style="margin-top: 4px;"
      >
        导入后将打开 {{ parsedResults.length }} 个草稿 Tab（不会写入左侧集合，需手动保存）。
      </n-alert>

      <n-collapse
        v-if="previewResult"
        :default-expanded-names="['preview']"
        class="curl-import__preview"
      >
        <n-collapse-item
          :title="parsedResults.length > 1 ? '首条预览' : '解析结果预览'"
          name="preview"
        >
          <n-alert
            v-if="previewResult.warnings?.length"
            type="warning"
            :title="`解析警告（${previewResult.warnings.length} 条）`"
            closable
            style="margin-bottom: 12px;"
          >
            <ul style="margin: 4px 0; padding-left: 20px;">
              <li v-for="(w, i) in previewResult.warnings" :key="i">{{ w }}</li>
            </ul>
          </n-alert>

          <n-descriptions :column="2" bordered size="small" label-placement="left">
            <n-descriptions-item label="请求 URL">
              <n-ellipsis style="max-width: 400px;">{{ previewResult.url || '-' }}</n-ellipsis>
            </n-descriptions-item>
            <n-descriptions-item label="请求方法">
              <n-tag :type="methodTagType" size="small">{{ previewResult.method }}</n-tag>
            </n-descriptions-item>
            <n-descriptions-item label="请求头">
              <div v-if="Object.keys(previewResult.headers).length > 0" class="curl-import__headers">
                <div
                  v-for="(value, key) in previewResult.headers"
                  :key="key"
                  class="curl-import__header-item"
                >
                  <span class="curl-import__header-key">{{ key }}:</span>
                  <span class="curl-import__header-value">{{ value }}</span>
                </div>
              </div>
              <span v-else>-</span>
            </n-descriptions-item>
            <n-descriptions-item label="Content-Type">
              {{ previewResult.contentType || '-' }}
            </n-descriptions-item>
            <n-descriptions-item label="请求体" :span="2">
              <n-ellipsis
                v-if="previewResult.body"
                :line-clamp="5"
                :tooltip="{ width: 500 }"
                style="max-width: 100%; font-family: monospace; font-size: 12px; white-space: pre-wrap;"
              >
                {{ previewResult.body }}
              </n-ellipsis>
              <span v-else>-</span>
            </n-descriptions-item>
            <n-descriptions-item label="跟随重定向">
              {{ previewResult.followRedirects ? '是' : '否' }}
            </n-descriptions-item>
          </n-descriptions>
        </n-collapse-item>
      </n-collapse>

      <n-alert
        v-if="parseError"
        type="error"
        title="解析失败"
        closable
        @close="parseError = ''"
        style="margin-top: 12px;"
      >
        {{ parseError }}
      </n-alert>
    </div>

    <template #footer>
      <n-space justify="end">
        <n-button @click="$emit('update:show', false)">取消</n-button>
        <n-button
          type="primary"
          :disabled="parsedResults.length === 0"
          @click="handleImport"
        >
          导入{{ parsedResults.length > 1 ? `（${parsedResults.length}）` : '' }}
        </n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useMessage } from 'naive-ui'
import { curlApi } from '@/modules/api-test/debug/api/curl'
import type { CurlParseResultVO } from '@/modules/api-test/debug/types/curl'
import { splitCurlCommands } from '@/modules/api-test/debug/utils/splitCurlCommands'

defineProps<{
  show: boolean
}>()

const emit = defineEmits<{
  'update:show': [value: boolean]
  imported: [results: CurlParseResultVO[]]
}>()

const message = useMessage()

const curlText = ref('')
const parsing = ref(false)
const parsedResults = ref<CurlParseResultVO[]>([])
const parseError = ref('')

const previewResult = computed(() => parsedResults.value[0] ?? null)

const methodTagType = computed(() => {
  const method = previewResult.value?.method
  if (method === 'GET') return 'success'
  if (method === 'POST') return 'info'
  if (method === 'PUT') return 'warning'
  if (method === 'DELETE') return 'error'
  if (method === 'PATCH') return 'primary'
  return 'default'
})

async function handleParse() {
  if (!curlText.value.trim()) return

  parsing.value = true
  parseError.value = ''
  parsedResults.value = []

  try {
    const commands = splitCurlCommands(curlText.value)
    const settled = await Promise.allSettled(commands.map((cmd) => curlApi.parse(cmd)))
    const ok: CurlParseResultVO[] = []
    const errors: string[] = []
    settled.forEach((item, index) => {
      if (item.status === 'fulfilled') {
        ok.push(item.value)
      } else {
        const reason = item.reason?.message || String(item.reason || '未知错误')
        errors.push(`第 ${index + 1} 条：${reason}`)
      }
    })
    parsedResults.value = ok
    if (ok.length === 0) {
      parseError.value = errors.join('\n') || '解析失败，请检查 cURL 命令格式'
      message.error('解析失败')
      return
    }
    if (errors.length > 0) {
      message.warning(`解析完成：成功 ${ok.length} 条，失败 ${errors.length} 条`)
      parseError.value = errors.join('\n')
    } else if (ok.some((r) => r.warnings?.length > 0)) {
      message.warning(`解析完成 ${ok.length} 条，部分含警告`)
    } else {
      message.success(ok.length > 1 ? `解析成功（${ok.length} 条）` : '解析成功')
    }
  } catch (e: any) {
    parseError.value = e.message || '解析失败，请检查 cURL 命令格式'
    message.error('解析失败')
  } finally {
    parsing.value = false
  }
}

function handleImport() {
  if (parsedResults.value.length === 0) return
  emit('imported', [...parsedResults.value])
  emit('update:show', false)
  curlText.value = ''
  parsedResults.value = []
  parseError.value = ''
}
</script>

<style scoped>
.curl-import {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.curl-import__hint {
  font-size: 13px;
  color: #888;
  margin: 0;
}

.curl-import__actions {
  display: flex;
  justify-content: flex-end;
}

.curl-import__preview {
  border: 1px solid #e8e8e8;
  border-radius: 4px;
}

.curl-import__headers {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.curl-import__header-item {
  font-size: 12px;
  font-family: monospace;
}

.curl-import__header-key {
  font-weight: 600;
  margin-right: 4px;
}

.curl-import__header-value {
  color: #666;
}
</style>
