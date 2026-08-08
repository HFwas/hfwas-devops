<script setup lang="ts">
import { useAuthStore } from '@/modules/user/stores/auth'
import { useMessage } from 'naive-ui'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const message = useMessage()

const username = ref('')
const password = ref('')
const loading = ref(false)

async function submit() {
  if (!username.value.trim() || !password.value) {
    message.warning('请输入用户名和密码')
    return
  }
  loading.value = true
  try {
    await auth.login(username.value.trim(), password.value)
    message.success('登录成功')
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/workbench'
    router.replace(redirect)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <n-card title="用户中心 · 登录" style="width: 400px">
      <n-form @submit.prevent="submit">
        <n-form-item label="用户名">
          <n-input v-model:value="username" placeholder="用户名" autocomplete="username" />
        </n-form-item>
        <n-form-item label="密码">
          <n-input
            v-model:value="password"
            type="password"
            show-password-on="click"
            placeholder="密码"
            autocomplete="current-password"
            @keyup.enter="submit"
          />
        </n-form-item>
        <n-button type="primary" block :loading="loading" @click="submit">登录</n-button>
      </n-form>
      <n-text depth="3" style="display: block; margin-top: 16px; font-size: 12px">
        管理员：admin / admin123（首次启动自动创建）
      </n-text>
    </n-card>
  </div>
</template>

<style scoped>
.login-page {
  min-height: calc(100vh - 56px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
</style>
