import type {
  Cluster, Product, ProductDetail, Deployment, ComponentInfo,
  PageResult, APIResponse, PlatformStats, NodeInfo, SCInfo, PVCInfo, NSInfo,
} from '../types/delivery'

const BASE = '/api/delivery'

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetch(url, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  })
  const json = await res.json()
  if (json.code !== 0) throw new Error(json.message || 'Request failed')
  return json.data as T
}

// =============================================================================
// Status
// =============================================================================
export const fetchStatus = (): Promise<APIResponse<PlatformStats>> =>
  fetch(`${BASE}/status`).then(r => r.json())

// =============================================================================
// Clusters
// =============================================================================
export const fetchClusters = (): Promise<Cluster[]> =>
  request(`${BASE}/clusters`)

export const importCluster = (name: string, kubeconfigText: string): Promise<Cluster> =>
  request(`${BASE}/clusters`, {
    method: 'POST',
    body: JSON.stringify({ name, kubeconfigText }),
  })

export const setCurrentCluster = (id: number): Promise<void> =>
  request(`${BASE}/clusters/${id}/current`, { method: 'POST' })

export const deleteCluster = (id: number): Promise<void> =>
  request(`${BASE}/clusters/${id}`, { method: 'DELETE' })

export const fetchClusterStatus = (id: number): Promise<{ version: string; nodeCount: number; readyNodes: number }> =>
  request(`${BASE}/clusters/${id}/status`)

// Inventory
export const fetchClusterNodes = (id: number): Promise<NodeInfo[]> =>
  request(`${BASE}/clusters/${id}/nodes`)

export const fetchStorageClasses = (id: number): Promise<SCInfo[]> =>
  request(`${BASE}/clusters/${id}/storage-classes`)

export const fetchPVCs = (id: number, namespace?: string): Promise<PVCInfo[]> =>
  request(`${BASE}/clusters/${id}/pvcs${namespace ? `?namespace=${namespace}` : ''}`)

export const fetchNamespaces = (id: number): Promise<NSInfo[]> =>
  request(`${BASE}/clusters/${id}/namespaces`)

// =============================================================================
// Products
// =============================================================================
export const fetchProducts = (params: { pageNo: number; pageSize: number; keyword?: string }): Promise<PageResult<Product>> =>
  request(`${BASE}/products/page`, {
    method: 'POST',
    body: JSON.stringify(params),
  })

export const importProduct = async (file: File): Promise<{ message: string }> => {
  const formData = new FormData()
  formData.append('file', file)
  const res = await fetch(`${BASE}/products/import`, { method: 'POST', body: formData })
  const json = await res.json()
  if (json.code !== 0) throw new Error(json.message)
  return json.data
}

export const fetchProductDetail = (id: number): Promise<ProductDetail> =>
  request(`${BASE}/products/${id}`)

export const saveProductParams = (id: number, data: {
  displayName?: string
  globalParams?: Record<string, any>
  params?: Record<string, any>
  overrides?: Record<string, any>
}): Promise<void> =>
  request(`${BASE}/products/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  })

// =============================================================================
// Deploy
// =============================================================================
export const deployProduct = (id: number): Promise<Deployment> =>
  request(`${BASE}/products/${id}/deploy`, { method: 'POST' })

export const uninstallProduct = (id: number): Promise<void> =>
  request(`${BASE}/products/${id}/uninstall`, { method: 'POST' })

export const fetchDeployments = (productId: number): Promise<Deployment[]> =>
  request(`${BASE}/products/${productId}/deployments`)

export const fetchDeployment = (deployId: number): Promise<Deployment> =>
  request(`${BASE}/deployments/${deployId}`)

// =============================================================================
// Components（读集群里的 CloudComponent CR，含 Pod 明细与期望/实际差异）
// =============================================================================
export const fetchProductComponents = (id: number): Promise<ComponentInfo[]> =>
  request(`${BASE}/products/${id}/components`)