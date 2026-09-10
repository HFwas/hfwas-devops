<template>
  <n-modal v-model:show="show" :mask-closable="false" preset="card" :title="isEdit ? '编辑镜像仓库' : '注册镜像仓库'" style="width: 600px">
    <n-form ref="formRef" :model="form" :rules="rules" label-placement="left" label-width="100">
      <n-form-item label="标识名称" path="name">
        <n-input v-model:value="form.name" placeholder="唯一标识，如 production-harbor" :disabled="isEdit" />
      </n-form-item>
      <n-form-item label="显示别名">
        <n-input v-model:value="form.alias" placeholder="如 生产 Harbor" />
      </n-form-item>
      <n-form-item label="类型" path="type">
        <n-select v-model:value="form.type" :options="typeOptions" />
      </n-form-item>
      <n-form-item label="仓库地址" path="url">
        <n-input v-model:value="form.url" placeholder="如 http://harbor.example.com:80" />
      </n-form-item>
      <n-form-item label="跳过 TLS">
        <n-switch v-model:value="form.insecure" />
      </n-form-item>
      <n-form-item label="用户名">
        <n-input v-model:value="form.credentialUsername" placeholder="admin 或 robot$project+token" />
      </n-form-item>
      <n-form-item label="密码">
        <n-input
          v-model:value="form.credentialPassword"
          type="password"
          show-password-on="click"
          placeholder="Harbor 密码或 Robot token"
        />
      </n-form-item>
    </n-form>

    <template #footer>
      <div style="display: flex; justify-content: flex-end; gap: 12px">
        <n-button @click="show = false">取消</n-button>
        <n-button type="primary" :loading="submitting" @click="handleSubmit">{{ isEdit ? '保存修改' : '注册并测试连接' }}</n-button>
      </div>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import type { FormInst, FormRules } from 'naive-ui'
import type { RegistrySaveDTO, RegistryUpdateDTO, RegistryVO } from '../../types/registry'
import { registryApi } from '../../api/registry'

const props = defineProps<{
  show: boolean
  registry?: RegistryVO | null
}>()
const emit = defineEmits<{
  'update:show': [value: boolean]
  created: []
  updated: []
}>()

const message = useMessage()
const formRef = ref<FormInst | null>(null)
const submitting = ref(false)

const isEdit = computed(() => !!props.registry)

const form = reactive<RegistrySaveDTO>({
  name: '',
  alias: '',
  type: 'harbor',
  url: '',
  insecure: false,
  credentialUsername: '',
  credentialPassword: '',
})

const typeOptions = [
  { label: 'Harbor', value: 'harbor' },
  { label: 'Docker Registry V2', value: 'registry_v2' },
]

const rules = computed<FormRules>(() => ({
  name: isEdit.value ? {} : { required: true, message: '请输入标识名称', trigger: 'blur' },
  type: { required: true, message: '请选择仓库类型', trigger: 'blur' },
  url: { required: true, message: '请输入仓库地址', trigger: 'blur' },
}))

const show = computed({
  get: () => props.show,
  set: (val: boolean) => emit('update:show', val),
})

// Populate form when editing
watch(() => props.registry, (reg) => {
  if (reg) {
    form.name = reg.name
    form.alias = reg.alias || ''
    form.type = reg.type
    form.url = reg.url
    form.insecure = reg.insecure
    form.credentialUsername = reg.credentialUsername || ''
    form.credentialPassword = ''
  } else {
    form.name = ''
    form.alias = ''
    form.type = 'harbor'
    form.url = ''
    form.insecure = false
    form.credentialUsername = ''
    form.credentialPassword = ''
  }
}, { immediate: true })

async function handleSubmit() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    if (isEdit.value && props.registry) {
      const updateData: RegistryUpdateDTO = {
        alias: form.alias || undefined,
        url: form.url,
        insecure: form.insecure,
        credentialUsername: form.credentialUsername || undefined,
        credentialPassword: form.credentialPassword || undefined,
        labels: form.labels || undefined,
      }
      await registryApi.update(props.registry.id, updateData)
      message.success('更新成功')
      show.value = false
      emit('updated')
    } else {
      await registryApi.create(form)
      message.success('注册成功')
      show.value = false
      emit('created')
    }
  } catch (e: any) {
    message.error(e.message || (isEdit.value ? '更新失败' : '注册失败'))
  } finally {
    submitting.value = false
  }
}
</script>