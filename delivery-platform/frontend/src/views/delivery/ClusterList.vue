<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { fetchClusters, importCluster, setCurrentCluster, deleteCluster } from '../../api/delivery'
import type { Cluster } from '../../types/delivery'
import { Server, Plus, Trash2, CheckCircle2, XCircle, Activity, Globe, ChevronRight, Copy, Terminal, Key } from 'lucide-vue-next'

const clusters = ref<Cluster[]>([])
const loading = ref(true)
const showImport = ref(false)
const importName = ref('')
const importKubeconfig = ref('')
const importing = ref(false)

onMounted(() => loadClusters())

async function loadClusters() {
  loading.value = true
  try { clusters.value = await fetchClusters() } catch (e) { console.error(e) }
  loading.value = false
}

async function handleImport() {
  if (!importName.value || !importKubeconfig.value) return
  importing.value = true
  try {
    await importCluster(importName.value, importKubeconfig.value)
    showImport.value = false; importName.value = ''; importKubeconfig.value = ''
    await loadClusters()
  } catch (e: any) { alert('导入失败: ' + e.message) }
  importing.value = false
}

async function handleSetCurrent(id: number) {
  try { await setCurrentCluster(id); await loadClusters() } catch (e: any) { alert(e.message) }
}

async function handleDelete(id: number) {
  if (!confirm('确定删除此集群？')) return
  try { await deleteCluster(id); await loadClusters() } catch (e: any) { alert(e.message) }
}

function statusStyle(s: string) {
  switch(s) {
    case 'UP': return { dot: 'dot-ready', label: '在线', cls: 'text-green-600 bg-green-50' }
    case 'DOWN': return { dot: 'dot-failed', label: '离线', cls: 'text-red-600 bg-red-50' }
    default: return { dot: 'dot-pending', label: '未知', cls: 'text-gray-600 bg-gray-50' }
  }
}
</script>

<template>
  <div class="p-6 max-w-7xl mx-auto">
    <!-- Page header -->
    <div class="flex items-center justify-between mb-6">
      <div>
        <h1 class="text-xl font-bold text-foreground">集群管理</h1>
        <p class="text-sm text-muted-foreground mt-0.5">{{ clusters.length }} 个集群 · 管理 Kubernetes 集群连接</p>
      </div>
      <button @click="showImport = !showImport"
        class="h-9 px-4 rounded-lg bg-primary text-primary-foreground text-sm font-medium hover:bg-primary/90 transition-colors flex items-center gap-1.5">
        <Plus class="w-4 h-4" /> 导入集群
      </button>
    </div>

    <!-- Import panel -->
    <div v-if="showImport" class="mb-6 bg-white rounded-xl border border-primary/20 shadow-lg shadow-primary/5 overflow-hidden">
      <div class="px-5 py-3.5 bg-gradient-to-r from-primary/5 to-transparent border-b border-border/50">
        <h3 class="font-medium text-sm text-foreground flex items-center gap-2"><Key class="w-4 h-4 text-primary" />导入 Kubernetes 集群</h3>
      </div>
      <div class="p-5 space-y-4">
        <div class="grid grid-cols-2 gap-4">
          <div class="space-y-1.5">
            <label class="text-sm font-medium text-foreground">集群名称</label>
            <input v-model="importName" placeholder="例如：production-cluster"
              class="w-full h-9 px-3 rounded-lg border border-input bg-background text-sm focus:outline-none focus:ring-2 focus:ring-ring/30 focus:border-ring transition-colors" />
            <p class="text-xs text-muted-foreground">唯一标识，导入后不可重名</p>
          </div>
        </div>
        <div class="space-y-1.5">
          <label class="text-sm font-medium text-foreground">Kubeconfig 内容</label>
          <textarea v-model="importKubeconfig" rows="5" placeholder="apiVersion: v1&#10;clusters:&#10;- cluster:&#10;    server: https://...&#10;  name: ..."
            class="w-full px-3 py-2 rounded-lg border border-input bg-background text-sm font-mono focus:outline-none focus:ring-2 focus:ring-ring/30 focus:border-ring transition-colors" />
          <p class="text-xs text-muted-foreground">粘贴 kubeconfig 文件内容，AES-256 加密存储</p>
        </div>
        <div class="flex items-center gap-2 justify-end pt-2">
          <button @click="showImport = false" class="h-9 px-4 rounded-lg border border-border text-sm text-muted-foreground hover:bg-secondary transition-colors">取消</button>
          <button @click="handleImport" :disabled="importing || !importName || !importKubeconfig"
            class="h-9 px-4 rounded-lg bg-primary text-primary-foreground text-sm font-medium hover:bg-primary/90 transition-colors disabled:opacity-50 flex items-center gap-1.5">
            <Activity class="w-4 h-4" /> {{ importing ? '验证连接中...' : '导入并验证' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Loading -->
    <div v-if="loading" class="flex justify-center py-20"><Activity class="w-6 h-6 animate-spin text-muted-foreground" /></div>

    <!-- Empty -->
    <div v-else-if="clusters.length === 0" class="bg-white rounded-xl border border-border py-20 text-center">
      <Server class="w-16 h-16 mx-auto text-muted-foreground/20 mb-4" />
      <h3 class="text-base font-medium text-muted-foreground">暂无集群</h3>
      <p class="text-sm text-muted-foreground/60 mt-1">导入 kubeconfig 开始管理集群</p>
      <button @click="showImport = true" class="mt-4 h-9 px-4 rounded-lg bg-primary text-primary-foreground text-sm font-medium hover:bg-primary/90 inline-flex items-center gap-1.5">
        <Plus class="w-4 h-4" /> 导入第一个集群
      </button>
    </div>

    <!-- Cluster cards -->
    <div v-else class="space-y-3">
      <div v-for="c in clusters" :key="c.id"
        class="group bg-white rounded-xl border border-border hover:border-primary/20 hover:shadow-md transition-all duration-200 p-5">
        <div class="flex items-center justify-between">
          <div class="flex items-center gap-4 min-w-0 flex-1">
            <div :class="['w-10 h-10 rounded-xl flex items-center justify-center shrink-0', c.status === 'UP' ? 'bg-green-50' : c.status === 'DOWN' ? 'bg-red-50' : 'bg-gray-50']">
              <Server :class="['w-5 h-5', c.status === 'UP' ? 'text-green-600' : c.status === 'DOWN' ? 'text-red-500' : 'text-gray-400']" />
            </div>
            <div class="min-w-0">
              <div class="flex items-center gap-2.5">
                <h3 class="font-semibold text-sm text-foreground">{{ c.name }}</h3>
                <span v-if="c.isCurrent"
                  class="text-[10px] px-1.5 py-0.5 rounded-md bg-primary/10 text-primary font-semibold border border-primary/20">当前</span>
                <span :class="['inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full text-xs font-medium border', statusStyle(c.status).cls]">
                  <div :class="['w-1.5 h-1.5 rounded-full', statusStyle(c.status).dot]" />
                  {{ statusStyle(c.status).label }}
                </span>
              </div>
              <div class="flex items-center gap-3 mt-1 text-xs text-muted-foreground">
                <span class="flex items-center gap-1"><Globe class="w-3 h-3" />{{ c.serverHost || '-' }}</span>
                <span v-if="c.version">Kubernetes {{ c.version }}</span>
              </div>
            </div>
          </div>

          <div class="flex items-center gap-2">
            <button v-if="!c.isCurrent" @click="handleSetCurrent(c.id)"
              class="h-8 px-3 rounded-lg border border-border text-xs text-muted-foreground hover:bg-secondary hover:text-foreground transition-colors">
              设为当前
            </button>
            <button @click="handleDelete(c.id)"
              class="h-8 w-8 rounded-lg text-muted-foreground hover:text-red-500 hover:bg-red-50 transition-colors flex items-center justify-center">
              <Trash2 class="w-4 h-4" />
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>