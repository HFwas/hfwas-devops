<script setup lang="ts">
import { useAuthStore } from '@/modules/user/stores/auth'

const auth = useAuthStore()
const router = useRouter()

onMounted(() => {
  if (auth.token && !auth.user) {
    void auth.fetchMe()
  }
})

function logout() {
  void auth.logout().then(() => router.push('/user/login'))
}
</script>

<template>
  <n-config-provider>
    <n-message-provider>
      <n-dialog-provider>
        <n-layout style="min-height: 100vh">
          <n-layout-header bordered style="height: 56px; padding: 0 24px; display: flex; align-items: center">
            <n-space align="center" justify="space-between" style="width: 100%">
              <router-link to="/pm/projects" style="text-decoration: none; color: inherit">
                <n-text strong>HFWAS DevOps</n-text>
              </router-link>
              <n-space align="center">
                <router-link to="/pm/projects">项目管理</router-link>
                <router-link v-if="auth.isAdmin" to="/user/accounts">用户中心</router-link>
                <template v-if="auth.isLoggedIn">
                  <n-text depth="3">{{ auth.user?.displayName ?? auth.user?.username }}</n-text>
                  <n-button text @click="logout">退出</n-button>
                </template>
                <router-link v-else to="/user/login">登录</router-link>
              </n-space>
            </n-space>
          </n-layout-header>
          <n-layout-content>
            <router-view />
          </n-layout-content>
        </n-layout>
      </n-dialog-provider>
    </n-message-provider>
  </n-config-provider>
</template>

<style>
body {
  margin: 0;
  font-family: system-ui, -apple-system, sans-serif;
  background: #f5f7fb;
}
a {
  color: #2080f0;
  text-decoration: none;
}
</style>
