<script setup lang="ts">
import { useAuthStore } from '@/modules/user/stores/auth'
import MessageBell from '@/modules/user/components/MessageBell.vue'
import { useMessage } from 'naive-ui'

const auth = useAuthStore()
const router = useRouter()
const message = useMessage()
const messageBellRef = ref<InstanceType<typeof MessageBell> | null>(null)

onMounted(() => {
  if (!auth.isLoggedIn) return
  if (!auth.user) {
    void auth.fetchMe()
  } else {
    void auth.fetchMyTenants()
  }
})

const showTenantSwitcher = computed(() => auth.myTenants.length > 1)

const tenantOptions = computed(() =>
  auth.myTenants.map((t) => ({
    label: t.name,
    key: String(t.id),
    disabled: String(t.id) === String(auth.user?.tenantId),
  })),
)

async function onSwitchTenant(tenantId: string | number) {
  try {
    await auth.switchTenant(tenantId)
    message.success('已切换租户')
    messageBellRef.value?.refresh()
    await router.replace('/pm/projects')
  } catch (e) {
    message.error(e instanceof Error ? e.message : '切换租户失败')
  }
}

function logout() {
  void auth.logout().then(() => router.push('/user/login'))
}
</script>

<template>
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
            <MessageBell ref="messageBellRef" />
            <n-dropdown
              v-if="showTenantSwitcher"
              trigger="click"
              :options="tenantOptions"
              :disabled="auth.switchingTenant"
              @select="onSwitchTenant"
            >
              <n-button text :loading="auth.switchingTenant">
                {{ auth.user?.tenantName ?? '选择租户' }} ▾
              </n-button>
            </n-dropdown>
            <n-text v-else-if="auth.user?.tenantName" depth="3">{{ auth.user.tenantName }}</n-text>
            <n-text depth="3">{{ auth.user?.displayName ?? auth.user?.username }}</n-text>
            <n-button text @click="logout">退出</n-button>
          </template>
          <router-link v-else to="/user/login">登录</router-link>
        </n-space>
      </n-space>
    </n-layout-header>
    <n-layout-content>
      <router-view :key="String(auth.user?.tenantId ?? '') + '-' + auth.tenantVersion" />
    </n-layout-content>
  </n-layout>
</template>
