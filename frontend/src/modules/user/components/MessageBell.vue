<script setup lang="ts">
import { Bell } from '@lucide/vue'
import { messageApi } from '@/modules/user/api'
import type { UserMessage } from '@/modules/user/types'
import { AUTH_TOKEN_KEY } from '@/modules/user/types'
import { formatDateTime } from '@/modules/pm/utils/comment'

const router = useRouter()
const unreadCount = ref(0)
const recent = ref<UserMessage[]>([])
const loading = ref(false)
const showPopover = ref(false)

async function refresh() {
  if (!localStorage.getItem(AUTH_TOKEN_KEY)) return
  loading.value = true
  try {
    const [count, list] = await Promise.all([messageApi.unreadCount(), messageApi.recent(5)])
    unreadCount.value = Number(count) || 0
    recent.value = list
  } finally {
    loading.value = false
  }
}

async function openMessage(msg: UserMessage) {
  if (msg.id != null && msg.readFlag === 0) {
    await messageApi.markRead(msg.id)
    unreadCount.value = Math.max(0, unreadCount.value - 1)
    msg.readFlag = 1
  }
  showPopover.value = false
  if (msg.linkUrl) {
    await router.push(msg.linkUrl)
  } else {
    await router.push('/messages')
  }
}

function goInbox() {
  showPopover.value = false
  void router.push('/messages')
}

onMounted(refresh)

defineExpose({ refresh })
</script>

<template>
  <n-popover v-model:show="showPopover" trigger="click" placement="bottom-end" :width="360">
    <template #trigger>
      <n-badge :value="unreadCount > 0 ? unreadCount : undefined" :max="99">
        <n-button quaternary circle size="small" title="消息" @click="refresh">
          <template #icon><Bell :size="16" /></template>
        </n-button>
      </n-badge>
    </template>
    <n-space vertical size="small" style="width: 100%">
      <n-space justify="space-between" align="center">
        <n-text strong>消息</n-text>
        <n-button text size="small" @click="goInbox">查看全部</n-button>
      </n-space>
      <n-spin :show="loading">
        <n-empty v-if="!loading && recent.length === 0" description="暂无消息" size="small" />
        <n-list v-else hoverable clickable>
          <n-list-item v-for="msg in recent" :key="msg.id" @click="openMessage(msg)">
            <n-thing>
              <template #header>
                <n-space align="center" size="small">
                  <n-text :depth="msg.readFlag === 1 ? 3 : 1">{{ msg.title }}</n-text>
                  <n-tag v-if="msg.readFlag === 0" size="tiny" type="info">未读</n-tag>
                </n-space>
              </template>
              <template #description>
                <n-text depth="3" style="font-size: 12px">
                  {{ msg.categoryLabel }} · {{ formatDateTime(msg.createTime) }}
                </n-text>
              </template>
            </n-thing>
          </n-list-item>
        </n-list>
      </n-spin>
    </n-space>
  </n-popover>
</template>
