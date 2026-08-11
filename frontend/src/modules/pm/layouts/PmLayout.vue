<script setup lang="ts">
import type { GlobalThemeOverrides } from 'naive-ui'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { useProjectIssueTypes } from '@/modules/pm/composables/useIssueTypes'
import { useProjectStore } from '@/modules/pm/stores'
import { resolveWorkItemTypeCode } from '@/modules/pm/types'
import { routeId } from '@/modules/pm/utils/id'
import '@/modules/pm/styles/pm-theme.css'

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()

const projectId = computed(() => routeId(route.params.projectId))
const projectName = computed(() => projectStore.currentProject?.name ?? '项目')
const { types: projectTypes, load: loadProjectTypes } = useProjectIssueTypes(projectId)

const themeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#3370ff',
    primaryColorHover: '#245bdb',
    primaryColorPressed: '#1c4fd6',
    primaryColorSuppl: '#4c88ff',
    infoColor: '#3370ff',
    successColor: '#2a9b6a',
    warningColor: '#c47d1a',
    errorColor: '#c24b4b',
    borderRadius: '6px',
    borderRadiusSmall: '4px',
    fontFamily:
      '"PingFang SC", "Hiragino Sans GB", "Microsoft YaHei", "Noto Sans SC", sans-serif',
  },
  Button: {
    borderRadiusMedium: '6px',
    borderRadiusSmall: '4px',
    heightMedium: '32px',
    heightSmall: '28px',
    fontWeight: '400',
  },
  DataTable: {
    thColor: '#fafbfc',
    thTextColor: '#646a73',
    tdColorHover: '#f7f8fa',
    borderColor: '#eef0f3',
    thFontWeight: '500',
  },
  Tag: {
    borderRadius: '4px',
  },
  Menu: {
    itemTextColorActive: '#3370ff',
    itemTextColorActiveHover: '#3370ff',
    itemIconColorActive: '#3370ff',
    itemColorActive: '#e8f0ff',
    itemColorActiveHover: '#e8f0ff',
    borderRadius: '6px',
  },
  Badge: {
    color: '#3370ff',
  },
}

onMounted(async () => {
  await projectStore.loadProjects()
  if (projectId.value) {
    await projectStore.selectProject(projectId.value)
    await loadProjectTypes(true)
  }
})

watch(projectId, async (id) => {
  if (id) {
    await projectStore.selectProject(id)
    await loadProjectTypes(true)
  }
})

const currentTypeCode = computed(() => {
  const fromPath = resolveWorkItemTypeCode(route.path, '')
  if (fromPath) return fromPath
  return projectTypes.value[0]?.code ?? 'task'
})

const menuRouteMap = computed(() => {
  const id = projectId.value
  return {
    [`settings-types:${id}`]: `/pm/projects/${id}/settings/types`,
    [`settings-fields:${id}`]: `/pm/projects/${id}/settings/fields`,
    [`settings-modules:${id}`]: `/pm/projects/${id}/settings/modules`,
  } as Record<string, string>
})

const menuOptions = computed(() => {
  const id = projectId.value
  if (!id) return []

  const typeItems = projectTypes.value.map((t) => ({
    label: t.name,
    key: `/pm/projects/${id}/items/${t.code}`,
  }))

  return [
    {
      type: 'group' as const,
      label: '事项',
      key: 'group-items',
      children: typeItems.length
        ? typeItems
        : [{ label: '暂无启用类型', key: `settings-types:${id}`, disabled: true }],
    },
    {
      type: 'group' as const,
      label: '设置',
      key: 'group-settings',
      children: [
        { label: '功能模块', key: `settings-modules:${id}` },
        { label: '事项类型', key: `settings-types:${id}` },
        { label: '自定义字段', key: `settings-fields:${id}` },
      ],
    },
  ]
})

const activeMenuKey = computed(() => {
  const path = route.path
  const id = projectId.value
  if (path.match(/\/items\/[a-z][a-z0-9_]*$/)) return path
  if (path.match(/\/items\/\d+/)) {
    const type = typeof route.query.type === 'string' ? route.query.type : currentTypeCode.value
    return `/pm/projects/${id}/items/${type}`
  }
  if (path.includes('/settings/types') || path.includes('/settings/workflow')) {
    return `settings-types:${id}`
  }
  if (path.endsWith('/settings/fields')) return `settings-fields:${id}`
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
  <n-config-provider :theme-overrides="themeOverrides" class="pm-theme">
    <n-layout has-sider class="pm-layout">
      <n-layout-sider
        :bordered="false"
        :width="220"
        class="pm-sider"
        content-style="display:flex;flex-direction:column;height:100%"
      >
        <div class="pm-sider-header">
          <n-text strong class="pm-sider-title" :title="projectName">{{ projectName }}</n-text>
          <n-button text size="tiny" @click="router.push('/pm/projects')">切换</n-button>
        </div>
        <n-scrollbar class="pm-sider-menu">
          <n-menu
            :value="activeMenuKey"
            :options="menuOptions"
            :root-indent="12"
            :indent="14"
            @update:value="onMenuSelect"
          />
        </n-scrollbar>
        <div class="pm-sider-footer">
          <n-button block quaternary size="small" @click="router.push('/pm/projects')">
            返回项目列表
          </n-button>
        </div>
      </n-layout-sider>
      <n-layout-content class="pm-content" content-style="padding: 12px 12px 16px">
        <RouterView :key="route.path" />
      </n-layout-content>
    </n-layout>
  </n-config-provider>
</template>

<style scoped>
.pm-layout {
  min-height: calc(100vh - 56px);
  background: var(--pm-bg, var(--wb-page-bg, #f5f7fb));
}

.pm-sider {
  background: var(--pm-surface, var(--wb-card-bg, #ffffff));
  border-right: 1px solid var(--pm-border, var(--wb-border, #e5e7eb));
}

.pm-sider-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 14px 14px 12px;
  border-bottom: 1px solid var(--pm-border-soft, var(--wb-border, #e5e7eb));
}

.pm-sider-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  color: var(--pm-text, #1f2329);
}

.pm-sider-menu {
  flex: 1;
  min-height: 0;
  padding: 8px 6px;
}

.pm-sider-footer {
  padding: 10px 12px 14px;
  border-top: 1px solid var(--pm-border-soft, var(--wb-border, #e5e7eb));
}

.pm-content {
  background: transparent;
}
</style>
