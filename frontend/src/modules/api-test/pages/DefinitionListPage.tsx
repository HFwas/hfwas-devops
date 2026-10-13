import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { DataTable } from '@/components/console/DataTable'
import { PageHeader } from '@/components/console/PageHeader'
import { Pagination } from '@/components/console/Pagination'
import { StatusIcon } from '@/components/console/StatusIcon'
import { Input } from '@/components/ui/input'
import { apiDefinitionApi } from '@/modules/api-test/define/api/definition'
import { apiStatusLabel } from '@/modules/api-test/define/statusLabel'
import type { ApiDefinitionVO } from '@/modules/api-test/define/types/definition'
import { ProjectSelect } from '@/modules/api-test/pages/ProjectSelect'
import { DEFAULT_PAGE_SIZE, pageCountOf } from '@/shared/types/pagination'

function formatTime(value?: string | null): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 19)
}

export function DefinitionListPage() {
  const [keyword, setKeyword] = useState('')
  const [projectId, setProjectId] = useState('')
  const [page, setPage] = useState(1)
  const query = useQuery({
    queryKey: ['api-definitions', projectId, keyword, page],
    queryFn: () =>
      apiDefinitionApi.page({
        ...(projectId ? { projectId } : {}),
        keyword: keyword.trim() || undefined,
        pageNo: page,
        pageSize: DEFAULT_PAGE_SIZE,
      }),
  })
  const rows = query.data?.records ?? []
  const total = query.data?.total ?? rows.length
  const pageCount = pageCountOf(query.data, DEFAULT_PAGE_SIZE)

  useEffect(() => {
    setPage(1)
  }, [keyword, projectId])

  return (
    <div className="flex w-full flex-col gap-4 md:gap-6">
      <PageHeader
        title="接口"
        description={query.isSuccess ? `共 ${total} 个接口定义` : '查看接口定义、状态与路径'}
        actions={
          <>
            <ProjectSelect value={projectId} onChange={setProjectId} clearLabel="全部项目" />
            <Input
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="搜索名称或路径"
              aria-label="搜索接口"
              className="h-8 w-56"
            />
          </>
        }
      />
      <DataTable<ApiDefinitionVO>
        columns={[
          {
            id: 'method',
            header: '方法',
            cell: (row) => <span className="font-mono text-xs">{row.method}</span>,
          },
          {
            id: 'name',
            header: '名称',
            cell: (row) => (
              <Link className="font-medium text-primary hover:underline" to={`/api-test/definitions/${row.id}`}>
                {row.name}
              </Link>
            ),
          },
          {
            id: 'path',
            header: '路径',
            className: 'max-w-sm truncate font-mono text-xs',
            cell: (row) => row.path,
          },
          {
            id: 'status',
            header: '状态',
            cell: (row) => <StatusIcon status={row.status} label={apiStatusLabel(row.status)} />,
          },
          {
            id: 'group',
            header: '分组',
            cell: (row) => row.groupName || '—',
          },
          {
            id: 'updated',
            header: '更新时间',
            cell: (row) => formatTime(row.updateTime),
          },
        ]}
        data={rows}
        getRowId={(row) => String(row.id)}
        loading={query.isFetching}
        error={query.isError ? '接口列表加载失败' : undefined}
        empty="还没有接口定义"
      />
      <Pagination page={Math.min(page, pageCount)} pageCount={pageCount} onPageChange={setPage} />
    </div>
  )
}
