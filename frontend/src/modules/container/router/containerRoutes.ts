import type { RouteRecordRaw } from 'vue-router'

export const containerRoutes: RouteRecordRaw[] = [
  {
    path: '/container',
    component: () => import('@/modules/container/views/ContainerShell.vue'),
    children: [
      { path: '', redirect: '/container/clusters' },
      {
        path: 'clusters',
        name: 'container-clusters',
        component: () => import('@/modules/container/views/ClusterListView.vue'),
      },
      {
        path: 'clusters/:id',
        name: 'container-cluster-detail',
        component: () => import('@/modules/container/views/ClusterDetailView.vue'),
      },
      {
        path: 'clusters/:clusterId/nodes',
        name: 'container-nodes',
        component: () => import('@/modules/container/views/NodeListView.vue'),
      },
      {
        path: 'clusters/:clusterId/nodes/:name',
        name: 'container-node-detail',
        component: () => import('@/modules/container/views/NodeDetailView.vue'),
      },
      {
        path: 'clusters/:clusterId/pods',
        name: 'container-pods',
        component: () => import('@/modules/container/views/PodListView.vue'),
      },
      {
        path: 'clusters/:clusterId/pods/:namespace/:name',
        name: 'container-pod-detail',
        component: () => import('@/modules/container/views/PodDetailView.vue'),
      },
      {
        path: 'clusters/:clusterId/deployments',
        name: 'container-deployments',
        component: () => import('@/modules/container/views/DeploymentListView.vue'),
      },
      {
        path: 'clusters/:clusterId/services',
        name: 'container-services',
        component: () => import('@/modules/container/views/ServiceListView.vue'),
      },
    ],
  },
]