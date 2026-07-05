import type { RouteLocationNormalized } from 'vue-router'

/** Snowflake project id from `/pm/projects/:projectId` routes. */
export function resolveRouteProjectId(to: RouteLocationNormalized): string | null {
  const id = to.params.projectId
  if (typeof id !== 'string' || !/^\d+$/.test(id)) {
    return null
  }
  return id
}
