import type { RouteRecordRaw } from 'vue-router'

export const pipelineRoutes: RouteRecordRaw[] = [
  { path: '/pipeline', redirect: '/pipeline/pipelines' },
  { path: '/pipeline/overview', redirect: '/pipeline/pipelines' },
  {
    path: '/pipeline/pipelines',
    name: 'pipeline-list',
    component: () => import('@/modules/pipeline/views/PipelineListView.vue'),
  },
  {
    path: '/pipeline/pipelines/new',
    name: 'pipeline-new',
    component: () => import('@/modules/pipeline/views/PipelineEditorView.vue'),
  },
  {
    path: '/pipeline/pipelines/:id/runs/:runId',
    name: 'pipeline-run',
    component: () => import('@/modules/pipeline/views/PipelineRunView.vue'),
  },
  {
    path: '/pipeline/pipelines/:id',
    name: 'pipeline-edit',
    component: () => import('@/modules/pipeline/views/PipelineEditorView.vue'),
  },
  {
    path: '/pipeline/credentials',
    name: 'pipeline-credentials',
    component: () => import('@/modules/pipeline/views/PipelineCredentialView.vue'),
  },
]
