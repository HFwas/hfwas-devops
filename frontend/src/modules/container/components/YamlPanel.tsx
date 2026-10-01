import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'

export function YamlPanel({
  queryKey,
  load,
  save,
}: {
  queryKey: unknown[]
  load: () => Promise<string>
  save?: (yaml: string) => Promise<void>
}) {
  const queryClient = useQueryClient()
  const query = useQuery({ queryKey, queryFn: load })
  const [text, setText] = useState('')

  useEffect(() => {
    if (query.data != null) setText(query.data)
  }, [query.data])

  const mutation = useMutation({
    mutationFn: () => {
      if (!save) return Promise.resolve()
      return save(text)
    },
    onSuccess: async () => {
      toast.success('YAML 已保存')
      await queryClient.invalidateQueries({ queryKey })
    },
    onError: (error: Error) => toast.error(error.message || '保存失败'),
  })

  if (query.isLoading) return <p className="text-sm text-muted-foreground">加载 YAML…</p>
  if (query.isError) return <p className="text-sm text-destructive">YAML 加载失败</p>

  return (
    <div className="flex flex-col gap-3">
      <textarea
        value={text}
        readOnly={!save}
        onChange={(event) => setText(event.target.value)}
        spellCheck={false}
        className="min-h-96 w-full rounded-md border border-input bg-background px-3 py-2 font-mono text-xs"
      />
      {save && (
        <div className="flex justify-end">
          <Button disabled={mutation.isPending} onClick={() => mutation.mutate()}>
            保存
          </Button>
        </div>
      )}
    </div>
  )
}
