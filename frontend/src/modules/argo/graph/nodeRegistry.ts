import {
  Code2,
  Globe,
  GitFork,
  GitMerge,
  Play,
  Square,
  type Component,
} from '@lucide/vue'
import type { NodeGroup, WorkflowNodeMeta, WorkflowNodePort } from '@/modules/argo/types/workflow'

// ============================================================
// 节点类型注册表
// ============================================================

const mkPort = (id: string, label: string, type: 'source' | 'target'): WorkflowNodePort => ({
  id,
  label,
  type,
})

const TASK: NodeGroup = 'task'
const TRIGGER: NodeGroup = 'trigger'
const FLOW: NodeGroup = 'flow-control'
const NOTIFY: NodeGroup = 'notification'

interface RegistryEntry {
  type: string
  label: string
  group: NodeGroup
  description: string
  icon: Component
  color: string
  bgColor: string
  defaultImage: string
  defaultCommand: string
  inputs: WorkflowNodePort[]
  outputs: WorkflowNodePort[]
}

export const NODE_REGISTRY: RegistryEntry[] = [
  // ---- Trigger ----
  {
    type: 'start',
    label: '开始',
    group: TRIGGER,
    description: '工作流入口',
    icon: Play,
    color: '#3370ff',
    bgColor: '#eff6ff',
    defaultImage: '',
    defaultCommand: '',
    inputs: [],
    outputs: [mkPort('out', '输出', 'source')],
  },
  {
    type: 'end',
    label: '结束',
    group: TRIGGER,
    description: '工作流出口',
    icon: Square,
    color: '#6b7280',
    bgColor: '#f3f4f6',
    defaultImage: '',
    defaultCommand: '',
    inputs: [mkPort('in', '输入', 'target')],
    outputs: [],
  },

  // ---- Task ----
  {
    type: 'script',
    label: '脚本',
    group: TASK,
    description: '执行 Shell/Bash 脚本',
    icon: Code2,
    color: '#059669',
    bgColor: '#ecfdf5',
    defaultImage: 'alpine:3.21',
    defaultCommand: 'echo "hello world"',
    inputs: [mkPort('in', '输入', 'target')],
    outputs: [mkPort('out', '输出', 'source')],
  },
  {
    type: 'http',
    label: 'HTTP 请求',
    group: TASK,
    description: '调用 HTTP API',
    icon: Globe,
    color: '#059669',
    bgColor: '#ecfdf5',
    defaultImage: 'curlimages/curl:8.11.1',
    defaultCommand: 'curl -fsSL https://api.example.com/health',
    inputs: [mkPort('in', '输入', 'target')],
    outputs: [mkPort('out', '输出', 'source')],
  },

  // ---- Flow Control ----
  {
    type: 'condition',
    label: '条件判断',
    group: FLOW,
    description: '根据表达式决定分支',
    icon: GitMerge,
    color: '#d97706',
    bgColor: '#fffbeb',
    defaultImage: '',
    defaultCommand: '',
    inputs: [mkPort('in', '输入', 'target')],
    outputs: [
      mkPort('true', '真', 'source'),
      mkPort('false', '假', 'source'),
    ],
  },
  {
    type: 'parallel',
    label: '并行',
    group: FLOW,
    description: '并行执行多个分支',
    icon: GitFork,
    color: '#d97706',
    bgColor: '#fffbeb',
    defaultImage: '',
    defaultCommand: '',
    inputs: [mkPort('in', '输入', 'target')],
    outputs: [mkPort('out1', '分支1', 'source'), mkPort('out2', '分支2', 'source')],
  },
]

// ============================================================
// 按 group 分组
// ============================================================

export interface NodeGroupEntry {
  group: NodeGroup
  label: string
  nodes: RegistryEntry[]
}

const GROUP_LABELS: Record<NodeGroup, string> = {
  trigger: '触发',
  task: '任务',
  'flow-control': '流程控制',
  notification: '通知',
  kubernetes: 'Kubernetes',
}

export function groupNodes(): NodeGroupEntry[] {
  const map = new Map<NodeGroup, RegistryEntry[]>()
  for (const node of NODE_REGISTRY) {
    const list = map.get(node.group) ?? []
    list.push(node)
    map.set(node.group, list)
  }
  return Array.from(map.entries()).map(([group, nodes]) => ({
    group,
    label: GROUP_LABELS[group] ?? group,
    nodes,
  }))
}

export function findNodeMeta(type: string): RegistryEntry | undefined {
  return NODE_REGISTRY.find((n) => n.type === type)
}