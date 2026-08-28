import type { RouteRecordRaw } from 'vue-router'

export const docgenRoutes: RouteRecordRaw[] = [
  {
    path: '/docgen',
    name: 'docgen',
    component: () => import('@/modules/docgen/views/DocgenView.vue'),
  },
]