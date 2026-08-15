<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { useAuthStore } from '@/modules/user/stores/auth'
import { useEnvironmentStore } from '@/modules/api-test/environment/stores/environment'
import VariableList from '@/modules/api-test/environment/components/VariableList.vue'
import type { EnvironmentVariableDTO } from '@/modules/api-test/environment/types/environment'
import { resolveVariablesForUpdate } from '@/modules/api-test/environment/utils/variablesForUpdate'

const props = defineProps<{
  show: boolean
  environmentId: number | null
  projectId: number
}>()

const emit = defineEmits<{
  'update:show': [value: boolean]
  saved: []
}>()

const message = useMessage()
const store = useEnvironmentStore()
const authStore = useAuthStore()

const userId = computed(() => Number(authStore.user?.id) || 0)
const saving = ref(false)
const loading = ref(false)

const form = ref<{
  name: string
  description: string
  sortOrder: number
  variables: EnvironmentVariableDTO[]
}>({
  name: '',
  description: '',
  sortOrder: 0,
  variables: [],
})

const title = computed(() => (props.environmentId ? '编辑环境变量' : '新建环境'))

function onUpdateShow(value: boolean) {
  emit('update:show', value)
}

watch(
  () => [props.show, props.environmentId] as const,
  async ([show, environmentId]) => {
    if (!show) return
    if (environmentId == null) {
      form.value = { name: '', description: '', sortOrder: 0, variables: [] }
      return
    }
    loading.value = true
    try {
      const detail = await store.loadDetail(environmentId)
      if (detail) {
        form.value = {
          name: detail.name,
          description: detail.description || '',
          sortOrder: detail.sortOrder || 0,
          variables: (detail.variables || []).map((v) => ({
            id: v.id,
            name: v.name,
            value: v.isSecret ? '' : v.value,
            description: v.description || '',
            isSecret: v.isSecret || false,
            sortOrder: v.sortOrder || 0,
          })),
        }
      }
    } catch (e: any) {
      message.error(e?.message || '加载失败')
    } finally {
      loading.value = false
    }
  },
  { immediate: true },
)

async function handleSave() {
  if (!form.value.name.trim()) {
    message.error('请输入环境名称')
    return
  }
  if (props.environmentId == null) {
    message.error('环境未指定')
    return
  }

  saving.value = true
  try {
    const previousVariables =
      store.currentDetail?.id === props.environmentId
        ? store.currentDetail.variables
        : []
    await store.update(
      props.environmentId,
      {
        name: form.value.name,
        description: form.value.description,
        sortOrder: form.value.sortOrder,
        variables: resolveVariablesForUpdate(form.value.variables, previousVariables),
      },
      userId.value,
    )
    message.success('保存成功')
    emit('saved')
  } catch (e: any) {
    message.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <n-drawer :show="show" :width="560" placement="right" @update:show="onUpdateShow">
    <n-drawer-content :title="title" closable>
      <n-spin :show="loading || saving">
        <n-form label-placement="top" size="small">
          <n-form-item label="环境名称">
            <n-input v-model:value="form.name" placeholder="请输入环境名称" />
          </n-form-item>
          <n-form-item label="环境变量">
            <VariableList v-model:variables="form.variables" />
          </n-form-item>
        </n-form>
      </n-spin>

      <template #footer>
        <div class="env-drawer__footer">
          <n-button @click="onUpdateShow(false)">取消</n-button>
          <n-button
            type="primary"
            data-testid="env-drawer-save"
            :loading="saving"
            @click="handleSave"
          >
            保存
          </n-button>
        </div>
      </template>
    </n-drawer-content>
  </n-drawer>
</template>

<style scoped>
.env-drawer__footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
