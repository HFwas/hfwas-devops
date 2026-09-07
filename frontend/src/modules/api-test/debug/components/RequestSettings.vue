<script setup lang="ts">
const props = defineProps<{
  timeoutMs: number
  followRedirects: boolean
}>()

const emit = defineEmits<{
  'update:timeoutMs': [value: number]
  'update:followRedirects': [value: boolean]
}>()
</script>

<template>
  <div class="request-settings" data-testid="request-settings">
    <n-form label-placement="left" label-width="120">
      <n-form-item label="超时 (ms)">
        <n-input-number
          :value="timeoutMs"
          :min="1"
          :max="300000"
          size="small"
          @update:value="(v: number | null) => emit('update:timeoutMs', v ?? 30000)"
        />
      </n-form-item>
      <n-form-item label="跟随重定向">
        <n-switch
          :value="followRedirects"
          @update:value="(v: boolean) => emit('update:followRedirects', v)"
        />
      </n-form-item>
    </n-form>
  </div>
</template>
