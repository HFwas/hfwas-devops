import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { DataTable } from '@/components/console/DataTable'
import { PageHeader } from '@/components/console/PageHeader'
import { StatusIcon } from '@/components/console/StatusIcon'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { pipelineTaskKindApi } from '@/modules/pipeline/api/pipeline'
import type { TaskKindVO } from '@/modules/pipeline/types/pipeline'

export function TaskKindListPage() {
  const queryClient = useQueryClient()
  const [keyword, setKeyword] = useState('')
  const query = useQuery({
    queryKey: ['pipeline-task-kinds'],
    queryFn: () => pipelineTaskKindApi.list(),
  })
  const toggle = useMutation({
    mutationFn: (kind: string) => pipelineTaskKindApi.toggle(kind),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['pipeline-task-kinds'] })
    },
    onError: (error: Error) => toast.error(error.message || '切换失败'),
  })

  const needle = keyword.trim().toLowerCase()
  const rows = (query.data ?? [])
    .filter((item) => {
      if (!needle) return true
      return (
        item.label.toLowerCase().includes(needle) ||
        item.kindValue.toLowerCase().includes(needle) ||
        item.taskGroup.toLowerCase().includes(needle)
      )
    })
    .slice()
    .sort((a, b) => a.sortOrder - b.sortOrder || a.label.localeCompare(b.label))

  return (
    <div className="flex w-full flex-col gap-4 md:gap-6">
      <PageHeader
        title="任务市场"
        description={query.isSuccess ? `共 ${rows.length} 种任务` : '流水线可选用的任务类型'}
        actions={
          <Input
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="搜索名称或分组"
            aria-label="搜索任务"
            className="h-8 w-56"
          />
        }
      />
      <DataTable<TaskKindVO>
        columns={[
          {
            id: 'label',
            header: '名称',
            cell: (row) => (
              <Link
                className="font-medium text-primary hover:underline"
                to={`/pipeline/task-kinds/${encodeURIComponent(row.kindValue)}`}
              >
                {row.label}
              </Link>
            ),
          },
          { id: 'kind', header: '标识', className: 'font-mono text-xs', cell: (row) => row.kindValue },
          { id: 'group', header: '分组', cell: (row) => row.taskGroup || '—' },
          {
            id: 'enabled',
            header: '状态',
            cell: (row) => (
              <StatusIcon status={row.enabled ? 'enabled' : 'disabled'} label={row.enabled ? '启用' : '停用'} />
            ),
          },
          { id: 'image', header: '镜像', className: 'max-w-sm truncate font-mono text-xs', cell: (row) => row.toolImage || row.defaultImage || '—' },
          {
            id: 'actions',
            header: '',
            className: 'text-right',
            cell: (row) => (
              <Button variant="ghost" size="sm" disabled={toggle.isPending} onClick={() => toggle.mutate(row.kindValue)}>
                {row.enabled ? '停用' : '启用'}
              </Button>
            ),
          },
        ]}
        data={rows}
        getRowId={(row) => row.kindValue}
        loading={query.isFetching}
        error={query.isError ? '任务市场加载失败' : undefined}
        empty="没有匹配的任务"
      />
    </div>
  )
}
