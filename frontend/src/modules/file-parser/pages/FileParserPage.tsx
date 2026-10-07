import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { DataTable } from '@/components/console/DataTable'
import { PageHeader } from '@/components/console/PageHeader'
import { StatusIcon } from '@/components/console/StatusIcon'
import { Button } from '@/components/ui/button'
import { uploadFile } from '@/modules/file-parser/api/fileParser'
import type { PageContent } from '@/modules/file-parser/types/fileParser'

export function FileParserPage() {
  const [file, setFile] = useState<File | null>(null)
  const parse = useMutation({
    mutationFn: (next: File) => uploadFile(next),
  })
  const result = parse.data
  const pages = result?.content?.pages ?? []

  return (
    <div className="flex w-full flex-col gap-4 md:gap-6">
      <PageHeader title="文件解析" description="上传文件后查看文本。历史列表没有独立接口，留在本次结果里。" />
      <div className="flex flex-wrap items-center gap-2">
        <input
          type="file"
          aria-label="选择文件"
          className="h-8 max-w-sm text-sm file:mr-2 file:h-8 file:rounded-md file:border-0 file:bg-muted file:px-2"
          onChange={(event) => setFile(event.target.files?.[0] ?? null)}
        />
        <Button size="sm" className="h-8" disabled={!file || parse.isPending} onClick={() => file && parse.mutate(file)}>
          解析
        </Button>
        {result && <StatusIcon status={result.success ? 'success' : 'failed'} label={result.success ? '成功' : '失败'} />}
        {result?.parseMethod && <span className="text-xs text-muted-foreground">{result.parseMethod}</span>}
      </div>
      {parse.isError && <p className="text-sm text-destructive">{(parse.error as Error).message || '解析失败'}</p>}
      {result?.errorMessage && <p className="text-sm text-destructive">{result.errorMessage}</p>}
      {result?.content?.text && (
        <pre className="max-h-[40vh] overflow-auto rounded-lg border bg-muted/40 p-3 font-mono text-xs leading-5 whitespace-pre-wrap">
          {result.content.text}
        </pre>
      )}
      {pages.length > 0 && (
        <DataTable<PageContent>
          columns={[
            { id: 'page', header: '页', cell: (row) => String(row.pageNum) },
            { id: 'text', header: '文本', className: 'max-w-xl truncate', cell: (row) => row.text || '—' },
          ]}
          data={pages}
          getRowId={(row) => String(row.pageNum)}
          empty="没有分页"
        />
      )}
    </div>
  )
}
