import type { EnvironmentVariableDTO, EnvironmentVariableItemVO } from '@/modules/api-test/environment/types/environment'

/** Backend replace-all cannot treat missing items as unchanged, so blanked secrets must keep the previous value. */
export function resolveVariablesForUpdate(
  uiVariables: EnvironmentVariableDTO[],
  previousVariables: EnvironmentVariableItemVO[] = [],
): EnvironmentVariableDTO[] {
  return uiVariables.map((variable) => {
    if (!variable.isSecret) return { ...variable }
    if ((variable.value ?? '') !== '') return { ...variable }
    const previous = previousVariables.find((item) =>
      variable.id != null ? item.id === variable.id : item.name === variable.name,
    )
    if (previous && previous.value !== undefined && previous.value !== '') {
      return { ...variable, value: previous.value }
    }
    return { ...variable }
  })
}
