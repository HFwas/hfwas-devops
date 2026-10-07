import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { DataTable } from '@/components/console/DataTable'
import { PageHeader } from '@/components/console/PageHeader'
import { Input } from '@/components/ui/input'
import { imageApi } from '@/modules/container/api/image'
import { repoPath } from '@/modules/container/pages/RegistryDrillPages'
import type { ImageSearchVO } from '@/modules/container/types/registry'

export function ImageSearchPage() {
  const [keyword, setKeyword] = useState('')
  const query = useQuery({
    queryKey: ['container-images', keyword],
    queryFn: () => imageApi.searchImages(keyword || undefined, 1, 50),
  })
  const records = query.data?.records ?? []

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title="镜像"
        description={query.isSuccess ? `共 ${query.data?.total ?? records.length} 个镜像` : '在已接入的仓库中搜索镜像。'}
        actions={
          <Input
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="搜索镜像名"
            aria-label="搜索镜像"
            className="h-8 w-56"
          />
        }
      />
      <DataTable<ImageSearchVO>
        columns={[
          { id: 'registry', header: '仓库', cell: (item) => item.registryName },
          { id: 'project', header: '项目', cell: (item) => item.projectName },
          {
            id: 'repo',
            header: '镜像',
            cell: (item) => (
              <Link
                className="font-medium text-primary hover:underline"
                to={repoPath(item.registryId, item.projectName, item.repoName)}
              >
                {item.repoName}
              </Link>
            ),
          },
          { id: 'artifacts', header: '制品数', cell: (item) => String(item.artifactCount) },
          { id: 'pulls', header: '拉取次数', cell: (item) => String(item.pullCount) },
        ]}
        data={records}
        getRowId={(item) => `${item.registryId}/${item.projectName}/${item.repoName}`}
        loading={query.isFetching}
        error={query.isError ? '镜像搜索失败' : undefined}
        empty="没有匹配的镜像"
      />
    </div>
  )
}
