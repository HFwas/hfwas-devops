import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { PageHeader } from '@/components/console/PageHeader'
import { Input } from '@/components/ui/input'
import { TaskKindCard } from '@/modules/pipeline/components/TaskKindCard'
import { pipelineTaskKindApi } from '@/modules/pipeline/api/pipeline'
import { filterTaskKinds, taskKindGroups } from '@/modules/pipeline/taskKindView'
import { useAuthStore } from '@/stores/auth'
import { cn } from '@/lib/utils'

export function TaskKindListPage() {
  const user = useAuthStore((state) => state.user)
  const isAdmin = useAuthStore((state) => state.isAdmin())
  const queryClient = useQueryClient()
  const [keyword, setKeyword] = useState('')
  const [group, setGroup] = useState('全部')
  const [pendingKind, setPendingKind] = useState<string | null>(null)
  const query = useQuery({
    queryKey: ['pipeline-task-kinds'],
    queryFn: () => pipelineTaskKindApi.list(),
  })
  const toggle = useMutation({
    mutationFn: (kind: string) => pipelineTaskKindApi.toggle(kind),
    onMutate: (kind: string) => setPendingKind(kind),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['pipeline-task-kinds'] })
    },
    onSettled: () => setPendingKind(null),
    onError: (error: Error) => toast.error(error.message || '切换失败'),
  })

  const items = query.data ?? []
  const groups = taskKindGroups(items)
  const rows = filterTaskKinds(items, keyword, group)
  const total = items.length

  useEffect(() => {
    if (group !== '全部' && !groups.includes(group)) setGroup('全部')
  }, [group, groups])

  return (
    <div className="flex w-full flex-col gap-4 md:gap-6">
      <PageHeader
        title="任务市场"
        description={
          user && !isAdmin
            ? '查看任务类型。修改概览和参数需要管理员（账号 role 为 admin）。'
            : query.isSuccess
              ? rows.length === total
                ? `共 ${total} 种任务`
                : `显示 ${rows.length} / ${total}`
              : '流水线可选用的任务类型'
        }
        actions={
          <Input
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="搜索名称、标识或分组"
            aria-label="搜索任务"
            className="h-8 w-56"
          />
        }
      />
      <div role="group" aria-label="任务分组" className="flex gap-1 overflow-x-auto">
        {['全部', ...groups].map((item) => {
          const selected = item === group
          return (
            <button
              key={item}
              type="button"
              aria-pressed={selected}
              className={cn(
                'rounded-md px-3 py-1.5 text-sm whitespace-nowrap',
                selected ? 'bg-primary text-primary-foreground' : 'bg-muted text-muted-foreground hover:text-foreground',
              )}
              onClick={() => setGroup(item)}
            >
              {item}
            </button>
          )
        })}
      </div>
      {query.isLoading ? <p className="text-sm text-muted-foreground">加载中…</p> : null}
      {query.isError ? <p className="text-sm text-destructive">任务市场加载失败</p> : null}
      {query.isSuccess && rows.length === 0 ? <p className="text-sm text-muted-foreground">没有匹配的任务</p> : null}
      {rows.length > 0 ? (
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {rows.map((item) => (
            <TaskKindCard
              key={item.kindValue}
              item={item}
              showToggle={isAdmin}
              togglePending={pendingKind === item.kindValue}
              onToggle={() => toggle.mutate(item.kindValue)}
            />
          ))}
        </div>
      ) : null}
    </div>
  )
}
