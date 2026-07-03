<script setup lang="ts">
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'

const props = defineProps<{
  modelValue?: string | null
  placeholder?: string
}>()

const emit = defineEmits<{ 'update:modelValue': [string] }>()

const text = computed({
  get: () => props.modelValue ?? '',
  set: (v) => emit('update:modelValue', v),
})
</script>

<template>
  <MdEditor
    v-model="text"
    language="zh-CN"
    :placeholder="placeholder"
    style="height: 360px"
  />
</template>
