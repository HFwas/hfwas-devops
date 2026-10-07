import { useEffect, useRef, useState } from 'react'
import { FitAddon } from '@xterm/addon-fit'
import { Terminal } from '@xterm/xterm'
import '@xterm/xterm/css/xterm.css'
import { toast } from 'sonner'
import { TerminalChrome, type ConnectionState } from '@/components/console/LogPanel'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { podApi } from '@/modules/container/api/pod'
import { getToken } from '@/shared/keycloak'

export function PodShell({
  clusterId,
  namespace,
  name,
  containers,
}: {
  clusterId: string
  namespace: string
  name: string
  containers: string[]
}) {
  const [container, setContainer] = useState(containers[0] ?? '')
  const [path, setPath] = useState('/tmp')
  const [connection, setConnection] = useState<ConnectionState>('idle')
  const hostRef = useRef<HTMLDivElement>(null)
  const fileRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    if (!container && containers[0]) setContainer(containers[0])
  }, [container, containers])

  useEffect(() => {
    const host = hostRef.current
    if (!host) return
    const fontFamily =
      getComputedStyle(document.documentElement).getPropertyValue('--font-mono').replace(/\s+/g, ' ').trim() ||
      '"Maple Mono", ui-monospace, monospace'
    const term = new Terminal({ cursorBlink: true, fontSize: 13, fontFamily })
    const fit = new FitAddon()
    const state: { ws: WebSocket | null; stopped: boolean } = { ws: null, stopped: false }
    term.loadAddon(fit)
    term.open(host)
    fit.fit()
    void document.fonts?.load('13px "Maple Mono"').then(() => {
      if (state.stopped) return
      fit.fit()
    })
    const send = (payload: unknown) => {
      if (state.ws?.readyState === WebSocket.OPEN) state.ws.send(JSON.stringify(payload))
    }

    const dataDisp = term.onData((data) => send({ type: 'input', data }))
    const resizeDisp = term.onResize(({ cols, rows }) => send({ type: 'resize', cols, rows }))
    const observer = new ResizeObserver(() => fit.fit())
    observer.observe(host)

    setConnection('connecting')
    void (async () => {
      const token = await getToken()
      if (state.stopped) return
      if (!token) {
        setConnection('disconnected')
        term.writeln('未登录，无法打开终端')
        return
      }
      const proto = location.protocol === 'https:' ? 'wss:' : 'ws:'
      const query = container ? `?container=${encodeURIComponent(container)}` : ''
      const ws = new WebSocket(
        `${proto}//${location.host}/api/ws/container/shell/${clusterId}/${encodeURIComponent(namespace)}/${encodeURIComponent(name)}${query}`,
        [token],
      )
      state.ws = ws
      ws.binaryType = 'arraybuffer'
      ws.onopen = () => {
        if (state.stopped) return
        setConnection('connected')
        send({ type: 'resize', cols: term.cols, rows: term.rows })
      }
      ws.onclose = () => {
        if (!state.stopped) setConnection('disconnected')
      }
      ws.onmessage = (event) => {
        if (typeof event.data === 'string') {
          try {
            const msg = JSON.parse(event.data) as { type?: string; message?: string }
            if (msg.type === 'ping') send({ type: 'pong' })
            else if (msg.type === 'error' && msg.message) term.writeln(`\r\n${msg.message}`)
          } catch {
            term.write(event.data)
          }
          return
        }
        term.write(new Uint8Array(event.data as ArrayBuffer))
      }
      ws.onerror = () => {
        if (state.stopped) return
        setConnection('disconnected')
        term.writeln('\r\n终端连接失败')
      }
    })()

    return () => {
      state.stopped = true
      setConnection('idle')
      dataDisp.dispose()
      resizeDisp.dispose()
      observer.disconnect()
      state.ws?.close()
      term.dispose()
    }
  }, [clusterId, container, name, namespace])

  async function onUpload(file: File) {
    try {
      await podApi.uploadFile(clusterId, namespace, name, container, path, file)
      toast.success('已上传')
    } catch (error) {
      toast.error(error instanceof Error ? error.message : '上传失败')
    }
  }

  async function onDownload() {
    try {
      const { blob, filename } = await podApi.downloadFile(clusterId, namespace, name, container, path)
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = filename
      anchor.click()
      URL.revokeObjectURL(url)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : '下载失败')
    }
  }

  return (
    <TerminalChrome
      connection={connection}
      toolbar={
        <>
          <select
            className="h-7 rounded-md border border-input bg-background px-2 text-sm"
            aria-label="容器"
            value={container}
            onChange={(event) => setContainer(event.target.value)}
          >
            {containers.length === 0 && <option value="">默认容器</option>}
            {containers.map((item) => (
              <option key={item} value={item}>
                {item}
              </option>
            ))}
          </select>
          <Input value={path} onChange={(event) => setPath(event.target.value)} className="h-7 max-w-xs" placeholder="容器内路径" />
          <input
            ref={fileRef}
            type="file"
            className="hidden"
            onChange={(event) => {
              const file = event.target.files?.[0]
              if (file) void onUpload(file)
              event.target.value = ''
            }}
          />
          <Button variant="outline" size="sm" onClick={() => fileRef.current?.click()}>
            上传到该路径
          </Button>
          <Button variant="outline" size="sm" onClick={() => void onDownload()}>
            下载该路径
          </Button>
        </>
      }
    >
      <div ref={hostRef} className="h-full min-h-[120px] overflow-hidden bg-background p-2" />
    </TerminalChrome>
  )
}
