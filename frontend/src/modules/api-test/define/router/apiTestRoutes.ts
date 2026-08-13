import type { RouteRecordRaw } from 'vue-router'

export const apiTestRoutes: RouteRecordRaw[] = [
  {
    path: '/api-test',
    redirect: '/api-test/definitions',
  },
  {
    path: '/api-test/definitions',
    name: 'api-definition-list',
    component: () => import('@/modules/api-test/define/views/ApiDefinitionList.vue'),
  },
  {
    path: '/api-test/definitions/:id',
    name: 'api-definition-detail',
    component: () => import('@/modules/api-test/define/views/ApiDefinitionDetail.vue'),
  },
]