<script setup lang="ts">
import { FlipHorizontal, Maximize2, RotateCcw, RotateCw, Undo2, ZoomIn } from '@lucide/vue'
import type { CropAspect, PreviewMode } from '@/modules/image/types/image'

defineProps<{
  aspect: CropAspect
  previewMode: PreviewMode
  hasResult: boolean
  serverDecode?: boolean
}>()

const emit = defineEmits<{
  'update:aspect': [value: CropAspect]
  'update:previewMode': [value: PreviewMode]
  fit: []
  oneToOne: []
  rotateLeft: []
  rotateRight: []
  flip: []
  reset: []
}>()

const aspectOptions = [
  { label: '自由', value: 'free' },
  { label: '1:1', value: '1:1' },
  { label: '4:3', value: '4:3' },
  { label: '16:9', value: '16:9' },
]
</script>

<template>
  <div class="toolbar">
    <n-button-group size="small">
      <n-button @click="emit('fit')">
        <template #icon><Maximize2 :size="14" /></template>
        适应窗口
      </n-button>
      <n-button @click="emit('oneToOne')">
        <template #icon><ZoomIn :size="14" /></template>
        1:1
      </n-button>
    </n-button-group>
    <n-button-group size="small">
      <n-button @click="emit('rotateLeft')">
        <template #icon><RotateCcw :size="14" /></template>
        左旋
      </n-button>
      <n-button @click="emit('rotateRight')">
        <template #icon><RotateCw :size="14" /></template>
        右旋
      </n-button>
      <n-button @click="emit('flip')">
        <template #icon><FlipHorizontal :size="14" /></template>
        水平翻转
      </n-button>
    </n-button-group>
    <n-select
      :value="aspect"
      :options="aspectOptions"
      size="small"
      style="width: 96px"
      @update:value="(v: CropAspect) => emit('update:aspect', v)"
    />
    <n-button size="small" @click="emit('reset')">
      <template #icon><Undo2 :size="14" /></template>
      复位
    </n-button>
    <n-radio-group
      v-if="hasResult"
      :value="previewMode"
      size="small"
      @update:value="(v: PreviewMode) => emit('update:previewMode', v)"
    >
      <n-radio-button value="original">原图</n-radio-button>
      <n-radio-button value="result">处理后</n-radio-button>
    </n-radio-group>
    <n-tag v-if="serverDecode" size="small" type="warning" :bordered="false">需服务端解码</n-tag>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}
</style>
