import { groupNodes } from '../registry/nodeRegistry'

export default function NodePalette() {
  const nodeGroups = groupNodes()

  const handleDragStart = (e: React.DragEvent, type: string) => {
    e.dataTransfer.setData('application/argonodetype', type)
    e.dataTransfer.effectAllowed = 'move'
  }

  return (
    <aside className="w-[220px] flex-shrink-0 flex flex-col bg-card-bg border-r border-border">
      <div className="px-3 py-2.5 text-xs font-semibold text-gray-800 border-b border-border">
        节点类型
      </div>
      <div className="flex-1 overflow-y-auto p-2 space-y-3">
        {nodeGroups.map((group) => (
          <div key={group.group}>
            <div className="text-[10px] font-semibold text-muted uppercase tracking-wider px-2 py-1">
              {group.label}
            </div>
            {group.nodes.map((node) => (
              <div
                key={node.type}
                className="flex items-center gap-2 px-2 py-1.5 rounded-md cursor-grab active:cursor-grabbing hover:bg-chip-bg transition-colors"
                draggable
                onDragStart={(e) => handleDragStart(e, node.type)}
              >
                <div
                  className="flex items-center justify-center w-7 h-7 rounded-md flex-shrink-0"
                  style={{ background: node.bgColor, color: node.color }}
                >
                  <span className="text-xs font-bold">
                    {node.type === 'start' ? '▶' : node.type === 'end' ? '■' :
                     node.type === 'condition' ? '◇' : node.type === 'parallel' ? '⬡' : '⚙'}
                  </span>
                </div>
                <div className="min-w-0">
                  <div className="text-xs font-medium text-gray-800">{node.label}</div>
                  <div className="text-[10px] text-muted truncate">{node.description}</div>
                </div>
              </div>
            ))}
          </div>
        ))}
      </div>
    </aside>
  )
}