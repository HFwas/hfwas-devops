<template>
  <div class="key-value-editor" :class="{ 'key-value-editor--typed': showType }">
    <div class="key-value-editor__head">
      <span class="key-value-editor__check" />
      <span>键</span>
      <span>值</span>
      <span v-if="showType">类型</span>
      <span class="key-value-editor__action" />
    </div>
    <div
      v-for="(row, index) in pairs"
      :key="index"
      class="key-value-editor__row"
    >
      <n-checkbox
        :checked="row.enabled"
        :disabled="readonly"
        @update:checked="(v: boolean) => patch(index, { enabled: v })"
      />
      <n-input
        :value="row.key"
        size="tiny"
        :placeholder="keyPlaceholder"
        :disabled="readonly"
        @update:value="(v: string) => patch(index, { key: v })"
      />
      <n-input
        :value="row.value"
        size="tiny"
        :placeholder="valuePlaceholder"
        :disabled="readonly"
        @update:value="(v: string) => patch(index, { value: v })"
      />
      <n-select
        v-if="showType"
        :value="row.type || 'text'"
        size="tiny"
        :options="TYPE_OPTIONS"
        :disabled="readonly"
        style="width: 88px;"
        @update:value="(v: string) => patch(index, { type: v as 'text' | 'file' })"
      />
      <n-button
        size="tiny"
        quaternary
        type="error"
        :disabled="readonly"
        @click="remove(index)"
      >
        删除
      </n-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { KeyValuePair } from '@/modules/api-test/shared/types/keyValue'
import { removePairAt, updatePairAt } from '@/modules/api-test/shared/utils/keyValue'

const TYPE_OPTIONS = [
  { label: 'Text', value: 'text' },
  { label: 'File', value: 'file' },
]

const props = defineProps<{
  pairs: KeyValuePair[]
  keyPlaceholder?: string
  valuePlaceholder?: string
  showType?: boolean
  readonly?: boolean
}>()

const emit = defineEmits<{
  'update:pairs': [value: KeyValuePair[]]
}>()

function patch(index: number, partial: Partial<KeyValuePair>) {
  emit('update:pairs', updatePairAt(props.pairs, index, partial))
}

function remove(index: number) {
  emit('update:pairs', removePairAt(props.pairs, index))
}
</script>

<style scoped>
.key-value-editor {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.key-value-editor__head,
.key-value-editor__row {
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr) minmax(0, 1.4fr) auto;
  gap: 6px;
  align-items: center;
}

.key-value-editor--typed .key-value-editor__head,
.key-value-editor--typed .key-value-editor__row {
  grid-template-columns: 22px minmax(0, 1fr) minmax(0, 1.2fr) 88px auto;
}

.key-value-editor__head {
  font-size: 12px;
  color: var(--wb-muted, #6b7280);
}

.key-value-editor__check,
.key-value-editor__action {
  width: 22px;
}
</style>
