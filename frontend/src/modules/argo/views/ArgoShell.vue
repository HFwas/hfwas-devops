<script setup lang="ts">
import { FileText, GitBranch, GitGraph, Timer } from '@lucide/vue'
import SecondaryTabs from '@/shared/console/SecondaryTabs.vue'

const tabs = [
  { key: 'workflows', label: '工作流', icon: GitBranch, href: '/argo/workflows' },
  { key: 'templates', label: '模板', icon: FileText, href: '/argo/templates' },
  { key: 'cron', label: '定时任务', icon: Timer, href: '/argo/cron' },
]
</script>

<template>
  <div class="pl-page">
    <div class="pl-hero">
      <div class="pl-hero-main">
        <h1 class="pl-hero-title">
          <span class="pl-hero-title-icon"><GitGraph :size="22" /></span>
          工作流
        </h1>
        <p class="pl-hero-desc">基于 Argo Workflows 的通用工作流引擎 — 编排、自动化、自愈</p>
      </div>
    </div>

    <SecondaryTabs :tabs="tabs" />

    <router-view />
  </div>
</template>

<style scoped>
.pl-hero-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}

.pl-hero-title-icon {
  display: inline-flex;
  color: var(--wb-primary, #3370ff);
}
</style>