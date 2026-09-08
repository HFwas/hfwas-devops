import type { RouteRecordRaw } from 'vue-router'

export const pipelineRoutes: RouteRecordRaw[] = [
  { path: '/pipeline/overview', redirect: '/pipeline/pipelines' },
  {
    path: '/pipeline',
    component: () => import('@/modules/pipeline/views/PipelineShell.vue'),
    children: [
      { path: '', redirect: '/pipeline/pipelines' },
      {
        path: 'pipelines',
        name: 'pipeline-list',
        component: () => import('@/modules/pipeline/views/PipelineListView.vue'),
      },
      {
        path: 'pipelines/new',
        name: 'pipeline-new',
        meta: { fill: true },
        component: () => import('@/modules/pipeline/views/PipelineEditorView.vue'),
      },
      {
        path: 'pipelines/:id/runs/:runId',
        name: 'pipeline-run',
        meta: { fill: true },
        component: () => import('@/modules/pipeline/views/PipelineRunView.vue'),
      },
      {
        path: 'pipelines/:id/edit',
        name: 'pipeline-edit',
        meta: { fill: true },
        component: () => import('@/modules/pipeline/views/PipelineEditorView.vue'),
      },
      {
        path: 'pipelines/:id',
        name: 'pipeline-home',
        component: () => import('@/modules/pipeline/views/PipelineHomeView.vue'),
      },
      {
        path: 'credentials',
        name: 'pipeline-credentials',
        component: () => import('@/modules/pipeline/views/PipelineCredentialView.vue'),
      },
    ],
  },
]
