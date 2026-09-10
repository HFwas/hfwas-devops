import { del, get } from '@/shared/api/request'
import type { PageResult } from '@/shared/types/common'
import type { ArtifactVO, ImageSearchVO, RegistryProjectVO, RegistryRepoVO, ScanOverviewVO, VulnerabilityVO } from '../types/registry'

export const imageApi = {
  /** List projects in a registry. */
  listProjects: (registryId: string) =>
    get<RegistryProjectVO[]>(`/container/registries/${registryId}/projects`),

  /** List repositories (images) in a project. */
  listRepositories: (registryId: string, project: string, page = 1, pageSize = 100) =>
    get<RegistryRepoVO[]>(
      `/container/registries/${registryId}/projects/${encodeURIComponent(project)}/repositories`,
      { page, pageSize }
    ),

  /** List artifacts (tags) in a repository. */
  listArtifacts: (registryId: string, project: string, repo: string, page = 1, pageSize = 100) =>
    get<ArtifactVO[]>(
      `/container/registries/${registryId}/projects/${encodeURIComponent(project)}/repositories/${encodeURIComponent(repo)}/artifacts`,
      { page, pageSize }
    ),

  /** Delete an artifact by digest or tag. */
  deleteArtifact: (registryId: string, project: string, repo: string, reference: string) =>
    del<void>(
      `/container/registries/${registryId}/projects/${encodeURIComponent(project)}/repositories/${encodeURIComponent(repo)}/artifacts/${encodeURIComponent(reference)}`
    ),

  /** Get scan overview for an artifact. */
  getScanOverview: (registryId: string, project: string, repo: string, reference: string) =>
    get<ScanOverviewVO>(
      `/container/registries/${registryId}/projects/${encodeURIComponent(project)}/repositories/${encodeURIComponent(repo)}/artifacts/${encodeURIComponent(reference)}/scan`
    ),

  /** List vulnerabilities from a scan report. */
  getVulnerabilities: (registryId: string, project: string, repo: string, reference: string, reportId: string) =>
    get<VulnerabilityVO[]>(
      `/container/registries/${registryId}/projects/${encodeURIComponent(project)}/repositories/${encodeURIComponent(repo)}/artifacts/${encodeURIComponent(reference)}/scan/${encodeURIComponent(reportId)}/vulnerabilities`
    ),

  /** Search images across all registries. */
  searchImages: (keyword?: string, page?: number, pageSize?: number) =>
    get<PageResult<ImageSearchVO>>('/container/images/search', { keyword, page, pageSize }),
}