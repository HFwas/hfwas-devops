import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { DataTable } from '@/components/console/DataTable'
import { DetailShell } from '@/components/console/DetailShell'
import { StatusIcon } from '@/components/console/StatusIcon'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { TaskKindOverviewForm } from '@/modules/pipeline/components/TaskKindOverviewForm'
import { TaskKindParamEditor } from '@/modules/pipeline/components/TaskKindParamEditor'
import { TaskKindVersionsPanel } from '@/modules/pipeline/components/TaskKindVersionsPanel'
import { pipelineTaskKindApi } from '@/modules/pipeline/api/pipeline'
import {
  displayVersion,
  paramTypeLabel,
  taskKindUpdatePayload,
  visibilityLabel,
  type TaskKindOverviewInput,
} from '@/modules/pipeline/taskKindView'
import type { TaskKindParam } from '@/modules/pipeline/types/pipeline'
import { useAuthStore } from '@/stores/auth'

export function TaskKindDetailPage() {
  const { kind = '' } = useParams()
  const isAdmin = useAuthStore((state) => state.isAdmin())
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const [editingOverview, setEditingOverview] = useState(false)
  const [paramEpoch, setParamEpoch] = useState(0)
  const tab = searchParams.get('tab') || '概览'
  const setTab = (value: string) => {
    setEditingOverview(false)
    const next = new URLSearchParams(searchParams)
    next.delete('edit')
    if (value === '概览') next.delete('tab')
    else next.set('tab', value)
    setSearchParams(next, { replace: true })
  }
  useEffect(() => {
    if (isAdmin && searchParams.get('edit') === 'overview' && tab === '概览') setEditingOverview(true)
  }, [isAdmin, searchParams, tab])
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
  const save = useMutation({
    mutationFn: (body: Parameters<typeof pipelineTaskKindApi.update>[1]) => pipelineTaskKindApi.update(kind, body),
    onSuccess: async () => {
      toast.success('已保存，版本号未变化')
      setEditingOverview(false)
      setParamEpoch((value) => value + 1)
      await queryClient.invalidateQueries({ queryKey: ['pipeline-task-kind', kind] })
      await queryClient.invalidateQueries({ queryKey: ['pipeline-task-kinds'] })
    },
    onError: (error: Error) => toast.error(error.message || '保存失败'),
  })
  const item = query.data
  const command = item?.commandTemplate || item?.defaultCommand || ''

  function saveOverview(values: TaskKindOverviewInput) {
    if (!item) return
    save.mutate(taskKindUpdatePayload(item, { overview: values }))
  }

  function saveParams(params: TaskKindParam[]) {
    if (!item) return
    const keys = new Set<string>()
    for (const param of params) {
      if (keys.has(param.paramKey)) {
        toast.error(`参数键重复: ${param.paramKey}`)
        return
      }
      keys.add(param.paramKey)
    }
    save.mutate(taskKindUpdatePayload(item, { params }))
  }

  return (
    <DetailShell
      leading={
        <Button variant="ghost" size="sm" asChild>
          <Link to="/pipeline/task-kinds">返回</Link>
        </Button>
      }
      title={item?.label || kind || '任务'}
      description={item ? `${item.taskGroup || '未分组'} · ${item.kindValue}` : undefined}
      meta={
        item ? (
          <span className="inline-flex items-center gap-2">
            <Badge variant="secondary">{displayVersion(item.version)}</Badge>
            <StatusIcon status={item.enabled ? 'enabled' : 'disabled'} label={item.enabled ? '启用' : '停用'} />
          </span>
        ) : query.isLoading ? (
          '加载中…'
        ) : query.isError ? (
          '加载失败'
        ) : null
      }
      actions={
        isAdmin ? (
          <>
            {tab === '概览' && !editingOverview ? (
              <Button size="sm" disabled={!item} onClick={() => setEditingOverview(true)}>
                编辑概览
              </Button>
            ) : null}
            <Button variant="outline" size="sm" disabled={!item || toggle.isPending} onClick={() => toggle.mutate()}>
              {item?.enabled ? '停用' : '启用'}
            </Button>
          </>
        ) : null
      }
      tabs={[
        { value: '概览', label: '概览' },
        { value: '参数', label: '参数' },
        { value: '版本', label: '版本' },
      ]}
      value={tab}
      onValueChange={setTab}
    >
      {tab === '概览' && query.isLoading && <p className="text-sm text-muted-foreground">加载中…</p>}
      {tab === '概览' && query.isError && <p className="text-sm text-destructive">任务加载失败</p>}
      {tab === '概览' && item && editingOverview && isAdmin && (
        <TaskKindOverviewForm
          item={item}
          saving={save.isPending}
          onSave={saveOverview}
          onCancel={() => setEditingOverview(false)}
        />
      )}
      {tab === '概览' && item && !(editingOverview && isAdmin) && (
        <div className="flex flex-col gap-4">
          {!isAdmin ? (
            <p className="text-sm text-muted-foreground">
              只有管理员可以编辑概览、参数和版本。判断依据是当前账号的 role 为 admin（忽略大小写），与用户管理使用同一套检查。
            </p>
          ) : null}
          <p className="text-sm text-muted-foreground">{item.description || item.hint || '没有说明'}</p>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Field label="版本" value={displayVersion(item.version)} />
            <Field label="镜像" value={item.toolImage || item.defaultImage} />
            <Field label="CPU 请求" value={item.cpuRequest} />
            <Field label="CPU 上限" value={item.cpuLimit} />
            <Field label="内存请求" value={item.memoryRequest} />
            <Field label="内存上限" value={item.memoryLimit} />
            <Field label="排序" value={String(item.sortOrder ?? '')} />
          </div>
          {item.hint ? (
            <section className="flex flex-col gap-2">
              <h2 className="text-sm font-medium">提示</h2>
              <p className="text-sm text-muted-foreground">{item.hint}</p>
            </section>
          ) : null}
          <section className="flex flex-col gap-2">
            <h2 className="text-sm font-medium">命令模板</h2>
            <pre className="overflow-auto rounded-lg border bg-muted/40 p-3 font-mono text-xs leading-5">
              {command || '没有默认命令'}
            </pre>
          </section>
        </div>
      )}
      {tab === '参数' && isAdmin && item && (
        <TaskKindParamEditor
          key={`${kind}-${paramEpoch}`}
          params={item.params ?? []}
          saving={save.isPending}
          onSave={saveParams}
          onCancel={() => setParamEpoch((value) => value + 1)}
        />
      )}
      {tab === '参数' && !isAdmin && (
        <div className="flex flex-col gap-3">
          <p className="text-sm text-muted-foreground">参数为只读。管理员可以在这里添加、修改和删除参数。</p>
          <DataTable<TaskKindParam>
            columns={[
              { id: 'label', header: '名称', cell: (row) => <span className="font-medium">{row.paramLabel}</span> },
              { id: 'key', header: '键', className: 'font-mono text-xs', cell: (row) => row.paramKey },
              { id: 'type', header: '类型', cell: (row) => paramTypeLabel(row.paramType) },
              { id: 'visibility', header: '可见性', cell: (row) => visibilityLabel(row.visibility) },
              { id: 'required', header: '必填', cell: (row) => (row.required ? '是' : '否') },
              { id: 'default', header: '默认值', cell: (row) => row.defaultValue || '—' },
            ]}
            data={item?.params ?? []}
            getRowId={(row) => row.paramKey}
            loading={query.isFetching && !item}
            error={query.isError ? '参数加载失败' : undefined}
            empty="这个任务没有参数"
          />
        </div>
      )}
      {tab === '版本' && <TaskKindVersionsPanel kind={kind} isAdmin={isAdmin} />}
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
