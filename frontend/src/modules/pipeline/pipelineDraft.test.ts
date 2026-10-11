import { describe, expect, it } from 'vitest'
import {
  applyJobKind,
  blankPipeline,
  resolvedBindings,
  toPipelineSavePayload,
  userVisibleParams,
  validatePipelineDraft,
  type DraftJob,
} from '@/modules/pipeline/pipelineDraft'
import type { TaskKindVO } from '@/modules/pipeline/types/pipeline'

const clone: TaskKindVO = {
  kindValue: 'CLONE',
  label: '代码克隆',
  taskGroup: '代码',
  description: '',
  hint: '',
  defaultCommand: '',
  requiresCommand: false,
  enabled: true,
  sortOrder: 1,
  toolImage: '',
  defaultImage: '',
  commandTemplate: '',
  params: [
    {
      paramKey: 'GIT_REF',
      paramLabel: '分支',
      paramType: 'input',
      defaultValue: 'main',
      required: false,
      sortOrder: 0,
      visibility: 'user',
    },
    {
      paramKey: 'GIT_TOKEN',
      paramLabel: '令牌',
      paramType: 'input',
      defaultValue: 'secret',
      required: false,
      sortOrder: 1,
      visibility: 'hidden',
    },
  ],
}

const build: TaskKindVO = {
  kindValue: 'BUILD',
  label: '构建',
  taskGroup: '构建',
  description: '',
  hint: '',
  defaultCommand: 'mvn package',
  requiresCommand: true,
  enabled: true,
  sortOrder: 2,
  toolImage: '',
  defaultImage: '',
  commandTemplate: '',
}

describe('pipeline draft', () => {
  it('drops hidden params from bindings and defaults user params to fixed', () => {
    expect(userVisibleParams(clone).map((param) => param.paramKey)).toEqual(['GIT_REF'])
    const draft = blankPipeline([clone, build])
    expect(draft.stages[0]?.jobs[0]?.kind).toBe('CLONE')
    expect(draft.stages[0]?.jobs[0]?.paramBindings).toEqual({ GIT_REF: { mode: 'fixed', value: 'main' } })
    const payload = toPipelineSavePayload(
      { ...draft, name: 'ci', repoUrl: 'https://github.com/acme/app.git' },
      [clone, build],
    )
    expect(payload.stages[0]?.jobs[0]?.paramBindings).not.toHaveProperty('GIT_TOKEN')
  })

  it('keeps runtime bindings and rejects a clone pipeline without a repo', () => {
    const job: DraftJob = {
      clientKey: 'job',
      name: '克隆',
      kind: 'CLONE',
      command: '',
      paramBindings: { GIT_REF: { mode: 'runtime' }, GIT_TOKEN: { mode: 'fixed', value: 'nope' } },
    }
    expect(resolvedBindings(job, clone)).toEqual({ GIT_REF: { mode: 'runtime' } })
    const draft = blankPipeline([clone])
    draft.name = 'ci'
    draft.repoUrl = ''
    draft.stages[0]!.jobs[0]!.paramBindings = { GIT_REF: { mode: 'runtime' } }
    expect(validatePipelineDraft(draft, [clone])).toBe('包含代码克隆时仓库地址不能为空')
    draft.repoUrl = 'https://github.com/acme/app.git'
    expect(validatePipelineDraft(draft, [clone])).toBeNull()
    const payload = toPipelineSavePayload(draft, [clone])
    expect(payload.stages[0]?.jobs[0]?.paramBindings).toEqual({ GIT_REF: { mode: 'runtime' } })
  })

  it('requires a command when the task kind says so, and fills the default on kind change', () => {
    const draft = blankPipeline([clone, build])
    draft.name = 'ci'
    draft.repoUrl = 'https://github.com/acme/app.git'
    const job = draft.stages[0]!.jobs[0]!
    const switched = applyJobKind(job, build, clone)
    expect(switched.command).toBe('mvn package')
    draft.stages[0]!.jobs = [{ ...switched, command: '   ' }]
    expect(validatePipelineDraft(draft, [clone, build])).toBe('任务「代码克隆」需要命令')
  })
})
