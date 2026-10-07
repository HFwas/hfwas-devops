export function helmChartPath(repositoryId: string, name: string, version?: string) {
  const path = `/container/helm/charts/${encodeURIComponent(repositoryId)}/${encodeURIComponent(name)}`
  return version ? `${path}?version=${encodeURIComponent(version)}` : path
}

export function helmReleasePath(namespace: string, name: string) {
  return `/container/helm/releases/${encodeURIComponent(namespace)}/${encodeURIComponent(name)}`
}
