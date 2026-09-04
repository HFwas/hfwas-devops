<template>
  <div class="docgen-view">
    <!-- 标题 -->
    <div class="page-header">
      <h2>文档生成</h2>
      <p class="page-desc">选择格式和大小，批量生成文档</p>
    </div>

    <!-- 格式选择（多选） -->
    <n-card title="1. 选择格式（可多选）" class="section-card" :bordered="true">
      <n-checkbox-group v-model:value="selectedFormats" class="format-group">
        <n-checkbox
          v-for="opt in FORMAT_OPTIONS"
          :key="opt.value"
          :value="opt.value"
          class="format-checkbox"
        >
          <span class="format-icon">{{ opt.icon }}</span>
          <span class="format-label">{{ opt.label }}</span>
          <n-tag size="tiny" :bordered="false" type="info">{{ opt.ext }}</n-tag>
        </n-checkbox>
      </n-checkbox-group>
    </n-card>

    <!-- 文件名 & 生成设置 -->
    <n-card class="section-card" :bordered="true">
      <n-space vertical>
        <n-space align="center" :wrap="false">
          <span class="setting-label">文件名</span>
          <n-input v-model:value="filename" placeholder="输入文件基础名" clearable style="flex:1" />
        </n-space>
        <n-space align="center">
          <span class="setting-label">文件数</span>
          <n-input-number v-model:value="fileCount" :min="1" :step="1" style="width:120px" />
          <span class="hint-text">个/每种格式每种大小</span>
        </n-space>
        <n-space align="center">
          <span class="setting-label">大小</span>
          <n-select v-model:value="selectedSizes" :options="FILE_SIZE_OPTIONS" multiple style="width:280px" placeholder="多选文件大小" />
          <span class="hint-text">留空则不限制大小</span>
        </n-space>
        <n-space align="center">
          <span class="setting-label">目录</span>
          <n-input v-model:value="outputDir" placeholder="文件生成后保存的目录路径，留空则使用默认目录" clearable style="flex:1" />
          <span class="hint-text">留空时保存到项目 files/ 目录</span>
        </n-space>
        <n-space align="center" :wrap="true">
          <span class="setting-label">预览</span>
          <n-tag :bordered="false" type="info">{{ namePreview }}</n-tag>
          <n-tag v-if="totalFileCount > 1" type="warning" :bordered="false">
            共 {{ totalFileCount }} 个
            （{{ selectedFormats.length }} 格式 × {{ selectedSizes.length || 1 }} 大小 × {{ fileCount }} 份）
          </n-tag>
        </n-space>
      </n-space>
    </n-card>

    <!-- Excel 专属设置 -->
    <n-card v-if="selectedFormats.includes('excel')" class="section-card" :bordered="true" title="2. Excel 设置">
      <n-space vertical>
        <n-space align="center">
          <span class="setting-label">列数</span>
          <n-input-number v-model:value="columnCount" :min="1" :max="1000" :step="1" style="width:120px" />
          <span class="hint-text">自定义列数，自动生成列名（列1, 列2...）</span>
        </n-space>
        <n-space align="center">
          <span class="setting-label">行大小</span>
          <n-input-number v-model:value="rowSize" :min="1" :max="10000" :step="1" style="width:120px" />
          <span class="hint-text">每个单元格的字符数，越大单元格内容越长</span>
        </n-space>
        <n-space align="center">
          <span class="setting-label">行数</span>
          <n-input-number v-model:value="rowCount" :min="1" :max="9999999" :step="1" style="width:120px" />
          <span class="hint-text">生成行数（不限制，可设置极大值）</span>
        </n-space>
      </n-space>
    </n-card>

    <!-- PDF 专属设置 -->
    <n-card v-if="selectedFormats.includes('pdf')" class="section-card" :bordered="true" title="2. PDF 设置">
      <n-space vertical>
        <n-space align="center">
          <span class="setting-label">页数</span>
          <n-input-number
            v-model:value="pageCount"
            :min="1"
            :max="9999"
            :step="1"
            :disabled="emptyPageCount"
            style="width:120px"
          />
          <span class="hint-text">{{ emptyPageCount ? '已开启空页数，按内容自动分页' : '生成 PDF 的页数' }}</span>
        </n-space>
        <n-space align="center">
          <span class="setting-label">空页数</span>
          <n-switch v-model:value="emptyPageCount" />
          <span class="hint-text">开启后不指定页数，按内容自动分页</span>
        </n-space>
        <n-space align="center">
          <span class="setting-label">空内容</span>
          <n-switch v-model:value="emptyContent" />
          <span class="hint-text">开启后不写入正文，只生成空白页</span>
        </n-space>
        <n-space align="center">
          <span class="setting-label">加密</span>
          <n-switch v-model:value="encrypt" />
          <n-input
            v-if="encrypt"
            v-model:value="pdfPassword"
            type="password"
            show-password-on="click"
            placeholder="打开密码"
            style="width:160px"
          />
          <span class="hint-text">{{ encrypt ? '打开 PDF 需要密码，默认 123456' : '开启后生成带密码的 PDF' }}</span>
        </n-space>
      </n-space>
    </n-card>

    <!-- 生成按钮 -->
    <n-card class="section-card action-card" :bordered="true">
      <div class="action-bar">
        <n-button
          size="large"
          type="primary"
          :loading="generating"
          :disabled="generating"
          @click="handleGenerate"
        >
          <template #icon>
            <n-icon><FileDown /></n-icon>
          </template>
          {{ generating ? '生成中...' : '批量生成' }}
        </n-button>

        <n-tag v-if="generating" type="info" :bordered="false">
          <template #icon>
            <n-icon><Loader /></n-icon>
          </template>
          {{ currentProgress }}
        </n-tag>

        <span v-if="!generating && !outputDir" class="hint-text">
          文件保存到项目 files/ 目录
        </span>
      </div>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useMessage, NSpace, NInputNumber, NSelect, NCheckbox, NCheckboxGroup, NTag, NSwitch } from 'naive-ui'
import { FileDown, Loader } from '@lucide/vue'
import { FORMAT_OPTIONS } from '@/modules/docgen/types/docgen'
import type { DocgenFormat } from '@/modules/docgen/types/docgen'
import { batchGenerate } from '@/modules/docgen/api/docgen'

const message = useMessage()

// ─── 状态 ───
const selectedFormats = ref<DocgenFormat[]>(['word'])
const generating = ref(false)
const filename = ref('文档')
const fileCount = ref(1)
const outputDir = ref('')
const selectedSizes = ref<number[]>([])
const currentProgress = ref('准备中...')
const columnCount = ref(8)
const rowSize = ref(10)
const rowCount = ref(20)
const pageCount = ref(5)
const emptyPageCount = ref(false)
const emptyContent = ref(false)
const encrypt = ref(false)
const pdfPassword = ref('123456')

const FILE_SIZE_OPTIONS = [
  { label: '不限制', value: 0 },
  { label: '100 KB', value: 100 * 1024 },
  { label: '500 KB', value: 500 * 1024 },
  { label: '1 MB', value: 1 * 1024 * 1024 },
  { label: '2 MB', value: 2 * 1024 * 1024 },
  { label: '5 MB', value: 5 * 1024 * 1024 },
  { label: '10 MB', value: 10 * 1024 * 1024 },
  { label: '15 MB', value: 15 * 1024 * 1024 },
  { label: '20 MB', value: 20 * 1024 * 1024 },
]

// ─── 计算属性 ───
const totalFileCount = computed(() => {
  const fmtCount = selectedFormats.value.length
  const sizeCount = selectedSizes.value.length || 1
  return fmtCount * sizeCount * fileCount.value
})

const namePreview = computed(() => {
  const fmt = selectedFormats.value[0] || 'word'
  const ext = FORMAT_OPTIONS.find(o => o.value === fmt)?.ext || '.bin'
  const size = selectedSizes.value[0] ?? 0
  const name = buildOutputFileName(filename.value, fmt, ext, size, 0, fileCount.value)
  if (totalFileCount.value > 1) {
    return `${name} 等`
  }
  return name
})

function sanitizeBaseName(name: string): string {
  const cleaned = (name || '文档').trim().replace(/[\\/:*?"<>|\s]+/g, '_').replace(/_+/g, '_').replace(/^_|_$/g, '')
  return cleaned || '文档'
}

function formatSizeLabel(bytes: number): string {
  if (bytes <= 0) return ''
  if (bytes < 1024 * 1024) return `${bytes / 1024}KB`
  return `${bytes / (1024 * 1024)}MB`
}

function todayPrefix(): string {
  const d = new Date()
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}${m}${day}`
}

function buildOutputFileName(
  rawName: string,
  fmt: DocgenFormat,
  ext: string,
  size: number,
  index: number,
  count: number,
): string {
  const parts = [todayPrefix(), sanitizeBaseName(rawName)]
  const sizeLabel = formatSizeLabel(size)
  if (sizeLabel) parts.push(sizeLabel)
  if (fmt === 'pdf') {
    if (emptyPageCount.value) parts.push('空页数')
    else if (pageCount.value > 0) parts.push(`${pageCount.value}页`)
    if (emptyContent.value) parts.push('空内容')
    if (encrypt.value) parts.push('加密')
  }
  if (count > 1) parts.push(String(index + 1))
  return `${parts.join('_')}${ext}`
}

// ─── 生成逻辑 ───
async function handleGenerate() {
  if (generating.value) return

  // 校验：至少选一个格式
  if (selectedFormats.value.length === 0) {
    message.error('请至少选择一个格式')
    return
  }

  const sizes = selectedSizes.value.length > 0 ? selectedSizes.value : [0]
  const total = selectedFormats.value.length * sizes.length * fileCount.value

  // 校验：组合数过大时警告
  if (total > 100) {
    message.warning(`即将生成 ${total} 个文件，可能需要较长时间，是否继续？`)
  }

  generating.value = true
  currentProgress.value = `正在生成 ${total} 个文件...`

  try {
    // 统一走批量生成接口，保存到目录（不下载）
    const result = await batchGenerate({
      formats: selectedFormats.value,
      filename: filename.value || '文档',
      sizes,
      fileCount: fileCount.value,
      directory: outputDir.value.trim() || undefined,
      columnCount: selectedFormats.value.includes('excel') ? columnCount.value : undefined,
      rowSize: selectedFormats.value.includes('excel') ? rowSize.value : undefined,
      rowCount: selectedFormats.value.includes('excel') ? rowCount.value : undefined,
      pageCount: selectedFormats.value.includes('pdf') && !emptyPageCount.value ? pageCount.value : undefined,
      encrypt: selectedFormats.value.includes('pdf') ? encrypt.value : undefined,
      pdfPassword: selectedFormats.value.includes('pdf') && encrypt.value ? (pdfPassword.value || '123456') : undefined,
      emptyContent: selectedFormats.value.includes('pdf') ? emptyContent.value : undefined,
      emptyPageCount: selectedFormats.value.includes('pdf') ? emptyPageCount.value : undefined,
    })

    if (result.success) {
      const fileList = result.files?.map(f => f.filename).join(', ') || ''
      message.success(result.message, { duration: 5000 })
      // 如果文件数不多，显示文件名列表
      if (result.files && result.files.length <= 10) {
        currentProgress.value = `已生成: ${fileList}`
      } else {
        currentProgress.value = `已生成 ${result.total} 个文件`
      }
    }
  } catch (err: any) {
    message.error(err?.message || '生成失败，请重试')
  } finally {
    generating.value = false
  }
}
</script>

<style scoped>
.docgen-view {
  max-width: 860px;
  margin: 0 auto;
  padding: 24px;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  margin: 0 0 4px;
  font-size: 22px;
  font-weight: 600;
}

.page-desc {
  margin: 0;
  color: #888;
  font-size: 14px;
}

.section-card {
  margin-bottom: 16px;
}

/* 格式选择 */
.format-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.format-checkbox {
  padding: 8px 14px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  transition: all 0.2s;
}

.format-checkbox:hover {
  border-color: #2080f0;
  background: rgba(32, 128, 240, 0.04);
}

:deep(.format-checkbox.n-checkbox--checked) {
  border-color: #2080f0;
  background: rgba(32, 128, 240, 0.06);
}

.format-icon {
  font-size: 18px;
  margin-right: 4px;
}

.format-label {
  font-size: 14px;
  font-weight: 500;
  margin-right: 6px;
}

/* 操作按钮 */
.action-bar {
  display: flex;
  align-items: center;
  gap: 16px;
}

.setting-label {
  font-size: 14px;
  font-weight: 500;
  color: #666;
  white-space: nowrap;
  min-width: 48px;
}

.hint-text {
  font-size: 12px;
  color: #999;
}
</style>