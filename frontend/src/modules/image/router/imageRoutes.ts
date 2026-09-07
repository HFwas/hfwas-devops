import type { RouteRecordRaw } from 'vue-router'

export const imageRoutes: RouteRecordRaw[] = [
  {
    path: '/image',
    name: 'image',
    component: () => import('@/modules/image/views/ImageWorkbenchView.vue'),
  },
]
