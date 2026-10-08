import { useEffect } from 'react'
import { createBrowserRouter, Navigate, Outlet, useLocation } from 'react-router'
import { AppShell } from '@/components/console/AppShell'
import { ContainerShell } from '@/modules/container/components/ContainerShell'
import { ClusterDetailPage } from '@/modules/container/pages/ClusterDetailPage'
import { ClusterListPage } from '@/modules/container/pages/ClusterListPage'
import { HelmChartDetailPage } from '@/modules/container/pages/HelmChartDetailPage'
import { HelmChartListPage } from '@/modules/container/pages/HelmChartListPage'
import { HelmReleaseDetailPage } from '@/modules/container/pages/HelmReleaseDetailPage'
import { HelmReleaseListPage } from '@/modules/container/pages/HelmReleaseListPage'
import { HelmUploadPage } from '@/modules/container/pages/HelmUploadPage'
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
import { CollectionDetailPage } from '@/modules/api-test/pages/CollectionDetailPage'
import { CollectionListPage } from '@/modules/api-test/pages/CollectionListPage'
import { DefinitionDetailPage } from '@/modules/api-test/pages/DefinitionDetailPage'
import { DefinitionListPage } from '@/modules/api-test/pages/DefinitionListPage'
import { EnvironmentDetailPage } from '@/modules/api-test/pages/EnvironmentDetailPage'
import { EnvironmentListPage } from '@/modules/api-test/pages/EnvironmentListPage'
import { DocgenPage } from '@/modules/docgen/pages/DocgenPage'
import { FileParserPage } from '@/modules/file-parser/pages/FileParserPage'
import { ImageToolPage } from '@/modules/image/pages/ImageToolPage'
import { CredentialDetailPage } from '@/modules/pipeline/pages/CredentialDetailPage'
import { CredentialListPage } from '@/modules/pipeline/pages/CredentialListPage'
import { PipelineListPage } from '@/modules/pipeline/pages/PipelineListPage'
import { PipelineRunDetailPage } from '@/modules/pipeline/pages/PipelineRunDetailPage'
import { PipelineRunsPage } from '@/modules/pipeline/pages/PipelineRunsPage'
import { TaskKindDetailPage } from '@/modules/pipeline/pages/TaskKindDetailPage'
import { TaskKindListPage } from '@/modules/pipeline/pages/TaskKindListPage'
import { BoardPage } from '@/modules/pm/pages/BoardPage'
import { ProjectMonitorPage } from '@/modules/pm/pages/ProjectMonitorPage'
import { WorkItemDetailPage } from '@/modules/pm/pages/WorkItemDetailPage'
import { WorkItemListPage } from '@/modules/pm/pages/WorkItemListPage'
import { WorkflowPage } from '@/modules/pm/pages/WorkflowPage'
import { UserListPage } from '@/modules/user/pages/UserListPage'
import { UserSettingsPage } from '@/modules/user/pages/UserSettingsPage'
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
          { path: 'user/accounts', element: <UserListPage /> },
          { path: 'user/settings', element: <UserSettingsPage /> },
          { path: 'pm/projects', element: <ProjectListPage /> },
          { path: 'pm/monitor', element: <ProjectMonitorPage /> },
          { path: 'pm/projects/:projectId/items/:typeCode', element: <WorkItemListPage /> },
          { path: 'pm/projects/:projectId/items/:typeCode/:itemId', element: <WorkItemDetailPage /> },
          { path: 'pm/projects/:projectId/board/:typeCode', element: <BoardPage /> },
          { path: 'pm/projects/:projectId/settings/workflow/:typeCode', element: <WorkflowPage /> },
          { path: 'pipeline/pipelines', element: <PipelineListPage /> },
          { path: 'pipeline/pipelines/:pipelineId', element: <PipelineRunsPage /> },
          { path: 'pipeline/pipelines/:pipelineId/runs/:runId', element: <PipelineRunDetailPage /> },
          { path: 'pipeline/credentials', element: <CredentialListPage /> },
          { path: 'pipeline/credentials/:credentialId', element: <CredentialDetailPage /> },
          { path: 'pipeline/task-kinds', element: <TaskKindListPage /> },
          { path: 'pipeline/task-kinds/:kind', element: <TaskKindDetailPage /> },
          { path: 'api-test', element: <DefinitionListPage /> },
          { path: 'api-test/definitions', element: <DefinitionListPage /> },
          { path: 'api-test/definitions/:definitionId', element: <DefinitionDetailPage /> },
          { path: 'api-test/collections', element: <CollectionListPage /> },
          { path: 'api-test/collections/:collectionId', element: <CollectionDetailPage /> },
          { path: 'api-test/environments', element: <EnvironmentListPage /> },
          { path: 'api-test/environments/:environmentId', element: <EnvironmentDetailPage /> },
          { path: 'file-parser', element: <FileParserPage /> },
          { path: 'docgen', element: <DocgenPage /> },
          { path: 'image', element: <ImageToolPage /> },
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
              { path: 'registries', element: <RegistryListPage /> },
              { path: 'registries/:registryId', element: <RegistryDetailPage /> },
              { path: 'registries/:registryId/projects/:project/repos', element: <RepoListPage /> },
              { path: 'registries/:registryId/projects/:project/repos/*', element: <RepoDetailPage /> },
              { path: 'images', element: <ImageSearchPage /> },
              { path: 'helm', element: <Navigate to="/container/helm/releases" replace /> },
              { path: 'helm/releases', element: <HelmReleaseListPage /> },
              { path: 'helm/releases/:namespace/:name', element: <HelmReleaseDetailPage /> },
              { path: 'helm/charts', element: <HelmChartListPage /> },
              { path: 'helm/charts/:repositoryId/:name', element: <HelmChartDetailPage /> },
              { path: 'helm/upload', element: <HelmUploadPage /> },
            ],
          },
          { path: '*', element: <RouteScreen /> },
        ],
      },
    ],
  },
])
