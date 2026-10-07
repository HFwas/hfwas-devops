import { useEffect } from 'react'
import { createBrowserRouter, Navigate, Outlet, useLocation } from 'react-router'
import { AppShell } from '@/components/console/AppShell'
import { ContainerShell } from '@/modules/container/components/ContainerShell'
import { ClusterDetailPage } from '@/modules/container/pages/ClusterDetailPage'
import { ClusterListPage } from '@/modules/container/pages/ClusterListPage'
import { ImageSearchPage } from '@/modules/container/pages/ImageSearchPage'
import { NamespaceListPage } from '@/modules/container/pages/NamespaceListPage'
import { ResourceStubPage } from '@/modules/container/pages/ResourceStubPage'
import { RegistryDetailPage, RepoDetailPage, RepoListPage } from '@/modules/container/pages/RegistryDrillPages'
import { RegistryListPage } from '@/modules/container/pages/RegistryListPage'
import {
  ConfigMapListPage,
  DeploymentListPage,
  NodeListPage,
  PodListPage,
  PvcListPage,
  SecretListPage,
  ServiceListPage,
  StatefulSetListPage,
  StorageClassListPage,
} from '@/modules/container/pages/ResourcePages'
import {
  ConfigMapDetailPage,
  DeploymentDetailPage,
  NodeDetailPage,
  PodDetailPage,
  ServiceDetailPage,
  StatefulSetDetailPage,
} from '@/modules/container/pages/WorkloadDetailPages'
import { PipelineListPage } from '@/modules/pipeline/pages/PipelineListPage'
import { ProjectListPage } from '@/pages/ProjectListPage'
import { RouteScreen } from '@/pages/RouteScreen'
import { WorkbenchPage } from '@/pages/WorkbenchPage'
import { appRedirectUri, isAuthenticated, login } from '@/shared/keycloak'
import { useAuthStore } from '@/stores/auth'

function RequireAuth() {
  const location = useLocation()
  const loggedIn = useAuthStore((s) => s.isLoggedIn())

  useEffect(() => {
    if (!loggedIn) {
      void login(appRedirectUri(`${location.pathname}${location.search}`))
    }
  }, [location.pathname, location.search, loggedIn])

  if (!isAuthenticated() && !loggedIn) {
    return <p className="p-6 text-sm text-muted-foreground">正在跳转登录…</p>
  }
  return <Outlet />
}

export const router = createBrowserRouter([
  {
    path: '/',
    element: <RequireAuth />,
    children: [
      {
        element: <AppShell />,
        children: [
          { index: true, element: <Navigate to="/workbench" replace /> },
          { path: 'workbench', element: <WorkbenchPage /> },
          { path: 'pm/projects', element: <ProjectListPage /> },
          { path: 'pipeline/pipelines', element: <PipelineListPage /> },
          {
            path: 'container',
            element: <ContainerShell />,
            children: [
              { index: true, element: <Navigate to="clusters" replace /> },
              { path: 'clusters', element: <ClusterListPage /> },
              { path: 'clusters/:clusterId', element: <ClusterDetailPage /> },
              { path: 'clusters/:clusterId/nodes', element: <NodeListPage /> },
              { path: 'clusters/:clusterId/nodes/:name', element: <NodeDetailPage /> },
              { path: 'clusters/:clusterId/pods', element: <PodListPage /> },
              { path: 'clusters/:clusterId/pods/:namespace/:name', element: <PodDetailPage /> },
              { path: 'clusters/:clusterId/deployments', element: <DeploymentListPage /> },
              { path: 'clusters/:clusterId/deployments/:namespace/:name', element: <DeploymentDetailPage /> },
              { path: 'clusters/:clusterId/statefulsets', element: <StatefulSetListPage /> },
              { path: 'clusters/:clusterId/statefulsets/:namespace/:name', element: <StatefulSetDetailPage /> },
              { path: 'clusters/:clusterId/services', element: <ServiceListPage /> },
              { path: 'clusters/:clusterId/services/:namespace/:name', element: <ServiceDetailPage /> },
              { path: 'clusters/:clusterId/configmaps', element: <ConfigMapListPage /> },
              { path: 'clusters/:clusterId/configmaps/:namespace/:name', element: <ConfigMapDetailPage /> },
              { path: 'clusters/:clusterId/secrets', element: <SecretListPage /> },
              { path: 'clusters/:clusterId/pvcs', element: <PvcListPage /> },
              { path: 'clusters/:clusterId/storageclasses', element: <StorageClassListPage /> },
              { path: 'clusters/:clusterId/daemonsets', element: <ResourceStubPage title="DaemonSet" /> },
              { path: 'clusters/:clusterId/jobs', element: <ResourceStubPage title="Job" /> },
              { path: 'clusters/:clusterId/cronjobs', element: <ResourceStubPage title="CronJob" /> },
              { path: 'clusters/:clusterId/ingresses', element: <ResourceStubPage title="Ingress" /> },
              { path: 'clusters/:clusterId/networkpolicies', element: <ResourceStubPage title="NetworkPolicy" /> },
              { path: 'clusters/:clusterId/pvs', element: <ResourceStubPage title="PV" /> },
              { path: 'clusters/:clusterId/hpa', element: <ResourceStubPage title="HPA" /> },
              { path: 'clusters/:clusterId/pdb', element: <ResourceStubPage title="PDB" /> },
              { path: 'clusters/:clusterId/serviceaccounts', element: <ResourceStubPage title="ServiceAccount" /> },
              { path: 'clusters/:clusterId/roles', element: <ResourceStubPage title="Role" /> },
              { path: 'clusters/:clusterId/rolebindings', element: <ResourceStubPage title="RoleBinding" /> },
              { path: 'clusters/:clusterId/clusterroles', element: <ResourceStubPage title="ClusterRole" /> },
              { path: 'clusters/:clusterId/clusterrolebindings', element: <ResourceStubPage title="ClusterRoleBinding" /> },
              { path: 'clusters/:clusterId/namespaces', element: <NamespaceListPage /> },
              { path: 'clusters/:clusterId/events', element: <ResourceStubPage title="Event" /> },
              { path: 'clusters/:clusterId/crds', element: <ResourceStubPage title="CRD" /> },
              { path: 'clusters/:clusterId/helm', element: <ResourceStubPage title="Helm Releases" /> },
              { path: 'helm/charts', element: <ResourceStubPage title="Helm Charts" /> },
              { path: 'registries', element: <RegistryListPage /> },
              { path: 'registries/:registryId', element: <RegistryDetailPage /> },
              { path: 'registries/:registryId/projects/:project/repos', element: <RepoListPage /> },
              { path: 'registries/:registryId/projects/:project/repos/*', element: <RepoDetailPage /> },
              { path: 'images', element: <ImageSearchPage /> },
            ],
          },
          { path: '*', element: <RouteScreen /> },
        ],
      },
    ],
  },
])
