<script setup lang="ts">
import { useAuthStore } from '@/modules/user/stores/auth'
import { isAuthenticated } from '@/shared/keycloak'

const router = useRouter()
const auth = useAuthStore()
const jumping = ref(false)

async function goLogin() {
  jumping.value = true
  try {
    await auth.login()
  } finally {
    jumping.value = false
  }
}

onMounted(() => {
  if (isAuthenticated()) {
    void router.replace('/workbench')
    return
  }
  void goLogin()
})
</script>

<template>
  <div class="login-page">
    <n-card title="统一认证" style="width: 400px">
      <n-text>正在跳转到统一认证…</n-text>
      <n-button type="primary" block style="margin-top: 20px" :loading="jumping" @click="goLogin">
        前往登录
      </n-button>
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
