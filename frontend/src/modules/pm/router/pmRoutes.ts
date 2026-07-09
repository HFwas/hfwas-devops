import type { RouteRecordRaw } from 'vue-router'

export const pmRoutes: RouteRecordRaw[] = [
  {
    path: '/pm/projects',
    name: 'pm-projects',
    component: () => import('@/modules/pm/views/ProjectListView.vue'),
  },
  {
    path: '/pm/projects/:projectId',
    component: () => import('@/modules/pm/layouts/PmLayout.vue'),
    children: [
      {
        path: '',
        redirect: (to) => `/pm/projects/${to.params.projectId}/items/task`,
      },
      {
        path: 'items',
        redirect: (to) => `/pm/projects/${to.params.projectId}/items/task`,
      },
      {
        path: 'items/:itemId(\\d+)',
        name: 'pm-work-item-detail',
        component: () => import('@/modules/pm/views/WorkItemDetailView.vue'),
      },
      {
        path: 'items/:typeCode',
        name: 'pm-work-items',
        component: () => import('@/modules/pm/views/WorkItemListView.vue'),
      },
      {
        path: 'board',
        redirect: (to) => `/pm/projects/${to.params.projectId}/board/task`,
      },
      {
        path: 'board/:typeCode',
        name: 'pm-board',
        component: () => import('@/modules/pm/views/ProjectBoardView.vue'),
      },
      {
        path: 'settings/modules',
        name: 'pm-module-settings',
        component: () => import('@/modules/pm/views/settings/ModuleManageView.vue'),
      },
      {
        path: 'settings/fields',
        name: 'pm-field-catalog',
        component: () => import('@/modules/pm/views/settings/FieldCatalogView.vue'),
      },
      {
        path: 'settings/types',
        name: 'pm-issue-type-settings',
        component: () => import('@/modules/pm/views/settings/IssueTypeListView.vue'),
      },
      {
        path: 'settings/types/:typeCode',
        name: 'pm-issue-type-layout',
        component: () => import('@/modules/pm/views/settings/IssueTypeLayoutView.vue'),
      },
      {
        path: 'settings/workflow',
        redirect: (to) => `/pm/projects/${to.params.projectId}/settings/workflow/task`,
      },
      {
        path: 'settings/workflow/:typeCode',
        name: 'pm-status-workflow',
        component: () => import('@/modules/pm/views/settings/StatusWorkflowView.vue'),
      },
      {
        path: 'fields',
        redirect: (to) => `/pm/projects/${to.params.projectId}/settings/types`,
      },
      {
        path: 'fields/:typeCode',
        redirect: (to) => `/pm/projects/${to.params.projectId}/settings/types/${to.params.typeCode}`,
      },
    ],
  },
]
