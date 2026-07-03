<script setup lang="ts">
import { RouterView, useRoute, useRouter } from 'vue-router'
import { useProjectStore } from '@/modules/pm/stores'

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()

const projectId = computed(() => Number(route.params.projectId))

onMounted(async () => {
  await projectStore.loadProjects()
  if (projectId.value) {
    await projectStore.selectProject(projectId.value)
  }
})

const menuOptions = computed(() => {
  const id = projectId.value
  if (!id) return []
  return [
    { label: '事项列表', key: `/pm/projects/${id}/items` },
    { label: '看板', key: `/pm/projects/${id}/board` },
    { label: '字段配置', key: `/pm/projects/${id}/fields` },
  ]
})

function onMenuSelect(key: string) {
  router.push(key)
}
</script>

<template>
  <n-layout has-sider style="min-height: calc(100vh - 56px)">
    <n-layout-sider bordered width="220">
      <n-menu
        :value="route.path"
        :options="menuOptions"
        @update:value="onMenuSelect"
      />
      <div style="padding: 12px">
        <n-button text @click="router.push('/pm/projects')">← 返回项目列表</n-button>
      </div>
    </n-layout-sider>
    <n-layout-content content-style="padding: 20px">
      <n-page-header v-if="projectStore.currentProject" :title="projectStore.currentProject.name" />
      <RouterView />
    </n-layout-content>
  </n-layout>
</template>
