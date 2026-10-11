import { describe, expect, it } from 'vitest'
import { createEditorJob } from '@/modules/pipeline/graph/pipelineGraph'
import {
  addPaletteJob,
  appendEmptyStage,
  bindingsForKind,
  changeJobKind,
  createJobFromKind,
  emptyDraft,
  moveJob,
  moveJobBefore,
  moveStage,
  removeJob,
  toSavePayload,
  validateDraft,
  visibleParams,
} from '@/modules/pipeline/pipelineDraft'
import type { TaskKindVO } from '@/modules/pipeline/types/pipeline'

const cloneKind: TaskKindVO = {
  kindValue: 'CLONE',
  label: '代码克隆',
  taskGroup: '代码',
  description: '拉取',
  hint: '',
  defaultCommand: '',
  requiresCommand: false,
  enabled: true,
  sortOrder: 1,
  toolImage: '',
  defaultImage: '',
  commandTemplate: '',
  params: [
    { paramKey: 'GIT_REF', paramLabel: '代码分支', paramType: 'input', defaultValue: 'main', required: true, sortOrder: 0 },
    { paramKey: 'GIT_PASSWORD', paramLabel: 'Git 密码', paramType: 'input', defaultValue: '', required: false, sortOrder: 1 },
    { paramKey: 'SECRET_TOKEN', paramLabel: '隐藏', paramType: 'input', defaultValue: 'x', required: false, sortOrder: 2, hidden: true },
    { paramKey: 'ADMIN_ONLY', paramLabel: '仅管理员', paramType: 'input', defaultValue: '', required: false, sortOrder: 3, visibility: 'hidden' },
  ],
}

const buildKind: TaskKindVO = {
  ...cloneKind,
  kindValue: 'BUILD',
  label: 'Maven 构建',
  taskGroup: '构建',
  description: '编译',
  defaultCommand: 'mvn package',
  requiresCommand: true,
  sortOrder: 2,
  params: [
    { paramKey: 'DEST', paramLabel: '镜像推送目标', paramType: 'input', defaultValue: '', required: true, sortOrder: 0 },
  ],
}

describe('pipelineDraft', () => {
  it('hides credential and flagged params from the inspector', () => {
    expect(visibleParams(cloneKind.params).map((item) => item.paramKey)).toEqual(['GIT_REF'])
  })

  it('validates the save payload and keeps hidden bindings already on the job', () => {
    const draft = emptyDraft()
    expect(validateDraft(draft)).toBe('名称不能为空')
    draft.name = '后端发布'
    expect(validateDraft(draft)).toBe('流水线至少需要一个阶段')

    const clone = createJobFromKind(cloneKind, 'dev')
    clone.paramBindings = { ...clone.paramBindings, GIT_PASSWORD: { mode: 'fixed', value: 'kept' } }
    const added = addPaletteJob([], null, clone)
    expect(added.error).toBeNull()
    draft.stages = added.stages
    expect(validateDraft(draft, [cloneKind])).toBe('仓库地址不能为空')
    draft.repoUrl = 'https://github.com/HFwas/hfwas-devops.git'
    draft.gitRef = 'dev'
    expect(validateDraft(draft, [cloneKind])).toBeNull()

    const payload = toSavePayload(draft)
    expect(payload.credentialId).toBeNull()
    expect(payload.gitRef).toBe('dev')
    expect(payload.stages[0]).not.toHaveProperty('clientKey')
    expect(payload.stages[0].name).toBe('拉取')
    expect(payload.stages[0].jobs[0].paramBindings).toMatchObject({
      GIT_REF: { mode: 'fixed', value: 'dev' },
      GIT_PASSWORD: { mode: 'fixed', value: 'kept' },
    })
    expect(payload.stages[0].jobs[0].paramBindings).not.toHaveProperty('SECRET_TOKEN')
  })

  it('requires a command and visible fixed params', () => {
    const job = createJobFromKind(buildKind, 'main')
    job.command = '   '
    const draft = { ...emptyDraft(), name: '构建', repoUrl: '', stages: addPaletteJob([], null, job).stages }
    expect(validateDraft(draft, [buildKind])).toBe('任务「Maven 构建」需要命令')
    draft.stages = draft.stages.map((stage) => ({
      ...stage,
      jobs: stage.jobs.map((item) => ({ ...item, command: 'mvn package', paramBindings: { DEST: { mode: 'fixed', value: '' } } })),
    }))
    expect(validateDraft(draft, [buildKind])).toBe('任务「Maven 构建」的参数 镜像推送目标 不能为空')
  })

  it('adds a palette job into the chosen stage and falls back when the stage cannot take it', () => {
    const clone = createJobFromKind(cloneKind, 'main')
    let stages = addPaletteJob([], null, clone).stages
    const build = createEditorJob('BUILD', { name: 'Maven 构建', command: 'mvn test' })
    stages = addPaletteJob(stages, stages[0].clientKey, build).stages
    expect(stages).toHaveLength(1)
    expect(stages[0].jobs.map((job) => job.kind)).toEqual(['CLONE', 'BUILD'])

    const approval = createEditorJob('APPROVAL', { name: '人工卡点' })
    const fallback = addPaletteJob(stages, stages[0].clientKey, approval)
    expect(fallback.error).toBeNull()
    expect(fallback.stages.map((stage) => stage.jobs.map((job) => job.kind))).toEqual([['CLONE', 'BUILD'], ['APPROVAL']])

    const again = addPaletteJob(fallback.stages, null, createJobFromKind(cloneKind, 'main'))
    expect(again.error).toBe('流水线至多一个克隆任务')
  })

  it('reorders and deletes stages and jobs without dropping an empty stage', () => {
    let stages = appendEmptyStage([])
    const first = createEditorJob('BUILD', { name: 'Maven 构建', command: 'mvn package' })
    const second = createEditorJob('TEST', { name: '测试', command: 'mvn test' })
    stages = addPaletteJob(stages, stages[0].clientKey, first).stages
    stages = addPaletteJob(stages, stages[0].clientKey, second).stages
    stages = moveJob(stages, second.clientKey, -1).stages
    expect(stages[0].jobs.map((job) => job.name)).toEqual(['测试', 'Maven 构建'])

    const extra = appendEmptyStage(stages)
    const moved = moveJobBefore(extra, first.clientKey, extra[1].clientKey, null)
    expect(moved.error).toBeNull()
    expect(moved.stages[0].jobs.map((job) => job.name)).toEqual(['测试'])
    expect(moved.stages[1].jobs.map((job) => job.name)).toEqual(['Maven 构建'])

    const swapped = moveStage(moved.stages, moved.stages[1].clientKey, -1)
    expect(swapped[0].jobs.map((job) => job.name)).toEqual(['Maven 构建'])
    const kept = removeJob(swapped, swapped[0].jobs[0].clientKey)
    expect(kept).toHaveLength(2)
    expect(kept[0].jobs).toEqual([])
  })

  it('changes kind without renaming a custom job name', () => {
    const job = createJobFromKind(cloneKind, 'dev')
    job.name = '拉代码'
    const stages = addPaletteJob([], null, job).stages
    const changed = changeJobKind(stages, job.clientKey, 'BUILD', [cloneKind, buildKind], 'dev')
    expect(changed.error).toBeNull()
    expect(changed.stages[0].jobs[0]).toMatchObject({ name: '拉代码', kind: 'BUILD', command: 'mvn package' })
    expect(bindingsForKind(buildKind.params, 'dev').DEST).toEqual({ mode: 'fixed', value: '' })
  })
})
