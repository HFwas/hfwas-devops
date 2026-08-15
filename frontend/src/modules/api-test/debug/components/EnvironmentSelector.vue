<template>
  <div class="environment-selector">
    <n-select
      :value="environmentId"
      :options="environmentOptions"
      placeholder="选择环境（变量自动渲染）"
      clearable
      size="small"
      style="width: 280px;"
      @update:value="onChange"
    />
    <n-button
      size="small"
      quaternary
      data-testid="env-create-btn"
      @click="openCreateModal"
    >
      新建环境
    </n-button>
    <n-button
      v-if="environmentId"
      size="small"
      quaternary
      data-testid="env-edit-btn"
      @click="handleEdit"
    >
      编辑变量
    </n-button>

    <n-modal
      v-model:show="createModalShow"
      preset="dialog"
      title="新建环境"
      positive-text="创建"
      negative-text="取消"
      :loading="creating"
      @positive-click="handleCreate"
    >
      <n-input
        v-model:value="createName"
        placeholder="请输入环境名称"
        data-testid="env-create-name"
        @keyup.enter="handleCreate"
      />
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMessage } from 'naive-ui'
import { useAuthStore } from '@/modules/user/stores/auth'
import { useEnvironmentStore } from '@/modules/api-test/environment/stores/environment'

const props = defineProps<{
  projectId: number
  environmentId: number | null
}>()

const emit = defineEmits<{
  'update:environmentId': [value: number | null]
  create: []
  edit: [id: number]
}>()

const message = useMessage()
const authStore = useAuthStore()
const environmentStore = useEnvironmentStore()

const userId = computed(() => Number(authStore.user?.id) || 0)
const createModalShow = ref(false)
const createName = ref('')
const creating = ref(false)

const environmentOptions = computed(() => {
  return environmentStore.allList.map((env) => ({
    label: env.name,
    value: env.id,
  }))
})

function onChange(value: number | null) {
  emit('update:environmentId', value)
}

function handleEdit() {
  if (props.environmentId != null) {
    emit('edit', props.environmentId)
  }
}

function openCreateModal() {
  createName.value = ''
  createModalShow.value = true
  emit('create')
}

async function handleCreate() {
  const name = createName.value.trim()
  if (!name) {
    message.error('请输入环境名称')
    return false
  }
  creating.value = true
  try {
    const detail = await environmentStore.create({ name }, props.projectId, userId.value)
    await environmentStore.loadAll(props.projectId)
    emit('update:environmentId', detail.id)
    createModalShow.value = false
    emit('edit', detail.id)
    message.success('创建成功')
    return true
  } catch (e: any) {
    message.error(e?.message || '创建失败')
    return false
  } finally {
    creating.value = false
  }
}
</script>

<style scoped>
.environment-selector {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
