<script setup lang="ts">
import { Check, ChevronDown, LayoutGrid } from '@lucide/vue'
import {
  CONSOLE_PRODUCTS,
  groupProducts,
  resolveActiveProduct,
  type ConsoleProduct,
} from '@/shared/console/products'

// 扩展点：产品清单来自 products.ts，新增产品无需改动本组件
const router = useRouter()
const route = useRoute()
const show = ref(false)
const groups = groupProducts(CONSOLE_PRODUCTS)

const current = computed(() => resolveActiveProduct(route.path))
const triggerLabel = computed(() => current.value?.name ?? '选择产品')

function select(product: ConsoleProduct) {
  if (product.comingSoon) return
  show.value = false
  void router.push(product.path)
}
</script>

<template>
  <n-popover v-model:show="show" trigger="click" placement="bottom-start" :show-arrow="false" raw>
    <template #trigger>
      <button type="button" class="ps-trigger">
        <LayoutGrid :size="15" />
        <span class="ps-trigger-name">{{ triggerLabel }}</span>
        <ChevronDown :size="14" class="ps-trigger-caret" />
      </button>
    </template>

    <div class="ps-panel">
      <div v-for="group in groups" :key="group.group" class="ps-group">
        <div class="ps-group-title">{{ group.group }}</div>
        <div class="ps-group-items">
          <button
            v-for="product in group.items"
            :key="product.key"
            type="button"
            class="ps-item"
            :class="{ 'is-disabled': product.comingSoon }"
            @click="select(product)"
          >
            <span class="ps-item-icon">
              <component :is="product.icon" :size="16" />
            </span>
            <span class="ps-item-body">
              <span class="ps-item-name">
                {{ product.name }}
                <n-tag v-if="product.comingSoon" size="tiny" :bordered="false">即将上线</n-tag>
              </span>
              <span class="ps-item-desc">{{ product.description }}</span>
            </span>
            <Check v-if="current && product.key === current.key" :size="15" class="ps-item-check" />
          </button>
        </div>
      </div>
    </div>
  </n-popover>
</template>

<style scoped>
.ps-trigger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 10px;
  border: 1px solid var(--wb-border, #e5e7eb);
  border-radius: 8px;
  background: var(--wb-chip-bg, #f8fafc);
  font: inherit;
  font-size: 13px;
  color: inherit;
  cursor: pointer;
  transition: border-color 0.2s, background-color 0.2s;
}

.ps-trigger:hover {
  border-color: #4098fc;
  color: #2d80e6;
}

.ps-trigger-name {
  font-weight: 500;
}

.ps-trigger-caret {
  color: var(--wb-muted, #6b7280);
}

.ps-panel {
  width: 420px;
  max-height: 420px;
  overflow-y: auto;
  padding: 10px;
  border: 1px solid var(--wb-border, #e5e7eb);
  border-radius: 10px;
  background: var(--wb-card-bg, #fff);
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.12);
}

.ps-group + .ps-group {
  margin-top: 10px;
}

.ps-group-title {
  padding: 0 6px 6px;
  font-size: 12px;
  color: var(--wb-muted, #6b7280);
}

.ps-group-items {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
}

.ps-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 8px;
  border: none;
  border-radius: 8px;
  background: transparent;
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.ps-item:hover {
  background: rgba(64, 152, 252, 0.08);
}

.ps-item.is-disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.ps-item.is-disabled:hover {
  background: transparent;
}

.ps-item-icon {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: #eff6ff;
  color: #2563eb;
}

.ps-item-body {
  min-width: 0;
}

.ps-item-name {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
}

.ps-item-desc {
  display: block;
  margin-top: 2px;
  overflow: hidden;
  font-size: 12px;
  color: var(--wb-muted, #6b7280);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ps-item-check {
  flex-shrink: 0;
  margin-top: 4px;
  color: #2d80e6;
}
</style>
