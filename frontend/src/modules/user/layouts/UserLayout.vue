<script setup lang="ts">
import { RouterView, useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const menuOptions = [
  { label: '账号管理', key: '/user/accounts' },
  { label: '租户管理', key: '/user/tenants' },
  { label: '在线会话', key: '/user/sessions' },
  { label: '登录日志', key: '/user/login-logs' },
  { label: '操作日志', key: '/user/oper-logs' },
  { label: '三方对接', key: '/user/integrations' },
]

const activeMenuKey = computed(() => {
  if (route.path.startsWith('/user/accounts') || route.path === '/user/manage') {
    return '/user/accounts'
  }
  if (route.path.startsWith('/user/tenants')) {
    return '/user/tenants'
  }
  if (route.path.startsWith('/user/sessions')) {
    return '/user/sessions'
  }
  if (route.path.startsWith('/user/login-logs')) {
    return '/user/login-logs'
  }
  if (route.path.startsWith('/user/oper-logs')) {
    return '/user/oper-logs'
  }
  if (route.path.startsWith('/user/integrations')) {
    return '/user/integrations'
  }
  return route.path
})

function onMenuSelect(key: string) {
  router.push(key)
}
</script>

<template>
  <n-layout has-sider style="min-height: calc(100vh - 56px)">
    <n-layout-sider bordered width="220">
      <div style="padding: 16px 16px 8px">
        <n-text strong>用户中心</n-text>
      </div>
      <n-menu :value="activeMenuKey" :options="menuOptions" @update:value="onMenuSelect" />
      <div style="padding: 12px">
        <n-button text @click="router.push('/pm/projects')">← 返回项目管理</n-button>
      </div>
    </n-layout-sider>
    <n-layout-content content-style="padding: 20px">
      <RouterView />
    </n-layout-content>
  </n-layout>
</template>
