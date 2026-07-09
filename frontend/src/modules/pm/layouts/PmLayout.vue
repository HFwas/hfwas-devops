<script setup lang="ts">
import { RouterView, useRoute, useRouter } from 'vue-router'
import { useProjectStore } from '@/modules/pm/stores'
import { TYPE_META, WORK_ITEM_TYPE_CODES, resolveWorkItemTypeCode } from '@/modules/pm/types'
import { routeId } from '@/modules/pm/utils/id'

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()

const projectId = computed(() => routeId(route.params.projectId))
const projectName = computed(() => projectStore.currentProject?.name ?? '项目')

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
    [`settings-modules:${id}`]: `/pm/projects/${id}/settings/modules`,
    [`settings-workflow:${id}`]: `/pm/projects/${id}/settings/workflow/${currentTypeCode.value}`,
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
    {
      type: 'group' as const,
      label: '事项',
      key: 'group-items',
      children: typeItems,
    },
    {
      type: 'group' as const,
      label: '视图',
      key: 'group-views',
      children: [
        { label: '看板', key: `/pm/projects/${id}/board/${currentTypeCode.value}` },
      ],
    },
    {
      type: 'group' as const,
      label: '设置',
      key: 'group-settings',
      children: [
        { label: '功能模块', key: `settings-modules:${id}` },
        { label: '事项配置', key: `settings-types:${id}` },
        { label: '状态流转', key: `settings-workflow:${id}` },
        { label: '自定义字段', key: `settings-fields:${id}` },
      ],
    },
  ]
})

const activeMenuKey = computed(() => {
  const path = route.path
  const id = projectId.value
  if (path.match(/\/items\/(requirement|task|bug|test_case)$/)) return path
  if (path.match(/\/items\/\d+/)) {
    const type = typeof route.query.type === 'string' ? route.query.type : resolveWorkItemTypeCode(path)
    return `/pm/projects/${id}/items/${type}`
  }
  if (path.match(/\/board\/(requirement|task|bug|test_case)$/)) return path
  if (path.endsWith('/settings/types') || path.match(/\/settings\/types\/(requirement|task|bug|test_case)$/)) {
    return `settings-types:${id}`
  }
  if (path.endsWith('/settings/fields')) return `settings-fields:${id}`
  if (path.includes('/settings/workflow')) return `settings-workflow:${id}`
  if (path.endsWith('/settings/modules')) return `settings-modules:${id}`
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
  <n-layout has-sider class="pm-layout">
    <n-layout-sider bordered :width="232" class="pm-sider" content-style="display:flex;flex-direction:column;height:100%">
      <div class="pm-sider-header">
        <n-text strong class="pm-sider-title" :title="projectName">{{ projectName }}</n-text>
        <n-button text size="tiny" type="primary" @click="router.push('/pm/projects')">切换项目</n-button>
      </div>
      <n-scrollbar class="pm-sider-menu">
        <n-menu
          :value="activeMenuKey"
          :options="menuOptions"
          :root-indent="16"
          :indent="18"
          @update:value="onMenuSelect"
        />
      </n-scrollbar>
      <div class="pm-sider-footer">
        <n-button block quaternary @click="router.push('/pm/projects')">返回项目列表</n-button>
      </div>
    </n-layout-sider>
    <n-layout-content class="pm-content" content-style="padding: 20px 24px 28px">
      <RouterView :key="route.path" />
    </n-layout-content>
  </n-layout>
</template>

<style scoped>
.pm-layout {
  min-height: calc(100vh - 56px);
  background: var(--n-color-embedded, transparent);
}

.pm-sider {
  background: var(--n-color);
}

.pm-sider-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 16px 16px 12px;
  border-bottom: 1px solid var(--n-border-color);
}

.pm-sider-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 15px;
}

.pm-sider-menu {
  flex: 1;
  min-height: 0;
  padding: 8px 0;
}

.pm-sider-footer {
  padding: 12px 16px 16px;
  border-top: 1px solid var(--n-border-color);
}

.pm-content {
  background: transparent;
}
</style>
