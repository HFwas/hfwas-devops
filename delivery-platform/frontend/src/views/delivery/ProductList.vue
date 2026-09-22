<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { fetchProducts, fetchStatus, importProduct } from '../../api/delivery'
import type { Product } from '../../types/delivery'
import { Package, Upload, RefreshCw, ChevronRight, ExternalLink, Search, Filter, Plus, CheckCircle2, AlertTriangle, XCircle, Clock } from 'lucide-vue-next'
import Badge from '../../components/ui/Badge.vue'

const router = useRouter()
const products = ref<Product[]>([])
const loading = ref(true)
const importing = ref(false)
const keyword = ref('')
const stats = ref({ total: 0, deployed: 0, failed: 0, notDeployed: 0 })

onMounted(async () => {
  try {
    const s = await fetchStatus()
    stats.value = s.data?.productCounts || stats.value
  } catch {}
  await loadProducts()
})

async function loadProducts() {
  loading.value = true
  try {
    const res = await fetchProducts({ pageNo: 1, pageSize: 100, keyword: keyword.value })
    products.value = res.records
  } catch (e) { console.error(e) }
  loading.value = false
}

async function handleImport() {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.zip,.gz,.tgz,.tar.gz'
  input.onchange = async () => {
    const file = input.files?.[0]
    if (!file) return
    importing.value = true
    try {
      await importProduct(file)
      await loadProducts()
    } catch (e: any) { alert('导入失败: ' + e.message) }
    importing.value = false
  }
  input.click()
}

function statusInfo(status: string) {
  switch (status) {
    case 'READY': return { dot: 'dot-ready', label: '运行中', bg: 'bg-green-50 text-green-700 border-green-200' }
    case 'DEGRADED': return { dot: 'dot-degraded', label: '降级', bg: 'bg-yellow-50 text-yellow-700 border-yellow-200' }
    case 'DEPLOYING': return { dot: 'dot-deploying', label: '部署中', bg: 'bg-blue-50 text-blue-700 border-blue-200' }
    case 'FAILED': return { dot: 'dot-failed', label: '失败', bg: 'bg-red-50 text-red-700 border-red-200' }
    default: return { dot: 'dot-pending', label: '未部署', bg: 'bg-gray-50 text-gray-600 border-gray-200' }
  }
}

function timeAgo(t: string) {
  if (!t) return ''
  const days = Math.floor((Date.now() - new Date(t).getTime()) / 86400000)
  if (days === 0) return '今天'
  if (days === 1) return '昨天'
  return `${days} 天前`
}

const filtered = computed(() => products.value)
</script>

<template>
  <div class="p-6 max-w-7xl mx-auto">
    <!-- Stats row -->
    <div class="grid grid-cols-4 gap-4 mb-6">
      <div class="bg-white rounded-xl border border-border p-4">
        <div class="flex items-center justify-between">
          <div>
            <div class="text-2xl font-bold text-foreground">{{ stats.total }}</div>
            <div class="text-xs text-muted-foreground mt-0.5">产品总数</div>
          </div>
          <div class="w-10 h-10 rounded-lg bg-blue-50 flex items-center justify-center"><Package class="w-5 h-5 text-blue-600" /></div>
        </div>
      </div>
      <div class="bg-white rounded-xl border border-border p-4">
        <div class="flex items-center justify-between">
          <div>
            <div class="text-2xl font-bold text-green-600">{{ stats.deployed }}</div>
            <div class="text-xs text-muted-foreground mt-0.5">运行中</div>
          </div>
          <div class="w-10 h-10 rounded-lg bg-green-50 flex items-center justify-center"><CheckCircle2 class="w-5 h-5 text-green-600" /></div>
        </div>
      </div>
      <div class="bg-white rounded-xl border border-border p-4">
        <div class="flex items-center justify-between">
          <div>
            <div class="text-2xl font-bold text-red-600">{{ stats.failed }}</div>
            <div class="text-xs text-muted-foreground mt-0.5">异常</div>
          </div>
          <div class="w-10 h-10 rounded-lg bg-red-50 flex items-center justify-center"><XCircle class="w-5 h-5 text-red-600" /></div>
        </div>
      </div>
      <div class="bg-white rounded-xl border border-border p-4">
        <div class="flex items-center justify-between">
          <div>
            <div class="text-2xl font-bold text-gray-500">{{ stats.notDeployed }}</div>
            <div class="text-xs text-muted-foreground mt-0.5">待部署</div>
          </div>
          <div class="w-10 h-10 rounded-lg bg-gray-50 flex items-center justify-center"><Clock class="w-5 h-5 text-gray-500" /></div>
        </div>
      </div>
    </div>

    <!-- Toolbar -->
    <div class="bg-white rounded-xl border border-border mb-4">
      <div class="flex items-center justify-between p-4">
        <div class="flex items-center gap-3">
          <div class="relative">
            <Search class="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
            <input v-model="keyword" placeholder="搜索产品..." @input="loadProducts"
              class="pl-9 pr-4 h-9 w-64 rounded-lg border border-input bg-background text-sm focus:outline-none focus:ring-2 focus:ring-ring/30" />
          </div>
        </div>
        <div class="flex items-center gap-2">
          <button @click="loadProducts" class="h-9 px-3 rounded-lg border border-border text-sm text-muted-foreground hover:bg-secondary transition-colors flex items-center gap-1.5">
            <RefreshCw class="w-4 h-4" /> 刷新
          </button>
          <button @click="handleImport" :disabled="importing"
            class="h-9 px-4 rounded-lg bg-primary text-primary-foreground text-sm font-medium hover:bg-primary/90 transition-colors flex items-center gap-1.5 disabled:opacity-50">
            <Upload class="w-4 h-4" /> {{ importing ? '导入中...' : '导入产品' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Content -->
    <div v-if="loading" class="flex justify-center py-20"><RefreshCw class="w-6 h-6 animate-spin text-muted-foreground" /></div>

    <div v-else-if="products.length === 0" class="bg-white rounded-xl border border-border py-20 text-center">
      <Package class="w-16 h-16 mx-auto text-muted-foreground/20 mb-4" />
      <h3 class="text-base font-medium text-muted-foreground">暂无产品</h3>
      <p class="text-sm text-muted-foreground/60 mt-1">导入产品包开始使用交付运维平台</p>
      <button @click="handleImport" class="mt-4 h-9 px-4 rounded-lg bg-primary text-primary-foreground text-sm font-medium hover:bg-primary/90 inline-flex items-center gap-1.5">
        <Plus class="w-4 h-4" /> 导入第一个产品
      </button>
    </div>

    <!-- Product cards -->
    <div v-else class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
      <div
        v-for="p in filtered"
        :key="p.id"
        @click="router.push(`/delivery/products/${p.id}`)"
        class="group bg-white rounded-xl border border-border hover:border-primary/30 hover:shadow-lg hover:shadow-primary/5 transition-all duration-200 cursor-pointer"
      >
        <div class="p-5">
          <!-- Top row -->
          <div class="flex items-start justify-between">
            <div class="flex items-center gap-3 min-w-0 flex-1">
              <div :class="['w-10 h-10 rounded-xl flex items-center justify-center shrink-0', statusInfo(p.status).bg]">
                <div :class="statusInfo(p.status).dot" />
              </div>
              <div class="min-w-0">
                <h3 class="font-semibold text-sm text-foreground truncate">{{ p.displayName }}</h3>
                <div class="flex items-center gap-2 mt-0.5">
                  <span class="text-xs text-muted-foreground">v{{ p.packageVersion }}</span>
                </div>
              </div>
            </div>
            <ChevronRight class="w-4 h-4 text-muted-foreground/30 group-hover:text-muted-foreground/60 transition-colors mt-1 shrink-0" />
          </div>

          <!-- Status row -->
          <div class="mt-4 flex items-center gap-2">
            <span :class="['inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium border', statusInfo(p.status).bg]">
              <div :class="['w-1.5 h-1.5 rounded-full', statusInfo(p.status).dot.replace('dot-', 'bg-') || 'bg-gray-400']" />
              {{ statusInfo(p.status).label }}
            </span>
            <span v-if="p.clusterName" class="text-xs text-muted-foreground bg-secondary px-2 py-1 rounded-full">{{ p.clusterName }}</span>
            <span v-if="p.lastDeployTime" class="text-xs text-muted-foreground/60 ml-auto">{{ timeAgo(p.lastDeployTime) }}</span>
          </div>

          <!-- Stats bar -->
          <div v-if="p.aggregatedSummary" class="mt-4 pt-4 border-t border-border/50">
            <div class="flex items-center gap-4 text-xs">
              <div class="flex items-center gap-1.5">
                <div class="w-1.5 h-1.5 rounded-full bg-green-500" />
                <span class="text-muted-foreground">组件</span>
                <span class="font-medium text-foreground">{{ p.aggregatedSummary.readyComponents }}/{{ p.aggregatedSummary.totalComponents }}</span>
              </div>
              <div class="flex items-center gap-1.5">
                <div class="w-1.5 h-1.5 rounded-full bg-blue-500" />
                <span class="text-muted-foreground">服务</span>
                <span class="font-medium text-foreground">{{ p.aggregatedSummary.readyServices }}/{{ p.aggregatedSummary.totalServices }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>