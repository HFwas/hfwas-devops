import { describe, expect, it } from 'vitest'
import { JOB_KIND_GROUPS, JOB_KIND_OPTIONS } from './jobCatalog'
import {
  addParallelJob,
  canAddKindToStage,
  canChangeJobKind,
  createEditorJob,
  createTemplateStages,
  groupRunJobs,
  hasClone,
  insertStageAt,
  kindSelectOptions,
  removeEditorJob,
  repoShortName,
  toEditorStages,
  toSaveStages,
} from './pipelineGraph'

describe('pipeline helpers', () => {
  it('exposes 14 job kinds in 云效-style groups and a clone/build/test template', () => {
    expect(JOB_KIND_OPTIONS).toHaveLength(14)
    expect(JOB_KIND_OPTIONS.map((item) => item.value)).toEqual(
      expect.arrayContaining(['LINT_SEMGREP', 'LINT_SONAR']),
    )
    expect(JOB_KIND_OPTIONS.map((item) => item.value)).not.toContain('LINT')
    expect([...JOB_KIND_GROUPS]).toEqual(['代码', '构建', '质量控制', '制品', '部署', '测试', '命令', '流程'])
    const graph = createTemplateStages()
    expect(graph.map((stage) => stage.jobs[0].kind)).toEqual(['CLONE', 'BUILD', 'TEST'])
    expect(hasClone(graph)).toBe(true)
    const jobs = graph.flatMap((stage) => stage.jobs.map((job) => ({ kind: job.kind, id: job.name })))
    expect(kindSelectOptions(jobs, '构建').some((item) => item.value === 'CLONE')).toBe(false)
    expect(kindSelectOptions(jobs, '代码克隆').some((item) => item.value === 'CLONE')).toBe(true)
  })

  it('summarizes repo', () => {
    expect(repoShortName('https://github.com/acme/demo.git')).toBe('acme/demo')
    expect(repoShortName('https://gitlab.example.com/group/proj.git')).toBe('group/proj')
  })

  it('inserts sequential stages and parallel jobs, then serializes without client keys', () => {
    let stages = toEditorStages([])
    const clone = createEditorJob('CLONE')
    stages = insertStageAt(stages, -1, clone)
    stages = insertStageAt(stages, 0, createEditorJob('BUILD'))
    stages = addParallelJob(stages, stages[1].clientKey, createEditorJob('TEST'))
    expect(stages).toHaveLength(2)
    expect(stages[0].jobs.map((job) => job.kind)).toEqual(['CLONE'])
    expect(stages[1].jobs.map((job) => job.kind)).toEqual(['BUILD', 'TEST'])

    const saved = toSaveStages(stages)
    expect(saved[0].sortOrder).toBe(0)
    expect(saved[1].jobs.map((job) => job.sortOrder)).toEqual([0, 1])
    expect(saved[0]).not.toHaveProperty('clientKey')

    stages = removeEditorJob(stages, stages[1].jobs[0].clientKey)
    expect(stages[1].jobs.map((job) => job.kind)).toEqual(['TEST'])
    stages = removeEditorJob(stages, stages[1].jobs[0].clientKey)
    expect(stages).toHaveLength(1)
  })

  it('blocks clone / approval / image constraints when adding or changing kinds', () => {
    let stages = insertStageAt(toEditorStages([]), -1, createEditorJob('CLONE'))
    expect(canAddKindToStage(stages, null, 'CLONE')).toBe('流水线至多一个克隆任务')
    expect(canAddKindToStage(stages, stages[0].clientKey, 'BUILD')).toBeNull()

    stages = insertStageAt(stages, 0, createEditorJob('APPROVAL'))
    expect(canAddKindToStage(stages, stages[1].clientKey, 'TEST')).toBe('审批任务必须独占一列')
    expect(canAddKindToStage(stages, null, 'TEST')).toBeNull()

    stages = insertStageAt(stages, 1, createEditorJob('IMAGE'))
    const imageStage = stages[2]
    expect(canAddKindToStage(stages, imageStage.clientKey, 'IMAGE')).toBe('镜像构建不能与其它镜像构建并行')
    expect(canAddKindToStage(stages, imageStage.clientKey, 'DEPLOY')).toBeNull()
    expect(canChangeJobKind(stages, imageStage.jobs[0].clientKey, 'CLONE')).toBe('流水线至多一个克隆任务')
  })

  it('maps run job status onto editor nodes for success chrome', () => {
    const stages = groupRunJobs([
      {
        id: 1,
        jobId: 11,
        stageName: '代码克隆',
        jobName: '代码克隆',
        kind: 'CLONE',
        status: 'SUCCEEDED',
        startedAt: '2026-09-08T09:37:00',
        finishedAt: '2026-09-08T09:37:15',
      },
      {
        id: 2,
        jobId: 12,
        stageName: '构建',
        jobName: '代码构建',
        kind: 'BUILD',
        status: 'RUNNING',
      },
    ])
    expect(stages[0].jobs[0].status).toBe('SUCCEEDED')
    expect(stages[1].jobs[0].status).toBe('RUNNING')
    expect(stages[0].jobs[0].runJobId).toBe(1)
  })
})
