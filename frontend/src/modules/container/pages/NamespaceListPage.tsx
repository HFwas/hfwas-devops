import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useParams } from 'react-router'
import { DataTable } from '@/components/console/DataTable'
import { PageHeader } from '@/components/console/PageHeader'
import { Pagination } from '@/components/console/Pagination'
import { Input } from '@/components/ui/input'
import { namespaceApi } from '@/modules/container/api/namespace'
import { StatusBadge } from '@/modules/container/components/StatusBadge'
import { DEFAULT_PAGE_SIZE, pageCountOf } from '@/shared/types/pagination'

export function NamespaceListPage() {
  const { clusterId = '' } = useParams()
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(1)
  useEffect(() => {
    setPage(1)
  }, [keyword, clusterId])
  const query = useQuery({
    queryKey: ['container-namespaces', clusterId, keyword, page],
    queryFn: () => namespaceApi.page(clusterId, { pageNo: page, pageSize: DEFAULT_PAGE_SIZE, keyword: keyword || undefined }),
    enabled: !!clusterId,
  })
  const rows = query.data?.records ?? []
  const total = Number(query.data?.total ?? rows.length)
  const pageCount = pageCountOf(query.data, DEFAULT_PAGE_SIZE)

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title="命名空间"
        description={query.isSuccess ? `共 ${total} 个 Namespace` : '当前集群中的 Namespace'}
        actions={
          <Input
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="搜索名称"
            aria-label="搜索命名空间"
            className="h-8 w-56"
          />
        }
      />
      <DataTable
        columns={[
          { id: 'name', header: '名称', cell: (row) => row.name },
          {
            id: 'status',
            header: '状态',
            cell: (row) => <StatusBadge status={row.status || row.phase} />,
          },
          { id: 'created', header: '创建时间', cell: (row) => row.creationTimestamp || '—' },
        ]}
        data={rows}
        getRowId={(row) => row.uid || row.name}
        loading={query.isLoading}
        error={query.isError ? '命名空间加载失败' : undefined}
        empty="还没有命名空间"
      />
      <Pagination page={Math.min(page, pageCount)} pageCount={pageCount} onPageChange={setPage} />
    </div>
  )
}