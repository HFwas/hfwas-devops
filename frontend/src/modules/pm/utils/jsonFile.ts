/** Download JSON object as a file in the browser. */
export function downloadJsonFile(data: unknown, filename: string) {
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

export async function readJsonFile<T>(file: File): Promise<T> {
  const text = await file.text()
  return JSON.parse(text) as T
}

export function schemeExportFilename(typeCode: string) {
  const date = new Date().toISOString().slice(0, 10)
  return `issue-type-scheme-${typeCode}-${date}.json`
}

export function projectSchemeExportFilename(projectCode = 'project') {
  const date = new Date().toISOString().slice(0, 10)
  return `issue-type-schemes-${projectCode}-${date}.json`
}
