import { memo } from 'react'
import { Handle, Position, type NodeProps } from '@xyflow/react'
import type { FlowNodeData } from '../types/workflow'

function CustomNode({ data, selected }: NodeProps) {
  const d = data as unknown as FlowNodeData
  const meta = d.meta
  const hasInput = meta.inputs.length > 0
  const hasOutput = meta.outputs.length > 0

  return (
    <div
      className={`min-w-[140px] rounded-lg bg-white shadow-sm border-t-4 text-center text-xs transition-shadow ${
        selected ? 'shadow-md ring-2 ring-primary/30' : 'hover:shadow-md'
      }`}
      style={{ borderTopColor: meta.color }}
    >
      {hasInput && (
        <Handle
          type="target"
          position={Position.Top}
          className="!w-3 !h-3 !border-2 !border-white !bg-gray-400"
        />
      )}

      <div className="px-4 py-2.5">
        <div className="flex items-center justify-center gap-1.5 mb-1">
          <div
            className="flex items-center justify-center w-5 h-5 rounded"
            style={{ background: meta.bgColor, color: meta.color }}
          >
            <span className="text-[10px] font-bold">
              {meta.type === 'start' ? '▶' : meta.type === 'end' ? '■' :
               meta.type === 'condition' ? '◇' : meta.type === 'parallel' ? '⬡' : '⚙'}
            </span>
          </div>
          <span className="font-semibold text-gray-800 text-xs">{d.label}</span>
        </div>
        {d.config?.image && (
          <div className="text-[10px] text-muted truncate max-w-[120px]">{d.config.image}</div>
        )}
      </div>

      {hasOutput && meta.outputs.length === 1 && (
        <Handle
          type="source"
          position={Position.Bottom}
          className="!w-3 !h-3 !border-2 !border-white !bg-gray-400"
        />
      )}
      {meta.outputs.length > 1 && meta.outputs.map((port) => (
        <Handle
          key={port.id}
          type="source"
          position={Position.Bottom}
          id={port.id}
          title={port.label}
          className="!w-3 !h-3 !border-2 !border-white"
          style={{ background: port.id === 'true' ? '#10b981' : port.id === 'false' ? '#ef4444' : '#6b7280' }}
        />
      ))}
    </div>
  )
}

export default memo(CustomNode)