<template>
  <div class="json-editor">
    <n-input
      :value="modelValue"
      type="textarea"
      :rows="rows"
      placeholder="输入 JSON 内容"
      style="font-family: monospace; font-size: 13px;"
      @update:value="onInput"
    />
    <div v-if="error" class="json-editor__error">
      {{ error }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{
  modelValue: string
  rows?: number
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'valid': [isValid: boolean]
}>()

const error = ref<string | null>(null)

const rows = props.rows ?? 10

watch(() => props.modelValue, (val) => {
  if (!val) {
    error.value = null
    emit('valid', true)
    return
  }
  try {
    JSON.parse(val)
    error.value = null
    emit('valid', true)
  } catch (e: any) {
    error.value = e.message
    emit('valid', false)
  }
})

function onInput(value: string) {
  emit('update:modelValue', value)
}
</script>

<style scoped>
.json-editor__error {
  color: #F56C6C;
  font-size: 12px;
  margin-top: 4px;
  padding: 4px 8px;
  background: #fef0f0;
  border-radius: 4px;
}
</style>