import { useRef, useState, type PointerEvent as ReactPointerEvent, type ReactNode } from 'react'
import { cn } from '@/lib/utils'

export type ConnectionState = 'idle' | 'connecting' | 'connected' | 'disconnected'

const CONNECTION_LABEL: Record<ConnectionState, string> = {
  idle: '未连接',
  connecting: '连接中',
  connected: '已连接',
  disconnected: '已断开',
}

const CONNECTION_DOT: Record<ConnectionState, string> = {
  idle: 'bg-muted-foreground/40',
  connecting: 'animate-pulse bg-yellow-500',
  connected: 'animate-pulse bg-green-500',
  disconnected: 'bg-red-500',
}

export function ConnectionIndicator({ state }: { state: ConnectionState }) {
  return (
    <span className="inline-flex items-center gap-1.5 text-xs text-muted-foreground">
      <span className={cn('size-2 rounded-full', CONNECTION_DOT[state])} aria-hidden />
      {CONNECTION_LABEL[state]}
    </span>
  )
}

/**
 * 日志 / 终端共用的底栏式外壳：约 40vh、可拖高、最小 120px、等宽内容区、连接指示。
 * 不负责 WebSocket 或 xterm 协议，只包住调用方已经接好的输出。
 */
export function LogPanel({
  title = '日志',
  connection = 'idle',
  toolbar,
  children,
  className,
  bodyClassName,
}: {
  title?: ReactNode
  connection?: ConnectionState
  toolbar?: ReactNode
  children: ReactNode
  className?: string
  bodyClassName?: string
}) {
  const panelRef = useRef<HTMLElement>(null)
  const [height, setHeight] = useState<number>()

  function onResizePointerDown(event: ReactPointerEvent<HTMLButtonElement>) {
    const handle = event.currentTarget
    const panel = panelRef.current
    if (!panel) return
    event.preventDefault()
    const startY = event.clientY
    const startH = panel.getBoundingClientRect().height
    handle.setPointerCapture(event.pointerId)
    const move = (ev: PointerEvent) => {
      setHeight(Math.max(120, Math.round(startH + (ev.clientY - startY))))
    }
    const up = (ev: PointerEvent) => {
      if (handle.hasPointerCapture(ev.pointerId)) handle.releasePointerCapture(ev.pointerId)
      handle.removeEventListener('pointermove', move)
      handle.removeEventListener('pointerup', up)
    }
    handle.addEventListener('pointermove', move)
    handle.addEventListener('pointerup', up)
  }

  return (
    <section
      ref={panelRef}
      data-slot="log-panel"
      style={height != null ? { height } : undefined}
      className={cn(
        'flex min-h-[120px] flex-col overflow-hidden rounded-lg border bg-background',
        height == null && 'h-[40vh]',
        className,
      )}
    >
      <header className="flex h-10 shrink-0 items-center gap-2 border-b bg-muted/50 px-2">
        <span className="text-sm font-medium">{title}</span>
        <ConnectionIndicator state={connection} />
        {toolbar ? <div className="ml-auto flex flex-wrap items-center gap-2">{toolbar}</div> : null}
      </header>
      <div className={cn('min-h-0 flex-1 overflow-auto font-mono text-xs leading-5', bodyClassName)}>{children}</div>
      <button
        type="button"
        aria-label="调整面板高度"
        className="h-1.5 shrink-0 cursor-ns-resize bg-border hover:bg-primary/40"
        onPointerDown={onResizePointerDown}
      />
    </section>
  )
}

export function TerminalChrome({
  title = '终端',
  ...props
}: {
  title?: ReactNode
  connection?: ConnectionState
  toolbar?: ReactNode
  children: ReactNode
  className?: string
  bodyClassName?: string
}) {
  return <LogPanel title={title} {...props} />
}
