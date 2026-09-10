<script setup lang="ts">
import { NButton } from 'naive-ui'
import { useClusterStore } from '@/modules/container/stores/cluster'
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'

const clusterStore = useClusterStore()
const router = useRouter()

onMounted(() => {
  if (clusterStore.clusterList.length === 0) {
    clusterStore.fetchClusters().catch(() => {})
  }
})

function goClusters() {
  router.push('/container/clusters')
}
</script>

<template>
  <div class="container-shell">
    <header class="container-header">
      <div class="header-left">
        <h2 class="header-title" @click="goClusters">容器管理</h2>
        <span v-if="clusterStore.currentCluster" class="cluster-badge">
          {{ clusterStore.currentCluster.alias || clusterStore.currentCluster.name }}
          <span :class="['status-dot', clusterStore.currentCluster.status]" />
          {{ clusterStore.currentCluster.status }}
        </span>
      </div>
      <div class="header-nav">
        <n-button
          v-if="clusterStore.currentCluster"
          text
          tag="a"
          :type="router.currentRoute.value.name === 'container-clusters' ? 'primary' : 'default'"
          @click="router.push('/container/clusters')"
        >
          集群
        </n-button>
        <n-button
          v-if="clusterStore.currentCluster"
          text
          tag="a"
          :type="String(router.currentRoute.value.name)?.startsWith('container-node') ? 'primary' : 'default'"
          @click="router.push(`/container/clusters/${clusterStore.currentCluster.id}/nodes`)"
        >
          Node
        </n-button>
        <n-button
          v-if="clusterStore.currentCluster"
          text
          tag="a"
          :type="String(router.currentRoute.value.name)?.startsWith('container-pod') ? 'primary' : 'default'"
          @click="router.push(`/container/clusters/${clusterStore.currentCluster.id}/pods`)"
        >
          Pod
        </n-button>
        <n-button
          v-if="clusterStore.currentCluster"
          text
          tag="a"
          :type="router.currentRoute.value.name === 'container-deployments' ? 'primary' : 'default'"
          @click="router.push(`/container/clusters/${clusterStore.currentCluster.id}/deployments`)"
        >
          Deployment
        </n-button>
        <n-button
          v-if="clusterStore.currentCluster"
          text
          tag="a"
          :type="router.currentRoute.value.name === 'container-services' ? 'primary' : 'default'"
          @click="router.push(`/container/clusters/${clusterStore.currentCluster.id}/services`)"
        >
          Service
        </n-button>
      </div>
    </header>
    <main class="container-content">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.container-shell {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.container-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  height: 48px;
  border-bottom: 1px solid var(--wb-border);
  flex-shrink: 0;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}
.header-title {
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  margin: 0;
}
.cluster-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  background: var(--wb-card-bg);
  border: 1px solid var(--wb-border);
  border-radius: 4px;
  padding: 2px 10px;
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
.header-nav {
  display: flex;
  gap: 4px;
}
.container-content {
  flex: 1;
  overflow: auto;
  padding: 16px;
}
</style>