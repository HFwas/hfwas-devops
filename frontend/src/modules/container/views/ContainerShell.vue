<script setup lang="ts">
import { Server, HardDrive, Box, Layers, Globe, Cpu, KeyRound, FileJson, HardDrive as Storage, Container, Search, Database } from '@lucide/vue'
import { useClusterStore } from '@/modules/container/stores/cluster'
import { h, onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import type { MenuOption } from 'naive-ui'

const clusterStore = useClusterStore()
const router = useRouter()
const route = useRoute()

onMounted(() => {
  if (clusterStore.clusterList.length === 0) {
    clusterStore.fetchClusters().catch(() => {})
  } else if (!clusterStore.currentCluster && clusterStore.clusterList.length > 0) {
    clusterStore.setCurrent(clusterStore.clusterList[0])
  }
})

// Fetch namespaces when cluster changes
watch(() => clusterStore.currentCluster?.id, (newId) => {
  if (newId) {
    clusterStore.fetchNamespaces()
  }
})

const clusterId = computed(() => clusterStore.currentCluster?.id)

const namespaceOptions = computed(() => {
  const list = clusterStore.namespaceList
  return [
    { label: 'All Namespaces', value: '' },
    ...list.map(ns => ({ label: ns.name, value: ns.name })),
  ]
})

const menuOptions = computed<MenuOption[]>(() => {
  const cid = clusterId.value
  const base = cid ? `/container/clusters/${cid}` : ''
  return [
    {
      label: '集群纳管',
      key: '/container/clusters',
      icon: () => h(Server, { size: 16 }),
    },
    {
      label: '节点',
      key: `${base}/nodes`,
      icon: () => h(HardDrive, { size: 16 }),
      disabled: !cid,
    },
    {
      type: 'divider',
      key: 'd1',
    },
    {
      label: '镜像仓库',
      key: '/container/registries',
      icon: () => h(Container, { size: 16 }),
    },
    {
      label: '镜像',
      key: '/container/images',
      icon: () => h(Search, { size: 16 }),
    },
    {
      type: 'divider',
      key: 'd1',
    },
    {
      label: 'Pod',
      key: `${base}/pods`,
      icon: () => h(Box, { size: 16 }),
      disabled: !cid,
    },
    {
      label: 'Deployment',
      key: `${base}/deployments`,
      icon: () => h(Layers, { size: 16 }),
      disabled: !cid,
    },
    {
      label: 'StatefulSet',
      key: `${base}/statefulsets`,
      icon: () => h(Cpu, { size: 16 }),
      disabled: !cid,
    },
    {
      label: 'Service',
      key: `${base}/services`,
      icon: () => h(Globe, { size: 16 }),
      disabled: !cid,
    },
    {
      label: 'ConfigMap',
      key: `${base}/configmaps`,
      icon: () => h(FileJson, { size: 16 }),
      disabled: !cid,
    },
    {
      label: 'Secret',
      key: `${base}/secrets`,
      icon: () => h(KeyRound, { size: 16 }),
      disabled: !cid,
    },
    {
      label: 'PVC',
      key: `${base}/pvcs`,
      icon: () => h(Storage, { size: 16 }),
      disabled: !cid,
    },
    {
      label: 'StorageClass',
      key: `${base}/storageclasses`,
      icon: () => h(Database, { size: 16 }),
      disabled: !cid,
    },
  ]
})

const activeMenuKey = computed(() => {
  const name = route.name as string
  const cid = clusterId.value
  const base = cid ? `/container/clusters/${cid}` : ''
  switch (name) {
    case 'container-clusters':
    case 'container-cluster-detail':
      return '/container/clusters'
    case 'container-registries':
    case 'container-registry-detail':
    case 'container-registry-repo-list':
    case 'container-registry-repo-detail':
      return '/container/registries'
    case 'container-images':
      return '/container/images'
    case 'container-nodes':
    case 'container-node-detail':
      return `${base}/nodes`
    case 'container-pods':
    case 'container-pod-detail':
      return `${base}/pods`
    case 'container-deployments':
      return `${base}/deployments`
    case 'container-statefulsets':
      return `${base}/statefulsets`
    case 'container-services':
      return `${base}/services`
    case 'container-configmaps':
      return `${base}/configmaps`
    case 'container-secrets':
      return `${base}/secrets`
    case 'container-pvcs':
      return `${base}/pvcs`
    case 'container-storageclasses':
      return `${base}/storageclasses`
    default:
      return null
  }
})

function onMenuSelect(key: string) {
  router.push(key)
}

function goClusters() {
  router.push('/container/clusters')
}

const statusTagType = (s?: string) => {
  switch (s) {
    case 'Connected': return 'success' as const
    case 'Degraded': return 'warning' as const
    case 'Disconnected': return 'error' as const
    default: return 'default' as const
  }
}
</script>

<template>
  <n-layout has-sider class="container-shell">
    <n-layout-sider
      :bordered="false"
      :width="220"
      class="container-sider"
      content-style="display:flex;flex-direction:column;height:100%"
    >
      <div class="container-sider-header">
        <n-text strong class="container-sider-title" @click="goClusters">容器管理</n-text>
      </div>
      <n-scrollbar class="container-sider-menu">
        <n-menu
          :value="activeMenuKey"
          :options="menuOptions"
          :root-indent="12"
          :indent="14"
          @update:value="onMenuSelect"
        />
      </n-scrollbar>
      <div v-if="clusterStore.currentCluster" class="container-sider-footer">
        <div class="footer-cluster">
          <span class="footer-cluster-name" :title="clusterStore.currentCluster.alias || clusterStore.currentCluster.name">
            {{ clusterStore.currentCluster.alias || clusterStore.currentCluster.name }}
          </span>
          <n-tag :type="statusTagType(clusterStore.currentCluster.status)" size="tiny" round>
            {{ clusterStore.currentCluster.status }}
          </n-tag>
        </div>
      </div>
    </n-layout-sider>
    <n-layout>
      <header class="container-header">
        <div class="header-left">
          <h2 class="header-title" @click="goClusters">容器管理</h2>
          <span v-if="clusterStore.currentCluster" class="cluster-badge">
            {{ clusterStore.currentCluster.alias || clusterStore.currentCluster.name }}
            <span :class="['status-dot', clusterStore.currentCluster.status]" />
            {{ clusterStore.currentCluster.status }}
          </span>
          <div v-if="clusterStore.currentCluster" class="namespace-selector">
            <n-select
              v-model:value="clusterStore.currentNamespace"
              :options="namespaceOptions"
              size="small"
              style="width: 180px"
              clearable
              @update:value="(val: string) => clusterStore.setNamespace(val || '')"
            />
          </div>
        </div>
      </header>
      <n-layout-content class="container-content">
        <router-view />
      </n-layout-content>
    </n-layout>
  </n-layout>
</template>

<style scoped>
.container-shell {
  height: 100%;
  background: var(--wb-page-bg, #f5f7fb);
}

.container-sider {
  background: var(--wb-card-bg, #ffffff);
  border-right: 1px solid var(--wb-border, #e5e7eb);
}

.container-sider-header {
  display: flex;
  align-items: center;
  padding: 14px 14px 12px;
  border-bottom: 1px solid var(--wb-border, #e5e7eb);
}

.container-sider-title {
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  color: var(--wb-text, #1f2329);
}

.container-sider-menu {
  flex: 1;
  min-height: 0;
  padding: 8px 6px;
}

.container-sider-footer {
  padding: 10px 12px 14px;
  border-top: 1px solid var(--wb-border, #e5e7eb);
}

.footer-cluster {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.footer-cluster-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--wb-muted, #6b7280);
}

.container-header {
  display: flex;
  align-items: center;
  padding: 0 16px;
  height: 48px;
  border-bottom: 1px solid var(--wb-border, #e5e7eb);
  flex-shrink: 0;
  background: var(--wb-card-bg, #ffffff);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: 1;
  min-width: 0;
}

.header-title {
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  margin: 0;
  color: var(--wb-text, #1f2329);
  white-space: nowrap;
}

.cluster-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  background: var(--wb-chip-bg, #f8fafc);
  border: 1px solid var(--wb-border, #e5e7eb);
  border-radius: 4px;
  padding: 2px 10px;
  white-space: nowrap;
}

.namespace-selector {
  margin-left: auto;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}
.status-dot.Connected { background: #18a058; }
.status-dot.Degraded { background: #f0a020; }
.status-dot.Disconnected { background: #d03050; }
.status-dot.Unknown { background: #909399; }

.container-content {
  background: transparent;
  padding: 16px;
}
</style>