import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { DataTable } from '@/components/console/DataTable'
import { DetailShell } from '@/components/console/DetailShell'
import { Button } from '@/components/ui/button'
import { environmentApi } from '@/modules/api-test/environment/api/environment'
import type { EnvironmentVariableItemVO } from '@/modules/api-test/environment/types/environment'

export function EnvironmentDetailPage() {
  const { environmentId = '' } = useParams()
  const query = useQuery({
    queryKey: ['api-environment', environmentId],
    queryFn: () => environmentApi.detail(environmentId),
    enabled: !!environmentId,
  })
  const item = query.data

  return (
    <DetailShell
      leading={
        <Button variant="ghost" size="sm" asChild>
          <Link to="/api-test/environments">返回</Link>
        </Button>
      }
      title={item?.name || '环境'}
      description={item?.description}
      meta={
        query.isLoading ? '加载中…' : query.isError ? '加载失败' : item ? `${item.variables?.length ?? 0} 个变量` : null
      }
    >
      <DataTable<EnvironmentVariableItemVO>
        columns={[
          { id: 'name', header: '名称', className: 'font-mono text-xs', cell: (row) => row.name },
          {
            id: 'value',
            header: '值',
            className: 'max-w-sm truncate font-mono text-xs',
            cell: (row) => (row.isSecret ? '••••••' : row.value || '—'),
          },
          { id: 'secret', header: '保密', cell: (row) => (row.isSecret ? '是' : '否') },
          { id: 'desc', header: '说明', cell: (row) => row.description || '—' },
        ]}
        data={item?.variables ?? []}
        getRowId={(row) => String(row.id)}
        loading={query.isFetching && !item}
        error={query.isError ? '环境加载失败' : undefined}
        empty="还没有变量"
      />
    </DetailShell>
  )
}
