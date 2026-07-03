<script setup lang="ts">
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'

defineProps<{
  content?: string | null
}>()
</script>

<template>
  <MdPreview v-if="content" language="zh-CN" :model-value="content" />
  <n-text v-else depth="3">暂无内容</n-text>
</template>
