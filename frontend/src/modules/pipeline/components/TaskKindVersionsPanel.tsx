import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { ConfirmDialog } from '@/components/console/ConfirmDialog'
import { DataTable } from '@/components/console/DataTable'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { pipelineTaskKindApi } from '@/modules/pipeline/api/pipeline'
import { formatDateTime } from '@/modules/pipeline/status'
import type { TaskKindVersion } from '@/modules/pipeline/types/pipeline'

const SEMVER = /^\d+\.\d+\.\d+$/

export function TaskKindVersionsPanel({ kind, isAdmin }: { kind: string; isAdmin: boolean }) {
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(false)
  const [note, setNote] = useState('')
  const [version, setVersion] = useState('')
  const query = useQuery({
    queryKey: ['pipeline-task-kind-versions', kind],
    queryFn: () => pipelineTaskKindApi.versions(kind),
    enabled: !!kind,
  })

  async function refresh() {
    await queryClient.invalidateQueries({ queryKey: ['pipeline-task-kind-versions', kind] })
    await queryClient.invalidateQueries({ queryKey: ['pipeline-task-kind', kind] })
    await queryClient.invalidateQueries({ queryKey: ['pipeline-task-kinds'] })
  }

  const publish = useMutation({
    mutationFn: () =>
      pipelineTaskKindApi.publish(kind, {
        note: note.trim() || undefined,
        version: version.trim() || undefined,
      }),
    onSuccess: async (created) => {
      toast.success(`已发布 ${created.version}`)
      setOpen(false)
      setNote('')
      setVersion('')
      await refresh()
    },
    onError: (error: Error) => toast.error(error.message || '发布失败'),
  })

  const activate = useMutation({
    mutationFn: (target: string) => pipelineTaskKindApi.activate(kind, target),
    onSuccess: async () => {
      toast.success('已切换当前版本')
      await refresh()
    },
    onError: (error: Error) => toast.error(error.message || '切换失败'),
  })

  function requestPublish() {
    const requested = version.trim()
    if (requested && !SEMVER.test(requested)) {
      toast.error('版本号须为 semver，例如 1.2.0')
      return
    }
    publish.mutate()
  }

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="text-sm text-muted-foreground">发布会把当前配置打成快照。保存本身不会升版本。</p>
        {isAdmin ? (
          <Button size="sm" onClick={() => setOpen(true)}>
            基于当前配置发布版本
          </Button>
        ) : null}
      </div>
      <DataTable<TaskKindVersion>
        columns={[
          {
            id: 'version',
            header: '版本',
            className: 'font-mono text-xs',
            cell: (row) => (
              <span className="inline-flex items-center gap-2">
                {row.version}
                {row.isCurrent ? <span className="rounded-md bg-primary/15 px-1.5 py-0.5 text-primary">当前</span> : null}
              </span>
            ),
          },
          { id: 'time', header: '发布时间', cell: (row) => formatDateTime(row.createdAt) },
          { id: 'note', header: '备注', cell: (row) => row.note?.trim() || '—' },
          {
            id: 'actions',
            header: '',
            className: 'text-right',
            cell: (row) =>
              isAdmin && !row.isCurrent ? (
                <ConfirmDialog
                  title={`切换到版本 ${row.version}？`}
                  description="未发布的修改会被该版本覆盖。"
                  confirmText="切换"
                  confirmDisabled={activate.isPending}
                  onConfirm={() => activate.mutate(row.version)}
                >
                  <Button variant="ghost" size="sm" disabled={activate.isPending}>
                    设为当前
                  </Button>
                </ConfirmDialog>
              ) : null,
          },
        ]}
        data={query.data ?? []}
        getRowId={(row) => row.version}
        loading={query.isFetching}
        error={query.isError ? '版本加载失败' : undefined}
        empty="还没有发布版本"
      />
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>发布版本</DialogTitle>
          </DialogHeader>
          <div className="mt-4 flex flex-col gap-3">
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="task-kind-version">版本号</Label>
              <Input
                id="task-kind-version"
                className="h-8"
                placeholder="留空则自动递增，首次为 1.0.0"
                value={version}
                onChange={(event) => setVersion(event.target.value)}
              />
            </div>
            <div className="flex flex-col gap-1.5">
              <Label htmlFor="task-kind-version-note">备注</Label>
              <Textarea
                id="task-kind-version-note"
                className="min-h-20"
                value={note}
                onChange={(event) => setNote(event.target.value)}
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" size="sm" onClick={() => setOpen(false)}>
              取消
            </Button>
            <Button size="sm" disabled={publish.isPending} onClick={requestPublish}>
              发布
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
