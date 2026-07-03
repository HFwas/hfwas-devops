<script setup lang="ts">
</script>

<template>
  <n-config-provider>
    <n-message-provider>
      <n-layout style="min-height: 100vh">
        <n-layout-header bordered style="height: 56px; padding: 0 24px; display: flex; align-items: center">
          <n-space align="center" justify="space-between" style="width: 100%">
            <router-link to="/pm/projects" style="text-decoration: none; color: inherit">
              <n-text strong>HFWAS DevOps · 项目管理</n-text>
            </router-link>
            <n-space>
              <router-link to="/pm/projects">项目</router-link>
            </n-space>
          </n-space>
        </n-layout-header>
        <n-layout-content>
          <router-view />
        </n-layout-content>
      </n-layout>
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
