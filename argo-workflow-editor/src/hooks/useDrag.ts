import { useCallback, useRef } from 'react'
import { useReactFlow } from '@xyflow/react'
import { findNodeMeta } from '../registry/nodeRegistry'

export function useDrop() {
  const reactFlowInstance = useReactFlow()
  const reactFlowWrapper = useRef<HTMLDivElement>(null)

  const onDragOver = useCallback((event: React.DragEvent) => {
    event.preventDefault()
    event.dataTransfer.dropEffect = 'move'
  }, [])

  const onDrop = useCallback(
    (event: React.DragEvent) => {
      event.preventDefault()
      const type = event.dataTransfer.getData('application/argonodetype')
      if (!type || !reactFlowWrapper.current) return

      const meta = findNodeMeta(type)
      if (!meta) return

      const bounds = reactFlowWrapper.current.getBoundingClientRect()
      const position = reactFlowInstance.screenToFlowPosition({
        x: event.clientX - bounds.left,
        y: event.clientY - bounds.top,
      })

      const newNode = {
        id: `node-${Date.now()}`,
        type: 'custom',
        position: { x: position.x - 70, y: position.y - 20 },
        data: {
          label: meta.label,
          type: meta.type,
          meta,
          config: {
            image: meta.defaultImage || undefined,
            command: meta.defaultCommand || undefined,
          },
        },
      }

      reactFlowInstance.addNodes(newNode)
    },
    [reactFlowInstance]
  )

  return { reactFlowWrapper, onDragOver, onDrop }
}