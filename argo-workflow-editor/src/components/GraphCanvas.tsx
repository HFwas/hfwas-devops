import {
  ReactFlow,
  Background,
  BackgroundVariant,
  Controls,
  MiniMap,
  type Node as RFNode,
  type Edge as RFEdge,
  type Connection,
  type OnNodesChange,
  type OnEdgesChange,
  MarkerType,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import CustomNode from './CustomNode'
import AddNodeButton from './AddNodeButton'
import { useDrop } from '../hooks/useDrag'
import { useCallback } from 'react'

const nodeTypes = { custom: CustomNode }
const edgeTypes = { addnode: AddNodeButton }

const defaultEdgeOptions = {
  type: 'smoothstep' as const,
  animated: false,
  style: { stroke: '#94a3b8', strokeWidth: 2 },
  markerEnd: { type: MarkerType.ArrowClosed, color: '#94a3b8' },
}

interface GraphCanvasProps {
  nodes: RFNode[]
  edges: RFEdge[]
  onNodesChange: OnNodesChange
  onEdgesChange: OnEdgesChange
  onConnect: (connection: Connection) => void
  onNodeClick: (event: React.MouseEvent, node: RFNode) => void
}

export default function GraphCanvas({
  nodes, edges, onNodesChange, onEdgesChange, onConnect, onNodeClick,
}: GraphCanvasProps) {
  const { reactFlowWrapper, onDragOver, onDrop } = useDrop()

  const handleConnect = useCallback((connection: Connection) => {
    onConnect(connection)
  }, [onConnect])

  return (
    <main ref={reactFlowWrapper} className="flex-1 relative bg-canvas-bg">
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={handleConnect}
        onNodeClick={onNodeClick}
        onDrop={onDrop}
        onDragOver={onDragOver}
        nodeTypes={nodeTypes as any}
        edgeTypes={edgeTypes as any}
        defaultEdgeOptions={defaultEdgeOptions}
        fitView
        deleteKeyCode={['Backspace', 'Delete']}
        className="workflow-canvas"
      >
        <Background variant={BackgroundVariant.Dots} gap={20} size={1} color="#e2e8f0" />
        <Controls position="bottom-left" showInteractive={false} className="!rounded-lg !border !border-border !shadow-sm" />
        <MiniMap
          position="bottom-right"
          nodeColor="#3370ff"
          maskColor="rgba(0,0,0,0.06)"
          className="!rounded-lg !border !border-border !shadow-sm"
        />
      </ReactFlow>
    </main>
  )
}