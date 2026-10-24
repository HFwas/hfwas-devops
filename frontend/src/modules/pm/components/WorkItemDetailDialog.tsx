import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Copy, Pencil, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { ConfirmDialog } from '@/components/console/ConfirmDialog'
import { StatusIcon } from '@/components/console/StatusIcon'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogTitle } from '@/components/ui/dialog'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Textarea } from '@/components/ui/textarea'
import { pmFieldApi, pmModuleApi, pmStatusApi, pmWorkItemApi } from '@/modules/pm/api'
import { priorityLabel, workflowStatusTone } from '@/modules/pm/statusTone'
import { PRIORITY_OPTIONS, typeLabel, type PmWorkItem } from '@/modules/pm/types'
import { formatDateTime } from '@/modules/pm/utils/format'
import { asId } from '@/modules/pm/utils/id'
import { renderMarkdown } from '@/modules/pm/utils/markdown'
import { userAuthApi } from '@/modules/user/api'
import { isApiError } from '@/shared/errors/apiError'
import { ResultCode } from '@/shared/errors/resultCode'

export type WorkItemDetailTab = 'detail' | 'comments' | 'activity'

const DETAIL_TABS: Array<{ value: WorkItemDetailTab; label: string }> = [
  { value: 'detail', label: '详情' },
  { value: 'activity', label: '操作记录' },
  { value: 'comments', label: '评论' },
]

interface Draft {
  title: string
  description: string
  priority?: string
  assigneeId?: number
  endTime?: string
  moduleId?: number | null
}

const selectClass =
  'h-8 w-full rounded-md border border-input bg-background px-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring'

function toDateInput(value?: string) {
  return value ? value.slice(0, 10) : ''
}

function draftOf(item: PmWorkItem): Draft {
  return {
    title: item.title ?? '',
    description: item.description ?? '',
    priority: item.priority,
    assigneeId: item.assigneeId,
    endTime: toDateInput(item.endTime),
    moduleId: item.moduleId ?? null,
  }
}

/**
 * 事项详情弹窗：叠加在列表上，关闭只移除详情参数，列表状态保持不变。
 * 布局对齐原型：顶部条（编号/类型/复制链接/关闭）、大标题与操作、状态信息、详情/操作记录/评论页签、左描述右属性双栏。
 */
export function WorkItemDetailDialog({
  projectId,
  typeCode,
  itemId,
  tab,
  open,
  onOpenChange,
  onTabChange,
  readOnly = false,
}: {
  projectId: string
  typeCode: string
  itemId: string | null
  tab: WorkItemDetailTab
  open: boolean
  onOpenChange: (open: boolean) => void
  onTabChange: (tab: WorkItemDetailTab) => void
  readOnly?: boolean
}) {
  const queryClient = useQueryClient()
  const [comment, setComment] = useState('')
  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState<Draft>({ title: '', description: '' })
  const [invalid, setInvalid] = useState(false)
  const [inline, setInline] = useState<string | null>(null)
  const [inlineValue, setInlineValue] = useState('')
  const [descEditing, setDescEditing] = useState(false)
  const [descTab, setDescTab] = useState<'write' | 'preview'>('write')
  const [pendingStatus, setPendingStatus] = useState('')
  const enabled = open && !!itemId

  const itemQuery = useQuery({
    queryKey: ['pm-work-item', itemId],
    queryFn: () => pmWorkItemApi.getById(itemId!),
    enabled,
  })
  const item = itemQuery.data
  const itemTypeCode = item?.typeCode || typeCode

  const statusQuery = useQuery({
    queryKey: ['pm-status-options', projectId, itemTypeCode],
    queryFn: () => pmStatusApi.options(projectId, itemTypeCode),
    enabled: enabled && !!projectId && !!itemTypeCode && !!item,
  })
  const transitionsQuery = useQuery({
    queryKey: ['pm-allowed-transitions', projectId, itemTypeCode, item?.status, itemId],
    queryFn: () => pmStatusApi.allowed(projectId, itemTypeCode, item?.status ?? '', itemId!),
    enabled: enabled && !!projectId && !!item,
  })
  const usersQuery = useQuery({
    queryKey: ['pm-list-users'],
    queryFn: () => userAuthApi.userOptions(),
    enabled,
  })
  const modulesQuery = useQuery({
    queryKey: ['pm-modules', projectId],
    queryFn: () => pmModuleApi.flat(projectId),
    enabled: enabled && !!projectId,
  })
  const fieldsQuery = useQuery({
    queryKey: ['pm-list-fields', projectId, itemTypeCode],
    queryFn: () => (itemTypeCode ? pmFieldApi.list(projectId, itemTypeCode) : Promise.resolve([])),
    enabled: enabled && !!projectId && !!itemTypeCode,
  })
  const commentsCountQuery = useQuery({
    queryKey: ['pm-work-item-comments-count', itemId],
    queryFn: () => pmWorkItemApi.countComments(itemId!),
    enabled,
  })
  const commentsQuery = useQuery({
    queryKey: ['pm-work-item-comments', itemId],
    queryFn: () => pmWorkItemApi.listComments(itemId!),
    enabled: enabled && tab === 'comments',
  })
  const activitiesQuery = useQuery({
    queryKey: ['pm-work-item-activities', itemId],
    queryFn: () => pmWorkItemApi.listActivities(itemId!),
    enabled,
  })

  useEffect(() => {
    setEditing(false)
    setInvalid(false)
    setInline(null)
    setDescEditing(false)
    setComment('')
    setPendingStatus('')
  }, [itemId])

  const statusOptions = statusQuery.data ?? []
  const definition = statusOptions.find((entry) => entry.statusCode === item?.status)
  const transitions = transitionsQuery.data?.transitions ?? []
  const users = usersQuery.data ?? []
  const modules = modulesQuery.data ?? []
  const fields = fieldsQuery.data ?? []

  const userLabel = useMemo(() => {
    const map = new Map<string, string>()
    for (const user of users) map.set(String(user.id), user.displayName || user.username)
    return (id?: number) => (id == null ? '未分配' : map.get(String(id)) ?? `#${id}`)
  }, [users])

  const moduleLabel = useMemo(() => {
    const map = new Map<string, string>()
    for (const module of modules) map.set(String(module.id), module.pathLabel || module.name)
    return (id?: number | null) => (id == null ? '未归属' : map.get(String(id)) ?? `#${id}`)
  }, [modules])

  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey: ['pm-work-item', itemId] })
    await queryClient.invalidateQueries({ queryKey: ['pm-work-items', projectId, typeCode] })
    await queryClient.invalidateQueries({ queryKey: ['pm-work-item-activities', itemId] })
  }

  const patchFields = async (pairs: Array<[string, unknown]>) => {
    if (!item) return
    let version = Number(item.version ?? 0)
    for (const [field, value] of pairs) {
      const transitionId = field === 'status'
        ? transitions.find((entry) => entry.toStatus === String(value))?.id
        : undefined
      if (field === 'status' && !transitionId) {
        throw new Error('该状态流转不被工作流允许')
      }
      const result = await pmWorkItemApi.updateField(itemId!, { field, value, version, transitionId })
      if (result?.item?.version != null) version = Number(result.item.version)
      else version += 1
    }
  }
  const patchMutation = useMutation({
    mutationFn: (pairs: Array<[string, unknown]>) => patchFields(pairs),
    onSuccess: async (_result, pairs) => {
      if (pairs.some(([field]) => field === 'status')) toast.success('状态已更新')
      await invalidate()
      await queryClient.invalidateQueries({ queryKey: ['pm-allowed-transitions'] })
    },
    onError: (error: Error) => toast.error(error.message || '保存失败'),
  })
  const transition = patchMutation
  const saveComment = useMutation({
    mutationFn: () => pmWorkItemApi.saveComment({ workItemId: itemId!, content: comment.trim() }),
    onSuccess: async () => {
      setComment('')
      toast.success('评论已保存')
      await queryClient.invalidateQueries({ queryKey: ['pm-work-item-comments', itemId] })
      await queryClient.invalidateQueries({ queryKey: ['pm-work-item-comments-count', itemId] })
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

  const startEdit = () => {
    if (!item) return
    setDraft(draftOf(item))
    setInvalid(false)
    setEditing(true)
    // 编辑态只保留详情页签；若从操作记录/评论进入，先切回详情。
    if (tab !== 'detail') onTabChange('detail')
  }

  const submitEdit = async () => {
    if (!item) return
    if (!draft.title.trim() || draft.title.trim().length > 120) {
      setInvalid(true)
      return
    }
    const pairs: Array<[string, unknown]> = []
    const nextTitle = draft.title.trim()
    if (nextTitle !== (item.title ?? '')) pairs.push(['title', nextTitle])
    if ((draft.description ?? '') !== (item.description ?? '')) pairs.push(['description', draft.description ?? ''])
    if ((draft.priority ?? null) !== (item.priority ?? null)) pairs.push(['priority', draft.priority ?? null])
    if ((draft.assigneeId ?? null) !== (item.assigneeId ?? null)) pairs.push(['assignee_id', draft.assigneeId ?? null])
    if ((draft.endTime || '') !== toDateInput(item.endTime)) pairs.push(['end_time', draft.endTime || null])
    if ((draft.moduleId ?? null) !== (item.moduleId ?? null)) pairs.push(['module_id', draft.moduleId ?? null])
    if (pendingStatus && pendingStatus !== item.status) pairs.push(['status', pendingStatus])
    if (!pairs.length) {
      setEditing(false)
      return
    }
    try {
      await patchMutation.mutateAsync(pairs)
      setEditing(false)
      setPendingStatus('')
      toast.success('已保存修改')
    } catch {
      // onError 已提示
    }
  }

  const insertMarkdown = (snippet: string) => {
    setDraft((prev) => ({
      ...prev,
      description: `${prev.description}${prev.description && !prev.description.endsWith('\n') ? '\n' : ''}${snippet}`,
    }))
  }

  const openInline = (field: string) => {
    if (readOnly || !item) return
    setInline(field)
    const value =
      field === 'priority' ? item.priority ?? ''
      : field === 'assignee' ? String(item.assigneeId ?? '')
      : field === 'endTime' ? toDateInput(item.endTime)
      : field === 'module' ? String(item.moduleId ?? '')
      : ''
    setInlineValue(value)
  }

  const applyInline = async () => {
    if (!item || !inline) return
    const pairs: Array<[string, unknown]> =
      inline === 'priority' ? [['priority', inlineValue || null]]
      : inline === 'assignee' ? [['assignee_id', inlineValue ? Number(inlineValue) : null]]
      : inline === 'endTime' ? [['end_time', inlineValue || null]]
      : inline === 'module' ? [['module_id', inlineValue ? Number(inlineValue) : null]]
      : []
    if (!pairs.length) return
    try {
      await patchMutation.mutateAsync(pairs)
      setInline(null)
      toast.success('已更新')
    } catch {
      // onError 已提示
    }
  }

  const customFieldLabel = (key: string) => fields.find((field) => field.fieldKey === key)?.fieldName ?? key

  const customEntries = Object.entries(item?.customFields ?? {}).filter(([, value]) => value !== null && value !== undefined && value !== '')
  // 原型右栏固定展示的扩展字段，先按别名取第一个非空值。
  const customValue = (...keys: string[]) => keys
    .map((key) => item?.customFields?.[key])
    .find((value) => value !== null && value !== undefined && value !== '')
  const displayCustom = (value: unknown) => (value === null || value === undefined || value === '' ? '未设置' : renderValue(value))
  const storyPoints = customValue('storyPoints', 'story_points')
  const risk = customValue('risk')
  const fixVersion = customValue('fixVersion', 'fix_version')
  const labels = customValue('labels', 'label')
  const watchers = customValue('watchers', 'watcher')
  const environment = customValue('environment', 'env')
  const knownCustomKeys = /^(story_?points?|risk|fix_?version|labels?|watchers?|env|environment)$/i
  const grouped = {
    other: customEntries.filter(([key]) => !knownCustomKeys.test(key)),
  }

  const draftDirty = editing && item ? (
    draft.title !== (item.title ?? '')
    || (draft.description ?? '') !== (item.description ?? '')
    || (draft.priority ?? null) !== (item.priority ?? null)
    || (draft.assigneeId ?? null) !== (item.assigneeId ?? null)
    || (draft.endTime ?? '') !== toDateInput(item.endTime)
    || (draft.moduleId ?? null) !== (item.moduleId ?? null)
    || (pendingStatus !== '' && pendingStatus !== (item.status ?? ''))
  ) : false
  const invalidMessages = invalid ? ['标题不能为空，且不超过 120 个字符'] : []

  const transitionMenu = (trigger: ReactNode) => (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>{trigger}</DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        {transitions.length === 0 ? (
          <DropdownMenuItem disabled>当前状态无可用流转</DropdownMenuItem>
        ) : (
          transitions.map((entry) => (
            <DropdownMenuItem key={entry.id} onSelect={() => transition.mutate([['status', entry.toStatus]])}>
              {entry.name || entry.toStatusName}
            </DropdownMenuItem>
          ))
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  )

  // 内联状态：列出全部状态，非法目标置灰并标注原因（原型画板 40）。
  const statusMenu = (trigger: ReactNode) => (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>{trigger}</DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-60">
        {statusOptions.length === 0 ? (
          <DropdownMenuItem disabled>暂无状态定义</DropdownMenuItem>
        ) : (
          statusOptions.map((entry) => {
            const current = entry.statusCode === item?.status
            const allowed = transitions.find((transition) => transition.toStatus === entry.statusCode)
            return (
              <DropdownMenuItem
                key={entry.statusCode}
                disabled={current || !allowed}
                onSelect={() => {
                  if (!allowed) return
                  setInline(null)
                  transition.mutate([['status', entry.statusCode]])
                }}
              >
                <StatusIcon status={entry.statusCode} label={entry.statusName} tone={workflowStatusTone(entry)} />
                <span className="flex-1 truncate">{entry.statusName}</span>
                <span className="text-xs text-muted-foreground">
                  {current ? '当前' : allowed ? '' : '工作流不允许'}
                </span>
              </DropdownMenuItem>
            )
          })
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  )


  const renderValue = (value: unknown) => {
    if (Array.isArray(value)) return value.join('、') || '—'
    if (typeof value === 'object' && value !== null) return JSON.stringify(value)
    return String(value)
  }

  const propRow = (
    label: string,
    field: string | null,
    value: ReactNode,
    control?: ReactNode,
    editControl?: ReactNode,
    /** 选中即保存的内联控件不显示「取消/保存」。 */
    instant = false,
  ) => {
    const editingField = editing && !!editControl
    const mutable = !readOnly && !editing && !!field
    const active = !!field && inline === field
    return (
      <div key={label} className="flex min-h-8 items-center gap-2 border-b py-1 last:border-b-0">
        <span className="w-20 shrink-0 text-xs text-muted-foreground">{label}</span>
        <div className="min-w-0 flex-1 text-sm">
          {editingField && editControl ? editControl : active && control ? control : <span className="flex items-center gap-1 truncate">{value}</span>}
        </div>
        {mutable && !active ? (
          <button
            type="button"
            aria-label={`编辑${label}`}
            className="shrink-0 rounded p-0.5 text-muted-foreground opacity-0 hover:bg-accent group-hover/row:opacity-100 focus-visible:opacity-100"
            onClick={() => openInline(field)}
          >
            <Pencil className="size-3.5" />
          </button>
        ) : null}
        {active && !instant ? (
          <div className="flex shrink-0 items-center gap-1">
            <Button size="sm" variant="ghost" className="h-7 px-2" onClick={() => setInline(null)}>
              取消
            </Button>
            <Button size="sm" className="h-7 px-2" onClick={() => void applyInline()} disabled={patchMutation.isPending}>
              保存
            </Button>
          </div>
        ) : null}
      </div>
    )
  }

  const inlineControl = (): React.ReactNode => {
    if (inline === 'priority') {
      // 选中即保存，不再需要二次确认按钮。
      return (
        <select
          aria-label="优先级"
          className={selectClass}
          value={item?.priority ?? ''}
          onChange={(event) => {
            setInline(null)
            transition.mutate([['priority', event.target.value || null]])
          }}
        >
          <option value="">未设置</option>
          {PRIORITY_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>{option.label}</option>
          ))}
        </select>
      )
    }
    if (inline === 'assignee') {
      return (
        <select aria-label="负责人" className={selectClass} value={inlineValue} onChange={(event) => setInlineValue(event.target.value)}>
          <option value="">未分配</option>
          {users.map((user) => (
            <option key={String(user.id)} value={String(user.id)}>{user.displayName || user.username}</option>
          ))}
        </select>
      )
    }
    if (inline === 'endTime') {
      return <input aria-label="截止时间" type="date" className={selectClass} value={inlineValue} onChange={(event) => setInlineValue(event.target.value)} />
    }
    if (inline === 'module') {
      return (
        <select aria-label="所属模块" className={selectClass} value={inlineValue} onChange={(event) => setInlineValue(event.target.value)}>
          <option value="">未归属</option>
          {modules.map((module) => (
            <option key={String(module.id)} value={String(module.id)}>{module.pathLabel || module.name}</option>
          ))}
        </select>
      )
    }
    return null
  }

  const descriptionBody = editing || descEditing ? (
    <div className="flex flex-col gap-2">
      <div className="flex items-center gap-1 border-b">
        {(['write', 'preview'] as const).map((option) => (
          <button
            key={option}
            type="button"
            className={`-mb-px border-b-2 px-3 py-1.5 text-xs ${descTab === option ? 'border-primary font-medium' : 'border-transparent text-muted-foreground'}`}
            onClick={() => setDescTab(option)}
          >
            {option === 'write' ? '编辑' : '预览'}
          </button>
        ))}
      </div>
      {descTab === 'write' ? (
        <>
          <div className="flex flex-wrap items-center gap-1 text-xs">
            {([
              ['H', '### 标题'],
              ['B', '**加粗**'],
              ['I', '*斜体*'],
              ['</>', '`代码`'],
              ['•', '- 列表项'],
              ['❝', '> 引用'],
              ['↗', '[链接](https://)'],
            ] as const).map(([label, snippet]) => (
              <Button
                key={label}
                type="button"
                size="sm"
                variant="ghost"
                className="h-7 px-2 font-mono"
                onClick={() => insertMarkdown(snippet)}
              >
                {label}
              </Button>
            ))}
            <span className="ml-auto text-muted-foreground">{draft.description.length} 字符</span>
          </div>
          <Textarea
          aria-label="描述"
          className="min-h-48"
          value={draft.description}
          onChange={(event) => setDraft((prev) => ({ ...prev, description: event.target.value }))}
          placeholder="支持 Markdown：标题、列表、代码块、链接"
          />
        </>
      ) : (
        <div className="prose-sm min-h-24 text-sm" dangerouslySetInnerHTML={{ __html: renderMarkdown(draft.description) }} />
      )}
      {descEditing && !editing ? (
        <div className="flex justify-end gap-2">
          <Button size="sm" variant="outline" onClick={() => { setDescEditing(false); setDraft(draftOf(item!)) }}>取消</Button>
          <Button
            size="sm"
            disabled={patchMutation.isPending}
            onClick={async () => {
              try {
                await patchMutation.mutateAsync([['description', draft.description ?? '']])
                setDescEditing(false)
                toast.success('描述已更新')
              } catch {
                // onError 已提示
              }
            }}
          >
            保存描述
          </Button>
        </div>
      ) : null}
    </div>
  ) : (
    <div className="text-sm text-muted-foreground">
      {item?.description?.trim() ? (
        <div className="space-y-1" dangerouslySetInnerHTML={{ __html: renderMarkdown(item.description) }} />
      ) : (
        <p>还没有描述</p>
      )}
    </div>
  )

  // 用函数延迟到所有面板定义之后再求值：页签切换时仍保留标题/页签/右栏。
  const renderDetailPane = () => (item ? (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-start gap-3">
        {editing ? (
          <div className="min-w-0 flex-1">
            <input
              aria-label="标题"
              className={`w-full rounded-md border bg-background px-2 py-1 text-3xl font-bold tracking-tight outline-none focus-visible:ring-2 focus-visible:ring-ring ${invalid ? 'border-destructive' : ''}`}
              value={draft.title}
              onChange={(event) => setDraft((prev) => ({ ...prev, title: event.target.value }))}
            />
            {invalid ? <p className="mt-1 text-xs text-destructive">标题不能为空，且不超过 120 个字符</p> : null}
          </div>
        ) : (
          <h1 className="min-w-0 flex-1 text-3xl font-bold leading-tight tracking-tight">{item.title}</h1>
        )}
        {readOnly ? <span className="rounded border px-2 py-0.5 text-xs text-muted-foreground">只读</span> : null}
        {!readOnly && !editing ? (
          <div className="flex shrink-0 items-center gap-2">
            {transitionMenu(
              <Button variant="outline" size="sm" disabled={!transitions.length}>状态流转</Button>,
            )}
            <Button size="sm" onClick={startEdit}>完整编辑</Button>
            <ConfirmDialog
              title={`删除事项「${item.title || itemId}」？`}
              description="删除后不可恢复。"
              confirmText="删除"
              destructive
              confirmDisabled={remove.isPending}
              onConfirm={() => remove.mutate()}
            >
              <Button variant="ghost" size="icon" aria-label="删除事项" className="text-destructive">
                <Trash2 />
              </Button>
            </ConfirmDialog>
          </div>
        ) : null}
      </div>

      <div className="flex flex-wrap items-center gap-2 text-sm text-muted-foreground">
        <StatusIcon
          status={item.status}
          label={definition?.statusName || item.status || '未设置'}
          tone={workflowStatusTone(definition)}
        />
        <span>优先级 {priorityLabel(item.priority)}</span>
        {risk != null ? <span>风险 {String(risk)}</span> : null}
        <span>·</span>
        <span>更新于 {formatDateTime(item.updateTime)}</span>
        {item.sprintId != null ? (
          <>
            <span>·</span>
            <span>迭代 #{item.sprintId}</span>
          </>
        ) : null}
        <span>·</span>
        <span>{typeLabel(item.typeCode)}</span>
      </div>

      <div role="tablist" aria-label="事项详情页签" className="flex gap-1 border-b">
        {DETAIL_TABS.map((option) => {
          const active = option.value === tab
          const count = option.value === 'comments'
            ? commentsCountQuery.data
            : option.value === 'activity'
              ? activitiesQuery.data?.length
              : undefined
          return (
            <button
              key={option.value}
              type="button"
              role="tab"
              aria-selected={active}
              className={`-mb-px border-b-2 px-3 py-2 text-sm transition-colors ${
                active ? 'border-primary font-medium text-foreground' : 'border-transparent text-muted-foreground hover:text-foreground'
              }`}
              disabled={editing && option.value !== 'detail'}
              onClick={() => onTabChange(option.value)}
            >
              {option.label}
              {count != null ? <span className="ml-1 rounded-full bg-muted px-1.5 text-xs">{count}</span> : null}
            </button>
          )
        })}
        {editing ? <span className="self-center px-2 text-xs text-muted-foreground">编辑中 · 保存后可查看操作记录与评论</span> : null}
      </div>

      {editing && invalid ? (
        <div role="alert" className="rounded-md border border-destructive/40 bg-destructive/5 px-3 py-2 text-sm text-destructive">
          有 {invalidMessages.length} 个字段需要修正
          <ul className="mt-1 list-inside list-disc text-xs">
            {invalidMessages.map((message) => <li key={message}>{message}</li>)}
          </ul>
        </div>
      ) : null}

      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_320px]">
        <section className="self-start rounded-lg border">
          <header className="flex items-center justify-between border-b px-3 py-2 text-xs text-muted-foreground">
            <span className="uppercase tracking-wide">
              {tab === 'detail' ? '描述' : tab === 'activity' ? '操作记录' : '评论'}
            </span>
            {tab === 'detail' && !readOnly && !editing && !descEditing ? (
              <Button size="sm" variant="ghost" className="h-7" onClick={() => { setDraft(draftOf(item)); setDescEditing(true); setDescTab('write') }}>
                编辑描述
              </Button>
            ) : null}
            {tab === 'activity' && (activitiesQuery.data?.length ?? 0) > 0 ? (
              <span>共 {activitiesQuery.data?.length} 条 · 最新在前</span>
            ) : null}
          </header>
          <div className="p-3">
            {tab === 'detail' ? descriptionBody : tab === 'activity' ? activityPane : commentsPane}
          </div>
        </section>

        <aside className="flex flex-col gap-3">
          <section className="rounded-lg border">
            <header className="flex items-center justify-between border-b px-3 py-2 text-xs text-muted-foreground">
              <span className="uppercase tracking-wide">属性</span>
              <span>{readOnly ? '只读' : '点击字段直接更新'}</span>
            </header>
            <div className="group/row px-3 py-1">
              <div className="pb-1 pt-2 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">基本信息</div>
              {propRow(
                '状态', null,
                readOnly || editing ? (
                  <span className="flex items-center gap-1">
                    <StatusIcon status={item.status} label={definition?.statusName || item.status || '未设置'} tone={workflowStatusTone(definition)} />
                  </span>
                ) : statusMenu(
                  <button type="button" aria-label="状态" className="flex items-center gap-1 hover:text-foreground">
                    <StatusIcon status={item.status} label={definition?.statusName || item.status || '未设置'} tone={workflowStatusTone(definition)} />
                  </button>,
                ),
                undefined,
                <select
                  aria-label="状态"
                  className={selectClass}
                  value={pendingStatus || item.status || ''}
                  onChange={(event) => setPendingStatus(event.target.value)}
                >
                  <option value={item.status ?? ''}>{definition?.statusName || item.status || '未设置'}</option>
                  {transitions.map((entry) => (
                    <option key={entry.id} value={entry.toStatus}>{entry.toStatusName || entry.name}</option>
                  ))}
                </select>,
              )}
              {propRow(
                '优先级', 'priority', priorityLabel(item.priority), inlineControl(),
                <select
                  aria-label="优先级"
                  className={selectClass}
                  value={draft.priority ?? ''}
                  onChange={(event) => setDraft((prev) => ({ ...prev, priority: event.target.value || undefined }))}
                >
                  <option value="">未设置</option>
                  {PRIORITY_OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>{option.label}</option>
                  ))}
                </select>,
                true,
              )}
              {propRow('类型', null, typeLabel(item.typeCode))}
              {propRow(
                '负责人', 'assignee', userLabel(item.assigneeId), inlineControl(),
                <select
                  aria-label="负责人"
                  className={selectClass}
                  value={draft.assigneeId != null ? String(draft.assigneeId) : ''}
                  onChange={(event) => setDraft((prev) => ({ ...prev, assigneeId: event.target.value ? Number(event.target.value) : undefined }))}
                >
                  <option value="">未分配</option>
                  {users.map((user) => (
                    <option key={String(user.id)} value={String(user.id)}>{user.displayName || user.username}</option>
                  ))}
                </select>,
              )}
              {propRow('报告人', null, userLabel(item.reporterId))}

              <div className="pb-1 pt-3 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">计划</div>
              {propRow(
                '截止时间', 'endTime', item.endTime ? formatDateTime(item.endTime) : '未设置', inlineControl(),
                <input
                  aria-label="截止时间"
                  type="date"
                  className={selectClass}
                  value={draft.endTime ?? ''}
                  onChange={(event) => setDraft((prev) => ({ ...prev, endTime: event.target.value }))}
                />,
              )}
              {propRow('所属迭代', null, item.sprintId != null ? `#${item.sprintId}` : '未设置')}
              {propRow('故事点', null, displayCustom(storyPoints))}
              {propRow('风险等级', null, displayCustom(risk))}
              {propRow('修复版本', null, displayCustom(fixVersion))}

              <div className="pb-1 pt-3 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">归属与协作</div>
              {propRow(
                '所属模块', 'module', moduleLabel(item.moduleId), inlineControl(),
                <select
                  aria-label="所属模块"
                  className={selectClass}
                  value={draft.moduleId != null ? String(draft.moduleId) : ''}
                  onChange={(event) => setDraft((prev) => ({ ...prev, moduleId: event.target.value ? Number(event.target.value) : null }))}
                >
                  <option value="">未归属</option>
                  {modules.map((module) => (
                    <option key={String(module.id)} value={String(module.id)}>{module.pathLabel || module.name}</option>
                  ))}
                </select>,
              )}
              {propRow('父事项', null, item.parentId != null ? `#${item.parentId}` : '无父事项')}
              {propRow('标签', null, displayCustom(labels))}
              {propRow('关注者', null, displayCustom(watchers))}

              <div className="pb-1 pt-3 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">环境</div>
              {propRow('环境', null, displayCustom(environment))}

              {grouped.other.length ? (
                <>
                  <div className="pb-1 pt-3 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">其他字段</div>
                  {grouped.other.map(([key, value]) => propRow(customFieldLabel(key), null, renderValue(value)))}
                </>
              ) : null}

              <div className="pb-1 pt-3 text-[11px] font-semibold uppercase tracking-wide text-muted-foreground">人员与时间</div>
              {propRow('创建人', null, item.createBy != null ? userLabel(item.createBy) : '—')}
              {propRow('创建时间', null, formatDateTime(item.createTime))}
              {propRow('更新时间', null, formatDateTime(item.updateTime))}
            </div>
          </section>
        </aside>
      </div>

      {editing ? (
        <div className="sticky bottom-0 flex items-center gap-3 rounded-lg border bg-card px-3 py-2 shadow-lg">
          <span className="text-xs text-muted-foreground">未保存的更改 · <b className="text-foreground">标题、描述与字段</b></span>
          <span className="flex-1" />
          {draftDirty ? (
            <ConfirmDialog
              title="放弃未保存的修改？"
              description="关闭后本次对标题、描述与字段的修改不会保存。"
              confirmText="放弃修改"
              cancelText="继续编辑"
              destructive
              onConfirm={() => { setEditing(false); setInvalid(false); setPendingStatus('') }}
            >
              <Button variant="outline" size="sm">取消</Button>
            </ConfirmDialog>
          ) : (
            <Button variant="outline" size="sm" onClick={() => { setEditing(false); setInvalid(false); setPendingStatus('') }}>
              取消
            </Button>
          )}
          <Button size="sm" onClick={() => void submitEdit()} disabled={patchMutation.isPending}>保存修改</Button>
        </div>
      ) : null}
    </div>
  ) : null)

  const activityPane = (
    <div className="flex flex-col gap-2">
      {activitiesQuery.isLoading && <p className="text-sm text-muted-foreground">加载操作记录…</p>}
      {activitiesQuery.isError && <p className="text-sm text-destructive">操作记录加载失败</p>}
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
            <span className="text-xs text-muted-foreground">{formatDateTime(entry.createTime)}</span>
          </li>
        ))}
        {activitiesQuery.isSuccess && (activitiesQuery.data ?? []).length === 0 && (
          <li className="text-sm text-muted-foreground">还没有操作记录</li>
        )}
      </ul>
    </div>
  )

  const inaccessible = isApiError(itemQuery.error) && [
    ResultCode.NOT_FOUND,
    ResultCode.FORBIDDEN,
    ResultCode.WORK_ITEM_NOT_FOUND,
    403,
    404,
  ].includes(itemQuery.error.code)

  const commentsPane = (
    <div className="flex flex-col gap-4">
      {readOnly ? null : (
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
            <Button type="submit" size="sm" disabled={!comment.trim() || saveComment.isPending}>发表</Button>
          </div>
        </form>
      )}
      {commentsQuery.isLoading && <p className="text-sm text-muted-foreground">加载评论…</p>}
      {commentsQuery.isError && <p className="text-sm text-destructive">评论加载失败</p>}
      <ul className="flex flex-col gap-3">
        {(commentsQuery.data ?? []).map((entry) => (
          <li key={asId(entry.id)} className="rounded-lg border px-3 py-2">
            <div className="flex flex-wrap items-baseline justify-between gap-2 text-xs text-muted-foreground">
              <span>{entry.authorName || '未知'}</span>
              <span>{formatDateTime(entry.createTime)}</span>
            </div>
            <div className="mt-1 whitespace-pre-wrap text-sm" dangerouslySetInnerHTML={{ __html: renderMarkdown(entry.content) }} />
          </li>
        ))}
        {commentsQuery.isSuccess && (commentsQuery.data ?? []).length === 0 && (
          <li className="text-sm text-muted-foreground">还没有评论</li>
        )}
      </ul>
    </div>
  )

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
              <span>{typeLabel(item.typeCode)}</span>
            </>
          ) : null}
          {readOnly ? <span className="rounded border px-1.5 py-0.5">只读</span> : null}
          <span className="flex-1" />
          {item ? (
            <Button
              variant="ghost"
              size="sm"
              onClick={() => {
                void navigator.clipboard?.writeText(window.location.href)
                toast.success('事项链接已复制')
              }}
            >
              <Copy className="size-3.5" />
              复制链接
            </Button>
          ) : null}
        </div>

        <div className="flex-1 overflow-y-auto p-4">
          {!itemId ? null
            : itemQuery.isPending ? <p className="text-sm text-muted-foreground">加载中…</p>
            : itemQuery.isError ? (
              <p className="text-sm text-muted-foreground">
                {inaccessible ? '事项不存在或无权访问' : '事项加载失败'}
              </p>
            )
            : !item ? <p className="text-sm text-muted-foreground">事项不存在或无权访问</p>
            : renderDetailPane()}
        </div>
      </DialogContent>
    </Dialog>
  )
}
