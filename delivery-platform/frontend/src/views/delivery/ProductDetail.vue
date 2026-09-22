<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchProductDetail, saveProductParams, deployProduct, uninstallProduct, fetchDeployments, fetchDeployment, fetchProductComponents } from '../../api/delivery'
import type { ProductDetail, FormField, Deployment, ComponentInfo } from '../../types/delivery'
import { ArrowLeft, Save, Play, Trash2, RotateCcw, Clock, CheckCircle2, AlertTriangle, XCircle, RefreshCw, History, Layers, Terminal, FileText, ChevronDown, ChevronRight, Boxes, ListOrdered } from 'lucide-vue-next'
import Badge from '../../components/ui/Badge.vue'

const route = useRoute()
const router = useRouter()
const product = ref<ProductDetail | null>(null)
const deployments = ref<Deployment[]>([])
const loading = ref(true)
const editedValues = ref<Record<string, any>>({})
const deploying = ref(false)
const deployResult = ref<Deployment | null>(null)
const pollingInterval = ref<ReturnType<typeof setInterval> | null>(null)
const activeTab = ref<'params' | 'components' | 'history' | 'resources'>('params')
const components = ref<ComponentInfo[]>([])
const expanded = ref<Record<string, boolean>>({})

// 组件状态读自集群里的 CloudComponent CR；读不到时置空而不是报错
async function loadComponents(id: number) {
  try {
    components.value = await fetchProductComponents(id)
  } catch (e) {
    console.error(e)
    components.value = []
  }
}

function toggleComponent(name: string) {
  expanded.value[name] = !expanded.value[name]
}

// releaseMismatch：期望发布号与实际观察到的发布号不一致，说明这次发布还在切换中
function releaseMismatch(c: ComponentInfo) {
  return !!c.releaseID && c.releaseID !== c.observedReleaseID
}

function phaseDot(phase?: string) {
  switch (phase) {
    case 'Ready': return 'dot-ready'
    case 'Failed': return 'dot-failed'
    case 'Degraded': return 'dot-degraded'
    case 'Deploying': return 'dot-deploying'
    default: return 'dot-pending'
  }
}

function phaseBadge(phase?: string) {
  switch (phase) {
    case 'Ready': return 'bg-green-50 text-green-700 border-green-200'
    case 'Failed': return 'bg-red-50 text-red-700 border-red-200'
    case 'Degraded': return 'bg-yellow-50 text-yellow-700 border-yellow-200'
    case 'Deploying': return 'bg-blue-50 text-blue-700 border-blue-200'
    default: return 'bg-gray-50 text-gray-600 border-gray-200'
  }
}

function taskDot(phase?: string) {
  switch (phase) {
    case 'Succeeded': return 'dot-ready'
    case 'Failed': return 'dot-failed'
    case 'Running': return 'dot-deploying'
    default: return 'dot-pending'
  }
}

onMounted(async () => {
  const id = Number(route.params.id)
  try {
    product.value = await fetchProductDetail(id)
    deployments.value = await fetchDeployments(id)
    const active = deployments.value.find(d => d.status === 'PENDING' || d.status === 'RUNNING')
    if (active) { deployResult.value = active; startPolling(active.id) }
  } catch (e) { console.error(e) }
  await loadComponents(id)
  loading.value = false
})

onUnmounted(() => stopPolling())

function startPolling(id: number) {
  pollingInterval.value = setInterval(async () => {
    try {
      const dep = await fetchDeployment(id); deployResult.value = dep
      if (['SUCCEEDED','FAILED','CANCELLED'].includes(dep.status)) {
        stopPolling()
        if (product.value) product.value = await fetchProductDetail(product.value.id)
        deployments.value = await fetchDeployments(Number(route.params.id))
        await loadComponents(Number(route.params.id))
      }
    } catch {}
  }, 2000)
}

function stopPolling() {
  if (pollingInterval.value) { clearInterval(pollingInterval.value); pollingInterval.value = null }
}

async function handleSave() {
  if (!product.value) return
  await saveProductParams(product.value.id, {
    globalParams: extractGroup('globalParams'), params: extractGroup('params'), overrides: extractGroup('overrides')
  })
}

async function handleDeploy() {
  if (!product.value) return; deploying.value = true
  try {
    const dep = await deployProduct(product.value.id); deployResult.value = dep; startPolling(dep.id)
  } catch (e: any) { alert('部署失败: ' + e.message) }
  deploying.value = false
}

async function handleUninstall() {
  if (!product.value || !confirm('确定卸载此产品？')) return
  try {
    await uninstallProduct(product.value.id)
    product.value = await fetchProductDetail(product.value.id)
    deployments.value = await fetchDeployments(product.value.id)
    await loadComponents(product.value.id)
  } catch (e: any) { alert(e.message) }
}

function extractGroup(prefix: string): Record<string, any> {
  const r: Record<string, any> = {}
  for (const [k, v] of Object.entries(editedValues.value))
    if (k.startsWith(prefix)) r[k.slice(prefix.length + 1)] = v
  return r
}

function onFieldChange(field: FormField, value: any) {
  editedValues.value[field.path] = value
}

const statusMeta = (s: string) => {
  switch(s) {
    case 'READY': return { dot: 'dot-ready', label: '运行中', icon: CheckCircle2, cls: 'text-green-600 bg-green-50 border-green-200' }
    case 'DEGRADED': return { dot: 'dot-degraded', label: '降级', icon: AlertTriangle, cls: 'text-yellow-600 bg-yellow-50 border-yellow-200' }
    case 'DEPLOYING': case 'RUNNING': return { dot: 'dot-deploying', label: '部署中', icon: RefreshCw, cls: 'text-blue-600 bg-blue-50 border-blue-200' }
    case 'FAILED': return { dot: 'dot-failed', label: '失败', icon: XCircle, cls: 'text-red-600 bg-red-50 border-red-200' }
    default: return { dot: 'dot-pending', label: '未部署', icon: Clock, cls: 'text-gray-600 bg-gray-50 border-gray-200' }
  }
}

const deployMeta = (s: string) => ({
  'SUCCEEDED': { dot: 'dot-ready', label: '成功', cls: 'text-green-600 bg-green-50' },
  'FAILED': { dot: 'dot-failed', label: '失败', cls: 'text-red-600 bg-red-50' },
  'RUNNING': { dot: 'dot-deploying', label: '运行中', cls: 'text-blue-600 bg-blue-50' },
  'PENDING': { dot: 'dot-pending', label: '等待中', cls: 'text-gray-600 bg-gray-50' },
  'CANCELLED': { dot: 'dot-pending', label: '已取消', cls: 'text-gray-500 bg-gray-50' },
}[s] || { dot: 'dot-pending', label: s, cls: 'text-gray-600 bg-gray-50' })
</script>

<template>
  <div class="p-6 max-w-7xl mx-auto">
    <div v-if="loading" class="flex justify-center py-20"><RefreshCw class="w-6 h-6 animate-spin text-muted-foreground" /></div>

    <template v-else-if="product">
      <!-- Back + title bar -->
      <div class="flex items-center justify-between mb-6">
        <div class="flex items-center gap-4">
          <button @click="router.push('/delivery/products')" class="p-2 rounded-lg hover:bg-secondary text-muted-foreground hover:text-foreground transition-colors">
            <ArrowLeft class="w-4 h-4" />
          </button>
          <div>
            <div class="flex items-center gap-3">
              <h1 class="text-xl font-bold text-foreground">{{ product.displayName }}</h1>
              <span class="text-sm text-muted-foreground bg-secondary px-2 py-0.5 rounded-md font-mono">v{{ product.packageVersion }}</span>
              <Badge :variant="product.status === 'READY' ? 'success' : product.status === 'FAILED' ? 'danger' : product.status === 'DEPLOYING' ? 'default' : 'default'">
                {{ statusMeta(product.status).label }}
              </Badge>
            </div>
            <div class="flex items-center gap-3 mt-1 text-xs text-muted-foreground">
              <span v-if="product.clusterName">集群: {{ product.clusterName }}</span>
              <span v-if="product.targetNs">命名空间: {{ product.targetNs }}</span>
              <span>Key: {{ product.productKey }}</span>
            </div>
          </div>
        </div>
        <div class="flex items-center gap-2">
          <button @click="handleSave" class="h-9 px-3 rounded-lg border border-border text-sm text-muted-foreground hover:bg-secondary transition-colors flex items-center gap-1.5">
            <Save class="w-4 h-4" /> 保存
          </button>
          <button @click="handleDeploy" :disabled="deploying"
            class="h-9 px-4 rounded-lg bg-primary text-primary-foreground text-sm font-medium hover:bg-primary/90 transition-colors flex items-center gap-1.5 disabled:opacity-50">
            <Play class="w-4 h-4" /> {{ deploying ? '部署中...' : product.status === 'NOT_DEPLOYED' ? '部署' : '升级' }}
          </button>
          <button v-if="product.status !== 'NOT_DEPLOYED'" @click="handleUninstall"
            class="h-9 px-3 rounded-lg border border-red-200 text-red-600 text-sm hover:bg-red-50 transition-colors flex items-center gap-1.5">
            <Trash2 class="w-4 h-4" /> 卸载
          </button>
        </div>
      </div>

      <!-- Deploy progress banner -->
      <div v-if="deployResult && (deployResult.status === 'PENDING' || deployResult.status === 'RUNNING')" class="mb-6 bg-gradient-to-r from-blue-50 to-blue-50/50 border border-blue-200 rounded-xl p-4">
        <div class="flex items-center gap-4">
          <RefreshCw class="w-5 h-5 text-blue-600 animate-spin shrink-0" />
          <div class="flex-1 min-w-0">
            <p class="text-sm font-medium text-blue-700">{{ deployResult.action === 'ROLLBACK' ? '回滚' : deployResult.action === 'UNINSTALL' ? '卸载' : '部署' }}中...</p>
            <p class="text-xs text-blue-500/80 mt-0.5">v{{ deployResult.packageVersion }} · {{ deployResult.appName }}</p>
          </div>
          <div class="flex items-center gap-2">
            <div class="w-1.5 h-1.5 rounded-full bg-blue-500 animate-pulse" />
            <span class="text-xs text-blue-600 font-medium">进行中</span>
          </div>
        </div>
      </div>

      <div v-if="deployResult && deployResult.status === 'SUCCEEDED'" class="mb-6 bg-gradient-to-r from-green-50 to-green-50/50 border border-green-200 rounded-xl p-4">
        <div class="flex items-center gap-4">
          <CheckCircle2 class="w-5 h-5 text-green-600 shrink-0" />
          <div class="flex-1 min-w-0">
            <p class="text-sm font-medium text-green-700">部署成功</p>
            <p class="text-xs text-green-500/80 mt-0.5">v{{ deployResult.packageVersion }}</p>
          </div>
          <span class="text-xs text-green-600 font-medium">已完成</span>
        </div>
      </div>

      <!-- Tab bar -->
      <div class="flex items-center gap-1 mb-6 border-b border-border">
        <button @click="activeTab = 'params'" :class="['px-4 py-2.5 text-sm font-medium border-b-2 transition-colors -mb-px', activeTab === 'params' ? 'border-primary text-primary' : 'border-transparent text-muted-foreground hover:text-foreground']">
          <Layers class="w-4 h-4 inline mr-1.5" />参数配置
        </button>
        <button @click="activeTab = 'components'" :class="['px-4 py-2.5 text-sm font-medium border-b-2 transition-colors -mb-px', activeTab === 'components' ? 'border-primary text-primary' : 'border-transparent text-muted-foreground hover:text-foreground']">
          <Boxes class="w-4 h-4 inline mr-1.5" />组件
        </button>
        <button @click="activeTab = 'history'" :class="['px-4 py-2.5 text-sm font-medium border-b-2 transition-colors -mb-px', activeTab === 'history' ? 'border-primary text-primary' : 'border-transparent text-muted-foreground hover:text-foreground']">
          <History class="w-4 h-4 inline mr-1.5" />部署历史
        </button>
        <button @click="activeTab = 'resources'" :class="['px-4 py-2.5 text-sm font-medium border-b-2 transition-colors -mb-px', activeTab === 'resources' ? 'border-primary text-primary' : 'border-transparent text-muted-foreground hover:text-foreground']">
          <Terminal class="w-4 h-4 inline mr-1.5" />运行资源
        </button>
      </div>

      <!-- Tab: Params -->
      <div v-if="activeTab === 'params'" class="grid grid-cols-[1fr_320px] gap-6">
        <div class="space-y-4">
          <div v-for="group in product.form?.groups" :key="group.id" class="bg-white rounded-xl border border-border overflow-hidden">
            <div class="px-5 py-3.5 bg-gradient-to-r from-gray-50 to-white border-b border-border/50">
              <h3 class="font-medium text-sm text-foreground flex items-center gap-2">
                <Layers class="w-4 h-4 text-muted-foreground" />{{ group.title }}
              </h3>
            </div>
            <div class="p-5 space-y-5">
              <div v-for="field in group.fields" :key="field.path" class="space-y-1.5">
                <label class="flex items-center gap-1 text-sm font-medium text-foreground">
                  {{ field.label }}
                  <span v-if="field.required" class="text-red-500 text-xs">*</span>
                </label>
                <p v-if="field.path === 'globalParams.imageRegistry' || field.path === 'globalParams.defaultStorageClass' || field.path === 'globalParams.domainSuffix'" class="text-xs text-muted-foreground mb-1">
                  {{ field.path === 'globalParams.imageRegistry' ? '镜像拉取地址前缀，例如 registry.example.com' : field.path === 'globalParams.defaultStorageClass' ? '集群默认 StorageClass 名称' : '产品访问域名后缀' }}
                </p>
                <!-- String / Password -->
                <div v-if="field.type === 'string' || field.type === 'password'" class="relative">
                  <input :type="field.type === 'password' ? 'password' : 'text'" :placeholder="field.placeholder"
                    :value="editedValues[field.path] ?? field.default ?? ''"
                    @input="onFieldChange(field, ($event.target as HTMLInputElement).value)"
                    class="w-full h-9 px-3 rounded-lg border border-input bg-background text-sm focus:outline-none focus:ring-2 focus:ring-ring/30 focus:border-ring transition-colors" />
                </div>
                <!-- Integer / Number -->
                <input v-else-if="field.type === 'integer' || field.type === 'number'" type="number"
                  :min="field.validation?.minimum" :max="field.validation?.maximum"
                  :value="editedValues[field.path] ?? field.default ?? ''"
                  @input="onFieldChange(field, Number(($event.target as HTMLInputElement).value))"
                  class="w-full h-9 px-3 rounded-lg border border-input bg-background text-sm focus:outline-none focus:ring-2 focus:ring-ring/30 focus:border-ring transition-colors" />
                <!-- Boolean -->
                <label v-else-if="field.type === 'boolean'" class="flex items-center gap-2.5 cursor-pointer">
                  <input type="checkbox" :checked="editedValues[field.path] ?? field.default ?? false"
                    @change="onFieldChange(field, ($event.target as HTMLInputElement).checked)"
                    class="w-4 h-4 rounded border-input text-primary focus:ring-primary" />
                  <span class="text-sm text-foreground">{{ field.description || field.label }}</span>
                </label>
                <!-- Select -->
                <select v-else-if="field.type === 'select'"
                  :value="editedValues[field.path] ?? field.default ?? ''"
                  @change="onFieldChange(field, ($event.target as HTMLSelectElement).value)"
                  class="w-full h-9 px-3 rounded-lg border border-input bg-background text-sm focus:outline-none focus:ring-2 focus:ring-ring/30 focus:border-ring transition-colors">
                  <option value="" disabled>请选择...</option>
                  <option v-for="opt in field.options" :key="opt.value" :value="opt.value">{{ opt.label }}</option>
                </select>
              </div>
            </div>
          </div>
        </div>

        <!-- Sidebar -->
        <div class="space-y-4">
          <!-- Component status -->
          <div v-if="product.aggregatedSummary" class="bg-white rounded-xl border border-border overflow-hidden">
            <div class="px-5 py-3.5 border-b border-border/50">
              <h3 class="font-medium text-sm text-foreground flex items-center gap-2"><Layers class="w-4 h-4 text-muted-foreground" />组件状态</h3>
            </div>
            <div class="p-4">
              <div class="space-y-3">
                <div class="flex items-center justify-between py-1.5">
                  <span class="text-sm text-muted-foreground">组件</span>
                  <span class="text-sm font-medium">{{ product.aggregatedSummary.readyComponents }}/{{ product.aggregatedSummary.totalComponents }}</span>
                </div>
                <div class="w-full h-1.5 bg-secondary rounded-full overflow-hidden">
                  <div class="h-full bg-green-500 rounded-full transition-all" :style="{ width: `${product.aggregatedSummary.totalComponents ? (product.aggregatedSummary.readyComponents / product.aggregatedSummary.totalComponents) * 100 : 0}%` }" />
                </div>
                <div class="flex items-center justify-between py-1.5">
                  <span class="text-sm text-muted-foreground">服务</span>
                  <span class="text-sm font-medium">{{ product.aggregatedSummary.readyServices }}/{{ product.aggregatedSummary.totalServices }}</span>
                </div>
                <div v-if="product.serviceStatuses" v-for="svc in product.serviceStatuses" :key="svc.name"
                  class="flex items-center gap-2.5 py-1.5 text-sm">
                  <div :class="svc.phase === 'Ready' ? 'dot-ready' : svc.phase === 'Failed' ? 'dot-failed' : 'dot-degraded'" />
                  <span class="text-foreground flex-1 min-w-0 truncate">{{ svc.name }}</span>
                  <span class="text-xs text-muted-foreground">{{ svc.phase }}</span>
                </div>
              </div>
            </div>
          </div>

          <!-- Quick actions -->
          <div class="bg-white rounded-xl border border-border overflow-hidden">
            <div class="px-5 py-3.5 border-b border-border/50">
              <h3 class="font-medium text-sm text-foreground flex items-center gap-2"><Terminal class="w-4 h-4 text-muted-foreground" />快速操作</h3>
            </div>
            <div class="p-4 space-y-2">
              <button class="w-full h-9 px-3 rounded-lg text-sm border border-border text-muted-foreground hover:bg-secondary transition-colors flex items-center gap-2">
                <FileText class="w-4 h-4" /> 查看部署日志
              </button>
              <button class="w-full h-9 px-3 rounded-lg text-sm border border-border text-muted-foreground hover:bg-secondary transition-colors flex items-center gap-2">
                <Layers class="w-4 h-4" /> 查看资源拓扑
              </button>
            </div>
          </div>
        </div>
      </div>

      <!-- Tab: Components -->
      <div v-if="activeTab === 'components'" class="space-y-4">
        <!-- 发布步骤：ProductTask，表达「init job 先跑完，网关再起」这类顺序 -->
        <div v-if="product.tasks && product.tasks.length" class="bg-white rounded-xl border border-border overflow-hidden">
          <div class="px-5 py-3.5 border-b border-border/50 flex items-center justify-between">
            <h3 class="font-medium text-sm text-foreground flex items-center gap-2">
              <ListOrdered class="w-4 h-4 text-muted-foreground" />发布步骤
            </h3>
            <span class="text-xs text-muted-foreground">release {{ product.releaseID || '—' }}</span>
          </div>
          <div class="divide-y divide-border/40">
            <div v-for="t in product.tasks" :key="t.name" class="px-5 py-3 flex items-start gap-3">
              <div :class="taskDot(t.phase)" class="mt-1.5 shrink-0" />
              <div class="min-w-0 flex-1">
                <div class="flex items-center gap-2">
                  <span class="text-sm font-medium text-foreground">{{ t.name }}</span>
                  <span v-if="t.opsType" class="text-xs text-muted-foreground bg-secondary px-2 py-0.5 rounded-full">{{ t.opsType }}</span>
                  <span class="text-xs text-muted-foreground ml-auto">{{ t.phase || 'Pending' }}</span>
                </div>
                <div class="text-xs text-muted-foreground mt-1 flex flex-wrap gap-x-3">
                  <span v-if="t.component">闸住组件：{{ t.component }}</span>
                  <span v-if="t.resource">作用对象：{{ t.resource }}</span>
                  <span v-if="t.after && t.after.length">前置：{{ t.after.join(', ') }}</span>
                </div>
                <div v-if="t.messages && t.messages.length" class="text-xs text-muted-foreground/70 mt-1">
                  {{ t.messages[t.messages.length - 1] }}
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 组件列表：来自集群里的 CloudComponent CR -->
        <div v-if="components.length === 0" class="bg-white rounded-xl border border-border p-12 text-center">
          <Boxes class="w-12 h-12 mx-auto text-muted-foreground/20 mb-3" />
          <p class="text-sm text-muted-foreground">暂无组件</p>
          <p class="text-xs text-muted-foreground/60 mt-1">组件读自集群里的 CloudComponent CR；未部署或集群不可达时为空</p>
        </div>

        <div v-for="c in components" :key="c.name" class="bg-white rounded-xl border border-border overflow-hidden">
          <div class="px-5 py-3.5 flex items-center gap-3 cursor-pointer" @click="toggleComponent(c.name)">
            <div :class="phaseDot(c.phase)" class="shrink-0" />
            <div class="min-w-0 flex-1">
              <div class="flex items-center gap-2">
                <span class="text-sm font-medium text-foreground truncate">{{ c.displayName || c.name }}</span>
                <span v-if="c.componentType" class="text-xs text-muted-foreground bg-secondary px-2 py-0.5 rounded-full">{{ c.componentType }}</span>
                <span v-if="c.chartVersion" class="text-xs text-muted-foreground">chart {{ c.chartVersion }}</span>
              </div>
              <div class="flex items-center gap-3 mt-0.5 text-xs text-muted-foreground">
                <span v-if="c.summary">{{ c.summary.readyWorkloads }}/{{ c.summary.totalWorkloads }} workload 就绪</span>
                <span v-if="c.releaseID">release {{ c.releaseID }}</span>
                <span v-if="releaseMismatch(c)" class="text-amber-600">observed {{ c.observedReleaseID || '—' }}（切换中）</span>
              </div>
            </div>
            <span :class="[phaseBadge(c.phase), 'text-xs px-2.5 py-1 rounded-full border shrink-0']">{{ c.phase || 'unknown' }}</span>
            <component :is="expanded[c.name] ? ChevronDown : ChevronRight" class="w-4 h-4 text-muted-foreground shrink-0" />
          </div>

          <div v-if="expanded[c.name]" class="border-t border-border/50">
            <!-- 期望与实际差异 -->
            <div v-if="c.diffs && c.diffs.length" class="px-5 py-3 border-b border-border/40">
              <div class="text-xs font-medium text-foreground mb-2 flex items-center gap-1.5">
                <AlertTriangle class="w-3.5 h-3.5 text-amber-500" />期望与实际差异
              </div>
              <div v-for="(d, i) in c.diffs" :key="i" class="text-xs text-muted-foreground py-1 flex flex-wrap items-center gap-2">
                <span class="font-mono">{{ d.kind }}/{{ d.name }}</span>
                <span class="font-mono text-muted-foreground/70">{{ d.path }}</span>
                <span class="line-through">{{ d.expected }}</span>
                <span>→</span>
                <span class="text-amber-600">{{ d.current }}</span>
                <span v-if="d.message" class="text-muted-foreground/60">{{ d.message }}</span>
              </div>
            </div>

            <!-- Pod 明细 -->
            <div v-if="c.pods && c.pods.length" class="px-5 py-3">
              <div class="text-xs font-medium text-foreground mb-2">Pod 明细</div>
              <table class="w-full text-xs">
                <thead>
                  <tr class="text-muted-foreground border-b border-border/40">
                    <th class="text-left py-1.5 font-medium">Pod</th>
                    <th class="text-left py-1.5 font-medium">工作负载</th>
                    <th class="text-left py-1.5 font-medium">容器状态</th>
                    <th class="text-right py-1.5 font-medium">重启</th>
                    <th class="text-left py-1.5 font-medium">修订</th>
                    <th class="text-left py-1.5 font-medium">节点</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="p in c.pods" :key="p.name" class="border-b border-border/20">
                    <td class="py-1.5 font-mono truncate max-w-[220px]">{{ p.name }}</td>
                    <td class="py-1.5 text-muted-foreground">{{ p.workloadKind }}/{{ p.workloadName }}</td>
                    <td class="py-1.5">
                      <span :class="p.ready ? 'text-green-600' : 'text-amber-600'">{{ p.state || p.phase }}</span>
                      <span v-if="p.message" class="text-muted-foreground/60 ml-2">{{ p.message }}</span>
                    </td>
                    <td class="py-1.5 text-right" :class="p.restarts > 0 ? 'text-amber-600' : 'text-muted-foreground'">{{ p.restarts }}</td>
                    <td class="py-1.5">
                      <span v-if="p.updatedRevision" class="text-green-600">当前</span>
                      <span v-else class="text-amber-600">旧版本</span>
                    </td>
                    <td class="py-1.5 text-muted-foreground">{{ p.hostIP || '—' }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div v-else class="px-5 py-3 text-xs text-muted-foreground">暂无 Pod（workload 可能还没创建出来）</div>
          </div>
        </div>
      </div>

      <!-- Tab: History -->
      <div v-if="activeTab === 'history'" class="bg-white rounded-xl border border-border overflow-hidden">
        <div class="px-5 py-3.5 border-b border-border/50">
          <h3 class="font-medium text-sm text-foreground flex items-center gap-2"><History class="w-4 h-4 text-muted-foreground" />部署历史</h3>
        </div>
        <div v-if="deployments.length === 0" class="p-12 text-center text-sm text-muted-foreground">暂无部署记录</div>
        <table v-else class="w-full">
          <thead>
            <tr class="text-xs text-muted-foreground border-b border-border/50">
              <th class="text-left py-3 px-5 font-medium">操作</th>
              <th class="text-left py-3 px-5 font-medium">版本</th>
              <th class="text-left py-3 px-5 font-medium">状态</th>
              <th class="text-left py-3 px-5 font-medium">时间</th>
              <th class="text-right py-3 px-5 font-medium">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="d in deployments" :key="d.id" class="border-b border-border/30 hover:bg-secondary/30 transition-colors">
              <td class="py-3 px-5">
                <span :class="['inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium border', deployMeta(d.status).cls]">
                  {{ d.action === 'DEPLOY' ? '部署' : d.action === 'UPGRADE' ? '升级' : d.action === 'ROLLBACK' ? '回滚' : '卸载' }}
                </span>
              </td>
              <td class="py-3 px-5 text-sm">v{{ d.packageVersion }}</td>
              <td class="py-3 px-5">
                <span :class="['inline-flex items-center gap-1.5 text-xs font-medium', deployMeta(d.status).cls.replace('bg-', '')]">
                  <div :class="['w-1.5 h-1.5 rounded-full', deployMeta(d.status).dot.replace('dot-', 'bg-')]" />
                  {{ deployMeta(d.status).label }}
                </span>
              </td>
              <td class="py-3 px-5 text-sm text-muted-foreground">{{ d.startedAt?.slice(0, 16)?.replace('T', ' ') || '-' }}</td>
              <td class="py-3 px-5 text-right">
                <button v-if="d.errorMessage" class="text-xs text-red-500 hover:underline">{{ d.errorMessage.slice(0, 30) }}...</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- Tab: Resources -->
      <div v-if="activeTab === 'resources'" class="bg-white rounded-xl border border-border p-12 text-center">
        <Terminal class="w-12 h-12 mx-auto text-muted-foreground/20 mb-3" />
        <h3 class="text-sm font-medium text-muted-foreground">资源视图</h3>
        <p class="text-xs text-muted-foreground/60 mt-1">部署后可通过此页面查看 Pod、Service 等资源</p>
      </div>
    </template>
  </div>
</template>