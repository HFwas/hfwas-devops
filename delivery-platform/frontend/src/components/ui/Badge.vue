<script setup lang="ts">
withDefaults(defineProps<{
  variant?: 'default' | 'success' | 'warning' | 'danger'
}>(), { variant: 'default' })
</script>

<template>
  <span
    :class="[
      'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium transition-colors',
      variant === 'default' && 'bg-secondary text-secondary-foreground',
      variant === 'success' && 'bg-green-100 text-green-700 dark:bg-green-950 dark:text-green-400',
      variant === 'warning' && 'bg-yellow-100 text-yellow-700 dark:bg-yellow-950 dark:text-yellow-400',
      variant === 'danger' && 'bg-red-100 text-red-700 dark:bg-red-950 dark:text-red-400',
    ]"
  >
    <slot />
  </span>
</template>