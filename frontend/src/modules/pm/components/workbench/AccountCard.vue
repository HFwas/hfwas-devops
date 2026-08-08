<script setup lang="ts">
import { BadgeCheck, Bell, Building2, ChevronRight, Copy, ShieldCheck, UserRound } from '@lucide/vue'
import { useMessage } from 'naive-ui'
import { useAuthStore } from '@/modules/user/stores/auth'

// 账号信息取自登录态，无需额外请求
const auth = useAuthStore()
const router = useRouter()
const message = useMessage()

const displayName = computed(() => auth.user?.displayName || auth.user?.username || '未登录')
const username = computed(() => auth.user?.username ?? '-')
const accountId = computed(() => (auth.user?.id != null ? String(auth.user.id) : '-'))
const avatarText = computed(() => displayName.value.slice(0, 1).toUpperCase())
const roleLabel = computed(() => (auth.isAdmin ? '管理员' : '普通成员'))
const tenantName = computed(() => auth.activeTenantName || auth.user?.tenantName || '未指定租户')

/** 认证状态：本地账号看启用状态，外部账号显示来源连接器 */
const verified = computed(() => auth.user?.enabled !== 0)
const authSourceLabel = computed(() => {
  if (auth.user?.authSource === 'ldap') return auth.user.connectorName || 'LDAP'
  return '本地账号'
})

/** 用户中心为管理员页面，普通成员改为进入站内信 */
const primaryAction = computed(() =>
  auth.isAdmin
    ? { label: '进入用户中心', path: '/user/accounts', icon: ShieldCheck }
    : { label: '查看我的消息', path: '/messages', icon: Bell },
)

async function copyAccountId() {
  if (accountId.value === '-') return
  try {
    await navigator.clipboard.writeText(accountId.value)
    message.success('账号 ID 已复制')
  } catch {
    message.error('复制失败，请手动选择复制')
  }
}
</script>

<template>
  <n-card :bordered="true" size="small" class="wb-card">
    <template #header>
      <span class="wb-card-title">
        <UserRound :size="16" class="wb-card-title-icon" />
        账号信息
      </span>
    </template>
    <template #header-extra>
      <n-button text size="small" @click="router.push('/user/settings')">
        账号设置
        <ChevronRight :size="14" />
      </n-button>
    </template>

    <div class="acc-head">
      <n-avatar round :size="44" class="acc-avatar">{{ avatarText }}</n-avatar>
      <div class="acc-head-body">
        <div class="acc-name">{{ displayName }}</div>
        <div class="acc-role">
          <n-tag size="tiny" :type="auth.isAdmin ? 'warning' : 'default'" :bordered="false">
            {{ roleLabel }}
          </n-tag>
          <span class="acc-source">{{ authSourceLabel }}</span>
        </div>
      </div>
    </div>

    <div class="acc-rows">
      <div class="acc-row">
        <span class="acc-label">账号名称</span>
        <span class="acc-value">{{ username }}</span>
      </div>
      <div class="acc-row">
        <span class="acc-label">账号 ID</span>
        <span class="acc-value acc-value-mono">
          {{ accountId }}
          <button type="button" class="acc-copy" title="复制账号 ID" @click="copyAccountId">
            <Copy :size="13" />
          </button>
        </span>
      </div>
      <div class="acc-row">
        <span class="acc-label">认证状态</span>
        <span class="acc-value">
          <n-tag v-if="verified" size="small" type="success" :bordered="false">
            <template #icon><BadgeCheck :size="13" /></template>
            已认证
          </n-tag>
          <n-tag v-else size="small" type="error" :bordered="false">未认证</n-tag>
        </span>
      </div>
      <div class="acc-row">
        <span class="acc-label">当前租户</span>
        <span class="acc-value acc-value-ellipsis">
          <Building2 :size="13" class="acc-value-icon" />
          {{ tenantName }}
        </span>
      </div>
    </div>

    <n-button block secondary class="acc-action" @click="router.push(primaryAction.path)">
      <template #icon><component :is="primaryAction.icon" :size="15" /></template>
      {{ primaryAction.label }}
    </n-button>
  </n-card>
</template>

<style scoped>
.wb-card-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
}

.wb-card-title-icon {
  color: var(--n-text-color-3, #909399);
}

.acc-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 10px;
  background: linear-gradient(120deg, rgba(64, 152, 252, 0.12), rgba(124, 58, 237, 0.08));
}

.acc-avatar {
  flex-shrink: 0;
  background: #2d80e6;
  color: #fff;
  font-weight: 600;
}

.acc-head-body {
  min-width: 0;
}

.acc-name {
  overflow: hidden;
  font-size: 15px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.acc-role {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 5px;
}

.acc-source {
  font-size: 12px;
  color: var(--wb-muted, #6b7280);
}

.acc-rows {
  margin-top: 14px;
}

.acc-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 34px;
  border-bottom: 1px dashed var(--wb-border, #e5e7eb);
  font-size: 13px;
}

.acc-row:last-child {
  border-bottom: none;
}

.acc-label {
  flex-shrink: 0;
  color: var(--wb-muted, #6b7280);
}

.acc-value {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.acc-value-mono {
  font-variant-numeric: tabular-nums;
}

.acc-value-ellipsis {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.acc-value-icon {
  flex-shrink: 0;
  color: var(--wb-muted, #6b7280);
}

.acc-copy {
  display: inline-flex;
  padding: 2px;
  border: none;
  background: transparent;
  color: var(--wb-muted, #6b7280);
  cursor: pointer;
}

.acc-copy:hover {
  color: #2d80e6;
}

.acc-action {
  margin-top: 14px;
}
</style>
