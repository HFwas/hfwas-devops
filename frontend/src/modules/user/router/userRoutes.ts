import type { RouteRecordRaw } from 'vue-router'

export const userRoutes: RouteRecordRaw[] = [
  {
    path: '/user/login',
    name: 'user-login',
    component: () => import('@/modules/user/views/LoginView.vue'),
    meta: { public: true },
  },
  {
    path: '/user',
    component: () => import('@/modules/user/layouts/UserLayout.vue'),
    meta: { admin: true },
    children: [
      {
        path: '',
        redirect: '/user/accounts',
      },
      {
        path: 'accounts',
        name: 'user-accounts',
        component: () => import('@/modules/user/views/UserManageView.vue'),
      },
      {
        path: 'sessions',
        name: 'user-sessions',
        component: () => import('@/modules/user/views/UserSessionView.vue'),
      },
      {
        path: 'login-logs',
        name: 'user-login-logs',
        component: () => import('@/modules/user/views/LoginLogView.vue'),
      },
    ],
  },
  {
    path: '/user/manage',
    redirect: '/user/accounts',
  },
]
