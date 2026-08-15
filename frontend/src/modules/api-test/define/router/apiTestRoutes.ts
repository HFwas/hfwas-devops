import type { RouteRecordRaw } from 'vue-router'

export const apiTestRoutes: RouteRecordRaw[] = [
  { path: '/api-test', name: 'api-test-shell', component: () => import('@/modules/api-test/shell/views/ApiTestShell.vue') },
  { path: '/api-test/definitions', redirect: (to) => ({ path: '/api-test', query: { ...to.query, module: 'apis' } }) },
  { path: '/api-test/definitions/:id', redirect: (to) => ({ path: '/api-test', query: { ...to.query, module: 'apis', def: String(to.params.id) } }) },
  { path: '/api-test/environments', redirect: () => ({ path: '/api-test' }) },
  { path: '/api-test/environments/:id', redirect: (to) => ({ path: '/api-test', query: { ...to.query, envEdit: String(to.params.id) } }) },
  { path: '/api-test/collections', redirect: () => ({ path: '/api-test', query: { module: 'collections' } }) },
  { path: '/api-test/collections/:id', redirect: (to) => ({ path: '/api-test', query: { module: 'collections', collectionId: String(to.params.id) } }) },
  { path: '/api-test/collections/:id/runs', redirect: (to) => ({ path: '/api-test', query: { module: 'collections', collectionId: String(to.params.id), runs: '1' } }) },
]

