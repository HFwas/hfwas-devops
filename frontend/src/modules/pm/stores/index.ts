import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { FieldDefinition, PmProject } from '@/modules/pm/types'
import type { EntityId } from '@/modules/pm/utils/id'
import { pmFieldApi, pmProjectApi } from '@/modules/pm/api'

export const useProjectStore = defineStore('pm-project', () => {
  const currentProject = ref<PmProject | null>(null)
  const projects = ref<PmProject[]>([])

  async function loadProjects(keyword = '') {
    const page = await pmProjectApi.page({ pageNo: 1, pageSize: 100, keyword })
    projects.value = page.records
  }

  async function selectProject(id: EntityId) {
    currentProject.value = await pmProjectApi.getById(id)
  }

  return { currentProject, projects, loadProjects, selectProject }
})

export const useFieldSchemaStore = defineStore('pm-field-schema', () => {
  const schemas = ref<Record<string, FieldDefinition[]>>({})

  async function loadSchema(projectId: EntityId, typeCode: string) {
    const key = `${projectId}:${typeCode}`
    schemas.value[key] = await pmFieldApi.list(projectId, typeCode)
  }

  function getSchema(projectId: EntityId, typeCode: string) {
    return schemas.value[`${projectId}:${typeCode}`] || []
  }

  return { schemas, loadSchema, getSchema }
})
