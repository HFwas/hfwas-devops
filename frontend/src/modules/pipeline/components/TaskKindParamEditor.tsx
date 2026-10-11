import { useState, type ReactNode } from 'react'
import { useMutation } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { pipelineJobParamApi } from '@/modules/pipeline/api/pipeline'
import { normalizeVisibility, paramTypeLabel, visibilityLabel } from '@/modules/pipeline/taskKindView'
import type { JobParamType, TaskKindParam, TaskKindParamVisibility } from '@/modules/pipeline/types/pipeline'

const controlClass =
  'flex h-8 w-full rounded-md border border-input bg-background px-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring'
const textAreaClass =
  'min-h-20 w-full rounded-md border border-input bg-background px-3 py-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring'

interface ParamDraft {
  paramKey: string
  paramLabel: string
  paramType: JobParamType
  defaultValue: string
  required: boolean
  visibility: TaskKindParamVisibility
  optionsText: string
  apiUrl: string
  apiMethod: string
  apiHeadersText: string
  apiResponsePath: string
  placeholder: string
}

interface PreviewState {
  success: boolean
  options: string[]
  errorMessage?: string
}

export function TaskKindParamEditor({
  params,
  saving,
  onSave,
  onCancel,
}: {
  params: TaskKindParam[]
  saving: boolean
  onSave: (params: TaskKindParam[]) => void
  onCancel: () => void
}) {
  const [drafts, setDrafts] = useState<ParamDraft[]>(() => params.map(toDraft))
  const [previews, setPreviews] = useState<Record<number, PreviewState>>({})
  const preview = useMutation({
    mutationFn: pipelineJobParamApi.previewApi,
  })

  function update(index: number, patch: Partial<ParamDraft>) {
    setDrafts((current) => current.map((item, itemIndex) => (itemIndex === index ? { ...item, ...patch } : item)))
  }

  function move(index: number, direction: -1 | 1) {
    const next = index + direction
    if (next < 0 || next >= drafts.length) return
    setDrafts((current) => {
      const copy = current.slice()
      const [item] = copy.splice(index, 1)
      if (!item) return current
      copy.splice(next, 0, item)
      return copy
    })
  }

  async function previewAt(index: number) {
    const draft = drafts[index]
    if (!draft?.apiUrl.trim()) {
      toast.error('请先填写接口地址')
      return
    }
    let apiHeaders: Record<string, string> | undefined
    try {
      apiHeaders = parseHeaders(draft.apiHeadersText, draft.paramKey || `第 ${index + 1} 个参数`)
    } catch (error) {
      toast.error(error instanceof Error ? error.message : '请求头不是合法 JSON')
      return
    }
    const result = await preview.mutateAsync({
      apiUrl: draft.apiUrl.trim(),
      apiMethod: draft.apiMethod || 'GET',
      apiHeaders,
      apiResponsePath: draft.apiResponsePath.trim() || undefined,
    })
    setPreviews((current) => ({ ...current, [index]: result }))
  }

  function submit() {
    try {
      onSave(drafts.map((draft, index) => fromDraft(draft, index)))
    } catch (error) {
      toast.error(error instanceof Error ? error.message : '参数校验失败')
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <p className="text-sm text-muted-foreground">
        「仅管理员」的参数不会出现在流水线绑定和运行弹框，运行时使用这里的默认值。保存不会发布新版本。
      </p>
      {drafts.length === 0 ? <p className="text-sm text-muted-foreground">还没有参数</p> : null}
      {drafts.map((draft, index) => (
        <section key={`${draft.paramKey}-${index}`} className="flex flex-col gap-3 rounded-xl border p-4">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h3 className="text-sm font-medium">
              {draft.paramLabel.trim() || draft.paramKey.trim() || '新参数'}
              <span className="ml-2 font-normal text-muted-foreground">
                {paramTypeLabel(draft.paramType)} · {visibilityLabel(draft.visibility)}
              </span>
            </h3>
            <div className="flex gap-1">
              <Button type="button" variant="ghost" size="sm" disabled={index === 0} onClick={() => move(index, -1)}>
                上移
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                disabled={index === drafts.length - 1}
                onClick={() => move(index, 1)}
              >
                下移
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="text-destructive"
                onClick={() => setDrafts((current) => current.filter((_, itemIndex) => itemIndex !== index))}
              >
                删除
              </Button>
            </div>
          </div>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            <Field label="键" id={`param-key-${index}`}>
              <Input
                id={`param-key-${index}`}
                className="h-8 font-mono text-xs"
                value={draft.paramKey}
                onChange={(event) => update(index, { paramKey: event.target.value })}
              />
            </Field>
            <Field label="名称" id={`param-label-${index}`}>
              <Input
                id={`param-label-${index}`}
                className="h-8"
                value={draft.paramLabel}
                onChange={(event) => update(index, { paramLabel: event.target.value })}
              />
            </Field>
            <Field label="类型" id={`param-type-${index}`}>
              <select
                id={`param-type-${index}`}
                className={controlClass}
                value={draft.paramType}
                onChange={(event) => update(index, { paramType: event.target.value as JobParamType })}
              >
                <option value="input">静态值</option>
                <option value="select">枚举</option>
                <option value="api_select">远程接口</option>
              </select>
            </Field>
            <Field label="默认值" id={`param-default-${index}`}>
              <Input
                id={`param-default-${index}`}
                className="h-8"
                value={draft.defaultValue}
                onChange={(event) => update(index, { defaultValue: event.target.value })}
              />
            </Field>
            <Field label="可见性" id={`param-visibility-${index}`}>
              <select
                id={`param-visibility-${index}`}
                className={controlClass}
                value={draft.visibility}
                onChange={(event) => update(index, { visibility: event.target.value as TaskKindParamVisibility })}
              >
                <option value="user">对用户开放</option>
                <option value="hidden">仅管理员</option>
              </select>
            </Field>
            <Field label="占位提示" id={`param-placeholder-${index}`}>
              <Input
                id={`param-placeholder-${index}`}
                className="h-8"
                value={draft.placeholder}
                onChange={(event) => update(index, { placeholder: event.target.value })}
              />
            </Field>
          </div>
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={draft.required}
              onChange={(event) => update(index, { required: event.target.checked })}
            />
            必填
          </label>
          {draft.paramType === 'select' ? (
            <Field label="枚举选项（每行一个）" id={`param-options-${index}`}>
              <textarea
                id={`param-options-${index}`}
                className={textAreaClass}
                value={draft.optionsText}
                onChange={(event) => update(index, { optionsText: event.target.value })}
              />
            </Field>
          ) : null}
          {draft.paramType === 'api_select' ? (
            <div className="grid gap-3">
              <div className="grid gap-3 sm:grid-cols-[8rem_1fr]">
                <Field label="方法" id={`param-method-${index}`}>
                  <select
                    id={`param-method-${index}`}
                    className={controlClass}
                    value={draft.apiMethod}
                    onChange={(event) => update(index, { apiMethod: event.target.value })}
                  >
                    <option value="GET">GET</option>
                    <option value="POST">POST</option>
                  </select>
                </Field>
                <Field label="接口地址" id={`param-url-${index}`}>
                  <Input
                    id={`param-url-${index}`}
                    className="h-8 font-mono text-xs"
                    value={draft.apiUrl}
                    onChange={(event) => update(index, { apiUrl: event.target.value })}
                  />
                </Field>
              </div>
              <Field label="响应路径" id={`param-path-${index}`}>
                <Input
                  id={`param-path-${index}`}
                  className="h-8 font-mono text-xs"
                  placeholder="$.data[].name"
                  value={draft.apiResponsePath}
                  onChange={(event) => update(index, { apiResponsePath: event.target.value })}
                />
              </Field>
              <Field label="请求头 JSON" id={`param-headers-${index}`}>
                <textarea
                  id={`param-headers-${index}`}
                  className={`${textAreaClass} font-mono text-xs`}
                  placeholder='{"Authorization":"Bearer token"}'
                  value={draft.apiHeadersText}
                  onChange={(event) => update(index, { apiHeadersText: event.target.value })}
                />
              </Field>
              <div className="flex flex-col gap-2">
                <Button type="button" variant="outline" size="sm" className="w-fit" disabled={preview.isPending} onClick={() => void previewAt(index)}>
                  预览选项
                </Button>
                {previews[index] ? (
                  <p className="text-sm text-muted-foreground">
                    {previews[index].success
                      ? `选项：${previews[index].options.join('、') || '（空）'}`
                      : previews[index].errorMessage || '预览失败'}
                  </p>
                ) : null}
              </div>
            </div>
          ) : null}
        </section>
      ))}
      <div className="flex flex-wrap gap-2">
        <Button type="button" variant="outline" size="sm" onClick={() => setDrafts((current) => [...current, emptyDraft()])}>
          添加参数
        </Button>
        <Button type="button" size="sm" disabled={saving} onClick={submit}>
          保存
        </Button>
        <Button type="button" variant="outline" size="sm" onClick={onCancel}>
          取消
        </Button>
      </div>
    </div>
  )
}

function Field({ id, label, children }: { id: string; label: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      {children}
    </div>
  )
}

function toDraft(param: TaskKindParam): ParamDraft {
  return {
    paramKey: param.paramKey,
    paramLabel: param.paramLabel,
    paramType: param.paramType,
    defaultValue: param.defaultValue ?? '',
    required: Boolean(param.required),
    visibility: normalizeVisibility(param.visibility),
    optionsText: (param.options ?? []).join('\n'),
    apiUrl: param.apiUrl ?? '',
    apiMethod: param.apiMethod || 'GET',
    apiHeadersText: param.apiHeaders && Object.keys(param.apiHeaders).length > 0 ? JSON.stringify(param.apiHeaders, null, 2) : '',
    apiResponsePath: param.apiResponsePath ?? '',
    placeholder: param.placeholder ?? '',
  }
}

function emptyDraft(): ParamDraft {
  return {
    paramKey: '',
    paramLabel: '',
    paramType: 'input',
    defaultValue: '',
    required: false,
    visibility: 'user',
    optionsText: '',
    apiUrl: '',
    apiMethod: 'GET',
    apiHeadersText: '',
    apiResponsePath: '',
    placeholder: '',
  }
}

function fromDraft(draft: ParamDraft, index: number): TaskKindParam {
  const paramKey = draft.paramKey.trim()
  const label = draft.paramLabel.trim() || paramKey
  if (!paramKey) throw new Error(`第 ${index + 1} 个参数缺少键`)
  if (!/^[A-Za-z_][A-Za-z0-9_]*$/.test(paramKey)) {
    throw new Error(`参数键只允许字母、数字和下划线: ${paramKey}`)
  }
  if (draft.paramType === 'api_select' && !draft.apiUrl.trim()) {
    throw new Error(`参数 ${paramKey} 需要接口地址`)
  }
  const options = draft.paramType === 'select'
    ? draft.optionsText.split('\n').map((item) => item.trim()).filter(Boolean)
    : undefined
  if (draft.paramType === 'select' && (!options || options.length === 0)) {
    throw new Error(`参数 ${paramKey} 至少需要一个枚举选项`)
  }
  return {
    paramKey,
    paramLabel: label,
    paramType: draft.paramType,
    defaultValue: draft.defaultValue,
    required: draft.required,
    visibility: draft.visibility,
    sortOrder: index,
    options,
    apiUrl: draft.paramType === 'api_select' ? draft.apiUrl.trim() : '',
    apiMethod: draft.paramType === 'api_select' ? draft.apiMethod || 'GET' : 'GET',
    apiHeaders: draft.paramType === 'api_select' ? parseHeaders(draft.apiHeadersText, paramKey) : undefined,
    apiResponsePath: draft.paramType === 'api_select' ? draft.apiResponsePath.trim() : '',
    placeholder: draft.placeholder,
  }
}

function parseHeaders(text: string, paramKey: string): Record<string, string> | undefined {
  const raw = text.trim()
  if (!raw) return undefined
  let parsed: unknown
  try {
    parsed = JSON.parse(raw)
  } catch {
    throw new Error(`参数 ${paramKey} 的请求头不是合法 JSON`)
  }
  if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
    throw new Error(`参数 ${paramKey} 的请求头必须是 JSON 对象`)
  }
  return Object.fromEntries(Object.entries(parsed).map(([key, value]) => [key, String(value)]))
}
