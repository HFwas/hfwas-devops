import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { DataTable } from '@/components/console/DataTable'
import { DetailShell } from '@/components/console/DetailShell'
import { StatusIcon } from '@/components/console/StatusIcon'
import { Button } from '@/components/ui/button'
import { pipelineTaskKindApi } from '@/modules/pipeline/api/pipeline'
import type { TaskKindParam } from '@/modules/pipeline/types/pipeline'

export function TaskKindDetailPage() {
  const { kind = '' } = useParams()
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const tab = searchParams.get('tab') || '概览'
  const setTab = (value: string) => {
    const next = new URLSearchParams(searchParams)
    if (value === '概览') next.delete('tab')
    else next.set('tab', value)
    setSearchParams(next, { replace: true })
  }
  const query = useQuery({
    queryKey: ['pipeline-task-kind', kind],
    queryFn: () => pipelineTaskKindApi.get(kind),
    enabled: !!kind,
  })
  const toggle = useMutation({
    mutationFn: () => pipelineTaskKindApi.toggle(kind),
    onSuccess: async () => {
      toast.success('已更新启用状态')
      await queryClient.invalidateQueries({ queryKey: ['pipeline-task-kind', kind] })
      await queryClient.invalidateQueries({ queryKey: ['pipeline-task-kinds'] })
    },
    onError: (error: Error) => toast.error(error.message || '切换失败'),
  })
  const item = query.data
  const command = item?.commandTemplate || item?.defaultCommand || ''

  return (
    <DetailShell
      leading={
        <Button variant="ghost" size="sm" asChild>
          <Link to="/pipeline/task-kinds">返回</Link>
        </Button>
      }
      title={item?.label || kind || '任务'}
      description={item?.taskGroup}
      meta={
        item ? (
          <StatusIcon status={item.enabled ? 'enabled' : 'disabled'} label={item.enabled ? '启用' : '停用'} />
        ) : query.isLoading ? (
          '加载中…'
        ) : query.isError ? (
          '加载失败'
        ) : null
      }
      actions={
        <Button variant="outline" size="sm" disabled={!item || toggle.isPending} onClick={() => toggle.mutate()}>
          {item?.enabled ? '停用' : '启用'}
        </Button>
      }
      tabs={[
        { value: '概览', label: '概览' },
        { value: '参数', label: '参数' },
      ]}
      value={tab}
      onValueChange={setTab}
    >
      {tab === '概览' && query.isLoading && <p className="text-sm text-muted-foreground">加载中…</p>}
      {tab === '概览' && query.isError && <p className="text-sm text-destructive">任务加载失败</p>}
      {tab === '概览' && item && (
        <div className="flex flex-col gap-4">
          <p className="text-sm text-muted-foreground">{item.description || item.hint || '没有说明'}</p>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Field label="镜像" value={item.toolImage || item.defaultImage} />
            <Field label="CPU" value={item.cpuRequest} />
            <Field label="内存" value={item.memoryRequest} />
          </div>
          <section className="flex flex-col gap-2">
            <h2 className="text-sm font-medium">命令模板</h2>
            <pre className="overflow-auto rounded-lg border bg-muted/40 p-3 font-mono text-xs leading-5">
              {command || '没有默认命令'}
            </pre>
          </section>
        </div>
      )}
      {tab === '参数' && (
        <DataTable<TaskKindParam>
          columns={[
            { id: 'label', header: '名称', cell: (row) => <span className="font-medium">{row.paramLabel}</span> },
            { id: 'key', header: '键', className: 'font-mono text-xs', cell: (row) => row.paramKey },
            { id: 'type', header: '类型', cell: (row) => row.paramType },
            { id: 'required', header: '必填', cell: (row) => (row.required ? '是' : '否') },
            { id: 'default', header: '默认值', cell: (row) => row.defaultValue || '—' },
          ]}
          data={item?.params ?? []}
          getRowId={(row) => row.paramKey}
          loading={query.isFetching && !item}
          error={query.isError ? '参数加载失败' : undefined}
          empty="这个任务没有参数"
        />
      )}
    </DetailShell>
  )
}

function Field({ label, value }: { label: string; value?: string | null }) {
  return (
    <div className="flex flex-col gap-1 rounded-md border px-3 py-2">
      <span className="text-xs text-muted-foreground">{label}</span>
      <span className="truncate text-sm">{value?.trim() ? value : '—'}</span>
    </div>
  )
}
