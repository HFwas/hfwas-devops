export interface PodSummary {
  name: string
  namespace: string
  status: string
  nodeName: string | null
  podIP: string | null
  containerCount: number
  readyContainers: number
  restarts: number
  age: string
  creationTimestamp: string | null
}

export interface ContainerStatus {
  name: string
  state: string
  image: string
  ready: boolean
  restartCount: number
  exitCode: number | null
}

export interface PodCondition {
  type: string
  status: string
  reason: string | null
  message: string | null
}

export interface PodDetail extends PodSummary {
  uid: string
  labels: Record<string, string> | null
  annotations: Record<string, string> | null
  containers: ContainerStatus[]
  conditions: PodCondition[]
  ownerReference: string | null
  qosClass: string | null
}

export interface DeploymentSummary {
  name: string
  namespace: string
  desiredReplicas: number
  readyReplicas: number
  availableReplicas: number
  strategy: string | null
  age: string
  creationTimestamp: string | null
}

export interface DeploymentDetail extends DeploymentSummary {
  uid: string
  image: string
  selector: string
  status: string
  containers?: ContainerResource[]
  volumes?: VolumeMount[]
  labels?: Record<string, string>
  annotations?: Record<string, string>
  revisionHistoryLimit?: string
  minReadySeconds?: string
}

export interface ContainerResource {
  name: string
  image: string
  cpuRequest?: string
  cpuLimit?: string
  memRequest?: string
  memLimit?: string
  volumeMounts?: VolumeMount[]
  ports?: ContainerPort[]
  command?: string
  args?: string
}

export interface VolumeMount {
  name: string
  mountPath?: string
  readOnly?: string
  subPath?: string
  volumeType?: string
}

export interface ContainerPort {
  name?: string
  containerPort: number
  protocol?: string
}

export interface ServiceSummary {
  name: string
  namespace: string
  type: string
  clusterIP: string
  externalIP: string | null
  portCount: number
  age: string
  creationTimestamp: string | null
}

export interface ServiceDetail extends ServiceSummary {
  uid: string
  selector?: Record<string, string>
  sessionAffinity?: string
  labels?: Record<string, string>
  annotations?: Record<string, string>
  ports?: ServicePortItem[]
}

export interface ServicePortItem {
  name?: string
  port: number
  targetPort?: string
  nodePort?: string
  protocol?: string
}

export interface NamespaceInfo {
  name: string
  uid: string
  phase: string | null
  status: string | null
  creationTimestamp: string | null
}

export interface EventInfo {
  type: string
  reason: string
  message: string
  count: number | null
  firstTimestamp: string | null
  lastTimestamp: string | null
  involvedKind: string | null
  involvedName: string | null
  involvedUid: string | null
  source: string | null
}

export interface StatefulSetSummary {
  name: string
  namespace: string
  desiredReplicas: number
  readyReplicas: number
  currentReplicas: number
  serviceName: string | null
  age: string
  creationTimestamp: string | null
}

export interface PvcSummary {
  name: string
  namespace: string
  status: string | null
  accessModes: string | null
  storageClass: string | null
  capacity: string | null
  age: string
  creationTimestamp: string | null
}

export interface SecretSummary {
  name: string
  namespace: string
  type: string | null
  dataCount: number
  age: string
  creationTimestamp: string | null
}

export interface ConfigMapSummary {
  name: string
  namespace: string
  dataCount: number
  age: string
  creationTimestamp: string | null
}

export interface NodeSummary {
  name: string
  status: string
  role: string
  kubeletVersion: string | null
  containerRuntime: string | null
  osImage: string | null
  kernelVersion: string | null
  architecture: string | null
  podCIDR: string | null
  providerID: string | null
  podCount: number
  cpuCapacity: number
  memoryCapacity: number
  age: string | null
  creationTimestamp: string | null
}

export interface NodeAddress {
  type: string
  address: string
}

export interface NodeTaint {
  key: string
  value: string | null
  effect: string
}

export interface NodeSystemInfo {
  machineID: string | null
  systemUUID: string | null
  bootID: string | null
  kernelVersion: string | null
  osImage: string | null
  containerRuntimeVersion: string | null
  kubeletVersion: string | null
  kubeProxyVersion: string | null
  operatingSystem: string | null
  architecture: string | null
}

export interface NodeImage {
  name: string
  sizeBytes: number
}

export interface NodeDetail {
  name: string
  status: string
  role: string
  kubeletVersion: string | null
  containerRuntime: string | null
  osImage: string | null
  kernelVersion: string | null
  architecture: string | null
  podCIDR: string | null
  providerID: string | null
  podCount: number
  cpuCapacity: number
  memoryCapacity: number
  age: string | null
  creationTimestamp: string | null
  uid: string | null
  labels: Record<string, string> | null
  annotations: Record<string, string> | null
  addresses: NodeAddress[] | null
  taints: NodeTaint[] | null
  nodeInfo: NodeSystemInfo | null
  capacity: Record<string, string> | null
  allocatable: Record<string, string> | null
  images: NodeImage[] | null
}