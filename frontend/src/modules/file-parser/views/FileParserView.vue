<template>
  <div class="file-parser-view">
    <!-- 上传区域 -->
    <n-card class="upload-card" :bordered="true">
      <n-upload
        ref="uploadRef"
        :default-upload="false"
        :show-file-list="false"
        accept=".docx,.pptx,.xlsx,.pdf,.jpg,.jpeg,.png,.bmp,.tiff,.tif,.webp,.txt,.csv,.md,.log,.json,.xml,.yaml,.yml"
        @change="handleFileChange"
      >
        <n-upload-dragger
          class="upload-dragger"
          :class="{ 'upload-drager--active': isDragOver }"
          @dragover="isDragOver = true"
          @dragleave="isDragOver = false"
        >
          <div class="upload-content">
            <div class="upload-icon">
              <n-icon size="48" color="#2080f0">
                <FileText />
              </n-icon>
            </div>
            <p class="upload-title">拖拽文件到此处，或点击上传</p>
            <p class="upload-hint">支持 DOCX / PPTX / XLSX / PDF / 图片 / 纯文本</p>
          </div>
        </n-upload-dragger>
      </n-upload>
    </n-card>

    <!-- 解析中状态 -->
    <n-card v-if="uploadStatus === 'parsing'" class="status-card" :bordered="true">
      <div class="status-content">
        <n-spin size="small" />
        <span class="status-text">正在解析文件，请稍候...</span>
      </div>
    </n-card>

    <!-- 错误提示 -->
    <n-card v-if="uploadStatus === 'error'" class="status-card error-card" :bordered="true">
      <div class="status-content">
        <n-icon size="24" color="#e74c3c">
          <AlertCircle />
        </n-icon>
        <span class="status-text error-text">{{ errorMessage }}</span>
        <n-button size="small" @click="resetUpload">重新上传</n-button>
      </div>
    </n-card>

    <!-- 解析结果 -->
    <template v-if="uploadStatus === 'success' && result">
      <!-- 文件信息 -->
      <n-card class="result-card" :bordered="true">
        <template #header>
          <div class="result-header">
            <div class="file-info">
              <n-icon size="20" color="#2080f0">
                <FileText />
              </n-icon>
              <span class="file-name">{{ result.fileName }}</span>
              <n-tag :bordered="false" size="small" type="info">{{ formatFileSize(result.fileSize) }}</n-tag>
              <n-tag :bordered="false" size="small" type="success">{{ methodLabel(result.parseMethod) }}</n-tag>
              <n-tag :bordered="false" size="small" type="warning">{{ result.parseTimeMs }}ms</n-tag>
              <n-tag v-if="previewTruncated" :bordered="false" size="small" type="warning">
                预览截断 {{ formatCharCount(PREVIEW_CHAR_LIMIT) }}
              </n-tag>
            </div>
            <n-button size="small" @click="resetUpload">
              <template #icon><n-icon><RefreshCw /></n-icon></template>
              重新上传
            </n-button>
          </div>
        </template>

        <!-- 警告信息 -->
        <n-alert v-if="result.warnings?.length" type="warning" :bordered="false" class="warnings">
          <template #header>解析提示</template>
          <p v-for="(w, i) in result.warnings" :key="i">{{ w }}</p>
        </n-alert>

        <!-- 结果 Tab -->
        <n-tabs type="line" animated>
          <!-- 文本内容（Markdown 渲染） -->
          <n-tab-pane v-if="result.content?.text" name="text" tab="文本">
            <div class="text-actions">
              <n-button size="tiny" @click="copyText(displayText, previewTruncated)">
                <template #icon><n-icon><Clipboard /></n-icon></template>
                {{ previewTruncated ? '复制预览' : '复制全文' }}
              </n-button>
              <n-button v-if="previewTruncated" size="tiny" @click="downloadFullText">
                <template #icon><n-icon><Download /></n-icon></template>
                下载全文
              </n-button>
              <span v-if="previewTruncated" class="preview-hint">
                页面仅展示前 {{ formatCharCount(PREVIEW_CHAR_LIMIT) }}，全文 {{ formatCharCount(fullText.length) }}，点击下载查看全部
              </span>
            </div>
            <div class="markdown-body" v-html="renderedHtml" />
          </n-tab-pane>

          <!-- 分页内容（PDF 专用） -->
          <n-tab-pane v-if="result.content?.pages?.length" name="pages" tab="分页">
            <n-collapse>
              <n-collapse-item
                v-for="page in result.content.pages"
                :key="page.pageNum"
                :title="`第 ${page.pageNum} 页`"
                :name="String(page.pageNum)"
              >
                <n-input
                  :value="page.text"
                  type="textarea"
                  :autosize="{ minRows: 3, maxRows: 20 }"
                  readonly
                  placeholder="该页无文本内容"
                />
              </n-collapse-item>
            </n-collapse>
          </n-tab-pane>

          <!-- 表格内容（Excel 专用） -->
          <n-tab-pane v-if="result.content?.tables?.length" name="tables" tab="表格">
            <n-tabs v-if="result.content.tables.length > 1" type="card" size="small">
              <n-tab-pane
                v-for="(table, ti) in result.content.tables"
                :key="ti"
                :name="String(ti)"
                :tab="table.sheetName"
              >
                <n-data-table
                  :columns="buildTableColumns(table)"
                  :data="table.rows"
                  :max-height="400"
                  striped
                  size="small"
                />
              </n-tab-pane>
            </n-tabs>
            <n-data-table
              v-else-if="result.content.tables.length === 1"
              :columns="buildTableColumns(result.content.tables[0])"
              :data="result.content.tables[0].rows"
              :max-height="400"
              striped
              size="small"
            />
          </n-tab-pane>

          <!-- 幻灯片内容（PPTX 专用） -->
          <n-tab-pane v-if="result.content?.slides?.length" name="slides" tab="幻灯片">
            <n-collapse>
              <n-collapse-item
                v-for="slide in result.content.slides"
                :key="slide.slideNum"
                :title="`第 ${slide.slideNum} 页`"
                :name="String(slide.slideNum)"
              >
                <n-input
                  :value="slide.text"
                  type="textarea"
                  :autosize="{ minRows: 2, maxRows: 15 }"
                  readonly
                  placeholder="该页无文本内容"
                />
              </n-collapse-item>
            </n-collapse>
          </n-tab-pane>

          <!-- 元数据 -->
          <n-tab-pane v-if="result.content?.metadata" name="metadata" tab="元数据">
            <n-descriptions label-placement="left" bordered size="small" :column="1">
              <n-descriptions-item
                v-for="(value, key) in result.content.metadata"
                :key="key"
                :label="key"
              >
                {{ value }}
              </n-descriptions-item>
            </n-descriptions>
          </n-tab-pane>

          <!-- OCR 信息 -->
          <n-tab-pane v-if="result.ocrInfo" name="ocr" tab="OCR 信息">
            <n-descriptions label-placement="left" bordered size="small" :column="1">
              <n-descriptions-item label="识别引擎">
                {{ result.ocrInfo.engine }}
              </n-descriptions-item>
              <n-descriptions-item label="处理页数">
                {{ result.ocrInfo.pagesProcessed }}
              </n-descriptions-item>
              <n-descriptions-item label="置信度">
                <n-progress
                  type="line"
                  :percentage="Math.round(result.ocrInfo.confidence * 100)"
                  :color="confidenceColor(result.ocrInfo.confidence)"
                  :height="16"
                  :show-indicator="true"
                />
              </n-descriptions-item>
            </n-descriptions>
          </n-tab-pane>
        </n-tabs>
      </n-card>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMessage } from 'naive-ui'
import { FileText, AlertCircle, RefreshCw, Clipboard, Download } from '@lucide/vue'
import type { UploadFileInfo } from 'naive-ui'
import { uploadFile } from '@/modules/file-parser/api/fileParser'
import type { FileParseResult, UploadStatus } from '@/modules/file-parser/types/fileParser'
import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({
  html: false,
  linkify: true,
  typographer: true,
  breaks: true,
})

const PREVIEW_CHAR_LIMIT = 100_000

const message = useMessage()

const isDragOver = ref(false)
const uploadStatus = ref<UploadStatus>('idle')
const result = ref<FileParseResult | null>(null)
const errorMessage = ref('')

const fullText = computed(() => result.value?.content?.text ?? '')
const previewTruncated = computed(() => fullText.value.length > PREVIEW_CHAR_LIMIT)
const displayText = computed(() =>
  previewTruncated.value ? fullText.value.slice(0, PREVIEW_CHAR_LIMIT) : fullText.value
)

const renderedHtml = computed(() => {
  const text = displayText.value
  if (!text) return ''
  return md.render(text)
})

function handleFileChange({ file }: { file: UploadFileInfo }) {
  if (!file.file) return

  const rawFile = file.file
  // 校验文件大小（50MB）
  if (rawFile.size > 50 * 1024 * 1024) {
    errorMessage.value = '文件大小超过 50MB 限制'
    uploadStatus.value = 'error'
    return
  }

  startParse(rawFile)
}

function startParse(file: File) {
  uploadStatus.value = 'parsing'
  result.value = null
  errorMessage.value = ''

  uploadFile(file)
    .then((res) => {
      if (res.success) {
        result.value = res
        uploadStatus.value = 'success'
      } else {
        errorMessage.value = res.errorMessage || '解析失败，请重试'
        uploadStatus.value = 'error'
      }
    })
    .catch((err) => {
      errorMessage.value = err?.message || '网络错误，请重试'
      uploadStatus.value = 'error'
    })
}

function resetUpload() {
  uploadStatus.value = 'idle'
  result.value = null
  errorMessage.value = ''
}

function copyText(text: string, preview = false) {
  navigator.clipboard.writeText(text).then(() => {
    message.success(preview ? '已复制预览到剪贴板' : '已复制到剪贴板')
  })
}

function downloadFullText() {
  const text = fullText.value
  if (!text) return
  const blob = new Blob([text], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  const base = (result.value?.fileName || 'extracted').replace(/\.[^.]+$/, '')
  link.href = url
  link.download = `${base}.md`
  link.click()
  URL.revokeObjectURL(url)
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return bytes + 'B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + 'KB'
  return (bytes / (1024 * 1024)).toFixed(1) + 'MB'
}

function formatCharCount(count: number): string {
  if (count >= 10000) return `${(count / 10000).toFixed(count % 10000 === 0 ? 0 : 1)} 万字`
  return `${count} 字`
}

function methodLabel(method: string): string {
  const map: Record<string, string> = {
    tika: 'Tika 解析',
    ocr: 'OCR 识别',
    plain: '文本解析',
  }
  return map[method] || method
}

function confidenceColor(confidence: number): string {
  if (confidence >= 0.9) return '#18a058'
  if (confidence >= 0.7) return '#2080f0'
  return '#e74c3c'
}

function buildTableColumns(table: { sheetName: string; rows: string[][] }) {
  if (!table.rows.length) return []
  const headers = table.rows[0]
  return headers.map((h, i) => ({
    title: h || `列 ${i + 1}`,
    key: String(i),
    render: (row: string[]) => row[i] ?? '',
  }))
}
</script>

<style scoped>
.file-parser-view {
  max-width: 960px;
  margin: 0 auto;
  padding: 24px;
}

.upload-card {
  margin-bottom: 16px;
}

.upload-dragger {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  padding: 40px;
  cursor: pointer;
  transition: all 0.3s;
  border: 2px dashed #d9d9d9;
  border-radius: 8px;
}

.upload-drager--active {
  border-color: #2080f0;
  background-color: rgba(32, 128, 240, 0.05);
}

.upload-content {
  text-align: center;
}

.upload-icon {
  margin-bottom: 12px;
}

.upload-title {
  font-size: 16px;
  color: #333;
  margin-bottom: 8px;
  font-weight: 500;
}

.upload-hint {
  font-size: 13px;
  color: #999;
  margin: 0;
}

.status-card {
  margin-bottom: 16px;
}

.status-content {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
}

.status-text {
  flex: 1;
  font-size: 14px;
  color: #666;
}

.error-text {
  color: #e74c3c;
}

.error-card {
  border-color: #e74c3c;
}

.result-card {
  margin-bottom: 16px;
}

.result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.file-name {
  font-size: 15px;
  font-weight: 600;
  color: #333;
}

.text-actions {
  margin-bottom: 8px;
  display: flex;
  gap: 8px;
  align-items: center;
}

.preview-hint {
  font-size: 12px;
  color: #999;
}

.warnings {
  margin-bottom: 12px;
}

/* Markdown 渲染样式 */
.markdown-body {
  padding: 16px;
  background: #fafafa;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
  font-size: 14px;
  line-height: 1.7;
  color: #333;
  overflow-x: auto;
}

.markdown-body h1,
.markdown-body h2,
.markdown-body h3,
.markdown-body h4,
.markdown-body h5,
.markdown-body h6 {
  margin-top: 1.2em;
  margin-bottom: 0.6em;
  font-weight: 600;
  color: #1a1a1a;
}

.markdown-body h1 { font-size: 1.6em; border-bottom: 1px solid #eee; padding-bottom: 0.3em; }
.markdown-body h2 { font-size: 1.35em; border-bottom: 1px solid #eee; padding-bottom: 0.25em; }
.markdown-body h3 { font-size: 1.15em; }

.markdown-body p {
  margin: 0.5em 0;
}

.markdown-body strong {
  font-weight: 600;
  color: #1a1a1a;
}

.markdown-body em {
  font-style: italic;
}

.markdown-body ul,
.markdown-body ol {
  padding-left: 2em;
  margin: 0.5em 0;
}

.markdown-body li {
  margin: 0.25em 0;
}

.markdown-body blockquote {
  margin: 0.5em 0;
  padding: 0.5em 1em;
  border-left: 4px solid #2080f0;
  background: #f0f7ff;
  color: #555;
}

.markdown-body code {
  padding: 0.2em 0.4em;
  background: #f0f0f0;
  border-radius: 3px;
  font-size: 0.9em;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
}

.markdown-body pre {
  padding: 12px 16px;
  background: #282c34;
  border-radius: 4px;
  overflow-x: auto;
  margin: 0.5em 0;
}

.markdown-body pre code {
  padding: 0;
  background: none;
  color: #abb2bf;
  font-size: 0.85em;
}

.markdown-body table {
  border-collapse: collapse;
  width: 100%;
  margin: 0.5em 0;
}

.markdown-body th,
.markdown-body td {
  border: 1px solid #ddd;
  padding: 6px 12px;
  text-align: left;
}

.markdown-body th {
  background: #f5f5f5;
  font-weight: 600;
}

.markdown-body tr:nth-child(even) {
  background: #fafafa;
}

.markdown-body hr {
  border: none;
  border-top: 2px solid #e0e0e0;
  margin: 1em 0;
}

.markdown-body img {
  max-width: 100%;
  border-radius: 4px;
  margin: 0.5em 0;
}

.markdown-body a {
  color: #2080f0;
  text-decoration: none;
}

.markdown-body a:hover {
  text-decoration: underline;
}

.markdown-body .task-list-item {
  list-style: none;
}

.markdown-body .task-list-item input[type="checkbox"] {
  margin-right: 0.5em;
}
</style>