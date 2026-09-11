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
        props: true,
      },
      {
        path: 'clusters/:clusterId/nodes',
        name: 'container-nodes',
        component: () => import('@/modules/container/views/NodeListView.vue'),
        props: true,
      },
      {
        path: 'clusters/:clusterId/nodes/:name',
        name: 'container-node-detail',
        component: () => import('@/modules/container/views/NodeDetailView.vue'),
        props: true,
      },
      {
        path: 'clusters/:clusterId/pods',
        name: 'container-pods',
        component: () => import('@/modules/container/views/PodListView.vue'),
        props: true,
      },
      {
        path: 'clusters/:clusterId/pods/:namespace/:name',
        name: 'container-pod-detail',
        component: () => import('@/modules/container/views/PodDetailView.vue'),
        props: true,
      },
      {
        path: 'clusters/:clusterId/deployments',
        name: 'container-deployments',
        component: () => import('@/modules/container/views/DeploymentListView.vue'),
        props: true,
      },
      {
        path: 'clusters/:clusterId/deployments/:namespace/:name',
        name: 'container-deployment-detail',
        component: () => import('@/modules/container/views/DeploymentDetailView.vue'),
        props: true,
      },
      {
        path: 'clusters/:clusterId/statefulsets',
        name: 'container-statefulsets',
        component: () => import('@/modules/container/views/StatefulSetListView.vue'),
        props: true,
      },
      {
        path: 'clusters/:clusterId/services',
        name: 'container-services',
        component: () => import('@/modules/container/views/ServiceListView.vue'),
        props: true,
      },
      {
        path: 'clusters/:clusterId/services/:namespace/:name',
        name: 'container-service-detail',
        component: () => import('@/modules/container/views/ServiceDetailView.vue'),
        props: true,
      },
      {
        path: 'clusters/:clusterId/configmaps',
        name: 'container-configmaps',
        component: () => import('@/modules/container/views/ConfigMapListView.vue'),
        props: true,
      },
      {
        path: 'clusters/:clusterId/configmaps/:namespace/:name',
        name: 'container-configmap-detail',
        component: () => import('@/modules/container/views/ConfigMapDetailView.vue'),
        props: true,
      },
      {
        path: 'clusters/:clusterId/secrets',
        name: 'container-secrets',
        component: () => import('@/modules/container/views/SecretListView.vue'),
        props: true,
      },
      {
        path: 'clusters/:clusterId/pvcs',
        name: 'container-pvcs',
        component: () => import('@/modules/container/views/PvcListView.vue'),
        props: true,
      },
      // ─── 镜像仓库 ───
      {
        path: 'registries',
        name: 'container-registries',
        component: () => import('@/modules/container/views/Registry/RegistryListView.vue'),
      },
      {
        path: 'registries/:id',
        name: 'container-registry-detail',
        component: () => import('@/modules/container/views/Registry/RegistryDetailView.vue'),
        props: true,
      },
      {
        path: 'registries/:registryId/projects/:project/repos',
        name: 'container-registry-repo-list',
        component: () => import('@/modules/container/views/Registry/RepoListView.vue'),
        props: true,
      },
      {
        path: 'registries/:registryId/projects/:project/repos/:repo',
        name: 'container-registry-repo-detail',
        component: () => import('@/modules/container/views/Registry/RepoDetailView.vue'),
        props: true,
      },
      // ─── 镜像列表 ───
      {
        path: 'images',
        name: 'container-images',
        component: () => import('@/modules/container/views/Registry/ImageView.vue'),
      },
      // ─── StorageClass ───
      {
        path: 'clusters/:clusterId/storageclasses',
        name: 'container-storageclasses',
        component: () => import('@/modules/container/views/StorageClassListView.vue'),
        props: true,
      },
    ],
  },
]