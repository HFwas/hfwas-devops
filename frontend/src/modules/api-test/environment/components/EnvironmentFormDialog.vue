<template>
  <n-modal :show="show" @update:show="handleClose" :title="title" :mask-closable="false" preset="dialog" style="width: 700px;">
    <n-spin :show="saving">
      <n-form ref="formRef" :model="form" :rules="rules" label-placement="top" size="small">
        <n-form-item label="环境名称" path="name">
          <n-input v-model:value="form.name" placeholder="请输入环境名称" />
        </n-form-item>
        <n-form-item label="环境描述" path="description">
          <n-input v-model:value="form.description" type="textarea" :rows="2" placeholder="请输入环境描述" />
        </n-form-item>
        <n-form-item label="排序序号">
          <n-input-number v-model:value="form.sortOrder" :min="0" style="width: 120px;" />
        </n-form-item>

        <!-- 变量列表 -->
        <n-form-item label="环境变量">
          <variable-list v-model:variables="form.variables" />
        </n-form-item>
      </n-form>
    </n-spin>

    <template #action>
      <n-button @click="handleClose">取消</n-button>
      <n-button type="primary" :loading="saving" @click="handleSave">保存</n-button>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { useEnvironmentStore } from '@/modules/api-test/environment/stores/environment'
import { useAuthStore } from '@/modules/user/stores/auth'
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
const formRef = ref<any>(null)

function handleClose() {
  emit('update:show', false)
}

const title = computed(() => props.environmentId ? '编辑环境' : '新建环境')

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

const rules = {
  name: [{ required: true, message: '请输入环境名称', trigger: 'blur' }],
}

watch(() => props.show, async (val) => {
  if (val && props.environmentId) {
    try {
      const detail = await store.loadDetail(props.environmentId)
      if (detail) {
        form.value = {
          name: detail.name,
          description: detail.description || '',
          sortOrder: detail.sortOrder || 0,
          variables: (detail.variables || []).map(v => ({
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
      message.error(e.message || '加载失败')
    }
  } else if (val) {
    form.value = { name: '', description: '', sortOrder: 0, variables: [] }
  }
})

async function handleSave() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }

  saving.value = true
  try {
    const previousVariables = props.environmentId && store.currentDetail?.id === props.environmentId
      ? store.currentDetail.variables
      : []
    const payload = {
      ...form.value,
      variables: resolveVariablesForUpdate(form.value.variables, previousVariables),
    }
    if (props.environmentId) {
      await store.update(props.environmentId, payload, userId.value)
      message.success('更新成功')
    } else {
      await store.create(payload, props.projectId, userId.value)
      message.success('创建成功')
    }
    emit('saved')
  } catch (e: any) {
    message.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>