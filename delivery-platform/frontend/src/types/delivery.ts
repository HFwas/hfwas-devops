// =============================================================================
// Domain types
// =============================================================================

export interface Cluster {
  id: number
  name: string
  serverHost: string
  isCurrent: boolean
  status: string
  version?: string
  createTime: string
}

export interface Product {
  id: number
  productKey: string
  displayName: string
  packageVersion: string
  status: string
  clusterName?: string
  targetNs?: string
  phase?: string
  aggregatedSummary?: AggregatedSummary
  lastDeployTime?: string
  createTime: string
}

export interface ProductDetail {
  id: number
  productKey: string
  displayName: string
  packageVersion: string
  status: string
  clusterId?: number
  clusterName?: string
  targetNs?: string
  cloudServiceName?: string
  form?: FormSchema
  globalParams: Record<string, any>
  params: Record<string, any>
  overrides: Record<string, any>
  phase: string
  // 发布实例层：这一次发布的关联号与现场规划修订
  releaseID?: string
  planRevision?: string
  observedReleaseID?: string
  aggregatedSummary?: AggregatedSummary
  serviceStatuses?: ServiceStatus[]
  // 发布步骤（ProductTask）
  tasks?: ProductTaskView[]
  createTime: string
}

export interface ProductTaskView {
  name: string
  releaseID?: string
  opsType?: string
  phase?: string
  component?: string
  resource?: string
  after?: string[]
  messages?: string[]
}

// =============================================================================
// 组件级状态（来自集群里的 CloudComponent CR）
// =============================================================================

export interface ComponentInfo {
  name: string
  displayName?: string
  componentType?: string
  chartName?: string
  chartVersion?: string
  helmRelease?: string
  phase?: string
  releaseID?: string
  observedReleaseID?: string
  summary?: WorkloadSummary
  pods?: PodDetail[]
  diffs?: WorkloadDiff[]
  conditions?: ConditionInfo[]
}

export interface WorkloadSummary {
  totalWorkloads: number
  readyWorkloads: number
  degradedWorkloads: number
}

export interface PodDetail {
  name: string
  namespace?: string
  ip?: string
  hostIP?: string
  workloadKind?: string
  workloadName?: string
  ready: boolean
  phase?: string
  state?: string
  restarts: number
  /** 是否已切到当前修订；升级后排查「Pod 没切新版本」的直接判据 */
  updatedRevision: boolean
  message?: string
}

export interface WorkloadDiff {
  kind?: string
  name?: string
  namespace?: string
  path?: string
  expected?: string
  current?: string
  message?: string
}

export interface ConditionInfo {
  type: string
  status: string
  reason?: string
  message?: string
}

export interface AggregatedSummary {
  totalServices: number
  readyServices: number
  degradedServices: number
  failedServices: number
  totalComponents: number
  readyComponents: number
}

export interface ServiceStatus {
  name: string
  phase: string
  releaseID?: string
  observedReleaseID?: string
  error?: string
}

export interface FormSchema {
  groups: FormGroup[]
}

export interface FormGroup {
  id: string
  title: string
  fields: FormField[]
}

export interface FormField {
  path: string
  label: string
  type: string
  default?: any
  required?: boolean
  placeholder?: string
  validation?: FieldValidation
  value?: any
  options?: SelectOption[]
}

export interface FieldValidation {
  pattern?: string
  minLength?: number
  maxLength?: number
  minimum?: number
  maximum?: number
  enum?: string[]
}

export interface SelectOption {
  label: string
  value: string
}

export interface Deployment {
  id: number
  productId: number
  clusterId: number
  action: string
  packageVersion: string
  appName?: string
  appNS?: string
  status: string
  errorMessage?: string
  startedAt?: string
  finishedAt?: string
  createTime: string
}

// Cluster inventory types
export interface NodeInfo {
  name: string
  ready: boolean
  kubeletVersion: string
  osImage: string
  allocatableCpu: string
  allocatableMemory: string
}

export interface SCInfo {
  name: string
  provisioner: string
  isDefault: boolean
}

export interface PVCInfo {
  name: string
  namespace: string
  phase: string
  storageClass: string
  capacity: string
  accessModes: string[]
}

export interface NSInfo {
  name: string
  phase: string
}

export interface PlatformStats {
  currentCluster?: Cluster
  clusterCount: number
  productCounts: { total: number; deployed: number; notDeployed: number; failed: number }
}

export interface PageResult<T> {
  records: T[]
  total: number
  pageNo: number
  pageSize: number
}

export interface APIResponse<T> {
  code: number
  data?: T
  message?: string
}