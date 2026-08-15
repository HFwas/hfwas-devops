<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useMessage } from 'naive-ui'
import { useAuthStore } from '@/modules/user/stores/auth'
import { useEnvironmentStore } from '@/modules/api-test/environment/stores/environment'
import VariableList from '@/modules/api-test/environment/components/VariableList.vue'
import EnvironmentFormDialog from '@/modules/api-test/environment/components/EnvironmentFormDialog.vue'
import type { EnvironmentVariableDTO } from '@/modules/api-test/environment/types/environment'

const PROJECT_ID = 1

const message = useMessage()
const authStore = useAuthStore()
const envStore = useEnvironmentStore()
const { allList, currentDetail, selectedEnvironmentId } = storeToRefs(envStore)

const userId = computed(() => Number(authStore.user?.id) || 0)
const saving = ref(false)
const showDialog = ref(false)
const editingId = ref<number | null>(null)
const variables = ref<EnvironmentVariableDTO[]>([])

onMounted(async () => {
  try {
    await envStore.loadAll(PROJECT_ID)
  } catch (e: any) {
    message.error(e?.message || '加载环境失败')
  }
})

watch(selectedEnvironmentId, async (id) => {
  if (id == null) {
    variables.value = []
    return
  }
  try {
    await envStore.loadDetail(id)
    syncVariablesFromDetail()
  } catch (e: any) {
    message.error(e?.message || '加载环境详情失败')
  }
}, { immediate: true })

function syncVariablesFromDetail() {
  const detail = currentDetail.value
  variables.value = (detail?.variables || []).map((v) => ({
    id: v.id,
    name: v.name,
    value: v.isSecret ? '' : v.value,
    description: v.description || '',
    isSecret: v.isSecret || false,
    sortOrder: v.sortOrder || 0,
  }))
}

function onSelect(id: number) {
  envStore.selectEnvironment(id)
}

async function onSave() {
  const id = selectedEnvironmentId.value
  const detail = currentDetail.value
  if (id == null || !detail) return
  saving.value = true
  try {
    await envStore.update(id, {
      name: detail.name,
      description: detail.description,
      sortOrder: detail.sortOrder,
      variables: variables.value,
    }, userId.value)
    message.success('保存成功')
    await envStore.loadAll(PROJECT_ID)
  } catch (e: any) {
    message.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

function openCreate() {
  editingId.value = null
  showDialog.value = true
}

async function onSaved() {
  showDialog.value = false
  await envStore.loadAll(PROJECT_ID)
}
</script>

<template>
  <div class="environment-panel">
    <div class="environment-panel__toolbar">
      <span class="environment-panel__title">环境</span>
      <n-button size="tiny" type="primary" data-testid="env-create" @click="openCreate">
        新建
      </n-button>
    </div>

    <div class="environment-panel__list">
      <button
        v-for="env in allList"
        :key="env.id"
        type="button"
        class="environment-panel__item"
        :class="{ 'is-active': env.id === selectedEnvironmentId }"
        :data-testid="`env-item-${env.id}`"
        @click="onSelect(env.id)"
      >
        {{ env.name }}
      </button>
      <n-empty v-if="!allList.length" description="暂无环境" size="small" />
    </div>

    <div v-if="selectedEnvironmentId && currentDetail" class="environment-panel__vars">
      <div class="environment-panel__vars-header">
        <span class="environment-panel__vars-name">{{ currentDetail.name }}</span>
        <n-button
          size="tiny"
          type="primary"
          data-testid="env-save"
          :loading="saving"
          @click="onSave"
        >
          保存
        </n-button>
      </div>
      <VariableList v-model:variables="variables" />
    </div>
    <n-empty v-else description="选择环境查看变量" size="small" />

    <EnvironmentFormDialog
      :show="showDialog"
      :environment-id="editingId"
      :project-id="PROJECT_ID"
      @update:show="showDialog = $event"
      @saved="onSaved"
    />
  </div>
</template>

<style scoped>
.environment-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  height: 100%;
  overflow: hidden;
  color: inherit;
  background: var(--wb-card-bg, #fff);
}

.environment-panel__toolbar {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--wb-border, #e5e7eb);
}

.environment-panel__title {
  font-size: 13px;
  font-weight: 600;
}

.environment-panel__list {
  flex-shrink: 0;
  max-height: 40%;
  overflow: auto;
  padding: 4px 0;
}

.environment-panel__item {
  display: flex;
  width: 100%;
  padding: 6px 12px;
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font-size: 13px;
  text-align: left;
}

.environment-panel__item:hover {
  background: var(--wb-chip-bg, #f8fafc);
}

.environment-panel__item.is-active {
  background: var(--api-test-accent-soft, rgba(64, 152, 252, 0.12));
  color: var(--api-test-accent-strong, #2d80e6);
  font-weight: 600;
}

html.dark .environment-panel__item.is-active {
  background: var(--api-test-accent-soft, rgba(94, 176, 255, 0.2));
  color: var(--api-test-accent-strong, #82c4ff);
}

.environment-panel__vars {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  overflow: auto;
  padding: 8px 12px 12px;
  border-top: 1px solid var(--wb-border, #e5e7eb);
}

.environment-panel__vars-header {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.environment-panel__vars-name {
  overflow: hidden;
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
