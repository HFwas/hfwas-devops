<template>
  <div class="rounded-xl border border-border bg-card text-card-foreground shadow-sm">
    <div v-if="$slots.header" class="flex flex-col space-y-1.5 p-5">
      <slot name="header" />
    </div>
    <div v-if="$slots.default" :class="['p-5', !$slots.header && 'pt-5']">
      <slot />
    </div>
    <div v-if="$slots.footer" class="flex items-center p-5 pt-0">
      <slot name="footer" />
    </div>
  </div>
</template>