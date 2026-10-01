import { useState, useCallback } from 'react'
import { EdgeLabelRenderer, useReactFlow, getBezierPath, type EdgeProps } from '@xyflow/react'
import { Plus } from 'lucide-react'
import { findNodeMeta } from '../registry/nodeRegistry'
import type { FlowNodeData } from '../types/workflow'

export default function AddNodeButton({
  id,
  sourceX,
  sourceY,
  targetX,
  targetY,
  sourcePosition,
  targetPosition,
}: EdgeProps) {
  const [showMenu, setShowMenu] = useState(false)
  const { setNodes, setEdges } = useReactFlow()

  const [edgePath] = getBezierPath({
    sourceX,
    sourceY,
    sourcePosition,
    targetX,
    targetY,
    targetPosition,
  })

  const midX = (sourceX + targetX) / 2
  const midY = (sourceY + targetY) / 2

  const handleAddNode = useCallback((type: string) => {
    const meta = findNodeMeta(type)
    if (!meta) return

    const newNode = {
      id: `node-${Date.now()}`,
      type: 'custom',
      position: { x: midX - 70, y: midY - 30 },
      data: {
        label: meta.label,
        type: meta.type,
        meta,
        config: {
          image: meta.defaultImage || undefined,
          command: meta.defaultCommand || undefined,
        },
      } as FlowNodeData,
    }

    setNodes((nds) => [...nds, newNode])
    setEdges((eds) =>
      eds.flatMap((e) => {
        if (e.id === id) {
          return [
            { ...e, id: `${e.id}-a`, source: e.source, target: newNode.id },
            { ...e, id: `${e.id}-b`, source: newNode.id, target: e.target },
          ]
        }
        return [e]
      })
    )
    setShowMenu(false)
  }, [id, midX, midY, setNodes, setEdges])

  const availableTypes = ['script', 'http', 'condition', 'parallel', 'end']

  return (
    <EdgeLabelRenderer>
      <div style={{ position: 'absolute', transform: `translate(-50%, -50%) translate(${midX}px,${midY}px)`, zIndex: 10, pointerEvents: 'all' }}>
        <button
          className="flex items-center justify-center w-6 h-6 rounded-full bg-primary text-white shadow hover:bg-primary-hover transition-colors"
          onClick={() => setShowMenu(!showMenu)}
        >
          <Plus size={14} />
        </button>
        {showMenu && (
          <div className="absolute left-1/2 -translate-x-1/2 top-8 bg-white rounded-lg shadow-lg border border-border py-1 min-w-[140px] z-20">
            {availableTypes.map((type) => {
              const meta = findNodeMeta(type)
              if (!meta) return null
              return (
                <button
                  key={type}
                  className="flex items-center gap-2 w-full px-3 py-1.5 text-xs text-gray-700 hover:bg-chip-bg transition-colors"
                  onClick={() => handleAddNode(type)}
                >
                  <span className="w-2 h-2 rounded-full" style={{ background: meta.color }} />
                  {meta.label}
                </button>
              )
            })}
          </div>
        )}
      </div>
    </EdgeLabelRenderer>
  )
}