<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { fetchStatus } from '../api/delivery'
import { Cpu, Package, Server, ChevronLeft, ChevronRight, Bell } from 'lucide-vue-next'

const router = useRouter()
const route = useRoute()
const collapsed = ref(false)
const currentClusterName = ref('')
const currentClusterStatus = ref('')

onMounted(async () => {
  try {
    const res = await fetchStatus()
    if (res.data?.currentCluster?.name) {
      currentClusterName.value = res.data.currentCluster.name
      currentClusterStatus.value = res.data.currentCluster.status || 'UP'
    }
  } catch { /* server not ready */ }
})

const navItems = [
  { path: '/delivery/products', label: '产品管理', icon: Package },
  { path: '/delivery/clusters', label: '集群管理', icon: Server },
]

function isActive(path: string) {
  return route.path.startsWith(path)
}
</script>

<template>
  <div style="display:flex;height:100vh;overflow:hidden;background:var(--color-bg);">
    <!-- Sidebar -->
    <aside :style="{
      display:'flex', flexDirection:'column', flexShrink:0,
      width: collapsed ? '60px' : '220px',
      transition: 'width 0.2s',
      backgroundColor: 'var(--color-sidebar)', color: 'rgba(255,255,255,0.85)'
    }">
      <!-- Logo -->
      <div style="height:60px;display:flex;align-items:center;padding:0 16px;border-bottom:1px solid rgba(255,255,255,0.06);flex-shrink:0;">
        <div style="width:32px;height:32px;border-radius:8px;background:rgba(37,99,235,0.2);display:flex;align-items:center;justify-content:center;flex-shrink:0;">
          <Cpu style="width:16px;height:16px;color:rgba(37,99,235,0.8);" />
        </div>
        <span v-if="!collapsed" style="margin-left:10px;font-size:14px;font-weight:600;white-space:nowrap;">交付运维平台</span>
      </div>

      <!-- Nav -->
      <nav style="flex:1;padding:12px 8px;overflow-y:auto;">
        <div v-if="!collapsed" style="padding:4px 8px 8px;font-size:10px;text-transform:uppercase;letter-spacing:1px;color:rgba(255,255,255,0.2);">导航</div>
        <button
          v-for="item in navItems" :key="item.path"
          @click="router.push(item.path)"
          :class="['sidebar-nav-item', { active: isActive(item.path) }]"
        >
          <component :is="item.icon" style="width:18px;height:18px;flex-shrink:0;" />
          <span v-if="!collapsed">{{ item.label }}</span>
        </button>
      </nav>

      <!-- Bottom cluster -->
      <div v-if="!collapsed" style="padding:12px;border-top:1px solid rgba(255,255,255,0.06);flex-shrink:0;">
        <div style="display:flex;align-items:center;gap:8px;padding:0 4px;">
          <div :class="currentClusterStatus === 'UP' ? 'dot-ready' : currentClusterStatus === 'DOWN' ? 'dot-failed' : 'dot-pending'" />
          <div style="min-width:0;flex:1;">
            <div style="font-size:11px;color:rgba(255,255,255,0.3);">当前集群</div>
            <div style="font-size:13px;color:rgba(255,255,255,0.7);font-weight:500;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">
              {{ currentClusterName || '未设置' }}
            </div>
          </div>
        </div>
      </div>

      <!-- Collapse -->
      <button @click="collapsed = !collapsed" style="height:36px;border-top:1px solid rgba(255,255,255,0.06);display:flex;align-items:center;justify-content:center;color:rgba(255,255,255,0.2);cursor:pointer;border:none;background:none;flex-shrink:0;">
        <component :is="collapsed ? ChevronRight : ChevronLeft" style="width:16px;height:16px;" />
      </button>
    </aside>

    <!-- Right -->
    <div style="flex:1;display:flex;flex-direction:column;min-width:0;">
      <!-- Top bar -->
      <header style="height:60px;background:white;border-bottom:1px solid var(--color-border);display:flex;align-items:center;justify-content:space-between;padding:0 24px;flex-shrink:0;">
        <div style="display:flex;align-items:center;gap:12px;">
          <h2 style="font-size:16px;font-weight:600;color:var(--color-text);">
            {{ navItems.find(n => isActive(n.path))?.label || '交付运维' }}
          </h2>
          <span v-if="currentClusterName" style="font-size:11px;color:var(--color-text-secondary);background:var(--color-bg);padding:2px 10px;border-radius:999px;">
            {{ currentClusterName }}
          </span>
        </div>
        <div style="display:flex;align-items:center;gap:16px;">
          <button style="padding:6px;border-radius:8px;border:none;background:none;color:var(--color-text-secondary);cursor:pointer;">
            <Bell style="width:18px;height:18px;" />
          </button>
          <div style="display:flex;align-items:center;gap:8px;padding-left:16px;border-left:1px solid var(--color-border);">
            <div style="width:30px;height:30px;border-radius:50%;background:var(--color-primary-light);display:flex;align-items:center;justify-content:center;font-size:12px;font-weight:600;color:var(--color-primary);">A</div>
            <span style="font-size:13px;font-weight:500;">Admin</span>
          </div>
        </div>
      </header>

      <!-- Content -->
      <main style="flex:1;overflow:auto;background:var(--color-bg);">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>