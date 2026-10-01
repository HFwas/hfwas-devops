import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { Input } from '@/components/ui/input'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { imageApi } from '@/modules/container/api/image'
import { repoPath } from '@/modules/container/pages/RegistryDrillPages'

export function ImageSearchPage() {
  const [keyword, setKeyword] = useState('')
  const query = useQuery({
    queryKey: ['container-images', keyword],
    queryFn: () => imageApi.searchImages(keyword || undefined, 1, 50),
  })
  const records = query.data?.records ?? []

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold">镜像</h1>
          <p className="text-sm text-muted-foreground">在已接入的仓库中搜索镜像。</p>
        </div>
        <Input
          value={keyword}
          onChange={(event) => setKeyword(event.target.value)}
          placeholder="搜索镜像名"
          className="max-w-xs"
        />
      </div>
      {query.isLoading && <p className="text-sm text-muted-foreground">加载中…</p>}
      {query.isError && <p className="text-sm text-destructive">镜像搜索失败</p>}
      {query.isSuccess && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>仓库</TableHead>
              <TableHead>项目</TableHead>
              <TableHead>镜像</TableHead>
              <TableHead>制品数</TableHead>
              <TableHead>拉取次数</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {records.map((item) => (
              <TableRow key={`${item.registryId}/${item.projectName}/${item.repoName}`}>
                <TableCell>{item.registryName}</TableCell>
                <TableCell>{item.projectName}</TableCell>
                <TableCell className="font-medium">
                  <Link className="text-primary hover:underline" to={repoPath(item.registryId, item.projectName, item.repoName)}>
                    {item.repoName}
                  </Link>
                </TableCell>
                <TableCell>{item.artifactCount}</TableCell>
                <TableCell>{item.pullCount}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </div>
  )
}
