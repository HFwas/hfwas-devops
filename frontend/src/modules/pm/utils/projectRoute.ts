/** Snowflake project id from `/pm/projects/:projectId` routes. */
export function resolveRouteProjectId(projectId: string | undefined): string | null {
  if (typeof projectId !== 'string' || !/^\d+$/.test(projectId)) {
    return null
  }
  return projectId
}
