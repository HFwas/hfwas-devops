<template>
  <n-modal v-model:show="show" :mask-closable="false" preset="card" title="从镜像部署" style="width: 560px">
    <n-form ref="formRef" :model="form" :rules="rules" label-placement="left" label-width="120">
      <n-form-item label="镜像">
        <n-input :value="image" disabled />
      </n-form-item>
      <n-form-item label="目标集群" path="clusterId">
        <n-select
          v-model:value="form.clusterId"
          :options="clusterOptions"
          :loading="loadingClusters"
          placeholder="请选择集群"
          @update:value="onClusterChange"
        />
      </n-form-item>
      <n-form-item label="Namespace" path="namespace">
        <n-select
          v-model:value="form.namespace"
          :options="namespaceOptions"
          :loading="loadingNamespaces"
          placeholder="请选择命名空间"
        />
      </n-form-item>
      <n-form-item label="服务名称" path="name">
        <n-input v-model:value="form.name" placeholder="Deployment 名称" />
      </n-form-item>
      <n-form-item label="副本数">
        <n-input-number v-model:value="form.replicas" :min="1" :max="100" />
      </n-form-item>
      <n-form-item label="容器端口">
        <n-input-number v-model:value="form.containerPort" :min="0" :max="65535" placeholder="留空不创建 Service" clearable />
      </n-form-item>
      <n-form-item label="拉取凭据">
        <n-space vertical>
          <n-switch v-model:value="form.createPullSecret" />
          <n-input
            v-if="form.createPullSecret"
            v-model:value="form.pullSecretName"
            placeholder="Secret 名称（可选）"
            size="small"
          />
        </n-space>
      </n-form-item>

      <n-collapse>
        <n-collapse-item title="环境变量（可选）" name="env">
          <div v-for="(item, idx) in form.env" :key="idx" style="display: flex; gap: 8px; margin-bottom: 8px">
            <n-input v-model:value="item.name" placeholder="变量名" size="small" style="flex: 1" />
            <n-input v-model:value="item.value" placeholder="值" size="small" style="flex: 1" />
            <n-button size="small" @click="form.env.splice(idx, 1)">✕</n-button>
          </div>
          <n-button size="small" @click="form.env.push({ name: '', value: '' })">+ 添加</n-button>
        </n-collapse-item>
        <n-collapse-item title="资源限制（可选）" name="resources">
          <n-space>
            <n-input v-model:value="form.resources.cpu" placeholder="如 500m" size="small">
              <template #prefix>CPU</template>
            </n-input>
            <n-input v-model:value="form.resources.memory" placeholder="如 512Mi" size="small">
              <template #prefix>内存</template>
            </n-input>
          </n-space>
        </n-collapse-item>
      </n-collapse>
    </n-form>

    <template #footer>
      <div style="display: flex; justify-content: flex-end; gap: 12px">
        <n-button @click="show = false">取消</n-button>
        <n-button type="primary" :loading="deploying" @click="handleDeploy">确认部署</n-button>
      </div>
    </template>
  </n-modal>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useMessage } from 'naive-ui'
import type { FormInst, FormRules, SelectOption } from 'naive-ui'
import type { DeployFromImageRequest } from '../../types/registry'
import { clusterApi } from '../../api/cluster'
import { namespaceApi } from '../../api/namespace'
import { deployApi } from '../../api/deploy'

interface DeployForm {
  clusterId: string
  namespace: string
  name: string
  image: string
  replicas: number
  containerPort: number | null
  createPullSecret: boolean
  pullSecretName: string
  env: Array<{ name: string; value: string }>
  resources: { cpu: string; memory: string }
}

const props = defineProps<{
  show: boolean
  registryId: string
  image: string
}>()

const emit = defineEmits<{
  'update:show': [value: boolean]
  deployed: []
}>()

const router = useRouter()
const message = useMessage()
const formRef = ref<FormInst | null>(null)
const deploying = ref(false)
const loadingClusters = ref(false)
const loadingNamespaces = ref(false)

const clusters = ref<Array<{ id: string; name: string; alias: string }>>([])
const namespaces = ref<Array<{ name: string }>>([])

const form = reactive<DeployForm>({
  clusterId: '',
  namespace: '',
  name: '',
  image: props.image,
  replicas: 1,
  containerPort: null,
  createPullSecret: true,
  pullSecretName: '',
  env: [],
  resources: {
    cpu: '',
    memory: '',
  },
})

const rules: FormRules = {
  clusterId: [{ required: true, message: '请选择目标集群', trigger: 'blur' }],
  namespace: [{ required: true, message: '请选择 Namespace', trigger: 'blur' }],
  name: [{ required: true, message: '请输入服务名称', trigger: 'blur' }],
}

const show = computed({
  get: () => props.show,
  set: (val: boolean) => emit('update:show', val),
})

const clusterOptions = computed<SelectOption[]>(() =>
  clusters.value.map(c => ({
    label: c.alias || c.name,
    value: c.id,
  }))
)

const namespaceOptions = computed<SelectOption[]>(() =>
  namespaces.value.map(n => ({
    label: n.name,
    value: n.name,
  }))
)

watch(() => props.show, (val) => {
  if (val) {
    form.image = props.image
    loadClusters()
  }
})

async function loadClusters() {
  loadingClusters.value = true
  try {
    const result = await clusterApi.page({ pageNo: 1, pageSize: 100 })
    clusters.value = (result.records ?? []).map(c => ({
      id: String(c.id),
      name: c.name,
      alias: c.alias || c.name,
    }))
  } catch {
    clusters.value = []
  } finally {
    loadingClusters.value = false
  }
}

async function onClusterChange() {
  form.namespace = ''
  if (!form.clusterId) return
  loadingNamespaces.value = true
  try {
    const list = await namespaceApi.list(form.clusterId)
    namespaces.value = (list ?? []).map(n => ({ name: n.name }))
  } catch {
    namespaces.value = []
  } finally {
    loadingNamespaces.value = false
  }
}

async function handleDeploy() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  deploying.value = true
  try {
    const payload: DeployFromImageRequest = {
      clusterId: form.clusterId,
      namespace: form.namespace,
      name: form.name,
      image: form.image,
      replicas: form.replicas,
      createPullSecret: form.createPullSecret,
      env: form.env.filter(e => e.name),
    }
    if (form.containerPort && form.containerPort > 0) {
      payload.containerPort = form.containerPort
    }
    if (form.pullSecretName) {
      payload.pullSecretName = form.pullSecretName
    }
    if (form.resources.cpu || form.resources.memory) {
      payload.resources = {
        cpu: form.resources.cpu || undefined,
        memory: form.resources.memory || undefined,
      }
    }

    const result = await deployApi.deployFromImage(props.registryId, payload)
    message.success(`部署成功！Deployment: ${result.deploymentName}`)
    show.value = false
    emit('deployed')
    router.push(`/container/clusters/${result.clusterId}/deployments/${result.namespace}/${result.deploymentName}`)
  } catch (e: any) {
    message.error(e.message || '部署失败')
  } finally {
    deploying.value = false
  }
}
</script>