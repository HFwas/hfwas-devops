<template>
  <div class="script-editor">
    <n-input
      :value="modelValue"
      type="textarea"
      :rows="6"
      placeholder="// 在此编写 JavaScript 脚本&#10;// 前置脚本可用: pm.request, pm.environment, console&#10;// 后置脚本可用: pm.response, pm.environment, pm.variables, console"
      style="font-family: monospace; font-size: 13px;"
      @update:value="onInput"
    />
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{
  modelValue: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

function onInput(value: string) {
  emit('update:modelValue', value)
}
</script>