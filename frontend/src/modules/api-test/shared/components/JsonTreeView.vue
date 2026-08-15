<template>
  <div class="json-tree">
    <template v-for="(entry, i) in entries" :key="i">
      <div class="json-tree__node" :style="{ paddingLeft: depth * 16 + 'px' }">
        <!-- 展开/折叠按钮 -->
        <span
          v-if="entry.isExpandable"
          class="json-tree__toggle"
          @click="entry.toggle()"
        >
          <svg
            width="10" height="10" viewBox="0 0 10 10"
            :class="{ 'json-tree__toggle--expanded': entry.isExpanded }"
          >
            <path d="M3 1 L7 5 L3 9" fill="none" stroke="currentColor" stroke-width="1.5"/>
          </svg>
        </span>
        <span v-else class="json-tree__toggle-placeholder" />

        <!-- 键名 -->
        <span class="json-tree__key" v-if="entry.key !== null">{{ entry.key }}<span class="json-tree__colon">:</span></span>

        <!-- 数组索引 -->
        <span class="json-tree__index" v-if="entry.index !== null"><span class="json-tree__colon">{{ entry.index }}:</span></span>

        <!-- 展开的对象/数组 -->
        <template v-if="entry.isExpanded">
          <span class="json-tree__bracket">{{ entry.isArray ? '[' : '{' }}</span>
          <span class="json-tree__count">{{ entry.size }} 项</span>
          <json-tree-view
            :data="entry.value"
            :depth="depth + 1"
            :max-depth="maxDepth"
          />
          <span class="json-tree__bracket">{{ entry.isArray ? ']' : '}' }}</span>
        </template>

        <!-- 折叠的对象/数组 -->
        <span
          v-else-if="entry.isExpandable"
          class="json-tree__collapsed"
          @click="entry.toggle()"
        >
          {{ entry.isArray ? 'Array' : 'Object' }} ({{ entry.size }})
        </span>

        <!-- 原始值 -->
        <span
          v-else
          class="json-tree__value"
          :class="'json-tree__value--' + entry.type"
          @click="copyValue(entry.displayValue)"
          :title="depth > 0 ? '点击复制' : ''"
        >
          <span v-if="entry.type === 'string'">"{{ entry.displayValue }}"</span>
          <span v-else-if="entry.type === 'null'">null</span>
          <span v-else>{{ entry.displayValue }}</span>
        </span>

        <span class="json-tree__comma" v-if="i < entries.length - 1">,</span>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'

interface Entry {
  key: string | null
  index: number | null
  value: any
  type: string
  displayValue: string
  isExpandable: boolean
  isExpanded: boolean
  isArray: boolean
  size: number
  toggle: () => void
}

const props = withDefaults(defineProps<{
  data: any
  depth?: number
  maxDepth?: number
}>(), {
  depth: 0,
  maxDepth: 3,
})

const entries = ref<Entry[]>([])

// 持久化展开状态，key 为 `${depth}-${key||index}`
const expandedStates = new Map<string, boolean>()

watch(
  () => props.data,
  (newData) => {
    entries.value = buildEntries(newData, props.depth, props.maxDepth)
  },
  { immediate: true, deep: true },
)

function buildEntries(data: any, depth: number, maxDepth: number): Entry[] {
  if (data === null || data === undefined) {
    return [makeEntry(null, null, data, depth, maxDepth)]
  }
  if (Array.isArray(data)) {
    return data.map((item, idx) => makeEntry(null, idx, item, depth, maxDepth))
  }
  if (typeof data === 'object') {
    return Object.entries(data).map(([key, value]) => makeEntry(key, null, value, depth, maxDepth))
  }
  return [makeEntry(null, null, data, depth, maxDepth)]
}

function makeEntry(key: string | null, index: number | null, value: any, depth: number, maxDepth: number): Entry {
  const path = `${depth}-${key ?? index ?? '__root__'}`
  const isArray = Array.isArray(value)
  const isExpandable = value !== null && typeof value === 'object'

  // 读取持久化的展开状态，不存在时根据深度决定
  if (!expandedStates.has(path)) {
    expandedStates.set(path, isExpandable && (maxDepth === -1 || depth < maxDepth))
  }

  const entry = reactive({
    key,
    index,
    value,
    type: getType(value),
    displayValue: getDisplayValue(value),
    isExpandable,
    isExpanded: expandedStates.get(path) ?? false,
    isArray,
    size: isExpandable ? Object.keys(value).length : 0,
    toggle: () => {
      entry.isExpanded = !entry.isExpanded
      expandedStates.set(path, entry.isExpanded)
    },
  })

  return entry
}

function getType(value: any): string {
  if (value === null || value === undefined) return 'null'
  if (Array.isArray(value)) return 'array'
  return typeof value
}

function getDisplayValue(value: any): string {
  if (value === null) return 'null'
  if (typeof value === 'string') return value
  if (typeof value === 'number') return String(value)
  if (typeof value === 'boolean') return String(value)
  if (Array.isArray(value)) return `Array(${value.length})`
  if (typeof value === 'object') return `{${Object.keys(value).length}}`
  return String(value)
}

function copyValue(value: string) {
  navigator.clipboard.writeText(value).catch(() => {
    // 静默处理
  })
}
</script>

<style scoped>
.json-tree {
  font-family: 'SF Mono', 'Fira Code', 'Cascadia Code', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.6;
  color: #1a1a2e;
  user-select: text;
}

.json-tree__node {
  display: flex;
  align-items: flex-start;
  flex-wrap: nowrap;
  min-height: 22px;
  transition: background-color 0.12s;
  border-radius: 2px;
}

.json-tree__node:hover {
  background-color: rgba(0, 0, 0, 0.025);
}

.json-tree__toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  min-width: 16px;
  height: 20px;
  cursor: pointer;
  color: #999;
  border-radius: 3px;
  transition: color 0.15s, background-color 0.15s;
  margin-right: 2px;
}

.json-tree__toggle:hover {
  color: #333;
  background-color: rgba(0, 0, 0, 0.06);
}

.json-tree__toggle svg {
  transition: transform 0.15s;
}

.json-tree__toggle--expanded svg {
  transform: rotate(90deg);
}

.json-tree__toggle-placeholder {
  display: inline-block;
  width: 16px;
  min-width: 16px;
  margin-right: 2px;
}

.json-tree__key {
  color: #881391;
  flex-shrink: 0;
  margin-right: 2px;
}

.json-tree__colon {
  color: #999;
  margin-right: 2px;
}

.json-tree__index {
  color: #999;
  font-size: 11px;
  flex-shrink: 0;
  margin-right: 2px;
}

.json-tree__value {
  cursor: pointer;
  word-break: break-all;
  padding: 0 3px;
  border-radius: 2px;
  transition: background-color 0.12s;
}

.json-tree__value:hover {
  background-color: rgba(0, 0, 0, 0.06);
}

.json-tree__value--string {
  color: #0a7c3d;
}

.json-tree__value--number {
  color: #d9734f;
}

.json-tree__value--boolean {
  color: #2b6cb0;
}

.json-tree__value--null {
  color: #999;
  font-style: italic;
}

.json-tree__bracket {
  color: #666;
  font-weight: 500;
  margin-right: 4px;
}

.json-tree__count {
  color: #999;
  font-size: 11px;
  margin-right: 4px;
}

.json-tree__collapsed {
  color: #666;
  cursor: pointer;
  font-size: 12px;
  padding: 1px 6px;
  border-radius: 3px;
  border: 1px solid transparent;
  transition: background-color 0.15s, border-color 0.15s;
}

.json-tree__collapsed:hover {
  background-color: rgba(0, 0, 0, 0.04);
  border-color: #ddd;
  color: #333;
}

.json-tree__comma {
  color: #ccc;
  flex-shrink: 0;
}
</style>