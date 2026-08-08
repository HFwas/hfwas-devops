<script setup lang="ts">
import { BookOpen, Moon, Search, Settings, Sun, Users, UserRound } from '@lucide/vue'
import { useAuthStore } from '@/modules/user/stores/auth'
import MessageBell from '@/modules/user/components/MessageBell.vue'
import ProductSwitcher from '@/shared/console/ProductSwitcher.vue'
import SecondaryTabs from '@/shared/console/SecondaryTabs.vue'
import { resolveActiveTab } from '@/shared/console/tabs'
import { useHelpLinks } from '@/modules/pm/composables/useWorkbench'
import { useConsoleTheme } from '@/shared/console/useConsoleTheme'
import { useMessage } from 'naive-ui'
import type { DropdownDividerOption, DropdownOption } from 'naive-ui'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const message = useMessage()
const messageBellRef = ref<InstanceType<typeof MessageBell> | null>(null)
const helpLinks = useHelpLinks()
const { isDark, toggle: toggleTheme } = useConsoleTheme()
const keyword = ref('')

onMounted(() => {
  if (!auth.isLoggedIn) return
  if (!auth.user) {
    void auth.fetchMe()
  } else {
    void auth.fetchMyTenants()
  }
})

/**
 * 二级 Tab 只属于当前产品：工作台、用户中心、账号设置等平台页不展示 Tab
 */
const showTabs = computed(
  () => auth.isLoggedIn && !route.meta.public && resolveActiveTab(route.path) !== null,
)

const showTenantSwitcher = computed(() => auth.myTenants.length > 1)

const tenantOptions = computed(() =>
  auth.myTenants.map((t) => ({
    label: t.name,
    key: String(t.id),
    disabled: String(t.id) === String(auth.activeTenantId),
  })),
)

const userMenuOptions = computed<Array<DropdownOption | DropdownDividerOption>>(() => [
  { label: '账号设置', key: 'settings' },
  { type: 'divider', key: 'd1' },
  { label: '退出登录', key: 'logout' },
])

const avatarText = computed(() => {
  const name = auth.user?.displayName || auth.user?.username || '?'
  return name.slice(0, 1).toUpperCase()
})

async function onSwitchTenant(tenantId: string | number) {
  try {
    await auth.switchTenant(tenantId)
    message.success('已切换租户')
    messageBellRef.value?.refresh()
    await router.replace('/workbench')
  } catch (e) {
    message.error(e instanceof Error ? e.message : '切换租户失败')
  }
}

function onUserMenu(key: string | number) {
  if (key === 'settings') void router.push('/user/settings')
  else if (key === 'logout') logout()
}

function goUserCenter() {
  void router.push('/user/accounts')
}

function onSearch() {
  const value = keyword.value.trim()
  if (!value) return
  void router.push({ path: '/pm/projects', query: { keyword: value } })
}

function logout() {
  void auth.logout().then(() => router.push('/user/login'))
}
</script>

<template>
  <n-layout style="min-height: 100vh">
    <n-layout-header bordered class="top-bar">
      <!-- 左区：Logo + 产品目录下拉 -->
      <div class="top-left">
        <router-link to="/workbench" class="brand">
          <span class="brand-logo">H</span>
          <span class="brand-name">HFWAS DevOps</span>
        </router-link>
        <ProductSwitcher v-if="auth.isLoggedIn" />
      </div>

      <!-- 中区：全局搜索 -->
      <div class="top-center">
        <n-input
          v-if="auth.isLoggedIn"
          v-model:value="keyword"
          placeholder="搜索项目名称或编码"
          clearable
          round
          size="small"
          @keyup.enter="onSearch"
        >
          <template #prefix><Search :size="14" /></template>
        </n-input>
      </div>

      <!-- 右区：文档 / 站内信 / 主题 / 用户中心 / 租户 / 用户 -->
      <div class="top-right">
        <template v-if="auth.isLoggedIn">
          <n-popover trigger="click" placement="bottom-end">
            <template #trigger>
              <n-button quaternary circle size="small" title="文档">
                <template #icon><BookOpen :size="16" /></template>
              </n-button>
            </template>
            <div class="help-list">
              <a v-for="link in helpLinks" :key="link.key" :href="link.href" class="help-item">
                {{ link.label }}
              </a>
            </div>
          </n-popover>

          <MessageBell ref="messageBellRef" />

          <n-button
            quaternary
            circle
            size="small"
            :title="isDark ? '切换为浅色' : '切换为深色'"
            @click="toggleTheme"
          >
            <template #icon>
              <Sun v-if="isDark" :size="16" />
              <Moon v-else :size="16" />
            </template>
          </n-button>

          <n-button
            v-if="auth.isAdmin"
            size="small"
            quaternary
            class="uc-entry"
            title="用户中心"
            @click="goUserCenter"
          >
            <template #icon><Users :size="15" /></template>
            用户中心
          </n-button>

          <n-dropdown
            v-if="showTenantSwitcher"
            trigger="click"
            :options="tenantOptions"
            :disabled="auth.switchingTenant"
            @select="onSwitchTenant"
          >
            <n-button size="small" quaternary :loading="auth.switchingTenant">
              <template #icon><Settings :size="14" /></template>
              {{ auth.activeTenantName ?? '选择租户' }}
            </n-button>
          </n-dropdown>
          <n-text v-else-if="auth.activeTenantName" depth="3" class="tenant-text">
            {{ auth.activeTenantName }}
          </n-text>

          <n-dropdown trigger="click" :options="userMenuOptions" @select="onUserMenu">
            <button type="button" class="user-trigger">
              <n-avatar round :size="26" class="user-avatar">{{ avatarText }}</n-avatar>
              <span class="user-name">{{ auth.user?.displayName ?? auth.user?.username }}</span>
            </button>
          </n-dropdown>
        </template>
        <router-link v-else to="/user/login" class="login-link">
          <UserRound :size="14" />
          登录
        </router-link>
      </div>
    </n-layout-header>

    <!-- 二级 Tab 导航 -->
    <SecondaryTabs v-if="showTabs" />

    <n-layout-content>
      <router-view :key="String(auth.activeTenantId ?? '') + '-' + auth.tenantVersion" />
    </n-layout-content>
  </n-layout>
</template>

<style scoped>
.top-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  height: 56px;
  padding: 0 24px;
}

.top-left {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 12px;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: inherit;
  text-decoration: none;
}

.brand-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 7px;
  background: linear-gradient(135deg, #4098fc, #7c3aed);
  color: #fff;
  font-size: 14px;
  font-weight: 700;
}

.brand-name {
  font-size: 15px;
  font-weight: 600;
}

.top-center {
  flex: 1;
  display: flex;
  justify-content: center;
  min-width: 0;
}

.top-center :deep(.n-input) {
  max-width: 420px;
}

.top-right {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 10px;
}

.tenant-text {
  font-size: 13px;
}

.uc-entry {
  font-size: 13px;
}

.user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 3px 6px;
  border: none;
  border-radius: 999px;
  background: transparent;
  font: inherit;
  font-size: 13px;
  color: inherit;
  cursor: pointer;
}

.user-trigger:hover {
  background: rgba(64, 152, 252, 0.1);
}

.user-avatar {
  background: #2d80e6;
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}

.user-name {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.login-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
}

.help-list {
  display: flex;
  flex-direction: column;
  min-width: 140px;
}

.help-item {
  padding: 6px 4px;
  font-size: 13px;
}
</style>
