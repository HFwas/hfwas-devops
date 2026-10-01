import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import type { EnvItem, WorkloadEnv, WorkloadEnvUpdate } from '@/modules/container/types/resource'

function containerKey(name: string, init: boolean) {
  return `${init ? 'init' : 'main'}:${name}`
}

function sourceLabel(item: EnvItem) {
  if (item.sourceType === 'secret') return `Secret ${item.sourceName || ''}/${item.sourceKey || ''}`
  if (item.sourceType === 'configMap') return `ConfigMap ${item.sourceName || ''}/${item.sourceKey || ''}`
  if (item.sourceType === 'field') return `字段 ${item.sourceKey || ''}`
  if (item.sourceType === 'resource') return `资源 ${item.sourceKey || ''}`
  return ''
}

export function WorkloadEnvPanel({
  queryKey,
  load,
  save,
}: {
  queryKey: unknown[]
  load: () => Promise<WorkloadEnv>
  save: (data: WorkloadEnvUpdate) => Promise<void>
}) {
  const queryClient = useQueryClient()
  const query = useQuery({ queryKey, queryFn: load })
  const [rows, setRows] = useState<Record<string, EnvItem[]>>({})
  const [selected, setSelected] = useState('')
  const [dirty, setDirty] = useState(false)

  useEffect(() => {
    if (!query.data || dirty) return
    const next: Record<string, EnvItem[]> = {}
    for (const container of query.data.containers) {
      next[containerKey(container.name, container.init)] = container.env.map((item) => ({ ...item }))
    }
    setRows(next)
    setSelected((current) => {
      if (current && next[current]) return current
      const first = query.data?.containers[0]
      return first ? containerKey(first.name, first.init) : ''
    })
  }, [dirty, query.data])

  const mutation = useMutation({
    mutationFn: () => {
      const containers = (query.data?.containers ?? []).map((container) => ({
        name: container.name,
        init: container.init,
        env: (rows[containerKey(container.name, container.init)] ?? container.env).map((item) => ({
          name: item.name.trim(),
          value: item.sourceType ? item.value : item.value ?? '',
          sourceType: item.sourceType,
          sourceName: item.sourceName,
          sourceKey: item.sourceKey,
        })),
      }))
      if (containers.some((container) => container.env.some((item) => !item.name))) {
        return Promise.reject(new Error('环境变量名不能为空'))
      }
      return save({ containers })
    },
    onSuccess: async () => {
      toast.success('环境变量已保存')
      setDirty(false)
      await queryClient.invalidateQueries({ queryKey })
    },
    onError: (error: Error) => toast.error(error.message || '保存失败'),
  })

  const containers = query.data?.containers ?? []
  const current = containers.find((container) => containerKey(container.name, container.init) === selected)
  const currentRows = rows[selected] ?? []

  function updateRow(index: number, patch: Partial<EnvItem>) {
    setDirty(true)
    setRows((prev) => ({
      ...prev,
      [selected]: (prev[selected] ?? []).map((item, itemIndex) => (itemIndex === index ? { ...item, ...patch } : item)),
    }))
  }

  if (query.isLoading) return <p className="text-sm text-muted-foreground">加载环境变量…</p>
  if (query.isError) return <p className="text-sm text-destructive">环境变量加载失败</p>

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <select
          className="h-9 rounded-md border border-input bg-background px-3 text-sm"
          value={selected}
          onChange={(event) => setSelected(event.target.value)}
        >
          {containers.map((container) => (
            <option key={containerKey(container.name, container.init)} value={containerKey(container.name, container.init)}>
              {container.init ? `init / ${container.name}` : container.name}
            </option>
          ))}
        </select>
        <div className="flex gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              setDirty(true)
              setRows((prev) => ({ ...prev, [selected]: [...(prev[selected] ?? []), { name: '', value: '' }] }))
            }}
          >
            <Plus />
            新增
          </Button>
          <Button size="sm" disabled={mutation.isPending} onClick={() => mutation.mutate()}>
            保存
          </Button>
        </div>
      </div>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>名称</TableHead>
            <TableHead>值</TableHead>
            <TableHead className="w-16" />
          </TableRow>
        </TableHeader>
        <TableBody>
          {currentRows.length === 0 && (
            <TableRow>
              <TableCell colSpan={3} className="py-8 text-center text-muted-foreground">
                还没有直接定义的环境变量
              </TableCell>
            </TableRow>
          )}
          {currentRows.map((item, index) => {
            const sourced = !!item.sourceType
            return (
              <TableRow key={`${item.name}-${index}`}>
                <TableCell>
                  <Input
                    value={item.name}
                    disabled={sourced}
                    onChange={(event) => updateRow(index, { name: event.target.value })}
                  />
                </TableCell>
                <TableCell>
                  {sourced ? (
                    <span className="text-sm text-muted-foreground">{sourceLabel(item)}</span>
                  ) : (
                    <Input
                      value={item.value ?? ''}
                      onChange={(event) => updateRow(index, { value: event.target.value })}
                    />
                  )}
                </TableCell>
                <TableCell>
                  <Button
                    variant="ghost"
                    size="icon"
                    onClick={() => {
                      setDirty(true)
                      setRows((prev) => ({
                        ...prev,
                        [selected]: (prev[selected] ?? []).filter((_, itemIndex) => itemIndex !== index),
                      }))
                    }}
                  >
                    <Trash2 />
                  </Button>
                </TableCell>
              </TableRow>
            )
          })}
        </TableBody>
      </Table>
      {current && current.imported.length > 0 && (
        <section className="flex flex-col gap-2">
          <h2 className="text-sm font-medium">整组引入</h2>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>名称</TableHead>
                <TableHead>值</TableHead>
                <TableHead>来源</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {current.imported.map((item) => (
                <TableRow key={`${item.origin}/${item.name}`}>
                  <TableCell>{item.name}</TableCell>
                  <TableCell>{item.secret ? '已隐藏' : item.value || '—'}</TableCell>
                  <TableCell>{item.origin}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </section>
      )}
    </div>
  )
}
