import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { DetailShell } from '@/components/console/DetailShell'
import { StatusIcon } from '@/components/console/StatusIcon'
import { Button } from '@/components/ui/button'
import { pmStatusApi, pmWorkItemApi } from '@/modules/pm/api'
import { priorityLabel, workflowStatusTone } from '@/modules/pm/statusTone'
import { asId } from '@/modules/pm/utils/id'
import { resolveRouteProjectId } from '@/modules/pm/utils/projectRoute'

function formatTime(value?: string | null): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 19)
}

function Field({ label, value }: { label: string; value?: string | number | null }) {
  return (
    <div className="flex flex-col gap-1 rounded-md border px-3 py-2">
      <span className="text-xs text-muted-foreground">{label}</span>
      <span className="text-sm">{value || '—'}</span>
    </div>
  )
}

export function WorkItemDetailPage() {
  const { projectId: projectParam = '', typeCode = '', itemId = '' } = useParams()
  const projectId = resolveRouteProjectId(projectParam)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const tab = searchParams.get('tab') || '概览'
  const [comment, setComment] = useState('')

  const setTab = (value: string) => {
    const next = new URLSearchParams(searchParams)
    if (value === '概览') next.delete('tab')
    else next.set('tab', value)
    setSearchParams(next, { replace: true })
  }

  const itemQuery = useQuery({
    queryKey: ['pm-work-item', itemId],
    queryFn: () => pmWorkItemApi.getById(itemId),
    enabled: !!itemId,
  })
  const statusQuery = useQuery({
    queryKey: ['pm-status-options', projectId, typeCode],
    queryFn: () => pmStatusApi.options(projectId!, typeCode),
    enabled: !!projectId && !!typeCode,
  })
  const transitionsQuery = useQuery({
    queryKey: ['pm-allowed-transitions', projectId, typeCode, itemQuery.data?.status, itemId],
    queryFn: () => pmStatusApi.allowed(projectId!, typeCode, itemQuery.data?.status ?? '', itemId),
    enabled: tab === '概览' && !!projectId && !!itemQuery.data,
  })
  const commentsQuery = useQuery({
    queryKey: ['pm-work-item-comments', itemId],
    queryFn: () => pmWorkItemApi.listComments(itemId),
    enabled: tab === '评论' && !!itemId,
  })
  const activitiesQuery = useQuery({
    queryKey: ['pm-work-item-activities', itemId],
    queryFn: () => pmWorkItemApi.listActivities(itemId),
    enabled: tab === '活动' && !!itemId,
  })

  const transition = useMutation({
    mutationFn: (transitionId: string) => pmWorkItemApi.transition(itemId, { transitionId }),
    onSuccess: async () => {
      toast.success('状态已更新')
      await queryClient.invalidateQueries({ queryKey: ['pm-work-item', itemId] })
      await queryClient.invalidateQueries({ queryKey: ['pm-work-items', projectId, typeCode] })
      await queryClient.invalidateQueries({ queryKey: ['pm-allowed-transitions'] })
    },
    onError: (error: Error) => toast.error(error.message || '流转失败'),
  })
  const saveComment = useMutation({
    mutationFn: () => pmWorkItemApi.saveComment({ workItemId: itemId, content: comment.trim() }),
    onSuccess: async () => {
      setComment('')
      toast.success('评论已保存')
      await queryClient.invalidateQueries({ queryKey: ['pm-work-item-comments', itemId] })
    },
    onError: (error: Error) => toast.error(error.message || '评论失败'),
  })
  const remove = useMutation({
    mutationFn: () => pmWorkItemApi.delete(itemId),
    onSuccess: async () => {
      toast.success('事项已删除')
      await queryClient.invalidateQueries({ queryKey: ['pm-work-items', projectId, typeCode] })
      void navigate(projectId ? `/pm/projects/${projectId}/items/${typeCode}` : '/pm/projects')
    },
    onError: (error: Error) => toast.error(error.message || '删除失败'),
  })

  const item = itemQuery.data
  const definition = (statusQuery.data ?? []).find((entry) => entry.statusCode === item?.status)
  const listPath = projectId ? `/pm/projects/${projectId}/items/${typeCode}` : '/pm/projects'

  if (!projectId || !itemId) {
    return <p className="text-sm text-destructive">事项地址无效</p>
  }

  return (
    <DetailShell
      leading={
        <Button variant="ghost" size="sm" asChild>
          <Link to={listPath}>返回</Link>
        </Button>
      }
      title={item?.itemKey || item?.title || '事项'}
      description={item?.itemKey ? item.title : undefined}
      meta={
        item ? (
          <StatusIcon
            status={item.status}
            label={definition?.statusName || item.status || '未设置'}
            tone={workflowStatusTone(definition)}
          />
        ) : itemQuery.isLoading ? (
          '加载中…'
        ) : itemQuery.isError ? (
          '加载失败'
        ) : null
      }
      actions={
        <Button
          variant="outline"
          size="sm"
          className="text-destructive"
          onClick={() => {
            if (window.confirm(`删除事项「${item?.title || itemId}」？`)) remove.mutate()
          }}
        >
          删除
        </Button>
      }
      tabs={[
        { value: '概览', label: '概览' },
        { value: '评论', label: '评论' },
        { value: '活动', label: '活动' },
      ]}
      value={tab}
      onValueChange={setTab}
    >
      {tab === '概览' && itemQuery.isLoading && <p className="text-sm text-muted-foreground">加载中…</p>}
      {tab === '概览' && itemQuery.isError && <p className="text-sm text-destructive">事项加载失败</p>}
      {tab === '概览' && item && (
        <div className="flex flex-col gap-4">
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Field label="类型" value={item.typeCode} />
            <Field label="状态" value={definition?.statusName || item.status} />
            <Field label="优先级" value={priorityLabel(item.priority)} />
            <Field label="更新时间" value={formatTime(item.updateTime)} />
          </div>
          <section className="flex flex-col gap-2">
            <h2 className="text-sm font-medium">描述</h2>
            <p className="whitespace-pre-wrap text-sm text-muted-foreground">{item.description?.trim() || '还没有描述'}</p>
          </section>
          <div className="flex flex-wrap gap-2">
            {(transitionsQuery.data?.transitions ?? []).map((entry) => (
              <Button
                key={entry.id}
                size="sm"
                variant="outline"
                disabled={transition.isPending}
                onClick={() => transition.mutate(entry.id)}
              >
                {entry.name}
              </Button>
            ))}
          </div>
        </div>
      )}
      {tab === '评论' && (
        <div className="flex flex-col gap-4">
          <form
            className="flex flex-col gap-2"
            onSubmit={(event) => {
              event.preventDefault()
              if (comment.trim()) saveComment.mutate()
            }}
          >
            <textarea
              value={comment}
              onChange={(event) => setComment(event.target.value)}
              placeholder="写一条评论"
              aria-label="评论内容"
              className="min-h-24 w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring"
            />
            <div>
              <Button type="submit" size="sm" disabled={!comment.trim() || saveComment.isPending}>
                发表
              </Button>
            </div>
          </form>
          {commentsQuery.isLoading && <p className="text-sm text-muted-foreground">加载评论…</p>}
          {commentsQuery.isError && <p className="text-sm text-destructive">评论加载失败</p>}
          <ul className="flex flex-col gap-3">
            {(commentsQuery.data ?? []).map((entry) => (
              <li key={asId(entry.id)} className="rounded-lg border px-3 py-2">
                <div className="flex flex-wrap items-baseline justify-between gap-2 text-xs text-muted-foreground">
                  <span>{entry.authorName || '未知'}</span>
                  <span>{formatTime(entry.createTime)}</span>
                </div>
                <p className="mt-1 whitespace-pre-wrap text-sm">{entry.content}</p>
              </li>
            ))}
            {commentsQuery.isSuccess && (commentsQuery.data ?? []).length === 0 && (
              <li className="text-sm text-muted-foreground">还没有评论</li>
            )}
          </ul>
        </div>
      )}
      {tab === '活动' && (
        <div className="flex flex-col gap-2">
          {activitiesQuery.isLoading && <p className="text-sm text-muted-foreground">加载活动…</p>}
          {activitiesQuery.isError && <p className="text-sm text-destructive">活动加载失败</p>}
          <ul className="flex flex-col gap-2">
            {(activitiesQuery.data ?? []).map((entry) => (
              <li key={asId(entry.id)} className="flex flex-wrap items-baseline justify-between gap-2 rounded-lg border px-3 py-2 text-sm">
                <span>
                  <span className="font-medium">{entry.actorName || '系统'}</span>
                  <span className="text-muted-foreground">
                    {' '}
                    {entry.eventType === 'CREATE'
                      ? '创建了事项'
                      : entry.fieldName
                        ? `将${entry.fieldName}从「${entry.oldLabel || entry.oldValue || '空'}」改为「${entry.newLabel || entry.newValue || '空'}」`
                        : entry.eventType}
                  </span>
                </span>
                <span className="text-xs text-muted-foreground">{formatTime(entry.createTime)}</span>
              </li>
            ))}
            {activitiesQuery.isSuccess && (activitiesQuery.data ?? []).length === 0 && (
              <li className="text-sm text-muted-foreground">还没有活动</li>
            )}
          </ul>
        </div>
      )}
    </DetailShell>
  )
}
