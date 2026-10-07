import { useEffect, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { DataTable } from '@/components/console/DataTable'
import { LogPanel } from '@/components/console/LogPanel'
import { StatusIcon } from '@/components/console/StatusIcon'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { debugApi } from '@/modules/api-test/debug/api/debug'
import { debugHistoryApi } from '@/modules/api-test/debug/api/debugHistory'
import type { ApiDebugHistoryVO, ApiDebugResultVO } from '@/modules/api-test/debug/types/debug'
import type { ApiDefinitionDetailVO, HttpMethod } from '@/modules/api-test/define/types/definition'
import { environmentApi } from '@/modules/api-test/environment/api/environment'

const METHODS: HttpMethod[] = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'HEAD', 'OPTIONS']

function buildUrl(item: ApiDefinitionDetailVO): string {
  const path = item.path.startsWith('/') ? item.path : `/${item.path}`
  const host = item.host?.trim().replace(/\/$/, '') ?? ''
  if (!host) return path
  if (/^https?:\/\//i.test(host)) return `${host}${path}`
  const protocol = (item.protocol || 'https').replace(/:$/, '').toLowerCase()
  return `${protocol}://${host}${path}`
}

function recordToLines(record?: Record<string, string> | null): string {
  if (!record) return ''
  return Object.entries(record)
    .map(([key, value]) => `${key}: ${value}`)
    .join('\n')
}

export function DebugWorkbench({ definition }: { definition: ApiDefinitionDetailVO }) {
  const [method, setMethod] = useState<string>(definition.method)
  const [url, setUrl] = useState(buildUrl(definition))
  const [environmentId, setEnvironmentId] = useState('')
  const [body, setBody] = useState('')
  const [result, setResult] = useState<ApiDebugResultVO | null>(null)

  useEffect(() => {
    setMethod(definition.method)
    setUrl(buildUrl(definition))
    setBody('')
    setResult(null)
  }, [definition])

  const environments = useQuery({
    queryKey: ['api-environments', 'debug', definition.projectId],
    queryFn: () => environmentApi.listAll(definition.projectId),
    enabled: definition.projectId != null,
  })
  const history = useQuery({
    queryKey: ['api-debug-history', definition.id],
    queryFn: () => debugHistoryApi.listByDefinition(definition.id, 20),
  })
  const execute = useMutation({
    mutationFn: () =>
      debugApi.execute({
        projectId: definition.projectId,
        definitionId: definition.id,
        environmentId: environmentId || undefined,
        url,
        method,
        body: body.trim() ? body : undefined,
        contentType: definition.contentType || undefined,
      }),
    onSuccess: async (value) => {
      setResult(value)
      await history.refetch()
    },
  })

  const responseText = result
    ? [result.errorMessage, result.responseBody, recordToLines(result.responseHeaders)].filter(Boolean).join('\n\n')
    : '发送后在这里看响应'

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-end gap-2">
        <div className="flex flex-col gap-1">
          <Label htmlFor="debug-method" className="text-xs">
            方法
          </Label>
          <select
            id="debug-method"
            aria-label="请求方法"
            className="h-8 w-28 rounded-md border border-input bg-background px-2 font-mono text-xs"
            value={method}
            onChange={(event) => setMethod(event.target.value)}
          >
            {METHODS.map((item) => (
              <option key={item} value={item}>
                {item}
              </option>
            ))}
          </select>
        </div>
        <div className="flex min-w-64 flex-1 flex-col gap-1">
          <Label htmlFor="debug-url" className="text-xs">
            URL
          </Label>
          <Input
            id="debug-url"
            value={url}
            onChange={(event) => setUrl(event.target.value)}
            className="h-8 font-mono text-xs"
            aria-label="请求地址"
          />
        </div>
        <div className="flex flex-col gap-1">
          <Label htmlFor="debug-env" className="text-xs">
            环境
          </Label>
          <select
            id="debug-env"
            aria-label="调试环境"
            className="h-8 max-w-48 rounded-md border border-input bg-background px-2 text-sm"
            value={environmentId}
            onChange={(event) => setEnvironmentId(event.target.value)}
          >
            <option value="">不使用环境</option>
            {(environments.data ?? []).map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
          </select>
        </div>
        <Button size="sm" className="h-8" disabled={!url.trim() || execute.isPending} onClick={() => execute.mutate()}>
          发送
        </Button>
      </div>
      <div className="grid gap-3 lg:grid-cols-2">
        <div className="flex flex-col gap-1">
          <Label htmlFor="debug-body" className="text-xs">
            请求体
          </Label>
          <textarea
            id="debug-body"
            value={body}
            onChange={(event) => setBody(event.target.value)}
            rows={8}
            placeholder={definition.contentType || '可选'}
            className="min-h-32 w-full rounded-lg border bg-background p-2 font-mono text-xs leading-5"
          />
        </div>
        <LogPanel
          title={
            result ? (
              <span className="inline-flex items-center gap-2">
                <StatusIcon status={result.status} label={result.status} />
                <span>
                  {result.responseStatusCode ?? '—'} · {result.durationMs} ms
                </span>
              </span>
            ) : (
              '响应'
            )
          }
          connection={execute.isPending ? 'connecting' : result ? (result.status === 'SUCCESS' ? 'connected' : 'disconnected') : 'idle'}
          className="h-56"
        >
          <pre className="p-3 whitespace-pre-wrap">{execute.isError ? (execute.error as Error).message : responseText}</pre>
        </LogPanel>
      </div>
      <section className="flex flex-col gap-2">
        <h2 className="text-sm font-medium">最近调试</h2>
        <DataTable<ApiDebugHistoryVO>
          columns={[
            { id: 'method', header: '方法', className: 'font-mono text-xs', cell: (row) => row.requestMethod },
            { id: 'url', header: '地址', className: 'max-w-sm truncate font-mono text-xs', cell: (row) => row.requestUrl },
            { id: 'status', header: '状态', cell: (row) => <StatusIcon status={row.status} /> },
            { id: 'code', header: '状态码', cell: (row) => String(row.responseStatusCode ?? '—') },
            { id: 'duration', header: '耗时', cell: (row) => `${row.durationMs} ms` },
            { id: 'time', header: '时间', cell: (row) => row.createTime?.replace('T', ' ').slice(0, 19) || '—' },
          ]}
          data={history.data ?? []}
          getRowId={(row) => String(row.id)}
          loading={history.isFetching}
          error={history.isError ? '调试历史加载失败' : undefined}
          empty="还没有调试记录"
        />
      </section>
    </div>
  )
}
