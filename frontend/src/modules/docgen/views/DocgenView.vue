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
          <n-input v-model:value="filename" placeholder="输入文件基础名" clearable style="flex:1">
            <template #suffix>
              <n-tag :bordered="false" size="tiny" type="info">自动追加格式名</n-tag>
            </template>
          </n-input>
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
        <!-- 预计生成总数 -->
        <n-space v-if="totalFileCount > 1" align="center">
          <span class="setting-label">合计</span>
          <n-tag type="warning" :bordered="false">
            预计生成 <strong>{{ totalFileCount }}</strong> 个文件
            （{{ selectedFormats.length }} 种格式 × {{ selectedSizes.length || 1 }} 种大小 × {{ fileCount }} 个）
          </n-tag>
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

        <span v-if="!generating && !outputDir && totalFileCount > 1" class="hint-text">
          多组合时自动保存到项目 files/ 目录
        </span>
      </div>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useMessage, NSpace, NInputNumber, NSelect, NCheckbox, NCheckboxGroup, NTag } from 'naive-ui'
import { FileDown, Loader } from '@lucide/vue'
import {
  FORMAT_OPTIONS, FORMAT_MAP,
} from '@/modules/docgen/types/docgen'
import type { DocgenFormat } from '@/modules/docgen/types/docgen'
import { generateDocument, batchGenerate, downloadBlob } from '@/modules/docgen/api/docgen'

const message = useMessage()

// ─── 状态 ───
const selectedFormats = ref<DocgenFormat[]>(['word'])
const generating = ref(false)
const filename = ref('文档')
const fileCount = ref(1)
const outputDir = ref('')
const selectedSizes = ref<number[]>([])
const currentProgress = ref('准备中...')

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
    const isSingleMode = selectedFormats.value.length === 1 && sizes.length === 1 && fileCount.value === 1

    if (isSingleMode && !outputDir.value.trim()) {
      // 单格式 + 单大小 + 单文件 + 无目录 → 沿用旧下载逻辑
      const fmt = selectedFormats.value[0]
      const size = sizes[0]
      const ext = FORMAT_MAP[fmt]?.ext ?? '.bin'
      const label = FORMAT_MAP[fmt]?.label ?? ''
      const sizeLabel = size === 0 ? '' : '_' + formatSizeLabel(size)
      const fileName = `${label}${sizeLabel}_${filename.value || '文档'}${ext}`

      const blob = await generateDocument({
        format: fmt,
        filename: fileName,
        data: { format: fmt, file_count: 1, file_size: size },
      })
      downloadBlob(blob, fileName)
      message.success(`${label} 文件已生成并下载`)
    } else {
      // 批量生成
      const result = await batchGenerate({
        formats: selectedFormats.value,
        filename: filename.value || '文档',
        sizes,
        fileCount: fileCount.value,
        directory: outputDir.value.trim() || undefined,
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
    }
  } catch (err: any) {
    message.error(err?.message || '生成失败，请重试')
  } finally {
    generating.value = false
  }
}

/** 格式化文件大小标签 */
function formatSizeLabel(bytes: number): string {
  if (bytes <= 0) return '不限'
  if (bytes < 1024 * 1024) return (bytes / 1024) + 'KB'
  return (bytes / (1024 * 1024)) + 'MB'
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