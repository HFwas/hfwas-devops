import { useState, useCallback } from 'react'
import {
  ReactFlowProvider,
  addEdge,
  useNodesState,
  useEdgesState,
  type Edge,
  type Connection,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import WorkflowToolbar from '../components/WorkflowToolbar'
import NodePalette from '../components/NodePalette'
import GraphCanvas from '../components/GraphCanvas'
import ConfigPanel from '../components/ConfigPanel'
import type { FlowNodeData } from '../types/workflow'
import { findNodeMeta } from '../registry/nodeRegistry'

function EditorInner() {
  const [nodes, setNodes, onNodesChange] = useNodesState([{
    id: 'start',
    type: 'custom' as const,
    position: { x: 300, y: 80 },
    data: {
      label: '开始',
      type: 'start',
      meta: findNodeMeta('start')!,
      config: {},
    },
  }])
  const [edges, setEdges, onEdgesChange] = useEdgesState([] as Edge[])
  const [selectedNode, setSelectedNode] = useState<{ id: string; data: FlowNodeData } | null>(null)
  const [workflowName, setWorkflowName] = useState('')
  const [isYamlMode, setIsYamlMode] = useState(false)

  const onConnect = useCallback(
    (connection: Connection) => {
      setEdges((eds) =>
        addEdge({ ...connection, type: 'smoothstep' }, eds)
      )
    },
    [setEdges]
  )

  const onNodeClick = useCallback((_: React.MouseEvent, node: any) => {
    setSelectedNode({ id: node.id, data: node.data as FlowNodeData })
  }, [])

  const onPaneClick = useCallback(() => {
    setSelectedNode(null)
  }, [])

  const handleConfigUpdate = useCallback(
    (nodeId: string, config: Partial<FlowNodeData['config']>) => {
      setNodes((nds) =>
        nds.map((n) => {
          if (n.id === nodeId) {
            return {
              ...n,
              data: { ...n.data, config: { ...(n.data.config || {}), ...config } },
            }
          }
          return n
        })
      )
      setSelectedNode((prev) =>
        prev && prev.id === nodeId
          ? { id: nodeId, data: { ...prev.data, config: { ...prev.data.config, ...config } as FlowNodeData['config'] } }
          : prev
      )
    },
    [setNodes]
  )

  const handleSave = useCallback(() => {
    if (!workflowName) {
      alert('请输入工作流名称')
      return
    }
    alert(`工作流 "${workflowName}" 保存成功（模拟）`)
  }, [workflowName])

  const handleRun = useCallback(() => {
    alert('已提交运行（模拟）')
  }, [])

  return (
    <div className="flex flex-col h-screen bg-canvas-bg">
      <WorkflowToolbar
        name={workflowName}
        onNameChange={setWorkflowName}
        onSave={handleSave}
        onRun={handleRun}
        onToggleMode={() => setIsYamlMode(!isYamlMode)}
        isYamlMode={isYamlMode}
      />
      {isYamlMode ? (
        <div className="flex-1 p-6">
          <textarea
            className="w-full h-full bg-gray-900 text-gray-100 font-mono text-sm p-6 rounded-lg resize-none outline-none"
            placeholder="在此编辑 Workflow YAML..."
            spellCheck={false}
          />
        </div>
      ) : (
        <div className="flex flex-1 overflow-hidden">
          <NodePalette />
          <GraphCanvas
            nodes={nodes}
            edges={edges}
            onNodesChange={onNodesChange as any}
            onEdgesChange={onEdgesChange as any}
            onConnect={onConnect}
            onNodeClick={onNodeClick}
          />
          <ConfigPanel node={selectedNode} onUpdate={handleConfigUpdate} />
        </div>
      )}
    </div>
  )
}

export default function WorkflowEditorPage() {
  return (
    <ReactFlowProvider>
      <EditorInner />
    </ReactFlowProvider>
  )
}