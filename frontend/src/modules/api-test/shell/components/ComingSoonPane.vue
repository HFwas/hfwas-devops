<script setup lang="ts">
defineProps<{
  title: string
  subtitle?: string
}>()
</script>

<template>
  <div class="coming-soon">
    <div class="coming-soon__title">{{ title }}</div>
    <n-empty :description="subtitle ?? '即将支持'" />
  </div>
</template>

<style scoped>
.coming-soon {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 100%;
  padding: 24px 16px;
  color: var(--wb-muted, #6b7280);
}

.coming-soon__title {
  font-size: 14px;
  font-weight: 600;
  color: inherit;
}
</style>
