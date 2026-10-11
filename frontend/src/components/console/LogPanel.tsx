import { useLayoutEffect, useRef, useState, type PointerEvent as ReactPointerEvent, type ReactNode } from 'react'
import { Button } from '@/components/ui/button'
import {
  followModeAfterScroll,
  scrollTopForFollow,
  type FollowMode,
  type ScrollMetrics,
} from '@/components/console/logFollow'
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

export type LogPanelProps = {
  title?: ReactNode
  connection?: ConnectionState
  toolbar?: ReactNode
  children: ReactNode
  className?: string
  bodyClassName?: string
  /**
   * 内容变长时贴底。用户上滚后暂停，直到滚回底部或点击「跟随最新」。
   * 适合运行中的日志；已结束的日志保持关闭，从开头阅读。
   */
  follow?: boolean
  /** 切换日志源（另一条任务、另一次运行）时重新开始跟随。 */
  followKey?: string | number | null
}

function readMetrics(el: HTMLElement): ScrollMetrics {
  return { scrollTop: el.scrollTop, scrollHeight: el.scrollHeight, clientHeight: el.clientHeight }
}

/**
 * 日志 / 终端共用的底栏式外壳：约 40vh、可拖高、最小 120px、等宽内容区、连接指示。
 * 不负责 WebSocket 或 xterm 协议，只包住调用方已经接好的输出。
 * `follow` 打开时，正文变长会滚到底部；读者上滚则暂停。
 */
export function LogPanel({
  title = '日志',
  connection = 'idle',
  toolbar,
  children,
  className,
  bodyClassName,
  follow = false,
  followKey,
}: LogPanelProps) {
  const panelRef = useRef<HTMLElement>(null)
  const bodyRef = useRef<HTMLDivElement>(null)
  const modeRef = useRef<FollowMode>('follow')
  const programmatic = useRef<number | null>(null)
  const [height, setHeight] = useState<number>()
  const [mode, setMode] = useState<FollowMode>('follow')

  useLayoutEffect(() => {
    modeRef.current = 'follow'
    setMode('follow')
  }, [followKey])

  useLayoutEffect(() => {
    const el = bodyRef.current
    if (!follow || !el || modeRef.current !== 'follow') return
    const top = scrollTopForFollow('follow', readMetrics(el))
    if (top == null) return
    programmatic.current = top
    el.scrollTop = top
  })

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

  function onBodyScroll() {
    if (!follow) return
    const el = bodyRef.current
    if (!el) return
    const metrics = readMetrics(el)
    if (programmatic.current != null && Math.abs(metrics.scrollTop - programmatic.current) <= 1) {
      programmatic.current = null
      return
    }
    programmatic.current = null
    const next = followModeAfterScroll(metrics)
    if (next === modeRef.current) return
    modeRef.current = next
    setMode(next)
  }

  function resumeFollow() {
    modeRef.current = 'follow'
    setMode('follow')
    const el = bodyRef.current
    if (!el) return
    const top = Math.max(0, el.scrollHeight - el.clientHeight)
    programmatic.current = top
    el.scrollTop = top
  }

  const showResume = follow && mode === 'paused'

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
      <header className="flex min-h-10 shrink-0 flex-wrap items-center gap-x-3 gap-y-2 border-b bg-muted/50 px-2 py-1.5">
        <div className="flex shrink-0 items-center gap-2">
          <span className="text-sm font-medium">{title}</span>
          <ConnectionIndicator state={connection} />
        </div>
        {toolbar || showResume ? (
          <div data-slot="log-toolbar" className="flex min-w-0 flex-1 flex-wrap items-center justify-end gap-2">
            {toolbar}
            {showResume ? (
              <Button type="button" variant="outline" size="sm" onClick={resumeFollow}>
                跟随最新
              </Button>
            ) : null}
          </div>
        ) : null}
      </header>
      <div
        ref={bodyRef}
        data-slot="log-body"
        data-follow={follow ? mode : 'off'}
        className={cn('min-h-0 flex-1 overflow-auto font-mono text-xs leading-5', bodyClassName)}
        onScroll={onBodyScroll}
      >
        {children}
      </div>
      <button
        type="button"
        aria-label="调整面板高度"
        className="h-1.5 shrink-0 cursor-ns-resize bg-border hover:bg-primary/40"
        onPointerDown={onResizePointerDown}
      />
    </section>
  )
}

export function TerminalChrome({ title = '终端', ...props }: LogPanelProps) {
  return <LogPanel title={title} {...props} />
}
