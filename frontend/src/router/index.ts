import { createRouter, createWebHistory } from 'vue-router'
import { pmRoutes } from '@/modules/pm/router/pmRoutes'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/pm/projects' },
    ...pmRoutes,
  ],
})

export default router
