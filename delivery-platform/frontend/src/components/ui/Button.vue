<script setup lang="ts">
withDefaults(defineProps<{
  variant?: 'default' | 'secondary' | 'outline' | 'destructive' | 'ghost'
  size?: 'sm' | 'md' | 'lg'
  disabled?: boolean
}>(), { variant: 'default', size: 'md' })

const emit = defineEmits<{ click: [] }>()
</script>

<template>
  <button
    :disabled="disabled"
    :class="[
      'inline-flex items-center justify-center gap-2 font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring disabled:pointer-events-none disabled:opacity-50',
      variant === 'default' && 'bg-primary text-primary-foreground hover:bg-primary/90 shadow-sm',
      variant === 'secondary' && 'bg-secondary text-secondary-foreground hover:bg-secondary/80',
      variant === 'outline' && 'border border-input bg-background hover:bg-accent hover:text-accent-foreground',
      variant === 'destructive' && 'bg-destructive text-destructive-foreground hover:bg-destructive/90',
      variant === 'ghost' && 'hover:bg-accent hover:text-accent-foreground',
      size === 'sm' && 'h-8 px-3 text-xs rounded-md',
      size === 'md' && 'h-10 px-4 text-sm rounded-lg',
      size === 'lg' && 'h-11 px-6 text-base rounded-lg',
    ]"
    @click="emit('click')"
  >
    <slot />
  </button>
</template>