<script setup lang="ts">
import { RouterView, useRoute, useRouter } from 'vue-router'
import { useProjectStore } from '@/modules/pm/stores'
import { TYPE_META, WORK_ITEM_TYPE_CODES, resolveWorkItemTypeCode } from '@/modules/pm/types'

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

const currentTypeCode = computed(() => resolveWorkItemTypeCode(route.path))

const menuRouteMap = computed(() => {
  const id = projectId.value
  return {
    [`settings-types:${id}`]: `/pm/projects/${id}/settings/types`,
    [`settings-fields:${id}`]: `/pm/projects/${id}/settings/fields`,
  } as Record<string, string>
})

const menuOptions = computed(() => {
  const id = projectId.value
  if (!id) return []

  const typeItems = WORK_ITEM_TYPE_CODES.map((code) => ({
    label: TYPE_META[code].label,
    key: `/pm/projects/${id}/items/${code}`,
  }))

  return [
    ...typeItems,
    { type: 'divider' as const, key: 'divider-1' },
    { label: '看板', key: `/pm/projects/${id}/board/${currentTypeCode.value}` },
    {
      label: '字段配置',
      key: 'field-settings',
      children: [
        { label: '事项配置', key: `settings-types:${id}` },
        { label: '字段', key: `settings-fields:${id}` },
      ],
    },
  ]
})

const expandedKeys = ref<string[]>([])

watch(
  () => route.path,
  (path) => {
    if (path.includes('/settings/') && !expandedKeys.value.includes('field-settings')) {
      expandedKeys.value = [...expandedKeys.value, 'field-settings']
    }
  },
  { immediate: true },
)

function onExpandedKeysUpdate(keys: string[]) {
  expandedKeys.value = keys
}

const activeMenuKey = computed(() => {
  const path = route.path
  const id = projectId.value
  if (path.match(/\/items\/(requirement|task|bug|test_case)$/)) return path
  if (path.match(/\/board\/(requirement|task|bug|test_case)$/)) return path
  if (path.endsWith('/settings/types') || path.match(/\/settings\/types\/(requirement|task|bug|test_case)$/)) {
    return `settings-types:${id}`
  }
  if (path.endsWith('/settings/fields')) return `settings-fields:${id}`
  return null
})

function onMenuSelect(key: string) {
  const mapped = menuRouteMap.value[key]
  if (mapped) {
    router.push(mapped)
    return
  }
  if (key.startsWith('/')) {
    router.push(key)
  }
}
</script>

<template>
  <n-layout has-sider style="min-height: calc(100vh - 56px)">
    <n-layout-sider bordered width="220">
      <n-menu
        :value="activeMenuKey"
        :expanded-keys="expandedKeys"
        :options="menuOptions"
        @update:value="onMenuSelect"
        @update:expanded-keys="onExpandedKeysUpdate"
      />
      <div style="padding: 12px">
        <n-button text @click="router.push('/pm/projects')">← 返回项目列表</n-button>
      </div>
    </n-layout-sider>
    <n-layout-content content-style="padding: 20px">
      <n-page-header v-if="projectStore.currentProject" :title="projectStore.currentProject.name" />
      <RouterView :key="route.path" />
    </n-layout-content>
  </n-layout>
</template>
