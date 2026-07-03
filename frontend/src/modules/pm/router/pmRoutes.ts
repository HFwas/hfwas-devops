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
        path: 'items',
        name: 'pm-work-items',
        component: () => import('@/modules/pm/views/WorkItemListView.vue'),
      },
      {
        path: 'items/:itemId',
        name: 'pm-work-item-detail',
        component: () => import('@/modules/pm/views/WorkItemDetailView.vue'),
      },
      {
        path: 'board',
        name: 'pm-board',
        component: () => import('@/modules/pm/views/ProjectBoardView.vue'),
      },
      {
        path: 'fields',
        name: 'pm-fields',
        component: () => import('@/modules/pm/views/FieldSettingsView.vue'),
      },
    ],
  },
]
