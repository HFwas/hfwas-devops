import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { ConfirmDialog } from '@/components/console/ConfirmDialog'
import { StatusIcon } from '@/components/console/StatusIcon'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog'
import { Textarea } from '@/components/ui/textarea'
import { pmStatusApi, pmWorkItemApi } from '@/modules/pm/api'
import { priorityLabel, workflowStatusTone } from '@/modules/pm/statusTone'
import { formatDateTime } from '@/modules/pm/utils/format'
import { asId } from '@/modules/pm/utils/id'

export type WorkItemDetailTab = 'detail' | 'comments' | 'activity'

interface DetailTabOption {
  value: WorkItemDetailTab
  label: string
}

const DETAIL_TABS: DetailTabOption[] = [
  { value: 'detail', label: '概览' },
  { value: 'comments', label: '评论' },
  { value: 'activity', label: '活动' },
]

function Field({ label, value }: { label: string; value?: string | number | null }) {
  return (
    <div className="flex flex-col gap-1 rounded-md border px-3 py-2">
      <span className="text-xs text-muted-foreground">{label}</span>
      <span className="text-sm">{value || '—'}</span>
    </div>
  )
}

/**
 * 事项详情弹窗：叠加在列表上，关闭只移除详情参数，列表状态保持不变。
 * 页签与数据都随 `detail` / `dtab` 参数驱动，URL 可直接分享。
 */
export function WorkItemDetailDialog({
  projectId,
  typeCode,
  itemId,
  tab,
  open,
  onOpenChange,
  onTabChange,
}: {
  projectId: string
  typeCode: string
  itemId: string | null
  tab: WorkItemDetailTab
  open: boolean
  onOpenChange: (open: boolean) => void
  onTabChange: (tab: WorkItemDetailTab) => void
}) {
  const queryClient = useQueryClient()
  const [comment, setComment] = useState('')
  const enabled = open && !!itemId

  const itemQuery = useQuery({
    queryKey: ['pm-work-item', itemId],
    queryFn: () => pmWorkItemApi.getById(itemId!),
    enabled,
  })
  const itemTypeCode = itemQuery.data?.typeCode || typeCode
  const statusQuery = useQuery({
    queryKey: ['pm-status-options', projectId, itemTypeCode],
    queryFn: () => pmStatusApi.options(projectId, itemTypeCode),
    enabled: enabled && !!projectId && !!itemTypeCode && !!itemQuery.data,
  })
  const transitionsQuery = useQuery({
    queryKey: ['pm-allowed-transitions', projectId, itemTypeCode, itemQuery.data?.status, itemId],
    queryFn: () => pmStatusApi.allowed(projectId, itemTypeCode, itemQuery.data?.status ?? '', itemId!),
    enabled: enabled && tab === 'detail' && !!projectId && !!itemQuery.data,
  })
  const commentsQuery = useQuery({
    queryKey: ['pm-work-item-comments', itemId],
    queryFn: () => pmWorkItemApi.listComments(itemId!),
    enabled: enabled && tab === 'comments',
  })
  const activitiesQuery = useQuery({
    queryKey: ['pm-work-item-activities', itemId],
    queryFn: () => pmWorkItemApi.listActivities(itemId!),
    enabled: enabled && tab === 'activity',
  })

  const transition = useMutation({
    mutationFn: (transitionId: string) => pmWorkItemApi.transition(itemId!, { transitionId }),
    onSuccess: async () => {
      toast.success('状态已更新')
      await queryClient.invalidateQueries({ queryKey: ['pm-work-item', itemId] })
      await queryClient.invalidateQueries({ queryKey: ['pm-work-items', projectId, typeCode] })
      await queryClient.invalidateQueries({ queryKey: ['pm-allowed-transitions'] })
    },
    onError: (error: Error) => toast.error(error.message || '流转失败'),
  })
  const saveComment = useMutation({
    mutationFn: () => pmWorkItemApi.saveComment({ workItemId: itemId!, content: comment.trim() }),
    onSuccess: async () => {
      setComment('')
      toast.success('评论已保存')
      await queryClient.invalidateQueries({ queryKey: ['pm-work-item-comments', itemId] })
    },
    onError: (error: Error) => toast.error(error.message || '评论失败'),
  })
  const remove = useMutation({
    mutationFn: () => pmWorkItemApi.delete(itemId!),
    onSuccess: async () => {
      toast.success('事项已删除')
      onOpenChange(false)
      await queryClient.invalidateQueries({ queryKey: ['pm-work-items', projectId, typeCode] })
    },
    onError: (error: Error) => toast.error(error.message || '删除失败'),
  })

  const item = itemQuery.data
  const definition = (statusQuery.data ?? []).find((entry) => entry.statusCode === item?.status)

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        aria-describedby={undefined}
        className="flex h-[min(860px,88vh)] w-[min(1280px,94vw)] max-w-none flex-col gap-0 overflow-hidden p-0"
      >
        <DialogTitle className="sr-only">{item?.title || '事项详情'}</DialogTitle>
        <div className="flex shrink-0 items-center gap-2 border-b bg-card px-4 py-2.5 pr-12 text-xs text-muted-foreground">
          <span className="font-mono text-[11px] text-foreground">{item?.itemKey || '—'}</span>
          {item?.typeCode ? (
            <>
              <span className="size-[3px] rounded-full bg-muted-foreground/50" />
              <span>{item.typeCode}</span>
            </>
          ) : null}
          {item ? (
            <StatusIcon
              status={item.status}
              label={definition?.statusName || item.status || '未设置'}
              tone={workflowStatusTone(definition)}
            />
          ) : null}
          <span className="flex-1" />
          {item ? (
            <ConfirmDialog
              title={`删除事项「${item.title || itemId}」？`}
              description="删除后不可恢复。"
              confirmText="删除"
              destructive
              confirmDisabled={remove.isPending}
              onConfirm={() => remove.mutate()}
            >
              <Button variant="ghost" size="sm" className="text-destructive">
                删除
              </Button>
            </ConfirmDialog>
          ) : null}
        </div>

        <div role="tablist" aria-label="事项详情页签" className="flex shrink-0 gap-1 border-b px-3">
          {DETAIL_TABS.map((option) => {
            const active = option.value === tab
            return (
              <button
                key={option.value}
                type="button"
                role="tab"
                aria-selected={active}
                className={`-mb-px border-b-2 px-3 py-2 text-sm transition-colors ${
                  active
                    ? 'border-primary font-medium text-foreground'
                    : 'border-transparent text-muted-foreground hover:text-foreground'
                }`}
                onClick={() => onTabChange(option.value)}
              >
                {option.label}
              </button>
            )
          })}
        </div>

        <div className="flex-1 overflow-y-auto p-4">
          {!itemId ? null : itemQuery.isPending ? (
            <p className="text-sm text-muted-foreground">加载中…</p>
          ) : itemQuery.isError ? (
            <p className="text-sm text-destructive">事项加载失败</p>
          ) : !item ? (
            <p className="text-sm text-muted-foreground">事项不存在或无权访问</p>
          ) : tab === 'detail' ? (
            <div className="flex flex-col gap-4">
              <div className="flex flex-col gap-1">
                <h2 className="text-2xl font-bold tracking-tight">{item.title}</h2>
                <p className="text-sm text-muted-foreground">
                  {item.itemKey} · 更新于 {formatDateTime(item.updateTime)}
                </p>
              </div>
              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                <Field label="类型" value={item.typeCode} />
                <Field label="状态" value={definition?.statusName || item.status} />
                <Field label="优先级" value={priorityLabel(item.priority)} />
                <Field label="更新时间" value={formatDateTime(item.updateTime)} />
              </div>
              <section className="flex flex-col gap-2">
                <h3 className="text-sm font-medium">描述</h3>
                <p className="whitespace-pre-wrap text-sm text-muted-foreground">
                  {item.description?.trim() || '还没有描述'}
                </p>
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
          ) : tab === 'comments' ? (
            <div className="flex flex-col gap-4">
              <form
                className="flex flex-col gap-2"
                onSubmit={(event) => {
                  event.preventDefault()
                  if (comment.trim()) saveComment.mutate()
                }}
              >
                <Textarea
                  value={comment}
                  onChange={(event) => setComment(event.target.value)}
                  placeholder="写一条评论"
                  aria-label="评论内容"
                  className="min-h-24"
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
                      <span>{formatDateTime(entry.createTime)}</span>
                    </div>
                    <p className="mt-1 whitespace-pre-wrap text-sm">{entry.content}</p>
                  </li>
                ))}
                {commentsQuery.isSuccess && (commentsQuery.data ?? []).length === 0 && (
                  <li className="text-sm text-muted-foreground">还没有评论</li>
                )}
              </ul>
            </div>
          ) : (
            <div className="flex flex-col gap-2">
              {activitiesQuery.isLoading && <p className="text-sm text-muted-foreground">加载活动…</p>}
              {activitiesQuery.isError && <p className="text-sm text-destructive">活动加载失败</p>}
              <ul className="flex flex-col gap-2">
                {(activitiesQuery.data ?? []).map((entry) => (
                  <li
                    key={asId(entry.id)}
                    className="flex flex-wrap items-baseline justify-between gap-2 rounded-lg border px-3 py-2 text-sm"
                  >
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
                    <span className="text-xs text-muted-foreground">{formatDateTime(entry.createTime)}</span>
                  </li>
                ))}
                {activitiesQuery.isSuccess && (activitiesQuery.data ?? []).length === 0 && (
                  <li className="text-sm text-muted-foreground">还没有活动</li>
                )}
              </ul>
            </div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}
