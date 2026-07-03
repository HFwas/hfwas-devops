<script setup lang="ts">
import { pmProjectApi } from '@/modules/pm/api'
import type { PmProject } from '@/modules/pm/types'
import { useMessage } from 'naive-ui'

const router = useRouter()
const message = useMessage()
const loading = ref(false)
const keyword = ref('')
const projects = ref<PmProject[]>([])
const showModal = ref(false)
const form = ref<PmProject>({ code: '', name: '', description: '' })

async function load() {
  loading.value = true
  try {
    const page = await pmProjectApi.page({ pageNo: 1, pageSize: 100, keyword: keyword.value })
    projects.value = page.records
  } finally {
    loading.value = false
  }
}

async function save() {
  await pmProjectApi.save(form.value)
  message.success('项目已保存')
  showModal.value = false
  form.value = { code: '', name: '', description: '' }
  await load()
}

function openProject(id: number) {
  router.push(`/pm/projects/${id}/items/task`)
}

onMounted(load)
</script>

<template>
  <n-space vertical size="large">
    <n-space justify="space-between">
      <n-input v-model:value="keyword" placeholder="搜索项目" style="width: 260px" @keyup.enter="load" />
      <n-space>
        <n-button @click="load">查询</n-button>
        <n-button type="primary" @click="showModal = true">新建项目</n-button>
      </n-space>
    </n-space>
    <n-spin :show="loading">
      <n-grid :cols="3" :x-gap="16" :y-gap="16">
        <n-gi v-for="p in projects" :key="p.id">
          <n-card hoverable @click="openProject(p.id!)">
            <n-space vertical>
              <n-text strong>{{ p.name }}</n-text>
              <n-text depth="3">{{ p.code }}</n-text>
              <n-text depth="3">{{ p.description || '暂无描述' }}</n-text>
            </n-space>
          </n-card>
        </n-gi>
      </n-grid>
    </n-spin>
    <n-modal v-model:show="showModal" preset="card" title="新建项目" style="width: 480px">
      <n-form label-placement="top">
        <n-form-item label="项目编码"><n-input v-model:value="form.code" /></n-form-item>
        <n-form-item label="项目名称"><n-input v-model:value="form.name" /></n-form-item>
        <n-form-item label="描述"><n-input v-model:value="form.description" type="textarea" /></n-form-item>
        <n-button type="primary" @click="save">保存</n-button>
      </n-form>
    </n-modal>
  </n-space>
</template>
