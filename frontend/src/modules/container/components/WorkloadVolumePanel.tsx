import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import type { VolumeMountItem, WorkloadVolumeItem, WorkloadVolumeUpdate, WorkloadVolumes } from '@/modules/container/types/resource'

function containerKey(name: string, init: boolean) {
  return `${init ? 'init' : 'main'}:${name}`
}

function parseContainer(value: string) {
  const init = value.startsWith('init:')
  return { init, container: value.slice(5) }
}

function clonePvcVolumes(data: WorkloadVolumes | undefined) {
  return (data?.volumes ?? [])
    .filter((volume) => volume.type === 'pvc')
    .map((volume) => ({ ...volume, mounts: volume.mounts.map((mount) => ({ ...mount })) }))
}

export function WorkloadVolumePanel({
  queryKey,
  load,
  save,
}: {
  queryKey: unknown[]
  load: () => Promise<WorkloadVolumes>
  save: (data: WorkloadVolumeUpdate) => Promise<void>
}) {
  const queryClient = useQueryClient()
  const query = useQuery({ queryKey, queryFn: load })
  const [editing, setEditing] = useState(false)
  const [volumes, setVolumes] = useState<WorkloadVolumeItem[]>([])
  const [pvc, setPvc] = useState('')
  const [target, setTarget] = useState('')
  const [mountPath, setMountPath] = useState('')
  const [subPath, setSubPath] = useState('')
  const [readOnly, setReadOnly] = useState(false)

  useEffect(() => {
    if (!query.data || editing) return
    setVolumes(clonePvcVolumes(query.data))
    const first = query.data.containers[0]
    setTarget(first ? containerKey(first.name, first.init) : '')
    setPvc(query.data.unboundPvcs[0]?.name ?? '')
  }, [editing, query.data])

  const mutation = useMutation({
    mutationFn: () => {
      for (const volume of volumes) {
        if (volume.mounts.some((mount) => !mount.mountPath.startsWith('/'))) {
          return Promise.reject(new Error('挂载路径需要以 / 开头'))
        }
      }
      return save({ volumes })
    },
    onSuccess: async () => {
      toast.success('挂载卷已保存')
      setEditing(false)
      await queryClient.invalidateQueries({ queryKey })
    },
    onError: (error: Error) => toast.error(error.message || '保存失败'),
  })

  const containers = query.data?.containers ?? []
  const unbound = query.data?.unboundPvcs ?? []

  function reset() {
    setVolumes(clonePvcVolumes(query.data))
    setMountPath('')
    setSubPath('')
    setReadOnly(false)
    setEditing(false)
  }

  function patchMount(volumeIndex: number, mountIndex: number, patch: Partial<VolumeMountItem>) {
    setVolumes((prev) =>
      prev.map((volume, index) =>
        index === volumeIndex
          ? { ...volume, mounts: volume.mounts.map((mount, itemIndex) => (itemIndex === mountIndex ? { ...mount, ...patch } : mount)) }
          : volume,
      ),
    )
  }

  function addPvc() {
    if (!pvc) {
      toast.error('没有未绑定的 PVC')
      return
    }
    if (!mountPath.startsWith('/')) {
      toast.error('挂载路径需要以 / 开头')
      return
    }
    if (volumes.some((volume) => volume.name === pvc || volume.source === pvc)) {
      toast.error('该 PVC 已经挂载')
      return
    }
    const parsed = parseContainer(target)
    setVolumes((prev) => [
      ...prev,
      {
        name: pvc,
        type: 'pvc',
        source: pvc,
        readOnly: false,
        mounts: [{ container: parsed.container, init: parsed.init, mountPath, subPath, readOnly }],
      },
    ])
    setMountPath('')
    setSubPath('')
    setReadOnly(false)
  }

  if (query.isLoading) return <p className="text-sm text-muted-foreground">加载挂载卷…</p>
  if (query.isError) return <p className="text-sm text-destructive">挂载卷加载失败</p>

  const rows = volumes.flatMap((volume, volumeIndex) =>
    volume.mounts.map((mount, mountIndex) => ({ volume, volumeIndex, mount, mountIndex })),
  )

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>PVC</TableHead>
          <TableHead>容器</TableHead>
          <TableHead>挂载路径</TableHead>
          <TableHead>子路径</TableHead>
          <TableHead>只读</TableHead>
          <TableHead className="w-36 text-right">
            {editing ? (
              <div className="flex justify-end gap-2">
                <Button variant="outline" size="sm" onClick={reset}>
                  取消
                </Button>
                <Button size="sm" disabled={mutation.isPending} onClick={() => mutation.mutate()}>
                  保存
                </Button>
              </div>
            ) : (
              <Button variant="outline" size="sm" onClick={() => setEditing(true)}>
                编辑
              </Button>
            )}
          </TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {rows.length === 0 && !editing && (
          <TableRow>
            <TableCell colSpan={6} className="py-8 text-center text-muted-foreground">
              还没有 PVC 挂载
            </TableCell>
          </TableRow>
        )}
        {rows.map(({ volume, volumeIndex, mount, mountIndex }) => (
          <TableRow key={`${volume.name}-${mountIndex}`}>
            <TableCell>{volume.source || volume.name}</TableCell>
            <TableCell>
              {editing ? (
                <select
                  className="h-8 rounded-md border border-input bg-background px-2 text-sm"
                  value={containerKey(mount.container, mount.init)}
                  onChange={(event) => patchMount(volumeIndex, mountIndex, parseContainer(event.target.value))}
                >
                  {containers.map((container) => (
                    <option key={containerKey(container.name, container.init)} value={containerKey(container.name, container.init)}>
                      {container.init ? `init / ${container.name}` : container.name}
                    </option>
                  ))}
                </select>
              ) : mount.init ? (
                `init / ${mount.container}`
              ) : (
                mount.container
              )}
            </TableCell>
            <TableCell>
              {editing ? (
                <Input value={mount.mountPath} onChange={(event) => patchMount(volumeIndex, mountIndex, { mountPath: event.target.value })} />
              ) : (
                mount.mountPath
              )}
            </TableCell>
            <TableCell>
              {editing ? (
                <Input value={mount.subPath ?? ''} onChange={(event) => patchMount(volumeIndex, mountIndex, { subPath: event.target.value })} />
              ) : (
                mount.subPath || '—'
              )}
            </TableCell>
            <TableCell>
              {editing ? (
                <input
                  type="checkbox"
                  checked={mount.readOnly}
                  onChange={(event) => patchMount(volumeIndex, mountIndex, { readOnly: event.target.checked })}
                />
              ) : mount.readOnly ? (
                '是'
              ) : (
                '否'
              )}
            </TableCell>
            <TableCell className="text-right">
              {editing && (
                <Button
                  variant="ghost"
                  size="icon"
                  onClick={() =>
                    setVolumes((prev) =>
                      prev
                        .map((item, index) =>
                          index === volumeIndex
                            ? { ...item, mounts: item.mounts.filter((_, itemIndex) => itemIndex !== mountIndex) }
                            : item,
                        )
                        .filter((item) => item.mounts.length > 0),
                    )
                  }
                >
                  <Trash2 />
                </Button>
              )}
            </TableCell>
          </TableRow>
        ))}
        {editing && (
          <TableRow>
            <TableCell>
              <select
                className="h-8 min-w-32 rounded-md border border-input bg-background px-2 text-sm"
                value={pvc}
                onChange={(event) => setPvc(event.target.value)}
              >
                {unbound.length === 0 && <option value="">无未绑定 PVC</option>}
                {unbound.map((item) => (
                  <option key={item.name} value={item.name}>
                    {item.name}
                  </option>
                ))}
              </select>
            </TableCell>
            <TableCell>
              <select
                className="h-8 rounded-md border border-input bg-background px-2 text-sm"
                value={target}
                onChange={(event) => setTarget(event.target.value)}
              >
                {containers.map((container) => (
                  <option key={containerKey(container.name, container.init)} value={containerKey(container.name, container.init)}>
                    {container.init ? `init / ${container.name}` : container.name}
                  </option>
                ))}
              </select>
            </TableCell>
            <TableCell>
              <Input value={mountPath} placeholder="/data" onChange={(event) => setMountPath(event.target.value)} />
            </TableCell>
            <TableCell>
              <Input value={subPath} onChange={(event) => setSubPath(event.target.value)} />
            </TableCell>
            <TableCell>
              <input type="checkbox" checked={readOnly} onChange={(event) => setReadOnly(event.target.checked)} />
            </TableCell>
            <TableCell className="text-right">
              <Button type="button" variant="outline" size="sm" disabled={!pvc} onClick={addPvc}>
                <Plus />
                新增
              </Button>
            </TableCell>
          </TableRow>
        )}
      </TableBody>
    </Table>
  )
}
