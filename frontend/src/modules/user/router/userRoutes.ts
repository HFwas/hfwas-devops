import type { RouteRecordRaw } from 'vue-router'

export const userRoutes: RouteRecordRaw[] = [
  {
    path: '/messages',
    name: 'message-inbox',
    component: () => import('@/modules/user/views/MessageInboxView.vue'),
  },
  {
    path: '/user/login',
    name: 'user-login',
    component: () => import('@/modules/user/views/LoginView.vue'),
    meta: { public: true },
  },
  {
    path: '/user/settings',
    name: 'user-account-settings',
    component: () => import('@/modules/user/views/AccountSettingsView.vue'),
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
        path: 'tenants',
        name: 'user-tenants',
        component: () => import('@/modules/user/views/TenantManageView.vue'),
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
      {
        path: 'oper-logs',
        name: 'user-oper-logs',
        component: () => import('@/modules/user/views/OperLogView.vue'),
      },
      {
        path: 'integrations',
        name: 'user-integrations',
        component: () => import('@/modules/user/views/IntegrationManageView.vue'),
      },
      {
        path: 'messages',
        name: 'user-messages-admin',
        component: () => import('@/modules/user/views/MessageManageView.vue'),
      },
      {
        path: 'message-notify',
        name: 'user-message-notify',
        component: () => import('@/modules/user/views/MessageNotifyView.vue'),
      },
    ],
  },
  {
    path: '/user/manage',
    redirect: '/user/accounts',
  },
]
