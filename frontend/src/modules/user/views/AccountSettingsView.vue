<script setup lang="ts">
import { useAuthStore } from '@/modules/user/stores/auth'

// 占位页：后端暂无「修改个人资料」接口，先只做只读展示
const auth = useAuthStore()

const rows = computed(() => [
  { label: '账号名称', value: auth.user?.username ?? '-' },
  { label: '显示名称', value: auth.user?.displayName ?? '-' },
  { label: '账号 ID', value: auth.user?.id != null ? String(auth.user.id) : '-' },
  { label: '角色', value: auth.isAdmin ? '管理员' : '普通成员' },
  { label: '邮箱', value: auth.user?.email || '未填写' },
  { label: '手机号', value: auth.user?.phone || '未填写' },
  {
    label: '认证来源',
    value: auth.user?.authSource === 'ldap' ? auth.user.connectorName || 'LDAP' : '本地账号',
  },
  { label: '当前租户', value: auth.activeTenantName || auth.user?.tenantName || '未指定' },
])
</script>

<template>
  <n-space vertical size="large" style="padding: 20px 24px 28px">
    <n-page-header title="账号设置" subtitle="个人资料与登录信息" />
    <n-alert type="info" :bordered="false">
      资料修改接口尚未开放，如需变更请联系管理员。
    </n-alert>
    <n-card :bordered="true" size="small" style="max-width: 640px">
      <n-descriptions :column="1" label-placement="left" bordered>
        <n-descriptions-item v-for="row in rows" :key="row.label" :label="row.label">
          {{ row.value }}
        </n-descriptions-item>
      </n-descriptions>
    </n-card>
  </n-space>
</template>
