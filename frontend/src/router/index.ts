import { createRouter, createWebHistory } from 'vue-router'
import { pmRoutes } from '@/modules/pm/router/pmRoutes'
import { userRoutes } from '@/modules/user/router/userRoutes'
import { apiTestRoutes } from '@/modules/api-test/define/router/apiTestRoutes'
import { fileParserRoutes } from '@/modules/file-parser/router/fileParserRoutes'
import { useAuthStore } from '@/modules/user/stores/auth'
import { resolveRouteProjectId } from '@/modules/pm/utils/projectRoute'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/workbench' },
    {
      path: '/workbench',
      name: 'workbench',
      component: () => import('@/modules/pm/views/WorkbenchView.vue'),
    },
    ...userRoutes,
    ...pmRoutes,
    ...apiTestRoutes,
    ...fileParserRoutes,
  ],
})

router.beforeEach(async (to) => {
  if (to.meta.public) return true
  const auth = useAuthStore()
  if (!auth.token) {
    return { path: '/user/login', query: { redirect: to.fullPath } }
  }
  if (!auth.user) {
    const me = await auth.fetchMe()
    if (!me) {
      return { path: '/user/login', query: { redirect: to.fullPath } }
    }
  }
  const projectId = resolveRouteProjectId(to)
  if (projectId) {
    try {
      await auth.ensureTenantForProject(projectId)
    } catch {
      return { path: '/pm/projects' }
    }
  }
  if (to.meta.admin && !auth.isAdmin) {
    return { path: '/workbench' }
  }
  return true
})

export default router
