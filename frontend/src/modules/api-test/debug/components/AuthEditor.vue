<script setup lang="ts">
import type { AuthConfig, AuthType } from '@/modules/api-test/debug/utils/auth'

const AUTH_OPTIONS = [
  { label: 'None', value: 'none' },
  { label: 'Bearer', value: 'bearer' },
  { label: 'Basic', value: 'basic' },
  { label: 'API Key', value: 'apikey' },
]

const ADD_TO_OPTIONS = [
  { label: 'Header', value: 'header' },
  { label: 'Query', value: 'query' },
]

const props = defineProps<{
  auth: AuthConfig
}>()

const emit = defineEmits<{
  'update:auth': [value: AuthConfig]
}>()

function patch(partial: Partial<AuthConfig>) {
  emit('update:auth', { ...props.auth, ...partial })
}
</script>

<template>
  <div class="auth-editor" data-testid="auth-editor">
    <n-select
      :value="auth.type"
      :options="AUTH_OPTIONS"
      size="small"
      style="width: 160px;"
      @update:value="(v: AuthType) => patch({ type: v })"
    />

    <div v-if="auth.type === 'bearer'" class="auth-editor__fields">
      <n-input
        :value="auth.token"
        size="small"
        placeholder="Token（支持 {{ var }}）"
        data-testid="auth-token"
        @update:value="(v: string) => patch({ token: v })"
      />
    </div>

    <div v-else-if="auth.type === 'basic'" class="auth-editor__fields">
      <n-input
        :value="auth.username"
        size="small"
        placeholder="Username"
        @update:value="(v: string) => patch({ username: v })"
      />
      <n-input
        :value="auth.password"
        type="password"
        show-password-on="click"
        size="small"
        placeholder="Password"
        @update:value="(v: string) => patch({ password: v })"
      />
    </div>

    <div v-else-if="auth.type === 'apikey'" class="auth-editor__fields">
      <n-input
        :value="auth.apiKey"
        size="small"
        placeholder="Key"
        @update:value="(v: string) => patch({ apiKey: v })"
      />
      <n-input
        :value="auth.apiValue"
        size="small"
        placeholder="Value"
        @update:value="(v: string) => patch({ apiValue: v })"
      />
      <n-select
        :value="auth.addTo"
        :options="ADD_TO_OPTIONS"
        size="small"
        style="width: 120px;"
        @update:value="(v: 'header' | 'query') => patch({ addTo: v })"
      />
    </div>

    <p v-else class="auth-editor__hint">不附加认证头。可继续在 Headers 里手写。</p>
  </div>
</template>

<style scoped>
.auth-editor {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 4px 0 12px;
}

.auth-editor__fields {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.auth-editor__hint {
  margin: 0;
  font-size: 12px;
  color: var(--wb-muted, #6b7280);
}
</style>
