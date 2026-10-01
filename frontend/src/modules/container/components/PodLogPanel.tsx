import { useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { podApi } from '@/modules/container/api/pod'
import { getToken } from '@/shared/keycloak'

export function PodLogPanel({
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
  const [follow, setFollow] = useState(true)
  const [live, setLive] = useState('')
  const viewRef = useRef<HTMLPreElement>(null)

  useEffect(() => {
    if (!container && containers[0]) setContainer(containers[0])
  }, [container, containers])

  const logs = useQuery({
    queryKey: ['container-pod-logs', clusterId, namespace, name, container],
    queryFn: () => podApi.logs(clusterId, namespace, name, { container: container || undefined, tailLines: 200 }),
    enabled: !!clusterId && !!namespace && !!name,
  })

  useEffect(() => {
    setLive('')
  }, [container, clusterId, namespace, name])

  useEffect(() => {
    if (!follow) return
    let ws: WebSocket | null = null
    let stopped = false
    void (async () => {
      const token = await getToken()
      if (stopped || !token) return
      const proto = location.protocol === 'https:' ? 'wss:' : 'ws:'
      const params = new URLSearchParams({ namespace, pod: name })
      if (container) params.set('container', container)
      ws = new WebSocket(
        `${proto}//${location.host}/api/ws/container/logs/${clusterId}?${params.toString()}`,
        [token],
      )
      ws.binaryType = 'arraybuffer'
      ws.onmessage = (event) => {
        if (typeof event.data === 'string') {
          setLive((prev) => prev + event.data)
          return
        }
        const text = new TextDecoder().decode(event.data as ArrayBuffer)
        setLive((prev) => prev + text)
      }
    })()
    return () => {
      stopped = true
      ws?.close()
    }
  }, [clusterId, container, follow, name, namespace])

  useEffect(() => {
    const node = viewRef.current
    if (node) node.scrollTop = node.scrollHeight
  }, [logs.data, live])

  const body = `${logs.data ?? ''}${live}`

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-2">
        <select
          className="h-8 rounded-md border border-input bg-background px-2 text-sm"
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
        <Button variant="outline" size="sm" onClick={() => void logs.refetch()}>
          刷新
        </Button>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={follow} onChange={(event) => setFollow(event.target.checked)} />
          实时跟踪
        </label>
      </div>
      {logs.isError && <p className="text-sm text-destructive">日志加载失败</p>}
      <pre
        ref={viewRef}
        className="h-[28rem] overflow-auto rounded-md border bg-muted/40 p-3 font-mono text-xs whitespace-pre-wrap"
      >
        {logs.isLoading ? '加载中…' : body || '暂无日志'}
      </pre>
    </div>
  )
}
