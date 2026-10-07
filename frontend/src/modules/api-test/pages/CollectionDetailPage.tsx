import { useQuery } from '@tanstack/react-query'
import { Link, useParams, useSearchParams } from 'react-router'
import { DataTable } from '@/components/console/DataTable'
import { DetailShell } from '@/components/console/DetailShell'
import { StatusIcon } from '@/components/console/StatusIcon'
import { Button } from '@/components/ui/button'
import { collectionApi } from '@/modules/api-test/collection/api/collection'
import type { CollectionDetailVO, CollectionFolderVO, CollectionItemVO, CollectionRunVO } from '@/modules/api-test/collection/types/collection'

interface FlatItem extends CollectionItemVO {
  folder: string
}

function flattenItems(detail: CollectionDetailVO): FlatItem[] {
  const rows: FlatItem[] = (detail.items ?? []).map((item) => ({ ...item, folder: '根目录' }))
  const walk = (folders: CollectionFolderVO[], prefix: string) => {
    for (const folder of folders ?? []) {
      const name = prefix ? `${prefix} / ${folder.name}` : folder.name
      for (const item of folder.items ?? []) rows.push({ ...item, folder: name })
      walk(folder.children ?? [], name)
    }
  }
  walk(detail.folders ?? [], '')
  return rows
}

function formatTime(value?: string | null): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 19)
}

export function CollectionDetailPage() {
  const { collectionId = '' } = useParams()
  const [searchParams, setSearchParams] = useSearchParams()
  const tab = searchParams.get('tab') || '接口'
  const setTab = (value: string) => {
    const next = new URLSearchParams(searchParams)
    if (value === '接口') next.delete('tab')
    else next.set('tab', value)
    setSearchParams(next, { replace: true })
  }
  const query = useQuery({
    queryKey: ['api-collection', collectionId],
    queryFn: () => collectionApi.detail(collectionId),
    enabled: !!collectionId,
  })
  const runs = useQuery({
    queryKey: ['api-collection-runs', collectionId],
    queryFn: () => collectionApi.runHistory(collectionId, { pageNo: 1, pageSize: 50 }),
    enabled: tab === '运行' && !!collectionId,
  })
  const item = query.data
  const rows = item ? flattenItems(item) : []

  return (
    <DetailShell
      leading={
        <Button variant="ghost" size="sm" asChild>
          <Link to="/api-test/collections">返回</Link>
        </Button>
      }
      title={item?.name || '集合'}
      description={item?.description}
      meta={query.isLoading ? '加载中…' : query.isError ? '加载失败' : item ? `${rows.length} 个接口` : null}
      tabs={[
        { value: '接口', label: '接口' },
        { value: '运行', label: '运行' },
      ]}
      value={tab}
      onValueChange={setTab}
    >
      {tab === '接口' && (
        <DataTable<FlatItem>
          columns={[
            { id: 'method', header: '方法', className: 'font-mono text-xs', cell: (row) => row.method },
            {
              id: 'name',
              header: '名称',
              cell: (row) =>
                row.definitionId ? (
                  <Link className="font-medium text-primary hover:underline" to={`/api-test/definitions/${row.definitionId}`}>
                    {row.name}
                  </Link>
                ) : (
                  <span className="font-medium">{row.name}</span>
                ),
            },
            { id: 'path', header: '路径', className: 'max-w-sm truncate font-mono text-xs', cell: (row) => row.path },
            { id: 'folder', header: '文件夹', cell: (row) => row.folder },
            {
              id: 'enabled',
              header: '状态',
              cell: (row) => (
                <StatusIcon status={row.enabled ? 'enabled' : 'disabled'} label={row.enabled ? '启用' : '停用'} />
              ),
            },
          ]}
          data={rows}
          getRowId={(row) => String(row.id)}
          loading={query.isFetching && !item}
          error={query.isError ? '集合加载失败' : undefined}
          empty="集合里还没有接口"
        />
      )}
      {tab === '运行' && (
        <DataTable<CollectionRunVO>
          columns={[
            { id: 'name', header: '名称', cell: (row) => row.name || `运行 ${row.id}` },
            { id: 'status', header: '状态', cell: (row) => <StatusIcon status={row.status} /> },
            { id: 'passed', header: '通过', cell: (row) => `${row.passedCount}/${row.totalCount}` },
            { id: 'failed', header: '失败', cell: (row) => String(row.failedCount) },
            { id: 'duration', header: '耗时', cell: (row) => `${row.durationMs} ms` },
            { id: 'time', header: '时间', cell: (row) => formatTime(row.createTime) },
          ]}
          data={runs.data?.records ?? []}
          getRowId={(row) => String(row.id)}
          loading={runs.isFetching}
          error={runs.isError ? '运行记录加载失败' : undefined}
          empty="还没有运行记录"
        />
      )}
    </DetailShell>
  )
}
