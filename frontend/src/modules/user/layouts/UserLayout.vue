<script setup lang="ts">
import { RouterView, useRoute, useRouter } from 'vue-router'
import type { MenuOption } from 'naive-ui'

const route = useRoute()
const router = useRouter()

/**
 * 用户中心为独立控制台：自带左侧导航，铺满顶栏以下整片区域，
 * 不作为项目管理的二级 Tab 存在。扩展点：往对应分组追加一项即可。
 */
const menuGroups = [
  { label: '成员与访问', items: ['/user/accounts', '/user/sessions', '/user/login-logs'] },
  { label: '租户与集成', items: ['/user/tenants', '/user/integrations'] },
  { label: '消息', items: ['/user/messages', '/user/message-notify'] },
  { label: '审计', items: ['/user/oper-logs'] },
]

const MENU_LABELS: Record<string, string> = {
  '/user/accounts': '账号管理',
  '/user/sessions': '在线会话',
  '/user/login-logs': '登录日志',
  '/user/tenants': '租户管理',
  '/user/integrations': '三方对接',
  '/user/messages': '消息管理',
  '/user/message-notify': '消息通知',
  '/user/oper-logs': '操作日志',
}

const menuOptions: MenuOption[] = menuGroups.map((group) => ({
  type: 'group',
  label: group.label,
  key: `group-${group.label}`,
  children: group.items.map((path) => ({ label: MENU_LABELS[path], key: path })),
}))

const menuPaths = menuGroups.flatMap((group) => group.items)

const activeMenuKey = computed(() => {
  if (route.path === '/user/manage') return '/user/accounts'
  // 取最长命中前缀，避免 /user/messages 与 /user/message-notify 互相误判
  return (
    menuPaths
      .filter((path) => route.path === path || route.path.startsWith(`${path}/`))
      .sort((a, b) => b.length - a.length)[0] ?? route.path
  )
})

function onMenuSelect(key: string) {
  router.push(key)
}
</script>

<template>
  <n-layout has-sider style="min-height: calc(100vh - 56px)">
    <n-layout-sider bordered width="220" content-style="display: flex; flex-direction: column">
      <div class="uc-title">用户中心</div>
      <n-menu
        :value="activeMenuKey"
        :options="menuOptions"
        :indent="18"
        @update:value="onMenuSelect"
      />
      <div class="uc-back">
        <n-button text size="small" @click="router.push('/workbench')">← 返回工作台</n-button>
      </div>
    </n-layout-sider>
    <n-layout-content content-style="padding: 20px">
      <RouterView />
    </n-layout-content>
  </n-layout>
</template>

<style scoped>
.uc-title {
  padding: 16px 16px 8px;
  font-size: 15px;
  font-weight: 600;
}

.uc-back {
  margin-top: auto;
  padding: 12px 16px;
  border-top: 1px solid var(--wb-border, #e5e7eb);
}
</style>
