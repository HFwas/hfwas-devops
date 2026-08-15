<template>
  <div class="response-body-renderer">
    <!-- 空状态 -->
    <n-empty v-if="!body" description="响应体为空" />

    <template v-else>
      <!-- 工具栏：类型标签 + 视图切换 -->
      <div class="response-body-renderer__toolbar">
        <n-tag :type="typeTagType" size="tiny" bordered>
          {{ detecedTypeLabel }}
        </n-tag>
        <n-button-group size="tiny">
          <n-button
            :type="activeTab === 'pretty' ? 'primary' : 'default'"
            size="tiny"
            @click="activeTab = 'pretty'"
          >
            Pretty
          </n-button>
          <n-button
            :type="activeTab === 'raw' ? 'primary' : 'default'"
            size="tiny"
            @click="activeTab = 'raw'"
          >
            Raw
          </n-button>
          <n-button
            v-if="showPreviewTab"
            :type="activeTab === 'preview' ? 'primary' : 'default'"
            size="tiny"
            @click="activeTab = 'preview'"
          >
            Preview
          </n-button>
        </n-button-group>
      </div>

      <!-- Pretty 视图 -->
      <div v-show="activeTab === 'pretty'" class="response-body-renderer__content">
        <!-- JSON 树形视图 -->
        <json-tree-view
          v-if="detecedType === 'json' && parsedJson"
          :data="parsedJson"
          :max-depth="3"
        />
        <!-- XML/HTML 语法高亮 -->
        <div v-else-if="detecedType === 'xml' || detecedType === 'html'" class="response-body-renderer__highlight">
          <pre><code class="response-body-renderer__code">{{ body }}</code></pre>
        </div>
        <!-- 图片预览 -->
        <img
          v-else-if="detecedType === 'image'"
          :src="imageSrc"
          class="response-body-renderer__image"
          alt="Response image"
          @error="onImageError"
        />
        <div v-else-if="imageError" class="response-body-renderer__error">
          图片加载失败，请在 Raw 视图查看原始数据
        </div>
        <!-- 纯文本 -->
        <pre v-else class="response-body-renderer__text">{{ body }}</pre>
      </div>

      <!-- Raw 视图（原始文本） -->
      <div v-show="activeTab === 'raw'" class="response-body-renderer__content">
        <n-input
          type="textarea"
          :value="body"
          readonly
          :rows="12"
          class="response-body-renderer__raw-input"
        />
      </div>

      <!-- Preview 视图（仅 HTML/图片） -->
      <div v-show="activeTab === 'preview'" class="response-body-renderer__content">
        <iframe
          v-if="detecedType === 'html'"
          :srcdoc="body"
          class="response-body-renderer__iframe"
          sandbox="allow-same-origin"
        />
        <img
          v-else-if="detecedType === 'image'"
          :src="imageSrc"
          class="response-body-renderer__image--full"
          alt="Response image"
          @error="onImageError"
        />
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import JsonTreeView from '@/modules/api-test/shared/components/JsonTreeView.vue'

type DetectedType = 'json' | 'xml' | 'html' | 'image' | 'text' | 'empty'

const props = defineProps<{
  contentType?: string | null
  body: string | null | undefined
  responseStatusCode?: number | null
}>()

const activeTab = ref<'pretty' | 'raw' | 'preview'>('pretty')
const imageError = ref(false)

// 检测内容类型
const detecedType = computed<DetectedType>(() => {
  if (!props.body) return 'empty'

  const ct = (props.contentType || '').toLowerCase()
  const trimmed = props.body.trim()

  // 按 Content-Type 检测
  if (ct.includes('image/') || ct === 'image') return 'image'
  if (ct.includes('html')) return 'html'
  if (ct.includes('xml')) return 'xml'
  if (ct.includes('json')) return 'json'

  // 按内容格式检测
  if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
    // 尝试解析 JSON
    try {
      JSON.parse(trimmed)
      return 'json'
    } catch {
      // 不以 JSON 格式
    }
  }
  if (trimmed.startsWith('<') && trimmed.includes('>') && trimmed.includes('</')) {
    return 'xml'
  }

  return 'text'
})

const detecedTypeLabel = computed(() => {
  const labels: Record<DetectedType, string> = {
    json: 'JSON',
    xml: 'XML',
    html: 'HTML',
    image: '图片',
    text: '纯文本',
    empty: '空',
  }
  return labels[detecedType.value]
})

const typeTagType = computed(() => {
  const types: Record<DetectedType, 'info' | 'success' | 'warning' | 'primary' | 'default' | 'error'> = {
    json: 'info',
    xml: 'warning',
    html: 'warning',
    image: 'success',
    text: 'default',
    empty: 'default',
  }
  return types[detecedType.value]
})

const showPreviewTab = computed(() => {
  return detecedType.value === 'html' || detecedType.value === 'image'
})

// JSON 解析
const parsedJson = computed(() => {
  if (detecedType.value !== 'json' || !props.body) return null
  try {
    return JSON.parse(props.body.trim())
  } catch {
    return null
  }
})

// 图片源
const imageSrc = computed(() => {
  if (detecedType.value !== 'image' || !props.body) return ''
  const body = props.body.trim()
  // 如果已经是 data URL，直接使用
  if (body.startsWith('data:')) return body
  // 如果是 URL，直接使用
  if (body.startsWith('http://') || body.startsWith('https://')) return body
  // 否则构建 data URL
  const ct = props.contentType || 'image/png'
  return `data:${ct};base64,${body}`
})

function onImageError() {
  imageError.value = true
}
</script>

<style scoped>
.response-body-renderer {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 60px;
}

.response-body-renderer__toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  flex-shrink: 0;
}

.response-body-renderer__content {
  flex: 1;
  overflow: auto;
  min-height: 40px;
}

/* JSON 树形视图容器 */
.response-body-renderer__content :deep(.json-tree) {
  padding: 8px 0;
}

/* 语法高亮 */
.response-body-renderer__highlight {
  background: #f8f9fa;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  padding: 12px;
  overflow: auto;
  max-height: 500px;
}

.response-body-renderer__code {
  font-family: 'SF Mono', 'Fira Code', 'Cascadia Code', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
  color: #333;
}

/* 纯文本 */
.response-body-renderer__text {
  font-family: 'SF Mono', 'Fira Code', 'Cascadia Code', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
  color: #333;
  margin: 0;
  padding: 8px 0;
}

/* 原始文本输入框 */
.response-body-renderer__raw-input :deep(textarea) {
  font-family: 'SF Mono', 'Fira Code', 'Cascadia Code', 'Consolas', monospace !important;
  font-size: 13px !important;
}

/* 图片预览 */
.response-body-renderer__image {
  max-width: 100%;
  max-height: 300px;
  object-fit: contain;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  background: #fafafa;
}

.response-body-renderer__image--full {
  max-width: 100%;
  max-height: 600px;
  object-fit: contain;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  background: #fafafa;
}

/* iframe 预览 */
.response-body-renderer__iframe {
  width: 100%;
  min-height: 400px;
  border: 1px solid #e8e8e8;
  border-radius: 4px;
  background: #fff;
}

/* 错误提示 */
.response-body-renderer__error {
  color: #e74c3c;
  font-size: 13px;
  padding: 16px;
  text-align: center;
  background: #fef2f2;
  border-radius: 4px;
}
</style>