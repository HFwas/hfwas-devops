import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Upload } from 'lucide-react'
import { Link } from 'react-router'
import { DataTable } from '@/components/console/DataTable'
import { PageHeader } from '@/components/console/PageHeader'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { helmApi } from '@/modules/container/api/helm'
import { HelmMockNotice } from '@/modules/container/components/HelmMockNotice'
import { helmChartPath } from '@/modules/container/helm/paths'
import { formatHelmTime } from '@/modules/container/helm/yaml'
import { errorMessage } from '@/shared/errors/apiError'

export function HelmChartListPage() {
  const [keyword, setKeyword] = useState('')
  const query = useQuery({
    queryKey: ['helm-charts', keyword],
    queryFn: () => helmApi.listCharts({ name: keyword.trim() || undefined }),
  })

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title="Chart 目录"
        description="已推送到 Chart 仓库的 Helm 包。安装时引用仓库中的 chartRef。"
        actions={
          <Button asChild>
            <Link to="/container/helm/upload">
              <Upload />
              上传 Chart
            </Link>
          </Button>
        }
      />
      <HelmMockNotice />
      <DataTable
        loading={query.isLoading}
        error={query.isError ? errorMessage(query.error, 'Chart 列表加载失败') : undefined}
        empty="还没有 Chart。可以上传 .tgz。"
        data={query.data ?? []}
        getRowId={(row) => `${row.repositoryId}/${row.chartName}`}
        toolbar={
          <Input
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="搜索名称、说明或仓库"
            aria-label="搜索 Chart"
            className="max-w-xs"
          />
        }
        columns={[
          {
            id: 'name',
            header: 'Chart',
            cell: (row) => (
              <Link className="font-medium text-primary hover:underline" to={helmChartPath(row.repositoryId, row.chartName)}>
                {row.chartName}
              </Link>
            ),
          },
          { id: 'repo', header: '仓库', cell: (row) => row.repositoryName },
          { id: 'version', header: '最新版本', cell: (row) => <span className="font-mono text-xs">{row.latestVersion}</span> },
          { id: 'app', header: 'App 版本', cell: (row) => row.appVersion || '—' },
          { id: 'count', header: '版本数', cell: (row) => row.versionCount },
          { id: 'desc', header: '说明', cell: (row) => row.description || '—' },
          { id: 'updated', header: '更新时间', cell: (row) => formatHelmTime(row.updatedAt) },
        ]}
      />
    </div>
  )
}
