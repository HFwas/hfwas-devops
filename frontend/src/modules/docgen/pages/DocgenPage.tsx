import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { DataTable } from '@/components/console/DataTable'
import { PageHeader } from '@/components/console/PageHeader'
import { StatusIcon } from '@/components/console/StatusIcon'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { generateToDir } from '@/modules/docgen/api/docgen'
import { DEFAULT_DATA_TEMPLATES, FORMAT_OPTIONS, type DocgenFormat } from '@/modules/docgen/types/docgen'

interface GeneratedFile {
  id: string
  filename: string
  message: string
}

export function DocgenPage() {
  const [format, setFormat] = useState<DocgenFormat>('word')
  const [filename, setFilename] = useState('示例文档')
  const [directory, setDirectory] = useState('')
  const [files, setFiles] = useState<GeneratedFile[]>([])
  const generate = useMutation({
    mutationFn: () =>
      generateToDir({
        format,
        filename: filename.trim() || '示例文档',
        directory: directory.trim(),
        data: DEFAULT_DATA_TEMPLATES[format],
      }),
    onSuccess: (result) => {
      setFiles((current) => [
        { id: `${Date.now()}`, filename: result.filename, message: result.message || result.directory },
        ...current,
      ])
    },
  })

  return (
    <div className="flex w-full flex-col gap-4 md:gap-6">
      <PageHeader title="文档生成" description="按模板写到目录。批量规格和加密选项留到后续。" />
      <form
        className="flex flex-wrap items-end gap-2"
        onSubmit={(event) => {
          event.preventDefault()
          if (directory.trim()) generate.mutate()
        }}
      >
        <div className="flex flex-col gap-1">
          <Label htmlFor="doc-format" className="text-xs">
            格式
          </Label>
          <select
            id="doc-format"
            aria-label="文档格式"
            className="h-8 rounded-md border border-input bg-background px-2 text-sm"
            value={format}
            onChange={(event) => setFormat(event.target.value as DocgenFormat)}
          >
            {FORMAT_OPTIONS.map((item) => (
              <option key={item.value} value={item.value}>
                {item.label}
              </option>
            ))}
          </select>
        </div>
        <div className="flex flex-col gap-1">
          <Label htmlFor="doc-name" className="text-xs">
            文件名
          </Label>
          <Input id="doc-name" value={filename} onChange={(event) => setFilename(event.target.value)} className="h-8 w-48" />
        </div>
        <div className="flex min-w-64 flex-1 flex-col gap-1">
          <Label htmlFor="doc-dir" className="text-xs">
            目录
          </Label>
          <Input
            id="doc-dir"
            value={directory}
            onChange={(event) => setDirectory(event.target.value)}
            placeholder="/tmp/docs"
            className="h-8 font-mono text-xs"
          />
        </div>
        <Button type="submit" size="sm" className="h-8" disabled={!directory.trim() || generate.isPending}>
          生成
        </Button>
        {generate.isSuccess && <StatusIcon status={generate.data.success ? 'success' : 'failed'} label={generate.data.success ? '成功' : '失败'} />}
      </form>
      {generate.isError && <p className="text-sm text-destructive">{(generate.error as Error).message || '生成失败'}</p>}
      <DataTable<GeneratedFile>
        columns={[
          { id: 'name', header: '文件', cell: (row) => <span className="font-medium">{row.filename}</span> },
          { id: 'message', header: '结果', cell: (row) => row.message || '—' },
        ]}
        data={files}
        getRowId={(row) => row.id}
        empty="还没有生成记录"
      />
    </div>
  )
}
