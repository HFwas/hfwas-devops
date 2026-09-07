<script setup lang="ts">
import type { ImageConvertRequest, ImageConvertVO } from '@/modules/image/types/image'

defineProps<{
  targetFormat: ImageConvertRequest['targetFormat']
  quality: number
  maxSide: number | null
  stripMetadata: boolean
  applyOrientation: boolean
  converting: boolean
  queueCount: number
  hasGps: boolean
  result: ImageConvertVO | null
}>()

const emit = defineEmits<{
  'update:targetFormat': [value: ImageConvertRequest['targetFormat']]
  'update:quality': [value: number]
  'update:maxSide': [value: number | null]
  'update:stripMetadata': [value: boolean]
  'update:applyOrientation': [value: boolean]
  convertDownload: []
  convertPreview: []
  convertAll: []
}>()

const formatOptions = [
  { label: 'JPEG', value: 'jpeg' },
  { label: 'PNG', value: 'png' },
  { label: 'WebP', value: 'webp' },
  { label: 'TIFF', value: 'tiff' },
]
</script>

<template>
  <n-space vertical>
    <n-form label-placement="left" label-width="88" size="small">
      <n-form-item label="目标格式">
        <n-select
          :value="targetFormat"
          :options="formatOptions"
          @update:value="(v: ImageConvertRequest['targetFormat']) => emit('update:targetFormat', v)"
        />
      </n-form-item>
      <n-form-item v-if="targetFormat !== 'png' && targetFormat !== 'tiff'" label="质量">
        <n-slider :value="quality" :min="1" :max="100" @update:value="(v: number) => emit('update:quality', v)" />
      </n-form-item>
      <n-form-item label="最长边">
        <n-input-number
          :value="maxSide"
          :min="1"
          :show-button="false"
          placeholder="不缩放"
          style="width: 100%"
          @update:value="(v: number | null) => emit('update:maxSide', v)"
        />
      </n-form-item>
      <n-form-item label="隐私">
        <n-checkbox
          :checked="stripMetadata"
          @update:checked="(v: boolean) => emit('update:stripMetadata', v)"
        >
          去除 GPS/元数据
        </n-checkbox>
      </n-form-item>
      <n-form-item label="方向">
        <n-checkbox
          :checked="applyOrientation"
          @update:checked="(v: boolean) => emit('update:applyOrientation', v)"
        >
          按 Orientation 写入已摆正像素
        </n-checkbox>
      </n-form-item>
    </n-form>
    <n-alert v-if="hasGps" type="warning" :bordered="false">
      此图含位置信息，导出默认建议去除 GPS
    </n-alert>
    <n-button type="primary" block :loading="converting" @click="emit('convertDownload')">
      转换并下载
    </n-button>
    <n-button block :disabled="converting" @click="emit('convertPreview')">
      转换后替换画布预览
    </n-button>
    <n-button v-if="queueCount > 1" block :disabled="converting" @click="emit('convertAll')">
      队列全部转换并下载
    </n-button>
    <n-text v-if="result" depth="3" style="font-size: 12px">
      最近结果 {{ result.resultFileName }} · {{ result.width }}×{{ result.height }}
      <span v-if="result.status && result.status !== 'completed'"> · {{ result.status }}</span>
    </n-text>
  </n-space>
</template>
