import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { DataTable } from '@/components/console/DataTable'
import { DetailShell } from '@/components/console/DetailShell'
import { StatusIcon } from '@/components/console/StatusIcon'
import { Button } from '@/components/ui/button'
import { DebugWorkbench } from '@/modules/api-test/pages/DebugWorkbench'
import { apiDefinitionApi } from '@/modules/api-test/define/api/definition'
import { apiStatusLabel } from '@/modules/api-test/define/statusLabel'
import type { ApiDefinitionParamVO, ApiDefinitionResponseVO } from '@/modules/api-test/define/types/definition'
import { useAuthStore } from '@/stores/auth'

const PARAM_LABEL: Record<string, string> = {
  path: '路径',
  query: 'Query',
  header: '请求头',
  body: '请求体',
}

export function DefinitionDetailPage() {
  const { definitionId = '' } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const userId = useAuthStore((s) => s.user?.id)
  const [searchParams, setSearchParams] = useSearchParams()
  const tab = searchParams.get('tab') || '概览'

  const setTab = (value: string) => {
    const next = new URLSearchParams(searchParams)
    if (value === '概览') next.delete('tab')
    else next.set('tab', value)
    setSearchParams(next, { replace: true })
  }

  const query = useQuery({
    queryKey: ['api-definition', definitionId],
    queryFn: () => apiDefinitionApi.detail(definitionId),
    enabled: !!definitionId,
  })
  const item = query.data

  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey: ['api-definition', definitionId] })
    await queryClient.invalidateQueries({ queryKey: ['api-definitions'] })
  }
  const publish = useMutation({
    mutationFn: () => apiDefinitionApi.publish(definitionId, userId!),
    onSuccess: async () => {
      toast.success('接口已发布')
      await refresh()
    },
    onError: (error: Error) => toast.error(error.message || '发布失败'),
  })
  const deprecate = useMutation({
    mutationFn: () => apiDefinitionApi.deprecate(definitionId, userId!),
    onSuccess: async () => {
      toast.success('接口已废弃')
      await refresh()
    },
    onError: (error: Error) => toast.error(error.message || '废弃失败'),
  })
  const revert = useMutation({
    mutationFn: () => apiDefinitionApi.revertDraft(definitionId, userId!),
    onSuccess: async () => {
      toast.success('已恢复为草稿')
      await refresh()
    },
    onError: (error: Error) => toast.error(error.message || '恢复失败'),
  })
  const remove = useMutation({
    mutationFn: () => apiDefinitionApi.delete(definitionId),
    onSuccess: async () => {
      toast.success('接口已删除')
      await queryClient.invalidateQueries({ queryKey: ['api-definitions'] })
      void navigate('/api-test')
    },
    onError: (error: Error) => toast.error(error.message || '删除失败'),
  })

  const busy = publish.isPending || deprecate.isPending || revert.isPending
  const canMutate = userId != null && userId !== ''

  return (
    <DetailShell
      leading={
        <Button variant="ghost" size="sm" asChild>
          <Link to="/api-test">返回</Link>
        </Button>
      }
      title={item?.name || '接口'}
      description={item ? `${item.method} ${item.path}` : undefined}
      meta={
        item ? (
          <StatusIcon status={item.status} label={apiStatusLabel(item.status)} />
        ) : query.isLoading ? (
          '加载中…'
        ) : query.isError ? (
          '加载失败'
        ) : null
      }
      actions={
        <>
          {item?.status === 'DRAFT' && (
            <Button size="sm" disabled={!canMutate || busy} onClick={() => publish.mutate()}>
              发布
            </Button>
          )}
          {item?.status === 'PUBLISHED' && (
            <Button size="sm" variant="outline" disabled={!canMutate || busy} onClick={() => deprecate.mutate()}>
              废弃
            </Button>
          )}
          {item && item.status !== 'DRAFT' && (
            <Button size="sm" variant="outline" disabled={!canMutate || busy} onClick={() => revert.mutate()}>
              恢复草稿
            </Button>
          )}
          <Button
            variant="outline"
            size="sm"
            className="text-destructive"
            onClick={() => {
              if (window.confirm(`删除接口「${item?.name || definitionId}」？`)) remove.mutate()
            }}
          >
            删除
          </Button>
        </>
      }
      tabs={[
        { value: '概览', label: '概览' },
        { value: '参数', label: '参数' },
        { value: '响应', label: '响应' },
        { value: '调试', label: '调试' },
      ]}
      value={tab}
      onValueChange={setTab}
    >
      {tab === '概览' && query.isLoading && <p className="text-sm text-muted-foreground">加载中…</p>}
      {tab === '概览' && query.isError && <p className="text-sm text-destructive">接口详情加载失败</p>}
      {tab === '概览' && item && (
        <div className="flex flex-col gap-4">
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Field label="协议" value={item.protocol} />
            <Field label="Host" value={item.host} />
            <Field label="分组" value={item.groupName} />
            <Field label="版本" value={item.version} />
            <Field label="Content-Type" value={item.contentType} />
            <Field label="标签" value={item.tags?.join('、')} />
          </div>
          <section className="flex flex-col gap-2">
            <h2 className="text-sm font-medium">说明</h2>
            <p className="whitespace-pre-wrap text-sm text-muted-foreground">{item.description?.trim() || '还没有说明'}</p>
          </section>
        </div>
      )}
      {tab === '参数' && (
        <DataTable<ApiDefinitionParamVO>
          columns={[
            { id: 'name', header: '名称', cell: (row) => <span className="font-medium">{row.name}</span> },
            { id: 'where', header: '位置', cell: (row) => PARAM_LABEL[row.paramType] ?? row.paramType },
            { id: 'type', header: '类型', cell: (row) => row.dataType },
            { id: 'required', header: '必填', cell: (row) => (row.required ? '是' : '否') },
            { id: 'example', header: '示例', cell: (row) => row.example || row.defaultValue || '—' },
            { id: 'desc', header: '说明', className: 'max-w-sm truncate', cell: (row) => row.description || '—' },
          ]}
          data={item?.params ?? []}
          getRowId={(row) => String(row.id)}
          loading={query.isFetching && !item}
          error={query.isError ? '参数加载失败' : undefined}
          empty="没有参数"
        />
      )}
      {tab === '响应' && (
        <div className="flex flex-col gap-4">
          <DataTable<ApiDefinitionResponseVO>
            columns={[
              { id: 'code', header: '状态码', cell: (row) => String(row.statusCode) },
              { id: 'type', header: 'Content-Type', cell: (row) => row.contentType || '—' },
              { id: 'desc', header: '说明', cell: (row) => row.description || '—' },
            ]}
            data={item?.responses ?? []}
            getRowId={(row) => String(row.id)}
            loading={query.isFetching && !item}
            error={query.isError ? '响应加载失败' : undefined}
            empty="没有响应定义"
          />
          {(item?.responses ?? []).map((response) => (
            <section key={response.id} className="flex flex-col gap-2">
              <h2 className="text-sm font-medium">{response.statusCode} 示例</h2>
              <pre className="overflow-auto rounded-lg border bg-muted/40 p-3 font-mono text-xs leading-5">
                {formatExample(response.bodyExample)}
              </pre>
            </section>
          ))}
        </div>
      )}
      {tab === '调试' && item && <DebugWorkbench definition={item} />}
      {tab === '调试' && query.isLoading && <p className="text-sm text-muted-foreground">加载中…</p>}
      {tab === '调试' && query.isError && <p className="text-sm text-destructive">接口详情加载失败</p>}
    </DetailShell>
  )
}

function Field({ label, value }: { label: string; value?: string | null }) {
  return (
    <div className="flex flex-col gap-1 rounded-md border px-3 py-2">
      <span className="text-xs text-muted-foreground">{label}</span>
      <span className="text-sm">{value?.trim() ? value : '—'}</span>
    </div>
  )
}

function formatExample(value: unknown): string {
  if (value == null || value === '') return '无示例'
  if (typeof value === 'string') return value
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}
