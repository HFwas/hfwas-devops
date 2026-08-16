import type { RouteRecordRaw } from 'vue-router'

export const fileParserRoutes: RouteRecordRaw[] = [
  {
    path: '/file-parser',
    name: 'file-parser',
    component: () => import('@/modules/file-parser/views/FileParserView.vue'),
  },
]