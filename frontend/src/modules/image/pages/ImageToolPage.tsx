import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { DataTable } from '@/components/console/DataTable'
import { PageHeader } from '@/components/console/PageHeader'
import { StatusIcon } from '@/components/console/StatusIcon'
import { fetchImageHealth, fetchImageHistory } from '@/modules/image/api/image'
import type { ImageHistoryVO } from '@/modules/image/types/image'

function formatBytes(value?: number | null): string {
  if (value == null) return '—'
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / 1024 / 1024).toFixed(1)} MB`
}

export function ImageToolPage() {
  const [limit] = useState(20)
  const health = useQuery({
    queryKey: ['image-health'],
    queryFn: fetchImageHealth,
  })
  const history = useQuery({
    queryKey: ['image-history', limit],
    queryFn: () => fetchImageHistory(limit),
  })
  const rows = history.data ?? []

  return (
    <div className="flex w-full flex-col gap-4 md:gap-6">
      <PageHeader
        title="图片处理"
        description="最近转换记录。裁剪画布留到后续。"
      />
      <div className="grid gap-3 sm:grid-cols-3">
        <Health label="ImageMagick" ok={health.data?.magick} loading={health.isLoading} error={health.isError} />
        <Health label="ExifTool" ok={health.data?.exiftool} loading={health.isLoading} error={health.isError} />
        <Health label="HEIC" ok={health.data?.heicDelegate} loading={health.isLoading} error={health.isError} />
      </div>
      <DataTable<ImageHistoryVO>
        columns={[
          { id: 'file', header: '文件', cell: (row) => <span className="font-medium">{row.fileName}</span> },
          { id: 'target', header: '目标', cell: (row) => row.targetFormat || '—' },
          { id: 'size', header: '结果大小', cell: (row) => formatBytes(row.resultSize) },
          { id: 'px', header: '尺寸', cell: (row) => (row.width && row.height ? `${row.width}×${row.height}` : '—') },
          { id: 'status', header: '状态', cell: (row) => <StatusIcon status={row.status} /> },
          { id: 'time', header: '时间', cell: (row) => row.createTime?.replace('T', ' ').slice(0, 19) || '—' },
        ]}
        data={rows}
        getRowId={(row) => String(row.id)}
        loading={history.isFetching}
        error={history.isError ? '转换记录加载失败' : undefined}
        empty="还没有转换记录"
      />
    </div>
  )
}

function Health({ label, ok, loading, error }: { label: string; ok?: boolean; loading: boolean; error: boolean }) {
  return (
    <div className="flex items-center justify-between rounded-lg border px-3 py-2">
      <span className="text-sm">{label}</span>
      {loading ? (
        <span className="text-xs text-muted-foreground">检查中…</span>
      ) : error ? (
        <StatusIcon status="failed" label="不可用" />
      ) : (
        <StatusIcon status={ok ? 'ready' : 'failed'} label={ok ? '可用' : '缺失'} />
      )}
    </div>
  )
}
