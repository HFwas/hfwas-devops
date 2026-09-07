import type { ToolchainOption } from '@/modules/pipeline/types/pipeline'

const STACK_LABELS: Record<string, string> = {
  JAVA_MAVEN: 'Java / Maven',
  NODE: 'Node',
  GO: 'Go',
  PYTHON: 'Python',
}

export function stackLabel(stack: string): string {
  return STACK_LABELS[stack] ?? stack
}

export function uniqueStacks(options: ToolchainOption[]): string[] {
  return [...new Set(options.map((item) => item.stack))]
}

export function runtimesFor(options: ToolchainOption[], stack: string): string[] {
  return [...new Set(options.filter((item) => item.stack === stack).map((item) => item.runtimeVersion))]
}

export function toolsFor(options: ToolchainOption[], stack: string, runtime: string): string[] {
  return [
    ...new Set(
      options
        .filter((item) => item.stack === stack && item.runtimeVersion === runtime && item.toolVersion)
        .map((item) => item.toolVersion as string),
    ),
  ]
}

export function findToolchain(
  options: ToolchainOption[],
  stack: string,
  runtime: string,
  tool?: string | null,
): ToolchainOption | undefined {
  const toolValue = tool || null
  return options.find((item) => {
    const itemTool = item.toolVersion || null
    return item.stack === stack && item.runtimeVersion === runtime && itemTool === toolValue
  })
}

export function firstToolchain(options: ToolchainOption[], stack?: string): ToolchainOption | undefined {
  if (!options.length) return undefined
  if (!stack) return options[0]
  return options.find((item) => item.stack === stack) ?? options[0]
}

export function coerceToolchain(
  options: ToolchainOption[],
  stack: string,
  runtime: string,
  tool?: string | null,
): ToolchainOption | undefined {
  const exact = findToolchain(options, stack, runtime, tool)
  if (exact) return exact
  const runtimes = runtimesFor(options, stack)
  const nextRuntime = runtimes.includes(runtime) ? runtime : runtimes[0]
  if (!nextRuntime) return firstToolchain(options, stack)
  const tools = toolsFor(options, stack, nextRuntime)
  const nextTool = tools.includes(tool ?? '') ? tool : tools[0] ?? null
  return findToolchain(options, stack, nextRuntime, nextTool)
}
