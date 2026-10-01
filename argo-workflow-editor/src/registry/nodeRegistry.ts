import { Play, Square, Code2, Globe, GitMerge, GitFork } from 'lucide-react'
import type { NodeMeta, NodeGroup, NodePortDef } from '../types/workflow'

const mkPort = (id: string, label: string, type: 'source' | 'target'): NodePortDef => ({ id, label, type })

interface RegistryEntry extends NodeMeta {
  icon: typeof Play
}

export const NODE_REGISTRY: RegistryEntry[] = [
  // ---- Trigger ----
  {
    type: 'start', label: '开始', group: 'trigger', description: '工作流入口',
    icon: Play, color: '#3370ff', bgColor: '#eff6ff',
    defaultImage: '', defaultCommand: '',
    inputs: [], outputs: [mkPort('out', '输出', 'source')],
  },
  {
    type: 'end', label: '结束', group: 'trigger', description: '工作流出口',
    icon: Square, color: '#6b7280', bgColor: '#f3f4f6',
    defaultImage: '', defaultCommand: '',
    inputs: [mkPort('in', '输入', 'target')], outputs: [],
  },

  // ---- Task ----
  {
    type: 'script', label: '脚本', group: 'task', description: '执行 Shell/Bash 脚本',
    icon: Code2, color: '#059669', bgColor: '#ecfdf5',
    defaultImage: 'alpine:3.21', defaultCommand: 'echo "hello world"',
    inputs: [mkPort('in', '输入', 'target')], outputs: [mkPort('out', '输出', 'source')],
  },
  {
    type: 'http', label: 'HTTP 请求', group: 'task', description: '调用 HTTP API',
    icon: Globe, color: '#059669', bgColor: '#ecfdf5',
    defaultImage: 'curlimages/curl:8.11.1', defaultCommand: 'curl -fsSL https://api.example.com/health',
    inputs: [mkPort('in', '输入', 'target')], outputs: [mkPort('out', '输出', 'source')],
  },

  // ---- Flow Control ----
  {
    type: 'condition', label: '条件判断', group: 'flow-control', description: '根据表达式决定分支',
    icon: GitMerge, color: '#d97706', bgColor: '#fffbeb',
    defaultImage: '', defaultCommand: '',
    inputs: [mkPort('in', '输入', 'target')],
    outputs: [mkPort('true', '真', 'source'), mkPort('false', '假', 'source')],
  },
  {
    type: 'parallel', label: '并行', group: 'flow-control', description: '并行执行多个分支',
    icon: GitFork, color: '#d97706', bgColor: '#fffbeb',
    defaultImage: '', defaultCommand: '',
    inputs: [mkPort('in', '输入', 'target')],
    outputs: [mkPort('out1', '分支1', 'source'), mkPort('out2', '分支2', 'source')],
  },
]

export interface NodeGroupEntry {
  group: NodeGroup
  label: string
  nodes: RegistryEntry[]
}

const GROUP_LABELS: Record<NodeGroup, string> = {
  trigger: '触发', task: '任务', 'flow-control': '流程控制', notification: '通知', kubernetes: 'Kubernetes',
}

export function groupNodes(): NodeGroupEntry[] {
  const map = new Map<NodeGroup, RegistryEntry[]>()
  for (const node of NODE_REGISTRY) {
    const list = map.get(node.group) ?? []
    list.push(node)
    map.set(node.group, list)
  }
  return Array.from(map.entries()).map(([group, nodes]) => ({
    group, label: GROUP_LABELS[group] ?? group, nodes,
  }))
}

export function findNodeMeta(type: string): NodeMeta | undefined {
  return NODE_REGISTRY.find((n) => n.type === type)
}