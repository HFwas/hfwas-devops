import type { RouteRecordRaw } from 'vue-router'

export const argoRoutes: RouteRecordRaw[] = [
  {
    path: '/argo',
    component: () => import('@/modules/argo/views/ArgoShell.vue'),
    children: [
      { path: '', redirect: '/argo/workflows' },
      {
        path: 'workflows',
        name: 'argo-workflows',
        component: () => import('@/modules/argo/views/WorkflowListView.vue'),
      },
      {
        path: 'workflows/new',
        name: 'argo-workflow-new',
        meta: { fill: true },
        component: () => import('@/modules/argo/views/WorkflowEditorView.vue'),
      },
      {
        path: 'workflows/:name',
        name: 'argo-workflow-detail',
        component: () => import('@/modules/argo/views/WorkflowHomeView.vue'),
      },
      {
        path: 'workflows/:name/edit',
        name: 'argo-workflow-edit',
        meta: { fill: true },
        component: () => import('@/modules/argo/views/WorkflowEditorView.vue'),
      },
      {
        path: 'workflows/:name/runs/:runId',
        name: 'argo-workflow-run',
        meta: { fill: true },
        component: () => import('@/modules/argo/views/WorkflowRunView.vue'),
      },
      {
        path: 'templates',
        name: 'argo-templates',
        component: () => import('@/modules/argo/views/TemplateListView.vue'),
      },
      {
        path: 'cron',
        name: 'argo-cron',
        component: () => import('@/modules/argo/views/CronListView.vue'),
      },
    ],
  },
]