import { describe, expect, it } from 'vitest'
import { filterTaskKinds, taskKindGroups, taskKindUpdatePayload } from '@/modules/pipeline/taskKindView'
import type { TaskKindVO } from '@/modules/pipeline/types/pipeline'

const kinds: TaskKindVO[] = [
  {
    kindValue: 'BUILD',
    label: '构建',
    taskGroup: '构建',
    description: '编译源码',
    hint: '',
    defaultCommand: 'mvn package',
    requiresCommand: true,
    enabled: true,
    sortOrder: 2,
    toolImage: '',
    defaultImage: '',
    commandTemplate: '',
    version: '1.2.0',
  },
  {
    kindValue: 'CLONE',
    label: '代码克隆',
    taskGroup: '代码',
    description: '拉取仓库',
    hint: '填写凭证',
    defaultCommand: '',
    requiresCommand: false,
    enabled: true,
    sortOrder: 1,
    toolImage: '',
    defaultImage: '',
    commandTemplate: '',
    version: '',
  },
]

describe('task kind view', () => {
  it('filters by group and keyword without dropping the version field', () => {
    expect(taskKindGroups(kinds)).toEqual(['代码', '构建'])
    expect(filterTaskKinds(kinds, '克隆', '全部').map((item) => item.kindValue)).toEqual(['CLONE'])
    expect(filterTaskKinds(kinds, '', '构建').map((item) => item.version)).toEqual(['1.2.0'])
    expect(filterTaskKinds(kinds, '凭证', '代码')).toHaveLength(1)
    expect(filterTaskKinds(kinds, '凭证', '构建')).toHaveLength(0)
  })

  it('saves the working copy without a version and omits params unless they are edited', () => {
    const overview = taskKindUpdatePayload(kinds[0], {
      overview: {
        label: '新构建',
        taskGroup: '构建',
        description: '说明',
        hint: '',
        toolImage: 'maven:3',
        commandTemplate: 'mvn test',
        sortOrder: 4,
        cpuRequest: '500m',
        cpuLimit: '',
        memoryRequest: '',
        memoryLimit: '',
      },
    })
    expect(overview.label).toBe('新构建')
    expect(overview.defaultCommand).toBe('mvn package')
    expect(overview).not.toHaveProperty('version')
    expect(overview.params).toBeUndefined()

    const withParams = taskKindUpdatePayload(kinds[0], {
      params: [
        {
          paramKey: 'TOKEN',
          paramLabel: 'Token',
          paramType: 'input',
          defaultValue: 'secret',
          required: false,
          sortOrder: 0,
          visibility: 'hidden',
        },
      ],
    })
    expect(withParams.label).toBe('构建')
    expect(withParams.params?.[0]?.visibility).toBe('hidden')
    expect(withParams).not.toHaveProperty('version')
  })
})
